package com.reelbot.mobile.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onCreateClick: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val recent by viewModel.recentVideos.collectAsState()

    Column(modifier = Modifier.fillMaxWidth()) {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { ConnectionStatusCard(state) }
            item { StatsGrid(state) }
            item {
                Text(
                    "Recent projects",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            if (recent.isEmpty()) item { EmptyRecentProjects() }
            items(recent, key = { it.importId }) { video ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(video.displayName, style = MaterialTheme.typography.titleMedium)
                        Text("${video.durationMs / 1000}s · ${video.requestedClipCount} requested Reels")
                    }
                }
            }
            // Recent-projects list itself is wired to SourceVideoDao in the Create-flow
            // phase; deliberately left as a real empty state (not sample rows) for now.
        }
    }
}

@Composable
private fun ConnectionStatusCard(state: HomeUiState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Instagram", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (state.instagramConnected) "Connected as @${state.instagramUsername}" else "Not connected",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = if (state.instagramConnected) Icons.Filled.CloudDone else Icons.Filled.CloudOff,
                contentDescription = null,
                tint = if (state.instagramConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun StatsGrid(state: HomeUiState) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors()) {
        Column(modifier = Modifier.padding(16.dp)) {
            StatRow("Videos processed", state.totalProcessed.toString())
            StatRow("Reels generated", state.reelsGenerated.toString())
            StatRow("Awaiting approval", state.awaitingApproval.toString())
            StatRow("Published", state.published.toString())
            StatRow("Failed jobs", state.failed.toString())
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun EmptyRecentProjects() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "No videos processed yet",
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                "Tap Create Reels to get started",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun HomeCreateFab(onClick: () -> Unit) {
    ExtendedFloatingActionButton(onClick = onClick, icon = { Icon(Icons.Filled.Add, null) }, text = { Text("Create Reels") })
}
