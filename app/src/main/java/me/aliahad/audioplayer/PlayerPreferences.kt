package me.aliahad.audioplayer

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.media3.common.Player
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.playerPreferencesDataStore by preferencesDataStore(name = "player_preferences")

data class PlayerPreferencesData(
    val folderUri: String? = null,
    val currentTrackUri: String? = null,
    val positionMs: Long = 0L,
    val shuffleEnabled: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val playbackSpeed: Float = 1f,
    val isNightMode: Boolean = true
)

/** Playback state owned and written by [PlaybackService]. */
data class PlaybackSnapshot(
    val trackUri: String?,
    val positionMs: Long,
    val shuffleEnabled: Boolean,
    val repeatMode: Int,
    val playbackSpeed: Float
)

class PlayerPreferences(context: Context) {

    private val dataStore = context.applicationContext.playerPreferencesDataStore

    private val preferencesFlow: Flow<PlayerPreferencesData> = dataStore.data.map { preferences ->
        PlayerPreferencesData(
            folderUri = preferences[FOLDER_URI_KEY],
            currentTrackUri = preferences[CURRENT_TRACK_URI_KEY],
            positionMs = preferences[POSITION_MS_KEY] ?: 0L,
            shuffleEnabled = preferences[SHUFFLE_ENABLED_KEY] ?: false,
            repeatMode = preferences[REPEAT_MODE_KEY] ?: Player.REPEAT_MODE_OFF,
            playbackSpeed = preferences[PLAYBACK_SPEED_KEY] ?: 1f,
            isNightMode = preferences[IS_NIGHT_MODE_KEY] ?: true
        )
    }

    suspend fun getPreferences(): PlayerPreferencesData = preferencesFlow.first()

    /**
     * Skip step sizes, observed by both the UI and [PlaybackService]. Unknown stored values fall back to
     * the default, and a read error emits the defaults instead of failing the collector.
     */
    val skipIntervals: Flow<SkipIntervals> = dataStore.data
        .map { preferences ->
            SkipIntervals(
                backMs = sanitizeSkipInterval(preferences[SKIP_BACK_MS_KEY]),
                forwardMs = sanitizeSkipInterval(preferences[SKIP_FORWARD_MS_KEY])
            )
        }
        .catch { error ->
            if (error !is IOException) throw error
            emit(SkipIntervals())
        }
        .distinctUntilChanged()

    suspend fun saveSkipBackInterval(intervalMs: Long) {
        dataStore.edit { preferences -> preferences[SKIP_BACK_MS_KEY] = sanitizeSkipInterval(intervalMs) }
    }

    suspend fun saveSkipForwardInterval(intervalMs: Long) {
        dataStore.edit { preferences -> preferences[SKIP_FORWARD_MS_KEY] = sanitizeSkipInterval(intervalMs) }
    }

    /** Saves the selected folder and resets the track/position so a new folder starts at its top. */
    suspend fun saveFolder(folderUri: Uri) {
        dataStore.edit { preferences ->
            preferences[FOLDER_URI_KEY] = folderUri.toString()
            preferences.remove(CURRENT_TRACK_URI_KEY)
            preferences[POSITION_MS_KEY] = 0L
        }
    }

    suspend fun savePlaybackState(snapshot: PlaybackSnapshot) {
        dataStore.edit { preferences ->
            if (snapshot.trackUri == null) {
                preferences.remove(CURRENT_TRACK_URI_KEY)
            } else {
                preferences[CURRENT_TRACK_URI_KEY] = snapshot.trackUri
            }
            preferences[POSITION_MS_KEY] = snapshot.positionMs
            preferences[SHUFFLE_ENABLED_KEY] = snapshot.shuffleEnabled
            preferences[REPEAT_MODE_KEY] = snapshot.repeatMode
            preferences[PLAYBACK_SPEED_KEY] = snapshot.playbackSpeed
        }
    }

    suspend fun clearPlaybackState() {
        dataStore.edit { preferences ->
            preferences.remove(FOLDER_URI_KEY)
            preferences.remove(CURRENT_TRACK_URI_KEY)
            preferences.remove(POSITION_MS_KEY)
            preferences.remove(SHUFFLE_ENABLED_KEY)
            preferences.remove(REPEAT_MODE_KEY)
            preferences.remove(PLAYBACK_SPEED_KEY)
        }
    }

    suspend fun saveThemeMode(isNightMode: Boolean) {
        dataStore.edit { preferences ->
            preferences[IS_NIGHT_MODE_KEY] = isNightMode
        }
    }

    private companion object {
        val FOLDER_URI_KEY = stringPreferencesKey("folder_uri")
        val CURRENT_TRACK_URI_KEY = stringPreferencesKey("current_track_uri")
        val POSITION_MS_KEY = longPreferencesKey("position_ms")
        val SHUFFLE_ENABLED_KEY = booleanPreferencesKey("shuffle_enabled")
        val REPEAT_MODE_KEY = intPreferencesKey("repeat_mode")
        val PLAYBACK_SPEED_KEY = floatPreferencesKey("playback_speed")
        val IS_NIGHT_MODE_KEY = booleanPreferencesKey("is_night_mode")
        val SKIP_BACK_MS_KEY = longPreferencesKey("skip_back_ms")
        val SKIP_FORWARD_MS_KEY = longPreferencesKey("skip_forward_ms")
    }
}
