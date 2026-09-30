package me.aliahad.audioplayer

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** A bookmark being created: everything is captured at tap time, so a track change while the dialog is open cannot re-target it. */
data class BookmarkDraft(
    val positionMs: Long,
    val audioFileUri: String,
    val folderUri: String,
    val trackTitle: String
)

data class PlayerUiState(
    val folderUri: Uri? = null,
    val tracks: List<AudioTrack> = emptyList(),
    val currentTrackIndex: Int = -1,
    val isPlaying: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val currentPosition: Long = 0L,
    val bufferedPosition: Long = 0L,
    val duration: Long = 0L,
    val isShuffleEnabled: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val playbackSpeed: Float = 1f,
    val isNightMode: Boolean = true,
    val timestamps: List<TimestampBookmark> = emptyList(),
    val bookmarkDraft: BookmarkDraft? = null
) {
    val bookmarkDialogPositionMs: Long? get() = bookmarkDraft?.positionMs
}

/**
 * UI-facing state holder. Playback itself lives in [PlaybackService]; this ViewModel drives it
 * through a [MediaController], so closing or recreating the screen never interrupts or rewinds audio.
 */
class AudioPlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application
    private val preferences = PlayerPreferences(application)
    private val scanner = TrackScanner(application)
    private val timestampDao = TimestampDatabase.getInstance(application).timestampDao()

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private val controllerFuture: ListenableFuture<MediaController>
    private val connectedController = CompletableDeferred<MediaController>()
    private var controller: MediaController? = null
    private var progressJob: Job? = null
    private var loadJob: Job? = null

    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            syncFromPlayer()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) {
                // The error message is kept here: after an automatic skip the next track starts playing,
                // and the user should still see which file was skipped. User actions clear it instead.
                startProgressUpdates()
            } else {
                stopProgressUpdates()
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            val title = controller?.currentMediaItem?.mediaMetadata?.title ?: ""
            _uiState.update { it.copy(errorMessage = app.getString(R.string.error_playback_failed, title)) }
        }
    }

    init {
        val token = SessionToken(application, ComponentName(application, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(application, token).buildAsync()
        controllerFuture.addListener({
            val connected = runCatching { controllerFuture.get() }.getOrElse { error ->
                Log.e(TAG, "Could not connect to PlaybackService", error)
                _uiState.update { it.copy(isLoading = false, errorMessage = app.getString(R.string.error_player_unavailable)) }
                return@addListener
            }
            controller = connected
            connected.addListener(playerListener)
            syncFromPlayer()
            if (connected.isPlaying) startProgressUpdates()
            connectedController.complete(connected)
        }, ContextCompat.getMainExecutor(application))

        viewModelScope.launch { restoreSession() }
        observeBookmarksForCurrentTrack()
    }

    fun toggleTheme() {
        val newValue = !_uiState.value.isNightMode
        _uiState.update { it.copy(isNightMode = newValue) }
        viewModelScope.launch {
            runCatching { preferences.saveThemeMode(newValue) }
                .onFailure { Log.w(TAG, "Failed to persist theme preference", it) }
        }
    }

    fun onFolderSelected(folderUri: Uri) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val tracks = scanner.scan(folderUri)
            if (tracks.isEmpty()) {
                // Keep whatever was playing; just report the empty pick and drop its grant.
                if (folderUri != _uiState.value.folderUri) releaseFolderGrant(folderUri)
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = app.getString(R.string.error_no_audio_in_folder))
                }
                return@launch
            }
            val player = connectedController.await()
            preferences.saveFolder(folderUri)
            releaseFolderGrantsExcept(folderUri)
            _uiState.update { it.copy(folderUri = folderUri, tracks = tracks, isLoading = false) }
            loadQueue(player, tracks, startIndex = 0, startPositionMs = 0L, playWhenReady = false)
        }
    }

    fun togglePlayPause() {
        val player = controller ?: return
        if (player.isPlaying) player.pause() else startPlayback(player)
    }

    fun playNext() {
        val player = controller ?: return
        if (player.hasNextMediaItem()) {
            player.seekToNextMediaItem()
            startPlayback(player)
        }
    }

    fun playPrevious() {
        val player = controller ?: return
        if (player.mediaItemCount == 0) return
        // Restarts the current track when past the first few seconds, otherwise goes back one track.
        player.seekToPrevious()
        startPlayback(player)
    }

    fun stopPlayback() {
        val player = controller ?: return
        player.pause()
        if (player.mediaItemCount > 0) player.seekTo(player.currentMediaItemIndex, 0L)
    }

    fun selectTrack(index: Int) {
        val player = controller ?: return
        if (index !in 0 until player.mediaItemCount) return
        player.seekTo(index, 0L)
        startPlayback(player)
    }

    fun toggleShuffle() {
        controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled }
    }

    fun cycleRepeatMode() {
        val player = controller ?: return
        player.repeatMode = when (player.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun cyclePlaybackSpeed() {
        val player = controller ?: return
        val currentSpeed = player.playbackParameters.speed
        val nextSpeed = SPEED_PRESETS.firstOrNull { it > currentSpeed + SPEED_EPSILON } ?: SPEED_PRESETS.first()
        player.setPlaybackSpeed(nextSpeed)
    }

    fun seekTo(positionMs: Long) {
        val player = controller ?: return
        val duration = player.duration.takeIf { it != C.TIME_UNSET && it >= 0 } ?: Long.MAX_VALUE
        player.seekTo(positionMs.coerceIn(0L, duration))
        updateProgress()
    }

    fun onBookmarkTap() {
        val state = _uiState.value
        val track = state.tracks.getOrNull(state.currentTrackIndex) ?: return
        val folderUri = state.folderUri ?: return
        val positionMs = controller?.currentPosition?.coerceAtLeast(0L) ?: state.currentPosition
        _uiState.update {
            it.copy(
                bookmarkDraft = BookmarkDraft(
                    positionMs = positionMs,
                    audioFileUri = track.uri.toString(),
                    folderUri = folderUri.toString(),
                    trackTitle = track.title
                )
            )
        }
    }

    fun saveBookmark(note: String?) {
        val draft = _uiState.value.bookmarkDraft ?: return
        _uiState.update { it.copy(bookmarkDraft = null) }
        viewModelScope.launch {
            timestampDao.insert(
                TimestampBookmark(
                    audioFileUri = draft.audioFileUri,
                    folderUri = draft.folderUri,
                    positionMs = draft.positionMs,
                    note = note?.trim()?.takeIf { it.isNotEmpty() }
                )
            )
        }
    }

    fun dismissBookmarkDialog() {
        _uiState.update { it.copy(bookmarkDraft = null) }
    }

    fun deleteBookmark(id: Long) {
        viewModelScope.launch { timestampDao.deleteById(id) }
    }

    fun seekToTimestamp(positionMs: Long) {
        val player = controller ?: return
        player.seekTo(positionMs.coerceAtLeast(0L))
        if (!player.isPlaying) startPlayback(player)
        updateProgress()
    }

    override fun onCleared() {
        stopProgressUpdates()
        controller?.removeListener(playerListener)
        controller = null
        MediaController.releaseFuture(controllerFuture)
        super.onCleared()
    }

    /**
     * Restores the saved folder. When the service is still holding that folder's queue (the app was
     * swiped away and reopened mid-playback) the live queue is adopted as-is instead of being rebuilt.
     */
    private suspend fun restoreSession() {
        val saved = runCatching { preferences.getPreferences() }.getOrNull() ?: PlayerPreferencesData()
        _uiState.update { it.copy(isNightMode = saved.isNightMode) }
        val folderUri = saved.folderUri?.let { runCatching { Uri.parse(it) }.getOrNull() } ?: return

        _uiState.update { it.copy(folderUri = folderUri, isLoading = true, errorMessage = null) }
        val tracks = scanner.scan(folderUri)
        val player = connectedController.await()

        if (tracks.isEmpty()) {
            _uiState.update {
                it.copy(isLoading = false, errorMessage = app.getString(R.string.error_saved_folder_empty))
            }
            return
        }
        _uiState.update { it.copy(tracks = tracks, isLoading = false) }

        when {
            queueMatches(player, tracks) -> syncFromPlayer()
            player.mediaItemCount > 0 -> {
                // Folder contents changed while the service kept playing: rebuild, but stay on the same track.
                val currentId = player.currentMediaItem?.mediaId
                val index = tracks.indexOfFirst { it.uri.toString() == currentId }
                if (index >= 0) {
                    loadQueue(player, tracks, index, player.currentPosition, player.playWhenReady)
                } else {
                    loadQueue(player, tracks, 0, 0L, playWhenReady = false)
                }
            }
            else -> {
                val index = saved.currentTrackUri?.let { uri -> tracks.indexOfFirst { it.uri.toString() == uri } } ?: -1
                player.shuffleModeEnabled = saved.shuffleEnabled
                player.repeatMode = saved.repeatMode
                if (saved.playbackSpeed > 0f) player.setPlaybackSpeed(saved.playbackSpeed)
                if (index >= 0) {
                    loadQueue(player, tracks, index, saved.positionMs, playWhenReady = false)
                } else {
                    loadQueue(player, tracks, 0, 0L, playWhenReady = false)
                }
            }
        }
    }

    private fun queueMatches(player: Player, tracks: List<AudioTrack>): Boolean {
        if (player.mediaItemCount != tracks.size) return false
        return tracks.indices.all { player.getMediaItemAt(it).mediaId == tracks[it].uri.toString() }
    }

    @androidx.annotation.OptIn(UnstableApi::class)
    private fun loadQueue(
        player: Player,
        tracks: List<AudioTrack>,
        startIndex: Int,
        startPositionMs: Long,
        playWhenReady: Boolean
    ) {
        val items = tracks.map { track ->
            MediaItem.Builder()
                .setMediaId(track.uri.toString())
                .setUri(track.uri)
                .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(track.uri).build())
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(track.title)
                        .setArtist(track.artist)
                        .setAlbumTitle(track.album)
                        .apply { track.durationMs?.let { setDurationMs(it) } }
                        .build()
                )
                .build()
        }
        player.setMediaItems(items, startIndex.coerceIn(0, items.lastIndex), startPositionMs.coerceAtLeast(0L))
        player.playWhenReady = playWhenReady
        player.prepare()
        syncFromPlayer()
    }

    /** Starts playback from whatever state the player is in, including after an error or the end of the queue. */
    private fun startPlayback(player: Player) {
        if (player.mediaItemCount == 0) return
        _uiState.update { it.copy(errorMessage = null) }
        when (player.playbackState) {
            Player.STATE_IDLE -> player.prepare()
            Player.STATE_ENDED -> {
                val first = player.currentTimeline.getFirstWindowIndex(player.shuffleModeEnabled)
                player.seekTo(if (first == C.INDEX_UNSET) 0 else first, 0L)
            }
        }
        player.play()
    }

    private fun syncFromPlayer() {
        val player = controller ?: return
        val tracks = _uiState.value.tracks
        val currentId = player.currentMediaItem?.mediaId
        val queueIndex = player.currentMediaItemIndex
        val trackIndex = when {
            currentId == null -> -1
            tracks.getOrNull(queueIndex)?.uri?.toString() == currentId -> queueIndex
            else -> tracks.indexOfFirst { it.uri.toString() == currentId }
        }
        _uiState.update {
            it.copy(
                currentTrackIndex = trackIndex,
                isPlaying = player.isPlaying,
                isShuffleEnabled = player.shuffleModeEnabled,
                repeatMode = player.repeatMode,
                playbackSpeed = player.playbackParameters.speed
            )
        }
        updateProgress()
    }

    private fun updateProgress() {
        val player = controller ?: return
        val duration = player.duration.takeIf { it != C.TIME_UNSET && it >= 0 } ?: 0L
        val position = player.currentPosition.coerceAtLeast(0L)
        val buffered = player.bufferedPosition.coerceAtLeast(0L)
        _uiState.update {
            it.copy(currentPosition = position, bufferedPosition = buffered, duration = maxOf(duration, position))
        }
    }

    private fun startProgressUpdates() {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            while (isActive) {
                updateProgress()
                delay(PROGRESS_UPDATE_INTERVAL_MS)
            }
        }
    }

    private fun stopProgressUpdates() {
        progressJob?.cancel()
        progressJob = null
        updateProgress()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeBookmarksForCurrentTrack() {
        viewModelScope.launch {
            _uiState
                .map { state ->
                    val track = state.tracks.getOrNull(state.currentTrackIndex)
                    val folder = state.folderUri
                    if (track != null && folder != null) track.uri.toString() to folder.toString() else null
                }
                .distinctUntilChanged()
                .flatMapLatest { key ->
                    if (key != null) timestampDao.getBookmarksForTrack(key.first, key.second) else flowOf(emptyList())
                }
                .collectLatest { bookmarks -> _uiState.update { it.copy(timestamps = bookmarks) } }
        }
    }

    /** Android caps persisted grants per app; drop the ones for folders the user has moved away from. */
    private fun releaseFolderGrantsExcept(keep: Uri) {
        app.contentResolver.persistedUriPermissions
            .filter { it.uri != keep }
            .forEach { releaseFolderGrant(it.uri) }
    }

    private fun releaseFolderGrant(uri: Uri) {
        runCatching {
            app.contentResolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private companion object {
        const val TAG = "AudioPlayerViewModel"
        const val PROGRESS_UPDATE_INTERVAL_MS = 500L
        val SPEED_PRESETS = listOf(0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
        const val SPEED_EPSILON = 0.05f
    }
}
