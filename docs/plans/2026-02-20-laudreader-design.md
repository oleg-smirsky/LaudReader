# LaudReader — Design Document

Native Android app (Kotlin) that reads web articles aloud using Google Cloud TTS with on-device article extraction.

## Architecture

```
┌──────────────────────────────────────────────┐
│                 Android App                   │
│                                               │
│  Chrome ──Share URL──► Share Receiver          │
│                            │                  │
│                     Readability4J             │
│                     (extract article text)    │
│                            │                  │
│                     Google Cloud TTS API      │
│                     (text → audio, WaveNet)   │
│                            │                  │
│                     ExoPlayer +               │
│                     MediaSession              │
│                     (background playback,     │
│                      notification controls)   │
│                            │                  │
│                     Room DB + File cache      │
│                     (article history,         │
│                      cached audio)            │
└──────────────────────────────────────────────┘
```

No backend. Everything runs on-device except the TTS API call.

## Components

### 1. Share Receiver

Registers as an Android Share target for `text/plain` (URLs). Entry point from any browser or app that shares text.

### 2. Article Extractor

- OkHttp fetches the web page
- Readability4J extracts title, author, and clean body text
- Returns structured article metadata + plain text content

### 3. TTS Engine

- Calls Google Cloud TTS API directly (WaveNet voices, MP3 output)
- Splits text into <5,000 character chunks at sentence boundaries
- Generates audio for each chunk, concatenates into a single MP3
- Runs as a foreground service with progress notification during generation

### 4. Audio Player

- ExoPlayer for playback
- MediaSession integration for lock screen / notification / Bluetooth controls
- Background playback via foreground service
- Tracks playback position per article

### 5. Article Queue (Room DB)

Stores articles with fields:
- `id`, `title`, `sourceUrl`, `domain`
- `extractedText` (for re-generation if needed)
- `audioFilePath`, `audioFileSizeBytes`
- `status`: GENERATING | READY | PLAYING | PLAYED
- `generationProgress` (0-100)
- `playbackPositionMs`, `durationMs`
- `createdAt`, `lastPlayedAt`

### 6. Offline Cache

Generated MP3 files stored in app-private storage. Cached indefinitely until user deletes the article.

## Authentication

Google Sign-In with OAuth2 — no embedded API keys.

1. GCP project with Cloud TTS API enabled
2. OAuth consent screen in "testing" mode (personal account only)
3. App uses Google Sign-In to obtain an access token with `https://www.googleapis.com/auth/cloud-platform` scope
4. Token used directly for Cloud TTS API calls
5. Usage billed against GCP project free tier (1M WaveNet chars/month free)

## UI

Single-screen app: article list + bottom player bar.

### Article List (Gmail-style)

Each item shows:
- Status icon: ◐ Generating | ░ Ready | ▶ Playing | ✓ Played
- Article title
- Source domain
- File size + listening progress (or generation progress)

### Tap Behavior

| State | Tap | Result |
|-------|-----|--------|
| Nothing playing, tap any article | → | Start from last position (or beginning) |
| Tap the currently playing article | → | Pause |
| Tap a different article while playing | → | Pause current, start tapped article |

### Other Interactions

- **Swipe left** → Delete with undo snackbar
- **Long press** → Menu: Open original URL, Delete, Mark as played/unplayed
- **Share flow** → URL shared → toast "Article added" → appears as "Generating..." → auto-plays when done if nothing else is playing

### Bottom Player Bar

Persistent when audio is active:
- Article title (scrolling if long)
- Play/pause button
- Skip back 15s / skip forward 15s
- Progress bar with elapsed / total time

### Notification Controls

Via MediaSession: play/pause, skip back 15s, skip forward 15s. Works with Bluetooth headset media buttons.

## Cost

Free for typical usage. Google Cloud TTS provides 1M WaveNet characters/month at no cost. At ~40K characters per article, that covers ~25 articles/month.

## Tech Stack

- **Language**: Kotlin
- **Min SDK**: 26 (Android 8.0)
- **UI**: Jetpack Compose + Material 3
- **Networking**: OkHttp
- **Article extraction**: Readability4J
- **Audio playback**: ExoPlayer (Media3)
- **Media controls**: MediaSession (Media3)
- **Database**: Room
- **Auth**: Google Sign-In (Credential Manager API)
- **TTS API**: Google Cloud TTS REST API
- **DI**: Hilt

## Future Expansion Ideas

- **Chaquopy + trafilatura**: Embed a Python runtime on Android via [Chaquopy](https://chaquo.com/chaquopy/) to run [trafilatura](https://trafilatura.readthedocs.io/) for article extraction. lxml 5.3.0 is available pre-built for Android ARM64. This would provide more robust extraction than Readability4J (better handling of edge-case sites, metadata, multiple fallback strategies). Adds ~20-30MB to app size for the Python runtime.
- **Multiple TTS backends**: Support ElevenLabs API as a premium voice option alongside Google Cloud TTS. Allow per-article voice selection.
- **Cloud Run fallback**: Add a server-side extraction endpoint for articles that Readability4J fails to parse correctly.
- **Import from Pocket/Instapaper**: Pull saved articles from read-later services.
- **Playback speed control**: 0.5x to 2.0x speed adjustment.
- **Auto-download on Wi-Fi**: Generate audio for queued articles automatically when on Wi-Fi.
