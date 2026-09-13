package com.reelbot.mobile.data.model

/**
 * The lifecycle of a single generated Reel candidate, from source video import through
 * to publication. Persisted on [com.reelbot.mobile.data.db.entity.ReelJobEntity] so it
 * survives process death and app restarts (Room-backed, not in-memory).
 *
 * Terminal states are FAILED, CANCELLED, REJECTED, and POSTED.
 */
enum class JobStatus {
    IMPORTED,
    AUDIO_EXTRACTION,
    TRANSCRIBING,
    TRANSCRIBED,
    ANALYSING,
    HIGHLIGHTS_READY,
    FACE_ANALYSIS,
    RENDERING,
    READY_FOR_REVIEW,
    APPROVED,
    POSTING,
    POSTED,
    FAILED,
    CANCELLED,
    REJECTED;

    val isTerminal: Boolean
        get() = this == POSTED || this == FAILED || this == CANCELLED || this == REJECTED

    val isProcessing: Boolean
        get() = this in setOf(
            AUDIO_EXTRACTION, TRANSCRIBING, ANALYSING, FACE_ANALYSIS, RENDERING, POSTING
        )
}

/** User-selectable target duration for a generated Reel. */
enum class ReelDuration(val seconds: Int) {
    SHORT_30(30),
    MEDIUM_45(45),
    LONG_60(60),
    EXTENDED_90(90);

    val milliseconds: Long get() = seconds * 1000L
}

/** A single processing step shown on the Create screen's progress UI. */
enum class PipelineStep(val label: String) {
    PREPARING_VIDEO("Preparing video"),
    EXTRACTING_AUDIO("Extracting audio"),
    TRANSCRIBING("Transcribing"),
    ANALYSING_TRANSCRIPT("Analysing transcript"),
    FINDING_HIGHLIGHTS("Finding highlights"),
    DETECTING_FACES("Detecting faces"),
    CREATING_CLIPS("Creating clips"),
    GENERATING_SUBTITLES("Generating subtitles"),
    RENDERING("Rendering"),
    PREPARING_CAPTIONS("Preparing captions")
}
