package com.reelbot.mobile.face

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.io.File

object FaceAnalyzer {
    fun averageFaceCenterX(video: File, startMs: Long, endMs: Long): Float {
        val detector = FaceDetection.getClient(FaceDetectorOptions.Builder().setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST).build())
        val retriever = MediaMetadataRetriever()
        val centers = mutableListOf<Float>()
        return try {
            retriever.setDataSource(video.absolutePath)
            val span = (endMs - startMs).coerceAtLeast(1L)
            repeat(7) { idx ->
                val t = startMs + (span * (idx + 1) / 8)
                val frame = retriever.getFrameAtTime(t * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC) ?: return@repeat
                val scaled = scaleDown(frame)
                if (scaled !== frame) frame.recycle()
                val faces = Tasks.await(detector.process(InputImage.fromBitmap(scaled, 0)))
                val face = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }
                if (face != null) centers += face.boundingBox.exactCenterX() / scaled.width.toFloat()
                scaled.recycle()
            }
            if (centers.isEmpty()) 0.5f else centers.average().toFloat().coerceIn(0.15f, 0.85f)
        } catch (_: Exception) { 0.5f }
        finally { retriever.release(); detector.close() }
    }

    private fun scaleDown(src: Bitmap): Bitmap {
        val max = 720
        if (src.width <= max && src.height <= max) return src
        val factor = max.toFloat() / maxOf(src.width, src.height)
        return Bitmap.createScaledBitmap(src, (src.width * factor).toInt(), (src.height * factor).toInt(), true)
    }
}
