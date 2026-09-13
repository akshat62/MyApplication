package com.reelbot.mobile.data.repository

import com.reelbot.mobile.data.db.ReelBotDatabase
import com.reelbot.mobile.data.db.entity.ReelJobEntity
import com.reelbot.mobile.data.db.entity.SourceVideoEntity
import com.reelbot.mobile.data.model.JobStatus
import kotlinx.coroutines.flow.Flow

/**
 * Single source of truth for everything about jobs and source videos. ViewModels talk to
 * this, never to the DAOs directly, so the persistence layer can change without touching UI code.
 */
class ReelRepository(private val db: ReelBotDatabase) {

    private val jobDao = db.reelJobDao()
    private val videoDao = db.sourceVideoDao()

    fun observeAllJobs(): Flow<List<ReelJobEntity>> = jobDao.observeAll()

    fun observeQueue(): Flow<List<ReelJobEntity>> = jobDao.observeAll()

    fun observeCount(status: JobStatus): Flow<Int> = jobDao.observeCountByStatus(status)
    fun observeTotalCount(): Flow<Int> = jobDao.observeTotalCount()
    fun observeRecentVideos(limit: Int = 10): Flow<List<SourceVideoEntity>> =
        videoDao.observeRecent(limit)

    suspend fun getJob(id: String): ReelJobEntity? = jobDao.getById(id)
    suspend fun getJobsForImport(importId: String): List<ReelJobEntity> = jobDao.getBySourceImportId(importId)
    suspend fun saveJob(job: ReelJobEntity) = jobDao.upsert(job)
    suspend fun saveJobs(jobs: List<ReelJobEntity>) = jobDao.upsertAll(jobs)
    suspend fun deleteJob(id: String) = jobDao.delete(id)
    suspend fun saveSourceVideo(video: SourceVideoEntity) = videoDao.insert(video)
    suspend fun getSourceVideo(importId: String): SourceVideoEntity? = videoDao.getById(importId)

    companion object {
        @Volatile private var instance: ReelRepository? = null

        fun getInstance(db: ReelBotDatabase): ReelRepository =
            instance ?: synchronized(this) {
                instance ?: ReelRepository(db).also { instance = it }
            }
    }
}
