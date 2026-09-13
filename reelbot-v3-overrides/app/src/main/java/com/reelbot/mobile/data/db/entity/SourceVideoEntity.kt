package com.reelbot.mobile.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** One row per video the user has submitted for processing. */
@Entity(tableName = "source_videos")
data class SourceVideoEntity(
    @PrimaryKey val importId: String,
    val uri: String,
    val displayName: String,
    val durationMs: Long,
    val fileSizeBytes: Long,
    val requestedClipCount: Int,
    val requestedDurationSeconds: Int,
    val importedAtEpochMs: Long = System.currentTimeMillis()
)
