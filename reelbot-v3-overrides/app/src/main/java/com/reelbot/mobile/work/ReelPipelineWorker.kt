package com.reelbot.mobile.work

import android.app.NotificationManager
import android.content.Context
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.media3.common.util.UnstableApi
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.reelbot.mobile.R
import com.reelbot.mobile.ReelBotApp
import com.reelbot.mobile.ai.AudioExtractor
import com.reelbot.mobile.ai.HighlightEngine
import com.reelbot.mobile.ai.ModelManager
import com.reelbot.mobile.ai.ModelNotReadyException
import com.reelbot.mobile.ai.PipelineException
import com.reelbot.mobile.ai.TranscriptionEngine
import com.reelbot.mobile.data.db.entity.ReelJobEntity
import com.reelbot.mobile.data.model.ClipCandidate
import com.reelbot.mobile.data.model.FailureReason
import com.reelbot.mobile.data.model.JobStatus
import com.reelbot.mobile.data.model.TranscriptSegment
import com.reelbot.mobile.data.model.WhisperModelSpec
import com.reelbot.mobile.data.repository.ReelRepository
import com.reelbot.mobile.export.ReelExporter
import com.reelbot.mobile.face.FaceCropPlanner
import java.io.File

@UnstableApi
class ReelPipelineWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    private val app = appContext.applicationContext as ReelBotApp
    private val repo: ReelRepository = app.repository
    private val modelManager = app.modelManager
    private val audioExtractor = AudioExtractor()
    private val transcriptionEngine = TranscriptionEngine(modelManager)
    private val highlightEngine = HighlightEngine()
    private val faceCropPlanner = FaceCropPlanner(appContext)
    private val exporter = ReelExporter(appContext)

    override suspend fun getForegroundInfo(): ForegroundInfo = buildForegroundInfo("Starting…")

    override suspend fun doWork(): Result {
        val importId = inputData.getString(KEY_IMPORT_ID) ?: return Result.failure()
        return try {
            processImport(importId)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.e("ReelBotPipeline", "Processing failed", e)
            runCatching { failAll(repo.getJobsForImport(importId).filter { !it.status.isTerminal }, FailureReason.UNKNOWN, e.stackTraceToString()) }
            Result.failure()
        } finally {
            faceCropPlanner.close()
            File(applicationContext.cacheDir, "staging/$importId").deleteRecursively()
        }
    }

    private suspend fun processImport(importId: String): Result {
        setForeground(buildForegroundInfo("Preparing video"))

        val sourceVideo = repo.getSourceVideo(importId) ?: return Result.failure()
        // One-shot read (not a Flow subscription) — jobs belonging to this import, in
        // creation order, so index i maps deterministically to the i-th highlight
        // candidate found later.
        val myJobs = repo.getJobsForImport(importId).filter { it.status in listOf(JobStatus.IMPORTED, JobStatus.AUDIO_EXTRACTION, JobStatus.TRANSCRIBING, JobStatus.ANALYSING, JobStatus.HIGHLIGHTS_READY, JobStatus.FACE_ANALYSIS, JobStatus.RENDERING) }
        if (myJobs.isEmpty()) return Result.success()

        val sourceUri = Uri.parse(sourceVideo.uri)
        val stagingDir = File(applicationContext.cacheDir, "staging/$importId").apply { mkdirs() }
        val wavFile = File(stagingDir, "audio.wav")

        val segments: List<TranscriptSegment>
        try {
            markAll(myJobs, JobStatus.AUDIO_EXTRACTION)
            setForeground(buildForegroundInfo("Extracting audio"))
            val videoFile = copyUriToStaging(sourceUri, File(stagingDir, "source.mp4"))
            audioExtractor.extract(videoFile, wavFile)

            markAll(myJobs, JobStatus.TRANSCRIBING)
            setForeground(buildForegroundInfo("Transcribing"))
            // TODO(settings phase): read the user's selected model from Settings/DataStore
            // instead of hardcoding TINY. modelManager.refreshState must show INSTALLED
            // before this call — otherwise it throws ModelNotReadyException, which is
            // the correct, honest outcome, not a fallback transcript.
            modelManager.refreshState(WhisperModelSpec.BASE)
            segments = transcriptionEngine.transcribe(wavFile, WhisperModelSpec.BASE)
        } catch (e: ModelNotReadyException) {
            failAll(myJobs, FailureReason.MODEL_NOT_INSTALLED, e.message)
            return Result.failure()
        } catch (e: PipelineException) {
            failAll(myJobs, e.reason, e.message)
            return Result.failure()
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            android.util.Log.e("ReelBotPipeline", "Pipeline failed", e)
            failAll(myJobs, FailureReason.UNKNOWN, e.message)
            return Result.failure()
        }

        val candidates: List<ClipCandidate>
        try {
            markAll(myJobs, JobStatus.ANALYSING)
            setForeground(buildForegroundInfo("Finding highlights"))
            candidates = highlightEngine.select(
                segments,
                count = myJobs.size,
                targetMs = sourceVideo.requestedDurationSeconds * 1000L
            )
            markAll(myJobs, JobStatus.HIGHLIGHTS_READY)
        } catch (e: PipelineException) {
            failAll(myJobs, e.reason, e.message)
            return Result.failure()
        }

        // Reconcile: exactly one job row per candidate found. If the highlight engine
        // found fewer good candidates than requested, the surplus pre-created rows are
        // cancelled — never padded with fabricated duplicate clips.
        val pairedJobs = myJobs.zip(candidates)
        val surplus = myJobs.drop(candidates.size)
        surplus.forEach { repo.saveJob(it.copy(status = JobStatus.CANCELLED, updatedAtEpochMs = System.currentTimeMillis())) }

        pairedJobs.forEachIndexed { index, (job, candidate) ->
            setForeground(buildForegroundInfo("Rendering ${index + 1} of ${pairedJobs.size}"))
            renderOne(job, candidate, sourceUri, sourceVideo.durationMs)
        }

        return if (repo.getJobsForImport(importId).any { it.status == JobStatus.FAILED }) Result.failure() else Result.success()
    }

    private suspend fun renderOne(job: ReelJobEntity, candidate: ClipCandidate, sourceUri: Uri, srcDurationMs: Long) {
        var currentJob = job
        try {
            currentJob = job.copy(
                    status = JobStatus.FACE_ANALYSIS,
                    startMs = candidate.startMs,
                    endMs = candidate.endMs,
                    highlightScore = candidate.score.toDouble(),
                    transcriptExcerpt = candidate.transcriptExcerpt,
                    hook = candidate.title,
                    caption = candidate.caption,
                    hashtags = candidate.hashtags.joinToString(" "),
                    updatedAtEpochMs = System.currentTimeMillis()
                )
            repo.saveJob(currentJob)

            val (srcWidth, srcHeight) = readVideoDimensions(sourceUri)
            val cropWindow = faceCropPlanner.planCrop(sourceUri, candidate.startMs, candidate.endMs)

            repo.saveJob(currentJob.copy(status = JobStatus.RENDERING, updatedAtEpochMs = System.currentTimeMillis()))
            val clipRelativeSegments = candidate.segments.map {
                TranscriptSegment(it.startMs - candidate.startMs, it.endMs - candidate.startMs, it.text)
            }
            val outputFile = File(File(applicationContext.filesDir, "reels"), "${job.id}.mp4")
            exporter.export(
                sourceVideoUri = sourceUri,
                startMs = candidate.startMs,
                endMs = candidate.endMs,
                srcWidth = srcWidth,
                srcHeight = srcHeight,
                cropWindow = cropWindow,
                segments = clipRelativeSegments,
                subtitlesEnabled = true,
                outputFile = outputFile
            )

            val thumbFile = extractThumbnail(sourceUri, candidate.startMs, job.id)

            repo.saveJob(
                currentJob.copy(
                    status = JobStatus.READY_FOR_REVIEW,
                    outputFilePath = outputFile.absolutePath,
                    thumbnailFilePath = thumbFile?.absolutePath,
                    updatedAtEpochMs = System.currentTimeMillis()
                )
            )
        } catch (e: PipelineException) {
            repo.saveJob(
                currentJob.copy(
                    status = JobStatus.FAILED,
                    failureReason = e.reason,
                    failureDetail = e.message,
                    updatedAtEpochMs = System.currentTimeMillis()
                )
            )
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            android.util.Log.e("ReelBotPipeline", "Render failed", e)
            repo.saveJob(
                currentJob.copy(
                    status = JobStatus.FAILED,
                    failureReason = FailureReason.RENDER_FAILED,
                    failureDetail = e.message,
                    updatedAtEpochMs = System.currentTimeMillis()
                )
            )
        }
    }

    private suspend fun markAll(jobs: List<ReelJobEntity>, status: JobStatus) {
        jobs.forEach { repo.saveJob(it.copy(status = status, updatedAtEpochMs = System.currentTimeMillis())) }
    }

    private suspend fun failAll(jobs: List<ReelJobEntity>, reason: FailureReason, detail: String?) {
        jobs.forEach {
            repo.saveJob(
                it.copy(
                    status = JobStatus.FAILED,
                    failureReason = reason,
                    failureDetail = detail,
                    updatedAtEpochMs = System.currentTimeMillis()
                )
            )
        }
    }

    private fun copyUriToStaging(uri: Uri, dest: File): File {
        applicationContext.contentResolver.openInputStream(uri)?.use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        } ?: throw PipelineException(FailureReason.UNSUPPORTED_CODEC, "Could not open selected video")
        return dest
    }

    private fun readVideoDimensions(uri: Uri): Pair<Int, Int> {
        val retriever = android.media.MediaMetadataRetriever()
        return try {
            retriever.setDataSource(applicationContext, uri)
            val w = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val h = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val rotation = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            if (rotation == 90 || rotation == 270) h to w else w to h
        } finally {
            retriever.release()
        }
    }

    private fun extractThumbnail(uri: Uri, atMs: Long, jobId: String): File? {
        val retriever = android.media.MediaMetadataRetriever()
        return try {
            retriever.setDataSource(applicationContext, uri)
            val frame = retriever.getFrameAtTime(atMs * 1000, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC) ?: return null
            val thumbDir = File(applicationContext.filesDir, "thumbnails").apply { mkdirs() }
            val file = File(thumbDir, "$jobId.jpg")
            file.outputStream().use { frame.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, it) }
            frame.recycle()
            file
        } catch (e: Exception) {
            null
        } finally {
            retriever.release()
        }
    }

    private fun buildForegroundInfo(status: String): ForegroundInfo {
        val notification = NotificationCompat.Builder(applicationContext, ReelBotApp.PROCESSING_CHANNEL_ID)
            .setContentTitle(applicationContext.getString(R.string.notif_channel_processing))
            .setContentText(status)
            .setSmallIcon(android.R.drawable.ic_menu_camera) // replace with app icon asset
            .setOngoing(true)
            .build()

        return if (android.os.Build.VERSION.SDK_INT >= 35) {
            ForegroundInfo(
                ReelBotApp.PROCESSING_NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING
            )
        } else {
            if (android.os.Build.VERSION.SDK_INT >= 29) ForegroundInfo(ReelBotApp.PROCESSING_NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            else ForegroundInfo(ReelBotApp.PROCESSING_NOTIFICATION_ID, notification)
        }
    }

    companion object {
        const val KEY_IMPORT_ID = "import_id"
    }
}
