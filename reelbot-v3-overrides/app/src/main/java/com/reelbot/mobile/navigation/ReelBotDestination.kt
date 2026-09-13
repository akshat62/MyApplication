package com.reelbot.mobile.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PlaylistPlay
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector

/** One entry per bottom-nav tab. Create is reached via the Home screen's primary CTA,
 *  not a tab, per the spec's IA (Home / Queue / Analytics / Settings are the persistent
 *  destinations; Create is a task flow launched from Home). */
sealed class ReelBotDestination(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    data object Home : ReelBotDestination(
        "home", "Home", Icons.Filled.Home, Icons.Outlined.Home
    )
    data object Queue : ReelBotDestination(
        "queue", "Queue", Icons.Filled.PlaylistPlay, Icons.Outlined.PlaylistPlay
    )
    data object Analytics : ReelBotDestination(
        "analytics", "Analytics", Icons.Filled.Analytics, Icons.Outlined.Analytics
    )
    data object Settings : ReelBotDestination(
        "settings", "Settings", Icons.Filled.Settings, Icons.Outlined.Settings
    )

    companion object {
        val bottomNavItems: List<ReelBotDestination>
            get() = listOf(Home, Queue, Analytics, Settings)
    }
}

/** Non-tab routes reached by navigating forward from a tab. */
object ReelBotRoutes {
    const val CREATE = "create"
    const val APPROVAL = "approval/{jobId}"
    fun approval(jobId: String) = "approval/$jobId"
}
