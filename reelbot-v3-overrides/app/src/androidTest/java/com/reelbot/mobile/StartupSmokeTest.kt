package com.reelbot.mobile

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.*
import org.junit.Rule
import org.junit.Test

class StartupSmokeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun navigationAndRecreation() {
        compose.waitUntil(30000) { compose.onAllNodesWithText("Home").fetchSemanticsNodes().isNotEmpty() }
        org.junit.Assert.assertFalse(java.io.File("/proc/self/maps").readText().contains("libreelbot_whisper"))
        repeat(3) {
            for (tab in listOf("Queue", "Analytics", "Settings", "Home")) {
                compose.onAllNodesWithText(tab).onLast().performClick()
                compose.waitForIdle()
            }
        }
        compose.onNodeWithText("Create Reels", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Select video").assertExists()
        compose.onNodeWithText("Select video").performClick()
        clickSystemNode("Show roots")
        clickSystemNode("Downloads")
        clickSystemNode("real-speech-fixture.mp4")
        android.os.SystemClock.sleep(500)
        compose.waitUntil(30000) { compose.onAllNodesWithText("real-speech-fixture.mp4", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        org.junit.Assert.assertTrue(compose.activity.contentResolver.persistedUriPermissions.any { it.isReadPermission })
        val bitmap = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        java.io.File(compose.activity.filesDir, "selected-video.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        compose.onAllNodesWithText("Home").onLast().performClick()
        compose.activityRule.scenario.recreate()
        compose.waitUntil(30000) { compose.onAllNodesWithText("Home").fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithText("Settings").onLast().performClick()
        compose.onNodeWithText("Login with Instagram").performClick()
        compose.onNodeWithText("Instagram setup required:", substring = true).assertExists()
    }
    private fun clickSystemNode(label: String) {
        val automation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation
        val deadline = android.os.SystemClock.uptimeMillis() + 20000
        fun find(node: android.view.accessibility.AccessibilityNodeInfo?): android.view.accessibility.AccessibilityNodeInfo? {
            if (node == null) return null
            if (node.isVisibleToUser && (node.text?.toString() == label || node.contentDescription?.toString() == label)) return node
            for (i in 0 until node.childCount) find(node.getChild(i))?.let { return it }
            return null
        }
        while (android.os.SystemClock.uptimeMillis() < deadline) {
            var node = find(automation.rootInActiveWindow)
            while (node != null && !node.isClickable) node = node.parent
            if (node?.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK) == true) return
            android.os.SystemClock.sleep(200)
        }
        throw AssertionError("System document picker did not expose: $label")
    }

}
