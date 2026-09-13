package com.reelbot.mobile.ui.queue

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reelbot.mobile.data.db.entity.ReelJobEntity
import com.reelbot.mobile.data.model.JobStatus
import com.reelbot.mobile.data.repository.ReelRepository
import com.reelbot.mobile.work.PublishWorker
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class QueueViewModel(
    private val repository: ReelRepository,
    private val appContext: Context
) : ViewModel() {

    val jobs: StateFlow<List<ReelJobEntity>> = repository.observeQueue()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun reject(jobId: String) = updateStatus(jobId, JobStatus.REJECTED)
    fun approve(jobId: String) = updateStatus(jobId, JobStatus.APPROVED)
    fun delete(jobId: String) = viewModelScope.launch {
        val job = repository.getJob(jobId) ?: return@launch
        if (job.status.isProcessing || job.status == JobStatus.IMPORTED) return@launch
        job.outputFilePath?.let { java.io.File(it).delete() }
        job.thumbnailFilePath?.let { java.io.File(it).delete() }
        repository.deleteJob(jobId)
    }
    fun regenerate(jobId: String) = viewModelScope.launch {
        val job = repository.getJob(jobId) ?: return@launch
        if (job.status.isProcessing || job.status == JobStatus.IMPORTED) return@launch
        val source = repository.getSourceVideo(job.sourceImportId) ?: return@launch
        val id = java.util.UUID.randomUUID().toString()
        repository.saveSourceVideo(source.copy(importId = id, requestedClipCount = 1))
        repository.saveJob(ReelJobEntity(sourceImportId = id, sourceVideoUri = job.sourceVideoUri,
            sourceVideoDurationMs = job.sourceVideoDurationMs, status = JobStatus.IMPORTED))
        com.reelbot.mobile.work.PipelineScheduler.enqueue(appContext, id)
    }

    fun saveText(jobId: String, caption: String, hashtags: String) = viewModelScope.launch {
        val job = repository.getJob(jobId) ?: return@launch
        if (job.status in listOf(JobStatus.READY_FOR_REVIEW, JobStatus.APPROVED, JobStatus.REJECTED)) {
            repository.saveJob(job.copy(caption = caption, hashtags = hashtags, updatedAtEpochMs = System.currentTimeMillis()))
        }
    }

    fun postNow(jobId: String) {
        PublishWorker.enqueue(appContext, jobId)
    }

    private fun updateStatus(jobId: String, status: JobStatus) {
        viewModelScope.launch {
            val job = repository.getJob(jobId) ?: return@launch
            if (job.status !in listOf(JobStatus.READY_FOR_REVIEW, JobStatus.APPROVED, JobStatus.REJECTED)) return@launch
            if (status == JobStatus.APPROVED && job.outputFilePath?.let { java.io.File(it).isFile } != true) return@launch
            repository.saveJob(job.copy(status = status, updatedAtEpochMs = System.currentTimeMillis()))
        }
    }
}
