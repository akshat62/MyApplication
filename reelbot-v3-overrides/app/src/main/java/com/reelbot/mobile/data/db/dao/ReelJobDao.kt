package com.reelbot.mobile.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.reelbot.mobile.data.db.entity.ReelJobEntity
import com.reelbot.mobile.data.model.JobStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface ReelJobDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(job: ReelJobEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(jobs: List<ReelJobEntity>)

    @Update
    suspend fun update(job: ReelJobEntity)

    @Query("SELECT * FROM reel_jobs WHERE id = :id")
    suspend fun getById(id: String): ReelJobEntity?

    @Query("SELECT * FROM reel_jobs WHERE sourceImportId = :importId ORDER BY createdAtEpochMs ASC")
    suspend fun getBySourceImportId(importId: String): List<ReelJobEntity>

    @Query("SELECT * FROM reel_jobs ORDER BY createdAtEpochMs DESC")
    fun observeAll(): Flow<List<ReelJobEntity>>

    /** Queue screen: everything still awaiting a human decision or mid-flight. */
    @Query(
        "SELECT * FROM reel_jobs WHERE status IN (:statuses) ORDER BY createdAtEpochMs DESC"
    )
    fun observeByStatuses(statuses: List<JobStatus>): Flow<List<ReelJobEntity>>

    @Query("SELECT COUNT(*) FROM reel_jobs WHERE status = :status")
    fun observeCountByStatus(status: JobStatus): Flow<Int>

    @Query("SELECT COUNT(*) FROM reel_jobs")
    fun observeTotalCount(): Flow<Int>

    @Query("DELETE FROM reel_jobs WHERE id = :id")
    suspend fun delete(id: String)
}
