package com.reelbot.mobile.ai

import com.reelbot.mobile.model.ClipCandidate
import com.reelbot.mobile.model.TranscriptSegment

object SmartClipEngine {
    private val hooks = listOf("how", "why", "secret", "mistake", "important", "best", "never", "must", "top", "truth", "imagine", "here's", "remember")
    private val stop = setOf("about","there","their","would","could","should","which","where","these","those","because","really","your","with","from","that","this","have","will","they")

    fun select(segments: List<TranscriptSegment>, count: Int = 3, targetMs: Long = 45_000L): List<ClipCandidate> {
        require(segments.isNotEmpty()) { "No speech was detected" }
        val candidates = segments.indices.map { i -> buildCandidate(segments, i, targetMs) }
            .filter { it.endMs - it.startMs >= 15_000 }
            .sortedByDescending { it.score }
        val picked = mutableListOf<ClipCandidate>()
        for (c in candidates) {
            if (picked.none { overlapRatio(it, c) > 0.25 }) picked += c
            if (picked.size == count) break
        }
        if (picked.isEmpty()) picked += buildCandidate(segments, 0, targetMs)
        return picked.sortedBy { it.startMs }
    }

    private fun buildCandidate(all: List<TranscriptSegment>, startIndex: Int, targetMs: Long): ClipCandidate {
        val start = all[startIndex].startMs
        val maxEnd = start + targetMs
        val part = all.drop(startIndex).takeWhile { it.startMs < maxEnd }.ifEmpty { listOf(all[startIndex]) }
        val end = part.last().endMs.coerceAtMost(maxEnd + 5_000)
        val text = part.joinToString(" ") { it.text }.replace(Regex("\\s+"), " ").trim()
        val first = part.takeWhile { it.startMs < start + 7_000 }.joinToString(" ") { it.text }.lowercase()
        var score = text.length / 80.0
        score += hooks.count { first.contains(it) } * 2.3
        score += first.count { it == '?' } * 1.8
        score += Regex("\\b\\d+[\\d,.%]*\\b").findAll(text).count() * 0.6
        score += text.count { it == '!' } * 0.5
        val gaps = part.zipWithNext().count { (a,b) -> b.startMs - a.endMs > 2_500 }
        score -= gaps * 1.0
        if (text.length in 240..850) score += 2.0
        val title = makeTitle(text)
        val caption = makeCaption(text, title)
        return ClipCandidate(start, end, score, title, caption, part)
    }

    private fun makeTitle(text: String): String {
        val sentence = text.split(Regex("(?<=[.!?])\\s+")).firstOrNull { it.length > 18 } ?: text
        return sentence.trim().take(72).trimEnd(',', '.', ' ')
    }

    private fun makeCaption(text: String, title: String): String {
        val words = Regex("[A-Za-z][A-Za-z'-]{4,}").findAll(text.lowercase()).map { it.value }.filterNot { it in stop }
        val tags = words.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(5).map { "#${it.key.replace("'", "")}" }
        return "$title\n\n${tags.joinToString(" ")} #reels #shorts"
    }

    private fun overlapRatio(a: ClipCandidate, b: ClipCandidate): Double {
        val overlap = (minOf(a.endMs,b.endMs) - maxOf(a.startMs,b.startMs)).coerceAtLeast(0)
        val shortest = minOf(a.endMs-a.startMs,b.endMs-b.startMs).coerceAtLeast(1)
        return overlap.toDouble()/shortest
    }
}
