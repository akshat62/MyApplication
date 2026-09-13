package com.reelbot.mobile.ai

import com.reelbot.mobile.ai.native.WhisperNative
import com.reelbot.mobile.data.model.FailureReason
import com.reelbot.mobile.data.model.ModelState
import com.reelbot.mobile.data.model.TranscriptSegment
import com.reelbot.mobile.data.model.WhisperModelSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ModelNotReadyException(val state: ModelState) :
    Exception("Speech model isn't ready yet (state=$state)")

/**
 * Runs real on-device transcription via the whisper.cpp JNI bridge. This class contains
 * no fallback path that returns sample/canned text: if the model isn't installed, if the
 * native library isn't built, or if whisper_full fails, callers get a specific thrown
 * exception and the job is marked FAILED with that reason — never a fabricated transcript.
 */
class TranscriptionEngine(private val modelManager: ModelManager) {

    suspend fun transcribe(
        wavFile: File,
        spec: WhisperModelSpec,
        language: String = "auto"
    ): List<TranscriptSegment> = withContext(Dispatchers.Default) {
        if (modelManager.state.value != ModelState.INSTALLED && modelManager.state.value != ModelState.READY) {
            throw ModelNotReadyException(modelManager.state.value)
        }
        val modelFile = modelManager.modelFile(spec)
        if (!modelFile.exists()) {
            throw ModelNotReadyException(ModelState.NOT_INSTALLED)
        }

        modelManager.setRuntimeState(ModelState.LOADING)
        val ctxPtr = try {
            WhisperNative.nativeLoadModel(modelFile.absolutePath)
        } catch (e: LinkageError) {
            modelManager.setRuntimeState(ModelState.FAILED)
            throw PipelineException(
                FailureReason.TRANSCRIPTION_FAILED,
                "Native Whisper library failed to load: ${e.message}"
            )
        }

        if (ctxPtr == 0L) {
            modelManager.setRuntimeState(ModelState.FAILED)
            throw PipelineException(FailureReason.TRANSCRIPTION_FAILED, "whisper_init_from_file failed for ${modelFile.name}")
        }

        modelManager.setRuntimeState(ModelState.READY)
        try {
            val samples = readWavAsFloatPcm(wavFile)
            val threads = Runtime.getRuntime().availableProcessors().coerceIn(2, 6)
            val raw = WhisperNative.nativeTranscribe(ctxPtr, samples, language, threads)
                ?: throw PipelineException(FailureReason.TRANSCRIPTION_FAILED, "whisper_full returned an error")

            val segments = parseSegments(raw)
            if (segments.isEmpty()) {
                throw PipelineException(FailureReason.NO_SPEECH_DETECTED)
            }
            segments
        } finally {
            WhisperNative.nativeRelease(ctxPtr)
        }
    }

    /** Reads the 16kHz mono 16-bit PCM WAV produced by AudioExtractor and converts it to
     *  the float32 [-1, 1] format whisper_full expects. */
    private fun readWavAsFloatPcm(wavFile: File): FloatArray {
        RandomAccessFile(wavFile, "r").use { raf ->
            val header = ByteArray(44)
            raf.readFully(header)
            val h = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
            require(String(header, 0, 4) == "RIFF" && String(header, 8, 4) == "WAVE" && h.getShort(20).toInt() == 1 && h.getShort(22).toInt() == 1 && h.getInt(24) == 16000 && h.getShort(34).toInt() == 16) { "Expected 16 kHz mono PCM16 WAV." }
            require(raf.length() in 46..(16000L * 2 * 60 * 30 + 44)) { "Audio is empty or exceeds the 30-minute on-device limit." }
            val dataSize = (raf.length() - 44).toInt()
            val pcmBytes = ByteArray(dataSize)
            raf.readFully(pcmBytes)

            val buffer = ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN)
            val sampleCount = dataSize / 2
            val floats = FloatArray(sampleCount)
            for (i in 0 until sampleCount) {
                floats[i] = buffer.short / 32768f
            }
            return floats
        }
    }

    private fun parseSegments(raw: String): List<TranscriptSegment> {
        if (raw.isBlank()) return emptyList()
        return raw.split("\u0001").mapNotNull { line ->
            val parts = line.split("|", limit = 3)
            if (parts.size != 3) return@mapNotNull null
            val start = parts[0].toLongOrNull() ?: return@mapNotNull null
            val end = parts[1].toLongOrNull() ?: return@mapNotNull null
            val text = parts[2].trim()
            if (text.isBlank() || start < 0 || end <= start) null else TranscriptSegment(start, end, text)
        }
    }
}
