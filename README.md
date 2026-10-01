# SoundVault for Android

A polished audio playback app tailored for my personal library. I wanted something modern, reliable, and beautiful to use every day, so this project focuses on the practical features I care about most.

## Highlights
- **Folder-based playlists** – choose any folder on device storage (subfolders included) and the app builds a playlist in natural file order ("Track 2" before "Track 10"). Playlist files such as `.m3u` and hidden or trashed files are ignored.
- **Persistent playback** – the chosen folder, current track, playback position, shuffle/repeat modes, and speed all come back where I left them. The position is saved every few seconds while playing.
- **Background ready** – a Media3 media session service owns the player, so playback, notification and lock-screen controls keep working when the app is closed, and stay available while paused.
- **Plays nicely with others** – pauses for calls and other media (audio focus) and when headphones are unplugged.
- **Timestamp bookmarks** – save the current position of a track with an optional note and jump back to it later.
- **Skip back / skip forward** – jump 5 s, 10 s, 20 s or 1 min (set separately for each direction in Settings, 10 s by default). The same steps apply to the notification, lock-screen and headset rewind / fast-forward buttons.
- **Equalizer and bass booster** – open it from the bars button in the player controls. One big bass boost control (0–100 %, with Off / Low / Mid / High / Max shortcuts), a loudness boost, 16 presets (Bass Boost, Subwoofer, Deep Bass, Hip-Hop, Rock, Vocal, Phone Speaker and more) and a 10-band equalizer from 31 Hz to 16 kHz (±12 dB). The sound is processed inside the app's own player, so it works the same on every phone, and an automatic pre-gain plus a limiter keep heavy bass from distorting. Settings are remembered, and moving any control switches the equalizer on.
- **Light and Night themes** – Night by default, toggled from the app bar.
- **Modern UI** – Material 3 design with compact controls, haptic feedback on the important actions, a track detail sheet, and speed presets from 0.75× to 2.0×.
- **Dynamic scroll** – the active song always stays in view; the playlist auto-scrolls whenever I move to the next or previous track.
- **Resilient** – files that can't be played are reported and skipped.

Supported formats: mp3, wav, m4a, aac, ogg/oga, opus, flac, plus other files the storage provider reports as audio.

> **Xiaomi / HyperOS:** swiping the app away in Recents force-stops it, which ends playback. Lock SoundVault's card in Recents or set its battery saver to "No restrictions" to keep it playing.

## Screenshots

|  | |  |
| --- | --- | --- |
| ![Now playing screen](screenshot/1.jpg) | ![Next track kept in view](screenshot/2.jpg) | ![Track details modal](screenshot/3.jpg) |

|  | |
| --- | --- |
| ![Notification & lockscreen controls](screenshot/4.jpg) | ![Folder selection flow](screenshot/5.jpg) |

|  | |  |
| --- | --- | --- |
| ![SoundVault screenshot 6](screenshot/6.jpg) | ![SoundVault screenshot 7](screenshot/7.jpg) | ![SoundVault screenshot 8](screenshot/8.jpg) |

|  | |  |
| --- | --- | --- |
| ![SoundVault screenshot 9](screenshot/9.jpg) | ![SoundVault screenshot 10](screenshot/10.jpg) | ![SoundVault screenshot 11](screenshot/11.jpg) |

## Tech Stack
- Kotlin + Jetpack Compose (Material 3)
- Media3 ExoPlayer, MediaSessionService & MediaController
- DataStore (preferences) and Room (bookmarks)
- Kotest for unit and property tests
- Android 12+ (min SDK 31, target SDK 36)

## Building
```bash
./gradlew assembleDebug             # debug build
./gradlew assembleQa                # minified build signed with the debug key, for testing on a device
./gradlew assembleRelease           # minified release build (signed when the properties below are set)
./gradlew testDebugUnitTest         # unit and property tests
./gradlew connectedDebugAndroidTest # bookmark database tests on a connected device
```

## Release Signing
Release signing is configured outside the repository. Set these Gradle properties or environment variables before building a signed release:

- `SOUNDVAULT_RELEASE_STORE_FILE`
- `SOUNDVAULT_RELEASE_STORE_PASSWORD`
- `SOUNDVAULT_RELEASE_KEY_ALIAS`
- `SOUNDVAULT_RELEASE_KEY_PASSWORD`
