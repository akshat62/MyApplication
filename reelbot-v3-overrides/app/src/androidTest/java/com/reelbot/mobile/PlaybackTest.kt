package com.reelbot.mobile

import android.media.MediaPlayer
import android.widget.VideoView
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class PlaybackTest {
    @Test fun exportedVideoRendersInAndroidPlayer() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val output = File(app.filesDir, "reels/verified-reel.mp4")
        assertTrue(output.isFile)
        val rendered = CountDownLatch(1)
        val failure = AtomicReference<String?>(null)
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        var video: VideoView? = null
        try {
            scenario.onActivity { activity ->
                video = VideoView(activity).also { player ->
                    activity.setContentView(player)
                    player.setOnErrorListener { _, what, extra ->
                        failure.set("Android playback error $what / $extra"); rendered.countDown(); true
                    }
                    player.setOnInfoListener { _, what, _ ->
                        if (what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) rendered.countDown()
                        false
                    }
                    player.setOnPreparedListener { player.start() }
                    player.setVideoPath(output.absolutePath)
                }
            }
            assertTrue("Android player must render a video frame", rendered.await(30, TimeUnit.SECONDS))
            assertNull(failure.get())
            Thread.sleep(500)
            scenario.onActivity {
                assertTrue("Playback position must advance", video!!.currentPosition > 0)
                video!!.stopPlayback()
            }
        } finally { scenario.close() }
    }
}
