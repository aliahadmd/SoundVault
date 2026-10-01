# Product Overview

SoundVault is a personal Android audio playback app. The user picks a folder from device storage and it builds a
playlist from the audio files inside (including subfolders).

## Core Features
- Folder-based playlist generation (recursive scan, natural order by path: "Track 2" before "Track 10")
- Persistent playback state: folder, track, position, shuffle, repeat and speed are restored on relaunch
- Background playback via a Media3 media session service with notification / lock-screen controls that stay available while paused
- Audio focus (pauses for calls and other media) and pausing when headphones are unplugged
- Shuffle, repeat (off / all / one), and playback speed presets 0.75× – 2.0×
- Skip back / skip forward by 5 s, 10 s, 20 s or 1 min, chosen separately per direction in the Settings sheet
  (default 10 s); the notification, lock screen and headset rewind / fast-forward keys use the same steps
- Timestamp bookmarks with optional notes, per track and folder
- Light / Night themes (Night by default), toggled in the app bar
- Track detail bottom sheet (artist, album, duration, file size, location)
- Haptic feedback on key interactions and auto-scroll to the active track
- Unplayable files are reported and skipped

## Supported Audio Formats
mp3, wav, m4a, aac, ogg/oga, opus, flac (plus other files the provider reports as `audio/*`). Playlist files
(m3u, m3u8, pls, cue, …) and hidden or trashed files are ignored.

## Target Audience
Single-user personal app — no accounts, no network, no cloud sync.
