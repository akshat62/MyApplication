package com.reelbot.mobile.export

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.CanvasOverlay
import com.reelbot.mobile.model.TranscriptSegment

@UnstableApi
class CaptionOverlay(private val clipStartMs: Long, private val segments: List<TranscriptSegment>) : CanvasOverlay(true) {
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 54f; textAlign = Paint.Align.CENTER; typeface = android.graphics.Typeface.DEFAULT_BOLD }
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xCC000000.toInt() }

    override fun onDraw(canvas: Canvas, presentationTimeUs: Long) {
        val absoluteMs = clipStartMs + presentationTimeUs / 1000L
        val text = segments.firstOrNull { absoluteMs in it.startMs..it.endMs }?.text?.trim().orEmpty()
        if (text.isBlank()) return
        val lines = wrap(text, textPaint, canvas.width * 0.84f).take(3)
        val lineHeight = textPaint.fontSpacing
        val blockHeight = lines.size * lineHeight + 36f
        val centerX = canvas.width / 2f
        val bottom = canvas.height * 0.86f
        val top = bottom - blockHeight
        canvas.drawRoundRect(RectF(canvas.width * .06f, top - 18, canvas.width * .94f, bottom + 18), 28f, 28f, bgPaint)
        lines.forEachIndexed { index, line -> canvas.drawText(line, centerX, top + (index + 1) * lineHeight - 8f, textPaint) }
    }

    private fun wrap(text: String, paint: Paint, maxWidth: Float): List<String> {
        val words = text.split(Regex("\\s+")); val lines = mutableListOf<String>(); var current = ""
        for (word in words) {
            val test = if (current.isEmpty()) word else "$current $word"
            if (paint.measureText(test) <= maxWidth) current = test else { if (current.isNotEmpty()) lines += current; current = word }
        }
        if (current.isNotEmpty()) lines += current
        return lines
    }
}
