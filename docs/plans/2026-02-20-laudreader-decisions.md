# LaudReader — Discussion & Decisions Log

## Problem Statement

Reading articles aloud while driving. Not all platforms offer a read-aloud function. Need a solution that works with any web article shared from the browser.

## Research: Existing Android Apps

Evaluated before deciding to build:

| App | Verdict |
|-----|---------|
| **ElevenReader** (ElevenLabs) | Best voice quality, free tier. But user reviews report it's glitchy — stops mid-article, bugs with URL sharing. |
| **Speechify** | Most popular (50M+ users). Subscription-heavy ($139/year for premium). |
| **@Voice Aloud Reader** | Free, has headset controls for driving. But uses Android's built-in TTS — mediocre voice quality. |
| **Pocket** | Has a "listen" feature but limited voice options. |

**Decision**: Build a custom app. Existing apps either have reliability issues, poor voice quality, or expensive subscriptions.

## Key Decisions

### 1. TTS Provider: Google Cloud TTS (not ElevenLabs)

**Context**: User's priorities are voice quality, reliability, and low cost.

**Real-world cost analysis** using a Pragmatic Engineer article (~40,000 characters):
- **ElevenLabs API** (Creator $22/mo): 100K chars included. At 5-10 articles/week of this size (800K-1.6M chars/month), cost would be $230-470/month with overages. Far too expensive.
- **Google Cloud TTS**: 1M WaveNet characters/month FREE. Covers ~25 long articles. Essentially zero cost.

**Decision**: Google Cloud TTS. WaveNet voices are good quality and the free tier covers typical usage.

### 2. Article Extraction: Readability4J (on-device)

**Options considered**:
- **Cloud Run + trafilatura** (Python): Best extraction quality, but requires hosting a backend. Raises question of securing the endpoint.
- **Chaquopy + trafilatura** (Python on Android): Possible — lxml 5.3.0 is pre-built for Android via Chaquopy. But adds ~20-30MB to app size for the Python runtime.
- **Readability4J** (Kotlin, on-device): Port of Mozilla's Readability.js. Native, lightweight, no backend needed. Less sophisticated than trafilatura but reliable for mainstream sites.

**Decision**: Readability4J. Simplest approach, no backend, no Python overhead. Can add trafilatura via Chaquopy as a future enhancement if extraction quality becomes an issue.

### 3. Backend: None

**Context**: User didn't want to expose an API to the public internet but needed mobile network access.

**Options considered**:
- **Tailscale + home/VPS**: Truly private, but requires an always-on machine.
- **Google Cloud Run**: Serverless, scales to zero, protected by API key.
- **No backend**: Everything on-device.

**Decision**: No backend needed. Once we chose Readability4J for extraction, the only external call is to Google Cloud TTS, which is authenticated via OAuth. No server to manage or secure.

### 4. Authentication: Google Sign-In (OAuth2)

**Context**: User wanted auth via Google account, no embedded API keys.

**Decision**: Google Sign-In → OAuth2 access token with `cloud-platform` scope → direct TTS API calls. User sets up a GCP project with TTS API enabled and OAuth consent screen in testing mode (personal use).

### 5. Platform: Native Android (Kotlin)

**Options considered**: Native Android (Kotlin), Flutter, Simple server + PWA.

**Decision**: Native Android. Best Share intent integration, native media controls, no cross-platform overhead needed (Android-only use case).

### 6. UI: Single-screen article list with bottom player

Gmail-style list showing title, domain, file size, progress. Bottom persistent player bar with play/pause + skip 15s + progress. Notification + lock screen + Bluetooth controls via MediaSession.

## Implementation Decisions (auto-generated during build)

The following decisions were made during the automated implementation phase:

### 7. Version Pinning

| Dependency | Version | Rationale |
|-----------|---------|-----------|
| **AGP** | 8.2.2 | Well-tested stable release |
| **Kotlin** | 1.9.22 | Stable, proven Compose compiler compatibility (1.5.10) |
| **Compose BOM** | 2024.02.00 | Matched to Kotlin/compiler version |
| **Hilt** | 2.50 | Stable with kapt on Kotlin 1.9.x |
| **Room** | 2.5.2 | Compatible with kapt on Kotlin 1.9.x (Room 2.6+ needs KSP/Kotlin 2.0) |
| **Media3** | 1.2.1 | Stable ExoPlayer + MediaSession bundle |
| **OkHttp** | 4.12.0 | Latest 4.x stable |
| **Readability4J** | 1.0.8 | Latest available release |
| **Gradle** | 8.5 | Wrapper pinned; compatible with AGP 8.2.x |
| **Target/Compile SDK** | 34 | Android 14 |
| **Min SDK** | 26 | Android 8.0 as specified in design |
| **JVM Target** | 17 | Recommended for AGP 8.x |

### 8. Annotation Processing: kapt (not KSP)

Used kapt for both Hilt and Room annotation processing. While KSP is faster, kapt provides the most battle-tested compatibility with Hilt 2.50 on Kotlin 1.9.x. Can migrate to KSP + Kotlin 2.0 in a future update.

### 9. Auth: GoogleSignIn + GoogleAuthUtil (not Credential Manager)

The design doc specified "Google Sign-In (Credential Manager API)" but the actual requirement is an OAuth2 access token with `cloud-platform` scope. Credential Manager only provides ID tokens, not access tokens with custom scopes. Used the `play-services-auth` library instead:
1. `GoogleSignIn` API handles the sign-in UI flow
2. `GoogleAuthUtil.getToken()` obtains an OAuth2 access token with the needed scope

This is the simplest path to getting a working access token for the Cloud TTS API.

### 10. TTS Voice: en-US-Wavenet-D

Selected `en-US-Wavenet-D` (male voice) as the default. Good clarity for article reading. The voice name is a constant in `TtsEngine.kt` and can be made configurable later.

### 11. Audio Chunking Strategy

Text is split at sentence boundaries (`. `, `! `, `? `) with a maximum chunk size of 4,900 characters (under the 5,000 char API limit). Falls back to paragraph/line boundaries if no sentence boundary is found. MP3 chunks are concatenated directly — MP3 frames are independently decodable so simple concatenation works.

### 12. Playback Architecture: Two Separate Services

- **TtsService**: Standard foreground service (`dataSync` type) for audio generation with progress notification
- **PlaybackService**: `MediaSessionService` for audio playback with media controls

Kept separate because they have different lifecycles — generation may complete while the user isn't actively listening, and playback may continue after all generation is done.

### 13. Package Structure: Layer-Based

```
com.laudreader/
├── auth/           # Google OAuth
├── data/           # Room entity, DAO, database
├── di/             # Hilt modules
├── extractor/      # Article extraction
├── player/         # ExoPlayer playback service
├── tts/            # TTS engine and generation service
└── ui/             # Compose UI
    ├── theme/
    └── components/
```

Layer-based (not feature-based) since this is a single-screen app. All UI is in one screen with reusable components.

### 14. State Management: StateFlow + Room Flow

- Room DAO returns `Flow<List<Article>>` for reactive article list updates
- ViewModel uses `StateFlow` for player state and snackbar messages
- MediaController position is polled every 500ms and pushed to StateFlow
- Playback position is saved to Room every 1s during active playback

## Future Expansion Ideas

Saved for later consideration:
- **Chaquopy + trafilatura**: Embed Python on Android for better article extraction
- **Multiple TTS backends**: Add ElevenLabs as a premium voice option
- **Cloud Run fallback**: Server-side extraction for edge cases
- **Pocket/Instapaper import**
- **Playback speed control**
- **Auto-download on Wi-Fi**
