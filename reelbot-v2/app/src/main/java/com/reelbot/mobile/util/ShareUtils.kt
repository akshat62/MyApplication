package com.reelbot.mobile.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

object ShareUtils {
    fun preview(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "video/mp4").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
    }
    fun instagram(context: Context, file: File, caption: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val intent = Intent(Intent.ACTION_SEND).apply { type = "video/mp4"; putExtra(Intent.EXTRA_STREAM, uri); putExtra(Intent.EXTRA_TEXT, caption); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION); setPackage("com.instagram.android") }
        try { context.startActivity(intent) } catch (_: Exception) { context.startActivity(Intent.createChooser(intent.setPackage(null), "Share Reel")) }
    }
}
