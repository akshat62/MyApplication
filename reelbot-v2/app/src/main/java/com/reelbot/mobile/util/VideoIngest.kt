package com.reelbot.mobile.util

import android.content.Context
import android.net.Uri
import java.io.File

object VideoIngest {
    fun copyToPrivateStorage(context: Context, uri: Uri): File {
        val dir = File(context.filesDir, "sources").apply { mkdirs() }
        val out = File(dir, "source_${System.currentTimeMillis()}.mp4")
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Unable to open selected video" }
            out.outputStream().use { output -> input.copyTo(output, 1024 * 1024) }
        }
        return out
    }
}
