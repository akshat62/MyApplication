package com.reelbot.mobile.data.model

/**
 * One candidate Reel proposed by the highlight engine, before rendering. `score` is
 * normalized to 0-100 so it can be shown to the user as "Highlight Score: 87/100" per spec.
 */
data class ClipCandidate(
    val startMs: Long,
    val endMs: Long,
    val score: Int,
    val title: String,
    val caption: String,
    val hashtags: List<String>,
    val transcriptExcerpt: String,
    val segments: List<TranscriptSegment>
)
