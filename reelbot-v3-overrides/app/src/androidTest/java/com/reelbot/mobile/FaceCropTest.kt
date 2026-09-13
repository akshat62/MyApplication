package com.reelbot.mobile

import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.reelbot.mobile.export.ReelExporter
import com.reelbot.mobile.face.FaceCropPlanner
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.concurrent.TimeUnit

class FaceCropTest {
    @Test fun detectedFaceChangesRenderedComposition() = runBlocking {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val input = File(app.filesDir, "face-fixture.mp4")
        assertTrue(input.isFile)
        val planner = FaceCropPlanner(app)
        val crop = try { planner.planCrop(Uri.fromFile(input), 0, 2000) } finally { planner.close() }
        assertTrue("ML Kit must find the off-center face", crop.faceDetected)
        assertTrue("Face detection must move crop away from center fallback", crop.left < 140)
        val output = File(app.filesDir, "reels/face-verified.mp4")
        ReelExporter(app).export(Uri.fromFile(input), 0, 2000, 640, 360, crop, emptyList(), false, output)
        val retriever = MediaMetadataRetriever()
        val detector = FaceDetection.getClient(com.google.mlkit.vision.face.FaceDetectorOptions.Builder().build())
        try {
            retriever.setDataSource(output.absolutePath)
            val frame = retriever.getFrameAtTime(500000)!!
            assertEquals(1080, frame.width)
            assertEquals(1920, frame.height)
            val faces = Tasks.await(detector.process(InputImage.fromBitmap(frame, 0)), 30, TimeUnit.SECONDS)
            val face = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }
            assertNotNull("Rendered output must retain the detected face", face)
            val center = face!!.boundingBox.exactCenterX() / frame.width
            assertTrue("Face should be near the rendered horizontal center: $center", center in 0.3f..0.7f)
            frame.recycle()
        } finally { detector.close(); retriever.release() }
    }
}
