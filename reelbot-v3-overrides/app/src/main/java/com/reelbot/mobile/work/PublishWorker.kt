package com.reelbot.mobile.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.reelbot.mobile.ReelBotApp
import com.reelbot.mobile.ai.PipelineException
import com.reelbot.mobile.data.model.FailureReason
import com.reelbot.mobile.data.model.JobStatus
import com.reelbot.mobile.instagram.InstagramPublisher
import com.reelbot.mobile.instagram.TokenVault
import java.io.File

/** Runs the real Meta publish flow for one job. Never marks a job POSTED unless Meta
 *  actually returned a media id (see InstagramPublisher). */
class PublishWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    private val repo = (applicationContext as ReelBotApp).repository
    private val tokenVault = TokenVault(applicationContext)
    private val publisher = InstagramPublisher()

    override suspend fun doWork(): Result {
        val jobId = inputData.getString(KEY_JOB_ID) ?: return Result.failure()
        val job = repo.getJob(jobId) ?: return Result.failure()

        if (job.status != JobStatus.APPROVED) return Result.failure()
        val session = tokenVault.currentSession()
        if (session == null || tokenVault.isExpired()) {
            repo.saveJob(
                job.copy(
                    status = JobStatus.FAILED,
                    failureReason = FailureReason.TOKEN_EXPIRED,
                    failureDetail = "Instagram isn't connected, or the connection expired. Reconnect in Settings.",
                    updatedAtEpochMs = System.currentTimeMillis()
                )
            )
            return Result.failure()
        }

        val outputPath = job.outputFilePath
        if (outputPath == null || !File(outputPath).exists()) {
            repo.saveJob(
                job.copy(
                    status = JobStatus.FAILED,
                    failureReason = FailureReason.RENDER_FAILED,
                    failureDetail = "No rendered file found for this job.",
                    updatedAtEpochMs = System.currentTimeMillis()
                )
            )
            return Result.failure()
        }

        repo.saveJob(job.copy(status = JobStatus.POSTING, updatedAtEpochMs = System.currentTimeMillis()))

        return try {
            val result = publisher.publish(File(outputPath), listOfNotNull(job.caption, job.hashtags).joinToString("\n\n"), session)
            repo.saveJob(
                job.copy(
                    status = JobStatus.POSTED,
                    instagramMediaId = result.mediaId,
                    postedAtEpochMs = System.currentTimeMillis(),
                    updatedAtEpochMs = System.currentTimeMillis()
                )
            )
            Result.success()
        } catch (e: PipelineException) {
            repo.saveJob(
                job.copy(
                    status = JobStatus.FAILED,
                    failureReason = e.reason,
                    failureDetail = e.message,
                    updatedAtEpochMs = System.currentTimeMillis()
                )
            )
            Result.failure()
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            android.util.Log.e("ReelBotPublish", "Publishing failed", e)
            repo.saveJob(
                job.copy(
                    status = JobStatus.FAILED,
                    failureReason = FailureReason.UPLOAD_FAILED,
                    failureDetail = e.message,
                    updatedAtEpochMs = System.currentTimeMillis()
                )
            )
            Result.failure()
        }
    }

    companion object {
        const val KEY_JOB_ID = "job_id"

        fun enqueue(context: Context, jobId: String) {
            val request = OneTimeWorkRequestBuilder<PublishWorker>()
                .setInputData(Data.Builder().putString(KEY_JOB_ID, jobId).build())
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "publish_$jobId",
                ExistingWorkPolicy.KEEP,
                request
            )
        }
    }
}
