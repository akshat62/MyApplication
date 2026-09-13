package com.reelbot.mobile.ai

import android.content.Context
import com.reelbot.mobile.data.model.FailureReason
import com.reelbot.mobile.data.model.ModelState
import com.reelbot.mobile.data.model.WhisperModelSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

data class ModelDownloadProgress(val bytesRead: Long, val totalBytes: Long) {
    val percent: Int get() = if (totalBytes <= 0) 0 else ((bytesRead * 100) / totalBytes).toInt()
}

/**
 * Owns the on-device Whisper model file: download with progress, SHA-256 verification,
 * and the state machine the Model Management screen displays (NOT_INSTALLED →
 * DOWNLOADING → VERIFYING → INSTALLED). Transcription refuses to run until this reports
 * a verified, installed model — per the "detect model-not-installed condition" and
 * "do not attempt transcription until the model is actually usable" requirements.
 */
class ModelManager(private val context: Context, networkTimeoutSeconds: Long = 900) {

    private val installMutex = Mutex()

    private val client = OkHttpClient.Builder()
        .callTimeout(networkTimeoutSeconds, TimeUnit.SECONDS)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.MINUTES)
        .build()

    private val modelsDir = File(context.filesDir, "models").apply { mkdirs() }

    private val _state = MutableStateFlow(ModelState.NOT_INSTALLED)
    val state: StateFlow<ModelState> = _state.asStateFlow()

    private val _progress = MutableStateFlow(ModelDownloadProgress(0, 0))
    val progress: StateFlow<ModelDownloadProgress> = _progress.asStateFlow()

    private var activeSpec: WhisperModelSpec? = null

    fun modelFile(spec: WhisperModelSpec): File = File(modelsDir, spec.fileName)

    suspend fun refreshState(spec: WhisperModelSpec) = withContext(Dispatchers.IO) { installMutex.withLock {
        activeSpec = spec
        val file = modelFile(spec)
        _state.value = if (!file.isFile) ModelState.NOT_INSTALLED else {
            _state.value = ModelState.VERIFYING
            if (sha256(file).equals(spec.sha256, true)) ModelState.INSTALLED else ModelState.FAILED
        }
    }

    }

    /** Downloads and verifies [spec]. Cancellable via the coroutine's Job. Throws
     *  PipelineException with a concrete FailureReason on any real failure — no silent
     *  fallback to "it's probably fine." */
    suspend fun download(spec: WhisperModelSpec) = withContext(Dispatchers.IO) { installMutex.withLock {
        activeSpec = spec
        android.util.Log.i("ReelBotModel", "Downloading ${spec.fileName}")
        _state.value = ModelState.DOWNLOADING
        val target = modelFile(spec)
        val temp = File(modelsDir, spec.fileName + ".part")

        try {
            val request = Request.Builder().url(spec.downloadUrl).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw PipelineException(
                        FailureReason.MODEL_VERIFICATION_FAILED,
                        "Model download failed: HTTP ${response.code}"
                    )
                }
                val body = response.body ?: throw PipelineException(
                    FailureReason.MODEL_VERIFICATION_FAILED, "Empty response body"
                )
                val total = body.contentLength()
                android.util.Log.i("ReelBotModel", "HTTP ${response.code}; expected bytes=$total")
                var bytesRead = 0L
                temp.outputStream().use { out ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val read = input.read(buffer)
                            if (read == -1) break
                            out.write(buffer, 0, read)
                            bytesRead += read
                            _progress.value = ModelDownloadProgress(bytesRead, total)
                        }
                    }
                }
            }

            android.util.Log.i("ReelBotModel", "Verifying downloaded bytes=${temp.length()}")
            _state.value = ModelState.VERIFYING
            val actualHash = sha256(temp)
            if (spec.sha256 == "REPLACE_WITH_PUBLISHED_SHA256") {
                // Scaffold placeholder — see README/SETUP: fill in the real published
                // checksum before shipping. Left as a hard failure rather than silently
                // skipping verification, so this can't accidentally ship unverified.
                throw PipelineException(
                    FailureReason.MODEL_VERIFICATION_FAILED,
                    "No published checksum configured for ${spec.displayName} yet " +
                        "(computed sha256: $actualHash) — add it to WhisperModelSpec before release."
                )
            }
            if (!actualHash.equals(spec.sha256, ignoreCase = true)) {
                temp.delete()
                throw PipelineException(
                    FailureReason.MODEL_VERIFICATION_FAILED,
                    "Checksum mismatch: expected ${spec.sha256}, got $actualHash"
                )
            }

            check(temp.renameTo(target)) { "Could not install the verified model." }
            _state.value = ModelState.INSTALLED
        } catch (e: CancellationException) {
            temp.delete()
            _state.value = ModelState.NOT_INSTALLED
            throw e
        } catch (e: PipelineException) {
            _state.value = ModelState.FAILED
            temp.delete()
            throw e
        } catch (e: Exception) {
            android.util.Log.e("ReelBotModel", "Download failed after ${temp.length()} bytes", e)
            _state.value = ModelState.FAILED
            temp.delete()
            throw PipelineException(FailureReason.MODEL_VERIFICATION_FAILED, e.message)
        }
    }

    }

    fun setRuntimeState(state: ModelState) { _state.value = state }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
