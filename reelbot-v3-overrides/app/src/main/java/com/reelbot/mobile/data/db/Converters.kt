package com.reelbot.mobile.data.db

import androidx.room.TypeConverter
import com.reelbot.mobile.data.model.FailureReason
import com.reelbot.mobile.data.model.JobStatus

class Converters {
    @TypeConverter
    fun fromJobStatus(value: JobStatus): String = value.name

    @TypeConverter
    fun toJobStatus(value: String): JobStatus = JobStatus.valueOf(value)

    @TypeConverter
    fun fromFailureReason(value: FailureReason?): String? = value?.name

    @TypeConverter
    fun toFailureReason(value: String?): FailureReason? = value?.let { FailureReason.valueOf(it) }
}
