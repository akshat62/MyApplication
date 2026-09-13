# ReelBot V3 verification report — 13 September 2026

## Outcome

The local video-to-Reel pipeline has passed Android runtime testing. This is **not full acceptance of every requested feature**: Instagram OAuth/live publication remain unfinished, but Android player playback and detection-driven face composition have now also passed focused runtime tests.

Verified application source commit: `8879ca4bb44daabccb85129804d68e0ed9614d58`.
Later commit `626edcb06c79b0b4ec2b3489972e5f1ebcdfef84` adds playback/face tests and packaging changes; it does not change production application code.

## Downloads

Final separate packages:
- [ReelBot-V3-Fixed.apk package](https://github.com/akshat62/MyApplication/actions/runs/34749201364/artifacts/10315002880)
- [Complete corrected source](https://github.com/akshat62/MyApplication/actions/runs/34749201364/artifacts/10314449291)
- [Executed test evidence and real exported Reels](https://github.com/akshat62/MyApplication/actions/runs/34749201364/artifacts/10314648774)

APK SHA-256: `e56c1d503d45589f65fe7599c1ac9d20c8e9d56a4d9eb611dbd154fcc61766ca`.
GitHub artifact downloads are ZIP containers; extract `ReelBot-V3-Fixed.apk` to install.

Original verification bundle:

[Verified APK, complete corrected source ZIP, exported Reel, transcript, screenshots, and logs](https://github.com/akshat62/MyApplication/actions/runs/34747988107/artifacts/10315435517)

Inside the download:
- `deliverables/ReelBot-V3-Fixed.apk`
- `deliverables/ReelBot-V3-Corrected-Source.zip`
- `build-source/ReelBotV3/test-results/verified-reel.mp4`
- Other test results and screenshots under `build-source/ReelBotV3/test-results/`.

The APK is a debug build of `com.reelbot.mobile`, with arm64-v8a and x86_64 native libraries, Android 8/API 26 minimum. An older installation signed with another debug key may require uninstalling before installation; uninstalling deletes that installation's local data.

## Executed tests

Device: Android 15/API 35 AOSP x86_64 emulator, hardware-accelerated GitHub Ubuntu runner, two emulator CPUs, SwiftShader graphics. No physical Xiaomi device was available.

| Check | Executed result |
|---|---|
| Clean build | PASS: equivalent Gradle `clean assembleDebug assembleDebugAndroidTest testDebugUnitTest` |
| APK and instrumentation installation | PASS |
| Cold start / Home | PASS |
| Home, Queue, Analytics, Settings repeatedly | PASS, three navigation cycles |
| Create and actual Android document picker | PASS |
| Selected local MP4 / persisted URI read access / preview | PASS |
| Activity recreation | PASS |
| Native Whisper absent from startup process mappings | PASS |
| Room opens | PASS |
| Base multilingual download on Android | PASS, approximately 16 seconds; SHA-256 verified |
| Model loading / real native transcription | PASS; 30-second audio produced three timestamped segments |
| Missing-model failure | PASS; actual failure, no substitute transcript |
| Highlight selection | PASS in real pipeline; two additional JVM regression tests passed |
| Media3 component export | PASS; 22-second output |
| Actual background processing worker | PASS; generated a 30-second review-ready Reel with retained caption, transcript, score and output path |
| Output format | PASS: H.264/AAC; portrait display 1080 × 1920 |
| Output validity | PASS: Android frame decoding and complete FFmpeg decoding, no decode errors |
| Subtitles | PASS: inspected exported frame; actual Whisper text wraps inside the portrait safe zone |
| Restart / Room queue persistence | PASS after force-stop and relaunch |
| Missing Instagram configuration | PASS: explicit setup error, no crash |
| Android VideoView playback-event test | PASS; frame-render event received and playback position advanced |
| Detection-driven crop / retained face in output test | PASS; off-center face changed crop, and face was detected near center of the exported frame |
| Live Instagram OAuth / publication | NOT IMPLEMENTED / NOT VERIFIED |
| Physical arm64/Xiaomi runtime | NOT TESTED |

[Successful complete local-pipeline run](https://github.com/akshat62/MyApplication/actions/runs/34747988107).
[Exported-frame and metadata inspection](https://github.com/akshat62/MyApplication/actions/runs/34748513696).
[Successful focused playback/face verification](https://github.com/akshat62/MyApplication/actions/runs/34748943778). The redundant full rerun is not used as verification evidence.

The successful startup test took 8.7 seconds, three pipeline tests 243.8 seconds, and persistence test 0.032 seconds. Focused Android playback passed in 3.6 seconds and face-composition verification in 5.1 seconds. Real transcription took approximately 70 seconds for 30 seconds of speech on this emulator. These are emulator observations, not performance promises for a phone.

The main fixture uses the real JFK speech WAV supplied with whisper.cpp, repeated into a 30-second MP4 with a generated video test pattern. It is not a natural moving-speaker video. The executed face test uses a NASA astronaut portrait distributed with scikit-image, deliberately placed off-center.

Media3 stores the video as 1920 × 1080 with a -90-degree display matrix; standard playback presents 1080 × 1920 portrait. The inspected output frame is upright and its subtitles are readable.

## Important changes

- Removed fragile eager navigation initialization; retained explicit non-null bottom destinations and icons.
- Normal launches are not permanently forced into safe mode by an old crash record; diagnostics remain retained.
- Opened Room before constructing startup ViewModels; kept native Whisper loading out of startup.
- Added model integrity/state handling and shared model management.
- Corrected WAV writing, PCM validation, native string handling and resource cleanup.
- Enabled optimized native Whisper code even in the debug APK. The earlier unoptimized build did not finish inference within the test deadline; optimized inference completed.
- Improved transcript-based ranking and duplicate penalties without fabricating engagement scores.
- Fixed crop argument order, full-segment subtitle layout, explicit codecs and output validation.
- Preserved actual worker results in the persistent approval queue; added caption/hashtag editing and review actions.
- Kept publishing failures honest and removed embedded Meta app secrets.

## Remaining limits

- **Instagram login cannot establish a session in this revision.** Even after supplying an App ID, the supported public-client OAuth integration still needs implementation and validation. This is more than a missing-credentials issue. Live publishing and Instagram insights must not be described as working.
- Meta Developer configuration, approved permissions, an eligible Instagram Professional account and network access are required for future official publishing.
- Initial model preparation requires network access and roughly 148 MB of model storage. Local transcription/rendering require no PC or self-hosted backend after preparation.
- Input videos are limited to 30 minutes. Usable RAM, free storage and Android H.264/AAC decoding/encoding capability are required; physical-device performance is unverified.
- Face composition currently uses one averaged crop per clip with center fallback, not continuous tracking. The pipeline exercised center fallback, and the focused test separately verified detection-driven composition in a real exported frame. Moving-face tracking was not tested or implemented.
- Physical rotation, invalid-video/network-failure combinations and physical Xiaomi behavior need further executed tests.
- Retained logs help diagnose managed exceptions. Fatal native/VM failures cannot safely be swallowed.
- The local workspace disconnected during verification; remaining work was completed through GitHub. Focused tests reused the verified application APK with a temporary test signing identity; production application code was unchanged. The delivered APK is the original artifact from the successful complete pipeline run. No unexecuted test is counted as passing.
