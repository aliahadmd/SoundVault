# Project Structure

Single-module Android app. All source lives under `app/src/main/java/me/aliahad/audioplayer/`.

```
app/src/main/java/me/aliahad/audioplayer/
├── MainActivity.kt          # Activity + all Compose UI (composables are private functions in this file)
├── AudioPlayerViewModel.kt  # UI state (PlayerUiState, BookmarkDraft); drives playback through a MediaController
├── PlaybackService.kt       # MediaSessionService: owns ExoPlayer + MediaSession, notification, audio focus,
│                            #   playback-state persistence, skipping unplayable files; AppScope
├── TrackScanner.kt          # AudioTrack + SAF folder scan (DocumentsContract queries, parallel tag reading)
├── LibraryRules.kt          # Pure rules: isPlayableAudio, NaturalOrderComparator, formatPlaybackSpeed
├── PlayerPreferences.kt     # DataStore wrapper: folder, playback snapshot, theme
├── TimestampBookmark.kt     # Room entity, DAO and database for bookmarks
├── TimestampFormatter.kt    # formatTimestamp / parseTimestamp
└── ui/theme/
    ├── Color.kt             # Light and Night palettes
    ├── Theme.kt             # AudioplayerTheme(isNightMode)
    └── Type.kt              # Typography
```

## Architecture Notes
- `PlaybackService` is the single owner of the player. Playback, the foreground notification (kept while paused),
  audio focus / becoming-noisy handling and state persistence (every 5 s while playing, on pause, seek, track change,
  task removal and destroy) all live there, so they keep working when the Activity is gone.
- `AudioPlayerViewModel` connects with `MediaController` and never owns playback. On start it rescans the saved folder;
  if the service already holds that queue (app reopened mid-playback) it adopts it instead of rebuilding.
- MediaItems carry the document URI as `mediaId` and `requestMetadata.mediaUri`; the session callback restores the
  playback URI in `onAddMediaItems`.
- Folder scanning uses `DocumentsContract` (one query per directory) and `MediaMetadataRetriever` (4 in parallel) on
  `Dispatchers.IO`. Tracks are ordered naturally by path relative to the chosen folder.
- Bookmarks are keyed by (document URI, tree URI). The track is captured when the bookmark button is tapped.
- No DI framework; `TimestampDatabase.getInstance()` and `PlayerPreferences(context)` are constructed directly.

## Resources
- `res/values/strings.xml` – all user-facing text (UI, content descriptions, errors, notification channel)
- `res/values/themes.xml`, `colors.xml` – window theme with a Night-colored background (no launch flash)
- `res/drawable/ic_stat_soundvault.xml` – monochrome notification icon
- `res/drawable/ic_launcher_*` – adaptive launcher icon

## Tests
- `app/src/test` – kotest unit/property tests (JUnit Platform; plain JUnit 4 tests do not run here)
- `app/src/androidTest` – instrumented Room DAO tests (`TimestampDaoTest`)
