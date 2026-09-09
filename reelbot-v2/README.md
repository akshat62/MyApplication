# ReelBot Mobile V2

Android-only source-video to Instagram Reels pipeline. No PC or self-hosted processing server is needed at runtime.

Implemented: Android video import; on-device Whisper transcription; transcript-aware highlight selection; three candidate Reels; bundled ML Kit face detection; face-aware 9:16 Media3 export; timed burned-in subtitles; automatic caption/hashtag drafting; persistent approval queue; optional Full Auto Instagram publishing; Meta resumable local-file upload; Android Keystore protection for the Instagram access token.

Direct API publishing needs a Professional Instagram account and a valid Content Publishing access token supplied by the user's Meta app. ReelBot never embeds a Meta app secret in the APK. Without API credentials, users can share generated MP4s to the installed Instagram app.

The free whisper-android AAR used by this build targets arm64-v8a. The app is minSdk 26 and arm64-v8a.

Build sync: 2026-09-09.
