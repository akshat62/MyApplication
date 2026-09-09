package com.reelbot.mobile.ai

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class ModelDownloader(private val context: Context) {
    val modelFile: File get() = File(context.filesDir, "models/ggml-base.bin")
    suspend fun download(onProgress: (Int) -> Unit): File = withContext(Dispatchers.IO) {
        modelFile.parentFile?.mkdirs()
        if (modelFile.exists() && modelFile.length() > 100_000_000) return@withContext modelFile
        val partial = File(modelFile.absolutePath + ".part")
        val url = URL("https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.bin?download=true")
        val conn = (url.openConnection() as HttpURLConnection).apply { connectTimeout = 20_000; readTimeout = 30_000; instanceFollowRedirects = true }
        conn.connect()
        require(conn.responseCode in 200..299) { "Model download failed: HTTP ${conn.responseCode}" }
        val total = conn.contentLengthLong
        conn.inputStream.use { input -> partial.outputStream().use { output ->
            val buffer = ByteArray(1024 * 1024); var done = 0L
            while (true) { val n = input.read(buffer); if (n < 0) break; output.write(buffer, 0, n); done += n; if (total > 0) onProgress(((done * 100) / total).toInt()) }
        } }
        if (modelFile.exists()) modelFile.delete(); require(partial.renameTo(modelFile)) { "Could not save AI model" }
        modelFile
    }
}
