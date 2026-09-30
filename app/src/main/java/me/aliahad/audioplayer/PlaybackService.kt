package me.aliahad.audioplayer

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Owns the ExoPlayer and MediaSession for the whole app.
 *
 * Playback, the media notification, the foreground-service lifecycle and playback-state
 * persistence all live here so they keep working after the Activity (and its ViewModel) is gone.
 * The UI talks to this service through a [androidx.media3.session.MediaController].
 */
@androidx.annotation.OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private lateinit var player: ExoPlayer
    private lateinit var preferences: PlayerPreferences

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var persistJob: Job? = null
    private var consecutiveErrors = 0

    override fun onCreate() {
        super.onCreate()
        preferences = PlayerPreferences(applicationContext)

        player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
        player.addListener(playerListener)

        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        mediaSession = MediaSession.Builder(this, player)
            .setCallback(SessionCallback)
            .setSessionActivity(openAppIntent)
            .build()

        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .setChannelId(NOTIFICATION_CHANNEL_ID)
                .setChannelName(R.string.notification_channel_name)
                .build()
                .apply { setSmallIcon(R.drawable.ic_stat_soundvault) }
        )
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        persistPlaybackState()
        // Keep playing in the background when the user swipes the app away mid-playback;
        // otherwise there is nothing to keep the service alive for.
        if (!player.playWhenReady || player.mediaItemCount == 0 || player.playbackState == Player.STATE_ENDED) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        persistPlaybackState()
        persistJob?.cancel()
        mediaSession?.run {
            player.removeListener(playerListener)
            player.release()
            release()
        }
        mediaSession = null
        serviceScope.cancel()
        super.onDestroy()
    }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) {
                consecutiveErrors = 0
                startPeriodicPersistence()
            } else {
                persistJob?.cancel()
                persistPlaybackState()
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            // A playlist replacement is persisted by whoever set it; everything else is a real move.
            if (reason != Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) {
                persistPlaybackState()
            }
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) = persistPlaybackState()

        override fun onRepeatModeChanged(repeatMode: Int) = persistPlaybackState()

        override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) = persistPlaybackState()

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) {
            // Seeks while paused (Stop, scrubbing, bookmark jumps) would otherwise not be saved until the next pause.
            if (reason == Player.DISCONTINUITY_REASON_SEEK && !player.isPlaying) persistPlaybackState()
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.w(TAG, "Playback failed for ${player.currentMediaItem?.mediaId}", error)
            consecutiveErrors++
            // Skip unplayable files, but give up once every item in the queue has failed in a row
            // so a folder full of broken files cannot spin forever with repeat enabled.
            if (consecutiveErrors < player.mediaItemCount && player.hasNextMediaItem()) {
                // Recover on a later main-loop message, not re-entrantly inside the error callback: the
                // session must first publish the error state, or connected controllers never see the
                // error and can miss the skip to the next track (UI stuck on "Ready to play").
                // Dispatchers.Main always posts, unlike serviceScope's Main.immediate.
                serviceScope.launch(Dispatchers.Main) {
                    if (player.playerError != null && player.hasNextMediaItem()) {
                        player.seekToNextMediaItem()
                        player.prepare()
                    }
                }
            }
        }
    }

    private fun startPeriodicPersistence() {
        persistJob?.cancel()
        persistJob = serviceScope.launch {
            while (isActive) {
                delay(PERSIST_INTERVAL_MS)
                persistPlaybackState()
            }
        }
    }

    private fun persistPlaybackState() {
        if (!::player.isInitialized || player.mediaItemCount == 0) return
        val snapshot = PlaybackSnapshot(
            trackUri = player.currentMediaItem?.mediaId,
            positionMs = player.currentPosition.coerceAtLeast(0L),
            shuffleEnabled = player.shuffleModeEnabled,
            repeatMode = player.repeatMode,
            playbackSpeed = player.playbackParameters.speed
        )
        // Written from an app-lifetime scope so a write started in onDestroy is not cancelled with the service.
        AppScope.io.launch {
            runCatching { preferences.savePlaybackState(snapshot) }
                .onFailure { Log.w(TAG, "Failed to persist playback state", it) }
        }
    }

    /**
     * Controllers send MediaItems without their playback URI (it is stripped when items cross the
     * session boundary), so rebuild it from the request metadata / media id.
     */
    private object SessionCallback : MediaSession.Callback {
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>
        ): ListenableFuture<MutableList<MediaItem>> {
            val resolved = mediaItems.map { item ->
                if (item.localConfiguration != null) {
                    item
                } else {
                    item.buildUpon()
                        .setUri(item.requestMetadata.mediaUri ?: Uri.parse(item.mediaId))
                        .build()
                }
            }.toMutableList()
            return Futures.immediateFuture(resolved)
        }
    }

    companion object {
        private const val TAG = "PlaybackService"
        private const val NOTIFICATION_CHANNEL_ID = "audio_playback_channel"
        private const val PERSIST_INTERVAL_MS = 5_000L
    }
}

/** Process-lifetime scope for fire-and-forget persistence that must outlive a ViewModel or service. */
object AppScope {
    val io: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
}
