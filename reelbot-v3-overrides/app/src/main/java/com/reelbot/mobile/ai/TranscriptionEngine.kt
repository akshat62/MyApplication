package com.reelbot.mobile.ai

import com.reelbot.mobile.ai.native.WhisperNative
import com.reelbot.mobile.data.model.FailureReason
import com.reelbot.mobile.data.model.ModelState
import com.reelbot.mobile.data.model.TranscriptSegment
import com.reelbot.mobile.data.model.WhisperModelSpec
import kotlinx.coroutines.ensureActive
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
class TranscriptionEngine(private val modelManager: ModelManager, private val chunkSeconds: Int = 60) {

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

        android.util.Log.i("ReelBotWhisper", "Loading verified model ${spec.fileName}")
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
            val threads = Runtime.getRuntime().availableProcessors().coerceIn(2, 6)
            val segments = mutableListOf<TranscriptSegment>()
            PcmChunkReader(wavFile, chunkSeconds).use { reader ->
                while (true) {
                    kotlinx.coroutines.currentCoroutineContext().ensureActive()
                    val chunk = reader.next() ?: break
                    android.util.Log.i("ReelBotWhisper", "Transcribing ${chunk.startMs}..${chunk.endMs} of ${reader.durationMs} ms")
                    val raw = WhisperNative.nativeTranscribe(ctxPtr, chunk.samples, language, threads)
                        ?: throw PipelineException(FailureReason.TRANSCRIPTION_FAILED, "Whisper failed at ${chunk.startMs / 1000} seconds")
                    segments += parseSegments(raw).mapNotNull { segment ->
                        val start = (chunk.startMs + segment.startMs).coerceAtMost(chunk.endMs)
                        val end = (chunk.startMs + segment.endMs).coerceAtMost(chunk.endMs)
                        if (end > start) segment.copy(startMs = start, endMs = end) else null
                    }
                }
            }
            android.util.Log.i("ReelBotWhisper", "Transcription returned ${segments.size} segments")
            if (segments.isEmpty()) {
                throw PipelineException(FailureReason.NO_SPEECH_DETECTED)
            }
            segments
        } finally {
            WhisperNative.nativeRelease(ctxPtr)
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
