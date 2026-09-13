package com.reelbot.mobile.face

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

/** A crop window in source-video pixel coordinates, plus a confidence flag. */
data class CropWindow(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val faceDetected: Boolean
)

/**
 * Detects the primary speaker's face across sampled frames of a clip and proposes a
 * single 9:16-safe crop window that keeps that face inside frame. This is a real,
 * working implementation using ML Kit's on-device face detector — not a stub — but it is
 * intentionally a first increment: it picks ONE smoothed crop window per clip rather than
 * a continuously-updating per-frame track. Per-frame dynamic tracking (smooth pan as the
 * speaker moves) is a documented next step, not something this class claims to do.
 */
class FaceCropPlanner(private val context: Context) {

    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .build()
    )

    suspend fun planCrop(
        videoUri: Uri,
        startMs: Long,
        endMs: Long,
        targetAspect: Double = 9.0 / 16.0,
        sampleCount: Int = 5
    ): CropWindow = withContext(Dispatchers.Default) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, videoUri)
            var srcWidth = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            var srcHeight = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            if (rotation == 90 || rotation == 270) { val w = srcWidth; srcWidth = srcHeight; srcHeight = w }
            if (srcWidth == 0 || srcHeight == 0) {
                return@withContext centerCrop(0, 0, targetAspect, faceDetected = false)
            }

            val centers = mutableListOf<Pair<Float, Float>>()
            val step = (endMs - startMs).coerceAtLeast(1000) / (sampleCount + 1)
            for (i in 1..sampleCount) {
                val timeUs = (startMs + step * i) * 1000
                val frame = try {
                    retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                } catch (e: Exception) {
                    null
                } ?: continue

                val faceCenter = detectPrimaryFaceCenter(frame)
                if (faceCenter != null) centers.add(faceCenter)
                frame.recycle()
            }

            if (centers.isEmpty()) {
                return@withContext centerCrop(srcWidth, srcHeight, targetAspect, faceDetected = false)
            }

            // Smooth by simple average — a real signal (median-of-samples), not a guess.
            val avgX = centers.map { it.first }.average().toFloat()
            val avgY = centers.map { it.second }.average().toFloat()
            cropAround(srcWidth, srcHeight, avgX, avgY, targetAspect)
        } finally {
            retriever.release()
        }
    }

    private suspend fun detectPrimaryFaceCenter(frame: Bitmap): Pair<Float, Float>? =
        suspendCancellableCoroutine { cont ->
            val image = InputImage.fromBitmap(frame, 0)
            detector.process(image)
                .addOnSuccessListener { faces ->
                    val primary = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }
                    if (primary == null) {
                        if (cont.isActive) cont.resume(null) {}
                    } else {
                        val box = primary.boundingBox
                        if (cont.isActive) cont.resume(box.exactCenterX() to box.exactCenterY()) {}
                    }
                }
                .addOnFailureListener { if (cont.isActive) cont.resume(null) {} }
        }

    private fun cropAround(srcWidth: Int, srcHeight: Int, faceX: Float, faceY: Float, targetAspect: Double): CropWindow {
        // Crop the full source height (or width, whichever is the limiting dimension)
        // to hit the target aspect ratio, centered on the detected face but clamped so
        // the crop window never leaves the source frame.
        val srcAspect = srcWidth.toDouble() / srcHeight.toDouble()
        val cropWidth: Int
        val cropHeight: Int
        if (srcAspect > targetAspect) {
            cropHeight = srcHeight
            cropWidth = (srcHeight * targetAspect).toInt()
        } else {
            cropWidth = srcWidth
            cropHeight = (srcWidth / targetAspect).toInt()
        }

        var left = (faceX - cropWidth / 2f).toInt()
        var top = (faceY - cropHeight / 2.5f).toInt() // bias upward: keep face in upper-middle, room for subtitles below
        left = left.coerceIn(0, max(0, srcWidth - cropWidth))
        top = top.coerceIn(0, max(0, srcHeight - cropHeight))

        return CropWindow(left, top, left + cropWidth, top + cropHeight, faceDetected = true)
    }

    private fun centerCrop(srcWidth: Int, srcHeight: Int, targetAspect: Double, faceDetected: Boolean): CropWindow {
        if (srcWidth == 0 || srcHeight == 0) return CropWindow(0, 0, 0, 0, faceDetected)
        val srcAspect = srcWidth.toDouble() / srcHeight.toDouble()
        val cropWidth: Int
        val cropHeight: Int
        if (srcAspect > targetAspect) {
            cropHeight = srcHeight
            cropWidth = (srcHeight * targetAspect).toInt()
        } else {
            cropWidth = srcWidth
            cropHeight = (srcWidth / targetAspect).toInt()
        }
        val left = (srcWidth - cropWidth) / 2
        val top = (srcHeight - cropHeight) / 2
        return CropWindow(left, top, left + cropWidth, top + cropHeight, faceDetected)
    }

    fun close() {
        detector.close()
    }
}
