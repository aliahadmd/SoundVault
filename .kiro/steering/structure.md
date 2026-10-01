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
├── SkipIntervals.kt         # Pure rules: skip step options, sanitizing, target position, labels
├── EqualizerRules.kt        # Pure rules: EqualizerSettings, 16 presets, sanitizing, RBJ biquad coefficients, pre-gain
├── EqualizerAudioProcessor.kt # Media3 AudioProcessor: cascaded biquads per channel, limiter, bypass crossfade
├── EqualizerRenderersFactory.kt # DefaultRenderersFactory that puts the processor into DefaultAudioSink
├── EqualizerSheet.kt        # Compose bottom sheet: switch, bass boost card, loudness, presets, 10 band sliders
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
- The session exposes a `ForwardingPlayer` (`SkipIntervalPlayer`) whose `seekBack`/`seekForward` use the step sizes
  from `PlayerPreferences.skipIntervals`, so headset keys, Wear OS / Auto and the notification's custom skip buttons
  (custom `SessionCommand`s, re-published on every settings change) all agree. The UI seeks by explicit offsets instead
  of `MediaController.seekBack()`, whose position masking uses a stale cached increment.
- The equalizer runs in-app, not through `android.media.audiofx`: `PlaybackService` builds ExoPlayer with
  `EqualizerRenderersFactory`, which puts one `EqualizerAudioProcessor` into `DefaultAudioSink`. The service collects
  `PlayerPreferences.equalizer` and hands each value to the processor (`setSettings`, volatile swap, applied on the
  audio thread). It handles PCM16 and float, bypasses when off or flat, crossfades on/off, and caps output with
  automatic pre-gain plus a peak limiter. The ViewModel only writes preferences, so the sound follows them even
  without the Activity.
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
