package com.reelbot.mobile

import androidx.test.platform.app.InstrumentationRegistry
import com.reelbot.mobile.ai.*
import com.reelbot.mobile.data.model.*
import com.reelbot.mobile.data.db.entity.ReelJobEntity
import com.reelbot.mobile.export.ReelExporter
import com.reelbot.mobile.face.FaceCropPlanner
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import android.net.Uri

class LocalPipelineTest {
    @Test fun realSpeechToPlayableReel() = runBlocking {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ReelBotApp
        val video = File(app.filesDir, "real-speech-fixture.mp4")
        assertTrue("Install the real speech fixture before running this test", video.isFile)
        val manager = ModelManager(app)
        if (InstrumentationRegistry.getArguments().getString("downloadModel") == "true") manager.download(WhisperModelSpec.BASE)
        manager.refreshState(WhisperModelSpec.BASE)
        assertEquals(ModelState.INSTALLED, manager.state.value)
        val wav = AudioExtractor().extract(video, File(app.cacheDir, "test-speech.wav"))
        val speech = TranscriptionEngine(manager).transcribe(wav, WhisperModelSpec.BASE)
        assertTrue(speech.isNotEmpty())
        assertTrue(speech.all { it.startMs >= 0 && it.endMs > it.startMs && it.text.isNotBlank() })
        File(app.filesDir, "verified-transcript.txt").writeText(speech.joinToString("\n"))
        val candidate = HighlightEngine().select(speech, 2, 15000).first()
        val planner = FaceCropPlanner(app)
        val crop = try { planner.planCrop(Uri.fromFile(video), candidate.startMs, candidate.endMs) } finally { planner.close() }
        val output = File(app.filesDir, "reels/verified-reel.mp4")
        ReelExporter(app).export(Uri.fromFile(video), candidate.startMs, candidate.endMs, 640, 360, crop,
            candidate.segments.map { it.copy(startMs = it.startMs - candidate.startMs, endMs = it.endMs - candidate.startMs) }, true, output)
        assertTrue(output.length() > 10000)
        val row = ReelJobEntity(id = "verified-test-reel", sourceImportId = "verified-test", sourceVideoUri = Uri.fromFile(video).toString(),
            sourceVideoDurationMs = 30000, status = JobStatus.READY_FOR_REVIEW, outputFilePath = output.absolutePath,
            caption = candidate.caption, hashtags = candidate.hashtags.joinToString(" "), highlightScore = candidate.score.toDouble())
        app.repository.saveJob(row)
        assertEquals(output.absolutePath, app.repository.getJob(row.id)?.outputFilePath)
    }
    @Test fun workerProducesReviewableReel() = runBlocking {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ReelBotApp
        val scenario = androidx.test.core.app.ActivityScenario.launch(MainActivity::class.java)
        try {
            val id = java.util.UUID.randomUUID().toString()
            val video = File(app.filesDir, "real-speech-fixture.mp4")
            app.repository.saveSourceVideo(com.reelbot.mobile.data.db.entity.SourceVideoEntity(
                importId = id, uri = Uri.fromFile(video).toString(), displayName = video.name,
                durationMs = 30000, fileSizeBytes = video.length(), requestedClipCount = 1, requestedDurationSeconds = 30))
            val job = ReelJobEntity(id = id, sourceImportId = id, sourceVideoUri = Uri.fromFile(video).toString(), sourceVideoDurationMs = 30000, status = JobStatus.IMPORTED)
            app.repository.saveJob(job)
            com.reelbot.mobile.work.PipelineScheduler.enqueue(app, id)
            kotlinx.coroutines.withTimeout(240000) {
                while (true) {
                    val status = app.repository.getJob(id)?.status
                    if (status == JobStatus.READY_FOR_REVIEW || status == JobStatus.FAILED || status == JobStatus.CANCELLED) break
                    kotlinx.coroutines.delay(500)
                }
            }
            val completed = app.repository.getJob(id)!!
            assertEquals(completed.failureDetail, JobStatus.READY_FOR_REVIEW, completed.status)
            assertNotNull(completed.highlightScore)
            assertFalse(completed.caption.isNullOrBlank())
            assertFalse(completed.transcriptExcerpt.isNullOrBlank())
            assertTrue(File(completed.outputFilePath!!).isFile)
        } finally { scenario.close() }
    }

    @Test fun missingModelFailsHonestly() = runBlocking {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val manager = ModelManager(app)
        try {
            TranscriptionEngine(manager).transcribe(File(app.cacheDir, "missing.wav"), WhisperModelSpec.BASE)
            fail("Missing model must fail")
        } catch (expected: ModelNotReadyException) {
            assertEquals(ModelState.NOT_INSTALLED, expected.state)
        }
    }
}
