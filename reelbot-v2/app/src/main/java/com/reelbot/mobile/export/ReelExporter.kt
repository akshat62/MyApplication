package com.reelbot.mobile.export

import android.content.Context
import android.graphics.Matrix
import androidx.core.net.toUri
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.MatrixTransformation
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import com.reelbot.mobile.model.ClipCandidate
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@UnstableApi
class ReelExporter(private val context: Context) {
    suspend fun export(source: File, clip: ClipCandidate, faceCenterX: Float, index: Int): File = suspendCancellableCoroutine { cont ->
        val dir = File(context.getExternalFilesDir(null), "ReelBot").apply { mkdirs() }
        val output = File(dir, "reel_${System.currentTimeMillis()}_${index}.mp4")
        val mediaItem = MediaItem.Builder().setUri(source.toUri()).setClippingConfiguration(MediaItem.ClippingConfiguration.Builder().setStartPositionMs(clip.startMs).setEndPositionMs(clip.endMs).build()).build()
        val shift = object : MatrixTransformation {
            override fun getMatrix(presentationTimeUs: Long): Matrix = Matrix().apply {
                val ndcShift = ((0.5f - faceCenterX) * 1.15f).coerceIn(-0.55f, 0.55f)
                setTranslate(ndcShift, 0f)
            }
        }
        val videoEffects: List<Effect> = listOf(
            shift,
            Presentation.createForWidthAndHeight(1080, 1920, Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP),
            OverlayEffect(listOf(CaptionOverlay(clip.startMs, clip.segments)))
        )
        val edited = EditedMediaItem.Builder(mediaItem).setEffects(Effects(emptyList(), videoEffects)).build()
        val transformer = Transformer.Builder(context).setVideoMimeType(MimeTypes.VIDEO_H264).setAudioMimeType(MimeTypes.AUDIO_AAC)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, result: ExportResult) { if (cont.isActive) cont.resume(output) }
                override fun onError(composition: Composition, result: ExportResult, exception: ExportException) { output.delete(); if (cont.isActive) cont.resumeWithException(exception) }
            }).build()
        cont.invokeOnCancellation { transformer.cancel(); output.delete() }
        transformer.start(edited, output.absolutePath)
    }
}
