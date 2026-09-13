package com.reelbot.mobile.data.model

enum class ModelState {
    NOT_INSTALLED,
    DOWNLOADING,
    VERIFYING,
    INSTALLED,
    LOADING,
    READY,
    FAILED
}

/** The Whisper model variants ReelBot offers, sized for on-device use. */
enum class WhisperModelSpec(
    val displayName: String,
    val fileName: String,
    val approxSizeMb: Int,
    val downloadUrl: String,
    val sha256: String
) {
    BASE(
        "Whisper Base multilingual", "ggml-base.bin", 142,
        "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.bin",
        "60ed5bc3dd14eea856493d334349b405782ddcaf0028d4b5df4088345fba2efe"
    );

    companion object {
        /** Picks a sensible default based on available RAM, per spec: don't default to
         *  a model that makes a normal phone unusable. */
        fun recommendedFor(totalRamMb: Long): WhisperModelSpec = when {
            totalRamMb >= 6000 -> BASE
            totalRamMb >= 3000 -> BASE
            else -> BASE
        }
    }
}
