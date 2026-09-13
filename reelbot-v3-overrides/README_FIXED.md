# ReelBot V3 continuation

This project continues the encoded ReelBotV3 source and overrides on `reelbot-v3-build-20260909`.

## Build

Use JDK 17, Gradle 8.11.1, Android SDK 36, build tools 34.0.0, NDK 27.0.12077973 and CMake 3.22.1. The native source is whisper.cpp v1.7.2 under `app/src/main/cpp/whisper.cpp`.

```
gradle clean assembleDebug assembleDebugAndroidTest testDebugUnitTest
```

The application ID is `com.reelbot.mobile`. Debug builds include arm64-v8a and x86_64.

## Mobile workflow

Download the verified Whisper Base multilingual model in Settings, select a local video with the Android document picker, then start processing. Processing is local and imports run sequentially. Source videos are limited to 30 minutes to bound transcription memory. Generated clips require review. Preview and edit captions/hashtags in the queue.

Highlight scores are deterministic transcript heuristics, not predicted engagement. Face composition uses one averaged face-aware crop per clip with a center fallback, not continuous speaker tracking. Subtitles show complete timestamped transcript segments.

## Instagram limitation

This revision does not claim working Meta OAuth or live publication. Login reports missing/incomplete developer configuration. No app secret is embedded. Completing supported public-client authentication, permissions and live API validation remains necessary; a Professional account and network access will be required. Instagram insights are not implemented. Existing publisher code is retained for further integration, but cannot establish a session in this build.

## Verification

`scripts/prepare-test-input.sh` constructs a test MP4 using the real JFK speech WAV distributed with whisper.cpp and a generated video test pattern. It downloads the Base model and checks SHA-256. `scripts/device-tests.sh` installs both APKs, exercises startup/navigation/recreation, runs the actual local transcription/export tests, and collects output and logs. Test definitions are not evidence of a pass: consult the accompanying test report for executed results.

Crash stack traces are retained in app-private `last_crash.txt` and can be read from Settings → Diagnostics & logs. A previous crash record does not force every subsequent launch into Safe Mode. Unrecoverable VM/native/process crashes cannot safely be swallowed.
