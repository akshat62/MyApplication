package com.reelbot.mobile.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.reelbot.mobile.ui.ReelBotViewModelFactory
import com.reelbot.mobile.ui.analytics.AnalyticsScreen
import com.reelbot.mobile.ui.analytics.AnalyticsViewModel
import com.reelbot.mobile.ui.create.CreateScreen
import com.reelbot.mobile.ui.create.CreateViewModel
import com.reelbot.mobile.ui.home.HomeCreateFab
import com.reelbot.mobile.ui.home.HomeScreen
import com.reelbot.mobile.ui.home.HomeViewModel
import com.reelbot.mobile.ui.queue.QueueScreen
import com.reelbot.mobile.ui.queue.QueueViewModel
import com.reelbot.mobile.ui.settings.SettingsScreen

@Composable
fun ReelBotNavHost(factory: ReelBotViewModelFactory) {
    val navController = rememberNavController()

    Scaffold(
        bottomBar = { ReelBotBottomBar(navController) },
        floatingActionButton = {
            val backStackEntry by navController.currentBackStackEntryAsState()
            if (backStackEntry?.destination?.route == ReelBotDestination.Home.route) {
                HomeCreateFab(onClick = { navController.navigate(ReelBotRoutes.CREATE) })
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = ReelBotDestination.Home.route,
            modifier = Modifier.padding(padding)
        ) {
            composable(ReelBotDestination.Home.route) {
                val vm: HomeViewModel = viewModel(factory = factory)
                HomeScreen(vm, onCreateClick = { navController.navigate(ReelBotRoutes.CREATE) })
            }
            composable(ReelBotDestination.Queue.route) {
                val vm: QueueViewModel = viewModel(factory = factory)
                QueueScreen(vm, onOpenApproval = { jobId -> navController.navigate(ReelBotRoutes.approval(jobId)) })
            }
            composable(ReelBotDestination.Analytics.route) {
                val vm: AnalyticsViewModel = viewModel(factory = factory)
                AnalyticsScreen(vm)
            }
            composable(ReelBotDestination.Settings.route) {
                val vm: com.reelbot.mobile.ui.settings.SettingsViewModel = viewModel(factory = factory)
                SettingsScreen(vm)
            }
            composable(ReelBotRoutes.CREATE) {
                val vm: CreateViewModel = viewModel(factory = factory)
                CreateScreen(vm, onSubmitted = {
                    navController.navigate(ReelBotDestination.Queue.route) {
                        popUpTo(ReelBotDestination.Home.route)
                    }
                })
            }
            composable(ReelBotRoutes.APPROVAL) { entry ->
                val vm: QueueViewModel = viewModel(factory = factory)
                com.reelbot.mobile.ui.queue.ApprovalScreen(vm, entry.arguments?.getString("jobId") ?: "")
            }
        }
    }
}

@Composable
private fun ReelBotBottomBar(navController: androidx.navigation.NavHostController) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    // Do not use ReelBotDestination.bottomNavItems here. The original companion
    // list can contain a null entry during JVM/ART static initialization on some
    // devices, causing an immediate startup NPE when Compose renders the icon.
    // Referencing the destination objects directly at composition time avoids
    // that initialization-order hazard entirely.
    val items = listOf(
        ReelBotDestination.Home,
        ReelBotDestination.Queue,
        ReelBotDestination.Analytics,
        ReelBotDestination.Settings
    )

    NavigationBar {
        items.forEach { item ->
            val selected = currentDestination?.hierarchy?.any { it.route == item.route } == true
            NavigationBarItem(
                selected = selected,
                onClick = {
                    navController.navigate(item.route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = {
                    androidx.compose.material3.Icon(
                        if (selected) item.selectedIcon else item.unselectedIcon,
                        contentDescription = item.label
                    )
                },
                label = { Text(item.label) }
            )
        }
    }
}
