package com.reelbot.mobile

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.*
import org.junit.Rule
import org.junit.Test

class StartupSmokeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun navigationAndRecreation() {
        compose.waitUntil(30000) { compose.onAllNodesWithText("Home").fetchSemanticsNodes().isNotEmpty() }
        repeat(3) {
            for (tab in listOf("Queue", "Analytics", "Settings", "Home")) {
                compose.onAllNodesWithText(tab).onLast().performClick()
                compose.waitForIdle()
            }
        }
        compose.onNodeWithText("Create Reels", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Select video").assertExists()
        compose.onAllNodesWithText("Home").onLast().performClick()
        compose.activityRule.scenario.recreate()
        compose.waitUntil(30000) { compose.onAllNodesWithText("Home").fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithText("Settings").onLast().performClick()
        compose.onNodeWithText("Login with Instagram").performClick()
        compose.onNodeWithText("Instagram setup required:", substring = true).assertExists()
    }
}
