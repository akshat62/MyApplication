package com.reelbot.mobile.export

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import com.reelbot.mobile.data.model.TranscriptSegment

data class SubtitleStyle(
    val fontSizeSp: Float = 52f,
    val maxWordsPerLine: Int = 5,
    val bottomSafeMarginFraction: Float = 0.16f,
    val textColor: Int = Color.WHITE,
    val backgroundColor: Int = Color.argb(160, 0, 0, 0)
)

@UnstableApi
class SubtitleOverlay(
    private val segments: List<TranscriptSegment>,
    private val outputWidth: Int,
    private val outputHeight: Int,
    private val subtitleStyle: SubtitleStyle = SubtitleStyle()
) : BitmapOverlay() {

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = subtitleStyle.textColor
        textSize = subtitleStyle.fontSizeSp * 3f
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT_BOLD, android.graphics.Typeface.BOLD)
        textAlign = Paint.Align.CENTER
        setShadowLayer(6f, 0f, 2f, Color.BLACK)
    }

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = subtitleStyle.backgroundColor
    }

    private var cachedBitmap: Bitmap? = null
    private var cachedLine: String? = null

    override fun getBitmap(presentationTimeUs: Long): Bitmap {
        val presentationMs = presentationTimeUs / 1000
        val activeLine = activeLineFor(presentationMs)
        if (activeLine == cachedLine && cachedBitmap != null) return cachedBitmap!!

        val bitmap = Bitmap.createBitmap(outputWidth, outputHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        if (!activeLine.isNullOrBlank()) drawCaption(canvas, activeLine)

        cachedLine = activeLine
        cachedBitmap = bitmap
        return bitmap
    }

    private fun activeLineFor(presentationMs: Long): String? {
        val segment = segments.firstOrNull { presentationMs in it.startMs..it.endMs } ?: return null
        return wrapToMaxWords(segment.text, subtitleStyle.maxWordsPerLine)
    }

    private fun wrapToMaxWords(text: String, maxWords: Int): String {
        val words = text.trim().split(Regex("\\s+"))
        return words.take(maxWords).joinToString(" ")
    }

    private fun drawCaption(canvas: Canvas, line: String) {
        val width = (outputWidth * 0.82f).toInt()
        val paint = android.text.TextPaint(textPaint).apply { textAlign = Paint.Align.LEFT }
        var layout: android.text.StaticLayout
        do {
            layout = android.text.StaticLayout.Builder.obtain(line, 0, line.length, paint, width)
                .setAlignment(android.text.Layout.Alignment.ALIGN_CENTER)
                .setIncludePad(false).build()
            if (layout.height <= outputHeight * 0.45f || paint.textSize <= 24f) break
            paint.textSize -= 2f
        } while (true)
        val x = (outputWidth - width) / 2f
        val y = outputHeight * (1 - style.bottomSafeMarginFraction) - layout.height
        canvas.drawRoundRect(RectF(x - 16, y - 12, x + width + 16, y + layout.height + 12), 20f, 20f, backgroundPaint)
        canvas.save()
        canvas.translate(x, y)
        layout.draw(canvas)
        canvas.restore()
    }
}
