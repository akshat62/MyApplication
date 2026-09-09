package com.reelbot.mobile.ai

import com.reelbot.mobile.data.model.ClipCandidate
import com.reelbot.mobile.data.model.FailureReason
import com.reelbot.mobile.data.model.TranscriptSegment
import java.util.Locale

/**
 * Deterministic, explainable clip-scoring heuristic — no LLM call, no network, runs
 * instantly on-device. This is deliberately not "fake AI": it's a real, inspectable
 * scoring function over genuine transcript signals (hook words, questions, numbers,
 * emphasis, pause structure, sentence-boundary alignment), the same category of approach
 * V2 used, extended here with a 0-100 normalized score and stronger overlap rejection.
 *
 * A future increment can swap this for an on-device small-LLM ranker without changing
 * the ClipCandidate contract downstream.
 */
class HighlightEngine {

    private val hookWords = listOf(
        "how", "why", "secret", "mistake", "important", "best", "never", "must", "top",
        "truth", "imagine", "here's", "remember", "biggest", "nobody", "wrong", "actually"
    )
    private val stopWords = setOf(
        "about", "there", "their", "would", "could", "should", "which", "where", "these",
        "those", "because", "really", "your", "with", "from", "that", "this", "have",
        "will", "they"
    )

    fun select(
        segments: List<TranscriptSegment>,
        count: Int,
        targetMs: Long
    ): List<ClipCandidate> {
        if (segments.isEmpty()) throw PipelineException(FailureReason.NO_SPEECH_DETECTED)

        val rawCandidates = segments.indices
            .map { buildCandidate(segments, it, targetMs) }
            .filter { it.endMs - it.startMs >= MIN_CLIP_MS }

        val maxRaw = rawCandidates.maxOfOrNull { it.rawScore } ?: 1.0
        val normalized = rawCandidates.sortedByDescending { it.rawScore }

        val picked = mutableListOf<ScoredCandidate>()
        for (candidate in normalized) {
            val overlapsExisting = picked.any { overlapRatio(it, candidate) > MAX_OVERLAP }
            if (!overlapsExisting) picked.add(candidate)
            if (picked.size == count) break
        }
        if (picked.isEmpty() && rawCandidates.isNotEmpty()) picked.add(rawCandidates.first())

        return picked
            .sortedBy { it.startMs }
            .map { toClipCandidate(it, maxRaw) }
    }

    private data class ScoredCandidate(
        val startMs: Long,
        val endMs: Long,
        val rawScore: Double,
        val part: List<TranscriptSegment>,
        val text: String
    )

    private fun toClipCandidate(candidate: ScoredCandidate, maxRawScore: Double): ClipCandidate {
        val normalizedScore = if (maxRawScore <= 0) 50
        else ((candidate.rawScore / maxRawScore) * 100).toInt().coerceIn(1, 100)
        val title = makeTitleFrom(candidate.text)
        val hashtags = makeHashtagsFrom(candidate.text)
        val caption = "$title\n\n${hashtags.joinToString(" ")} #reels #shorts"
        return ClipCandidate(
            startMs = candidate.startMs,
            endMs = candidate.endMs,
            score = normalizedScore,
            title = title,
            caption = caption,
            hashtags = hashtags,
            transcriptExcerpt = candidate.text,
            segments = candidate.part
        )
    }

    private fun buildCandidate(all: List<TranscriptSegment>, startIndex: Int, targetMs: Long): ScoredCandidate {
        val start = all[startIndex].startMs
        val maxEnd = start + targetMs

        val part = all.drop(startIndex).takeWhile { it.startMs < maxEnd }
            .ifEmpty { listOf(all[startIndex]) }

        val graceEnd = maxEnd + SENTENCE_GRACE_MS
        val extendedPart = all.drop(startIndex)
            .takeWhile { it.startMs < graceEnd }
            .ifEmpty { part }
        val boundaryPart = trimToSentenceBoundary(extendedPart, part)

        val end = boundaryPart.last().endMs.coerceAtMost(all.last().endMs)
        val text = boundaryPart.joinToString(" ") { it.text.trim() }
            .replace(Regex("\\s+"), " ")
            .trim()

        val openingWindow = boundaryPart.takeWhile { it.startMs < start + OPENING_WINDOW_MS }
        val opening = openingWindow.joinToString(" ") { it.text }.lowercase(Locale.ROOT)

        var score = text.length / 80.0
        score += hookWords.count { opening.contains(it) } * 2.3
        score += opening.count { it == '?' } * 1.8
        score += Regex("\\b\\d+[\\d,.%]*\\b").findAll(text).count() * 0.6
        score += text.count { it == '!' } * 0.5

        val gaps = boundaryPart.zipWithNext().count { (a, b) -> b.startMs - a.endMs > 2500 }
        score -= gaps * 1.0

        if (text.length in 240..850) score += 2.0
        if (text.trimEnd().lastOrNull() in listOf('.', '!', '?')) score += 1.5
        if (text.firstOrNull()?.isLowerCase() == true) score -= 1.0

        return ScoredCandidate(start, end, score.coerceAtLeast(0.01), boundaryPart, text)
    }

    private fun trimToSentenceBoundary(
        extended: List<TranscriptSegment>,
        fallback: List<TranscriptSegment>
    ): List<TranscriptSegment> {
        val idx = extended.indexOfFirst { it.text.trimEnd().lastOrNull() in listOf('.', '!', '?') }
        return if (idx >= 0) extended.subList(0, idx + 1) else fallback
    }

    private fun overlapRatio(a: ScoredCandidate, b: ScoredCandidate): Double {
        val overlap = (minOf(a.endMs, b.endMs) - maxOf(a.startMs, b.startMs)).coerceAtLeast(0)
        val shortest = minOf(a.endMs - a.startMs, b.endMs - b.startMs).coerceAtLeast(1)
        return overlap.toDouble() / shortest
    }

    private fun makeTitleFrom(text: String): String {
        val sentence = Regex("(?<=[.!?])\\s+").split(text).firstOrNull { it.length > 18 } ?: text
        return sentence.trim().take(72).trimEnd(',', '.', ' ')
    }

    private fun makeHashtagsFrom(text: String, limit: Int = 5): List<String> {
        val words = Regex("[A-Za-z][A-Za-z'-]{4,}").findAll(text.lowercase(Locale.ROOT))
            .map { it.value }
            .filterNot { it in stopWords }
        return words.groupingBy { it }.eachCount()
            .entries.sortedByDescending { it.value }
            .take(limit)
            .map { "#" + it.key.replace("'", "") }
    }

    companion object {
        private const val MIN_CLIP_MS = 15_000L
        private const val MAX_OVERLAP = 0.25
        private const val SENTENCE_GRACE_MS = 5_000L
        private const val OPENING_WINDOW_MS = 7_000L
    }
}
