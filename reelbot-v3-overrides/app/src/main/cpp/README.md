# Native Whisper module

This directory builds a thin JNI bridge (`whisper_jni.cpp`) around
[whisper.cpp](https://github.com/ggerganov/whisper.cpp). ReelBot does not vendor
whisper.cpp's source directly in this repo — add it as a submodule before building:

```bash
cd <project root>
git submodule add https://github.com/ggerganov/whisper.cpp app/src/main/cpp/whisper.cpp
git submodule update --init --recursive
```

Pin to a specific tagged release (check out that tag inside the submodule) rather than
tracking `main`, so native builds stay reproducible:

```bash
cd app/src/main/cpp/whisper.cpp
git checkout v1.7.2   # or whichever release you've validated
```

## Why a submodule instead of vendoring the source

whisper.cpp is a large, independently-versioned upstream project (it also pulls in
ggml). Vendoring a copy directly in this repo would mean manually tracking upstream
security/correctness fixes; a pinned submodule keeps the exact version explicit in git
history and makes upgrades a one-line `git checkout <new-tag>`.

## Build

Once the submodule is present, Gradle's `externalNativeBuild` (configured in
`app/build.gradle.kts`) invokes CMake automatically as part of `assembleDebug` /
`assembleRelease` — no separate native build step needed.

## What this bridge does and doesn't do

- `nativeLoadModel` — calls `whisper_init_from_file_with_params`. Returns 0 (mapped to a
  real failure in Kotlin) if the model file is missing/corrupt — never fabricates a
  context.
- `nativeTranscribe` — calls `whisper_full` on caller-supplied 16kHz mono float32 PCM
  (produced by `AudioExtractor` + `WhisperNative.kt`) and returns real timestamped
  segments, or `null` on failure. No sample/canned transcript path exists anywhere in
  this bridge.
- `nativeRelease` — frees the whisper context.
