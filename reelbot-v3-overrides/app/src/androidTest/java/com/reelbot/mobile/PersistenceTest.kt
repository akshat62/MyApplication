package com.reelbot.mobile

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import com.reelbot.mobile.data.model.JobStatus

class PersistenceTest {
    @Test fun exportedReelSurvivesNewProcess() = runBlocking {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ReelBotApp
        val job = app.repository.getJob("verified-test-reel")
        assertNotNull(job)
        assertEquals(JobStatus.READY_FOR_REVIEW, job!!.status)
        assertTrue(java.io.File(job.outputFilePath!!).isFile)
    }
}
