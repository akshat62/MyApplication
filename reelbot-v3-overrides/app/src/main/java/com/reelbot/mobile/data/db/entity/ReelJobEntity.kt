package com.reelbot.mobile.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.reelbot.mobile.data.db.Converters
import com.reelbot.mobile.data.model.FailureReason
import com.reelbot.mobile.data.model.JobStatus
import java.util.UUID

/**
 * One row per generated Reel candidate. A single source video import produces N of these
 * (one per requested clip). Persisted so the approval queue, analytics counts, and
 * in-flight processing all survive app restarts and process death.
 */
@Entity(tableName = "reel_jobs")
@TypeConverters(Converters::class)
data class ReelJobEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),

    /** Groups every clip generated from the same source video import together. */
    val sourceImportId: String,
    val sourceVideoUri: String,
    val sourceVideoDurationMs: Long,

    val status: JobStatus,
    val failureReason: FailureReason? = null,
    val failureDetail: String? = null,

    // --- Clip selection (filled once ANALYSING completes) ---
    val startMs: Long? = null,
    val endMs: Long? = null,
    val highlightScore: Double? = null,
    val transcriptExcerpt: String? = null,

    // --- Generated copy ---
    val hook: String? = null,
    val caption: String? = null,
    val hashtags: String? = null, // space-separated, e.g. "#fitness #mindset"

    // --- Render output (filled once RENDERING completes) ---
    val outputFilePath: String? = null,
    val thumbnailFilePath: String? = null,

    // --- Publishing ---
    val instagramMediaId: String? = null,
    val postedAtEpochMs: Long? = null,

    val createdAtEpochMs: Long = System.currentTimeMillis(),
    val updatedAtEpochMs: Long = System.currentTimeMillis()
)
