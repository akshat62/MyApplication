package com.reelbot.mobile.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private data class SettingsSection(val title: String, val items: List<String>)

// Structure mirrors the spec's Settings section. "Instagram account" is now a real,
// wired OAuth flow (see InstagramAccountCard below); the remaining items become live
// DataStore-backed controls in a following pass — listed here so the navigation/IA is
// complete, not to fake functionality.
private val sections = listOf(
    SettingsSection("AI model", listOf("Model management", "Diagnostics & logs")),
    SettingsSection(
        "Defaults",
        listOf("Reels per video", "Preferred Reel duration", "Subtitles", "Subtitle style", "Face tracking", "Hook/title generation", "Logo/watermark")
    ),
    SettingsSection("Publishing", listOf("Review mode", "Full-auto mode", "Maximum posts/day")),
    SettingsSection("System", listOf("Storage & cache"))
)

@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text("Account", style = MaterialTheme.typography.titleMedium) }
        item { InstagramAccountCard(viewModel) }

        item {
            val state by viewModel.modelState.collectAsState()
            val progress by viewModel.modelProgress.collectAsState()
            val error by viewModel.modelError.collectAsState()
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("On-device speech", style = MaterialTheme.typography.titleMedium)
                    Text("Whisper Base multilingual · 142 MB")
                    Text(state.name.replace('_', ' '))
                    if (state == com.reelbot.mobile.data.model.ModelState.DOWNLOADING) {
                        Text("${progress.percent}% downloaded")
                        androidx.compose.material3.LinearProgressIndicator(progress = { progress.percent / 100f }, modifier = Modifier.fillMaxWidth())
                        OutlinedButton(onClick = viewModel::cancelDownload) { Text("Cancel download") }
                    } else if (state != com.reelbot.mobile.data.model.ModelState.VERIFYING) {
                        Button(onClick = viewModel::downloadModel) { Text("Download / repair model") }
                    }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    Text("After download, transcription and video processing run on this phone. There is no fixed video-duration limit. Long videos take more time and storage.")
                }
            }
        }
        item {
            val diagnostics by viewModel.diagnostics.collectAsState()
            OutlinedButton(onClick = { viewModel.readDiagnostics() }) { Text("Diagnostics & logs") }
            if (diagnostics.isNotEmpty()) Text(diagnostics, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun InstagramAccountCard(viewModel: SettingsViewModel) {
    val state by viewModel.uiState.collectAsState()

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Instagram", style = MaterialTheme.typography.titleMedium)

            if (state.connected) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.CloudDone, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(
                            "  Connected as @${state.username}",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                }
                Row(modifier = Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = viewModel::startLogin) { Text("Reconnect") }
                    OutlinedButton(onClick = viewModel::disconnect) { Text("Disconnect") }
                }
            } else {
                Text(
                    "Connect your Instagram professional account to publish directly from ReelBot.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                )
                Button(onClick = viewModel::startLogin, enabled = !state.connecting) {
                    if (state.connecting) {
                        CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
                    }
                    Text("Login with Instagram")
                }
                OutlinedButton(
                    onClick = viewModel::startLogin,
                    enabled = !state.connecting,
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text("Continue with Facebook")
                }
                Text(
                    "Both buttons open the same Meta sign-in — Instagram publishing is " +
                        "authorized through Facebook Login for Business either way.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
                state.error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
    }
}
