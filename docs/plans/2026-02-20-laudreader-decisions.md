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

## Future Expansion Ideas

Saved for later consideration:
- **Chaquopy + trafilatura**: Embed Python on Android for better article extraction
- **Multiple TTS backends**: Add ElevenLabs as a premium voice option
- **Cloud Run fallback**: Server-side extraction for edge cases
- **Pocket/Instapaper import**
- **Playback speed control**
- **Auto-download on Wi-Fi**
