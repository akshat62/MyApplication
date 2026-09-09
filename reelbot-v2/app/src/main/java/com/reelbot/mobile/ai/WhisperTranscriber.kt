package com.reelbot.mobile.ai

import android.content.Context
import com.reelbot.mobile.model.TranscriptSegment
import dev.ffmpegkit.whisper.Whisper
import dev.ffmpegkit.whisper.WhisperConfig
import java.io.File

class WhisperTranscriber(private val context: Context) {
    suspend fun transcribe(audio: File, model: File, language: String): List<TranscriptSegment> {
        val handle = Whisper.loadModel(context, model.absolutePath)
        return try {
            val config = if (language.isBlank() || language.equals("auto", true)) WhisperConfig() else WhisperConfig(language = language.trim())
            val result = Whisper.transcribe(handle, audio.absolutePath, config)
            result.segments.map { TranscriptSegment(it.startMs, it.endMs, it.text.trim()) }.filter { it.text.isNotBlank() }
        } finally { Whisper.releaseModel(handle) }
    }
}
