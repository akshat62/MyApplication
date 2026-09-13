package com.reelbot.mobile.ai.native

/**
 * Thin external-fun bridge to whisper_jni.cpp. Nothing here interprets results —
 * that's TranscriptionEngine's job. Loading `reelbot_whisper` throws
 * UnsatisfiedLinkError if the native module hasn't been built (see
 * app/src/main/cpp/README.md) — TranscriptionEngine catches that and surfaces it as a
 * real, specific error rather than silently falling back to fake output.
 */
object WhisperNative {
    init {
        System.loadLibrary("reelbot_whisper")
    }

    /** Returns a native context pointer, or 0 on failure. */
    external fun nativeLoadModel(modelPath: String): Long

    external fun nativeRelease(ctxPtr: Long)

    /**
     * [samples] must be 16kHz mono float32 PCM in range [-1, 1]. [language] is an
     * ISO-639-1 code, or "auto"/empty for autodetect. Returns a string encoding segments
     * as "startMs|endMs|text" joined by U+0001, or null on failure.
     */
    external fun nativeTranscribe(
        ctxPtr: Long,
        samples: FloatArray,
        language: String,
        nThreads: Int
    ): String?
}
