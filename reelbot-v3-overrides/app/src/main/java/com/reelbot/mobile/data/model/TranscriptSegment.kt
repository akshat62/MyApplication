package com.reelbot.mobile.data.model

/** A single timestamped chunk of recognized speech, as returned by the transcription engine. */
data class TranscriptSegment(
    val startMs: Long,
    val endMs: Long,
    val text: String
)
