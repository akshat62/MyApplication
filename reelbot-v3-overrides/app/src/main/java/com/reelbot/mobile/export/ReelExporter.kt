package com.reelbot.mobile.export

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaItem.ClippingConfiguration
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Crop
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.Presentation
import androidx.media3.effect.TextureOverlay
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import com.google.common.collect.ImmutableList
import com.reelbot.mobile.ai.PipelineException
import com.reelbot.mobile.data.model.FailureReason
import com.reelbot.mobile.data.model.TranscriptSegment
import com.reelbot.mobile.face.CropWindow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.media3.common.MimeTypes
import java.io.File
import kotlin.coroutines.resumeWithException

private const val OUTPUT_WIDTH = 1080
private const val OUTPUT_HEIGHT = 1920

@UnstableApi
class ReelExporter(private val context: Context) {

    suspend fun export(
        sourceVideoUri: Uri,
        startMs: Long,
        endMs: Long,
        srcWidth: Int,
        srcHeight: Int,
        cropWindow: CropWindow,
        segments: List<TranscriptSegment>,
        subtitlesEnabled: Boolean,
        outputFile: File
    ): File = withContext(Dispatchers.Main.immediate) { suspendCancellableCoroutine { cont ->
        val mediaItem = MediaItem.Builder()
            .setUri(sourceVideoUri)
            .setClippingConfiguration(
                ClippingConfiguration.Builder()
                    .setStartPositionMs(startMs)
                    .setEndPositionMs(endMs)
                    .build()
            )
            .build()

        val videoEffects = mutableListOf<androidx.media3.common.Effect>()
        videoEffects.add(cropEffectFor(cropWindow, srcWidth, srcHeight))
        videoEffects.add(Presentation.createForWidthAndHeight(OUTPUT_WIDTH, OUTPUT_HEIGHT, Presentation.LAYOUT_SCALE_TO_FIT))
        if (subtitlesEnabled && segments.isNotEmpty()) {
            val overlay = SubtitleOverlay(segments, OUTPUT_WIDTH, OUTPUT_HEIGHT)
            videoEffects.add(OverlayEffect(listOf<TextureOverlay>(overlay)))
        }
        videoEffects.add(Presentation.createForWidthAndHeight(OUTPUT_WIDTH, OUTPUT_HEIGHT, Presentation.LAYOUT_SCALE_TO_FIT))

        val editedMediaItem = EditedMediaItem.Builder(mediaItem)
            .setEffects(Effects(ImmutableList.of(), videoEffects))
            .build()

        val transformer = Transformer.Builder(context)
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            .setAudioMimeType(MimeTypes.AUDIO_AAC)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    if (cont.isActive) {
                        val result = runCatching { validateOutput(outputFile); outputFile }
                        cont.resumeWith(result)
                    }
                }

                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException
                ) {
                    if (cont.isActive) {
                        cont.resumeWithException(
                            PipelineException(FailureReason.RENDER_FAILED, exportException.message)
                        )
                    }
                }
            })
            .build()

        outputFile.parentFile?.mkdirs()
        outputFile.delete()
        transformer.start(editedMediaItem, outputFile.absolutePath)
        cont.invokeOnCancellation { android.os.Handler(android.os.Looper.getMainLooper()).post { transformer.cancel(); outputFile.delete() } }
    } }

    private fun validateOutput(file: File) {
        require(file.isFile && file.length() > 0) { "Export did not create a video file." }
        val retriever = android.media.MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            require((retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0) > 0) { "Export has no duration." }
            val frame = retriever.getFrameAtTime(0) ?: error("Exported video cannot be decoded.")
            frame.recycle()
        } finally { retriever.release() }
    }

    private fun cropEffectFor(window: CropWindow, srcWidth: Int, srcHeight: Int): Crop {
        if (srcWidth <= 0 || srcHeight <= 0) return Crop(-1f, 1f, -1f, 1f)
        val left = (window.left.toFloat() / srcWidth) * 2f - 1f
        val right = (window.right.toFloat() / srcWidth) * 2f - 1f
        val top = 1f - (window.top.toFloat() / srcHeight) * 2f
        val bottom = 1f - (window.bottom.toFloat() / srcHeight) * 2f
        return Crop(left, right, bottom, top)
    }
}
