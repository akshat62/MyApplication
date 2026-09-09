package com.reelbot.mobile.model

data class TranscriptSegment(val startMs: Long, val endMs: Long, val text: String)

data class ClipCandidate(
    val startMs: Long,
    val endMs: Long,
    val score: Double,
    val title: String,
    val caption: String,
    val segments: List<TranscriptSegment>
)

enum class DraftState { PENDING, APPROVED, POSTED, FAILED }

data class ReelDraft(
    val id: String,
    val filePath: String,
    val startMs: Long,
    val endMs: Long,
    val title: String,
    val caption: String,
    var state: DraftState = DraftState.PENDING,
    var message: String = ""
)
