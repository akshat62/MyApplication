package com.reelbot.mobile

import androidx.test.platform.app.InstrumentationRegistry
import com.reelbot.mobile.ai.ModelManager
import com.reelbot.mobile.data.model.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class ModelDownloadTest {
    @Test fun androidCanDownloadAndVerifyModel() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val manager = ModelManager(context, networkTimeoutSeconds = 120)
        manager.download(WhisperModelSpec.BASE)
        assertEquals(ModelState.INSTALLED, manager.state.value)
    }
}
