package com.reelbot.mobile.ui.create

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.reelbot.mobile.data.model.ReelDuration

@Composable
fun CreateScreen(
    viewModel: CreateViewModel,
    onSubmitted: (String) -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    val pickVideo = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.onVideoSelected(it) }
    }

    LaunchedEffect(state.submittedImportId) {
        state.submittedImportId?.let { onSubmitted(it) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Create Reels", style = MaterialTheme.typography.headlineMedium)

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val video = state.selectedVideo
                if (video == null) {
                    Text("No video selected", style = MaterialTheme.typography.bodyLarge)
                } else {
                    com.reelbot.mobile.ui.VideoPreview(video.uri)
                    Text(video.displayName, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${formatDuration(video.durationMs)} • ${video.width}×${video.height} • ${formatSize(video.fileSizeBytes)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(onClick = { pickVideo.launch(arrayOf("video/*")) }) {
                    Text(if (video == null) "Select video" else "Change video")
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Number of Reels: ${state.reelCount}", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(1, 3, 5, 10).forEach { n ->
                        FilterChip(
                            selected = state.reelCount == n,
                            onClick = { viewModel.setReelCount(n) },
                            label = { Text(n.toString()) }
                        )
                    }
                }

                Text("Reel duration", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ReelDuration.entries.forEach { d ->
                        FilterChip(
                            selected = state.duration == d,
                            onClick = { viewModel.setDuration(d) },
                            label = { Text("${d.seconds}s") }
                        )
                    }
                }

                Text("Reels require your approval before publication.")
            }
        }

        state.submitError?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
        }

        Button(
            onClick = viewModel::submit,
            enabled = state.selectedVideo != null && !state.isSubmitting,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (state.isSubmitting) {
                CircularProgressIndicator(modifier = Modifier.width(20.dp), color = MaterialTheme.colorScheme.onPrimary)
            } else {
                Text("Start Processing")
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}

private fun formatSize(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    return "%.1f MB".format(mb)
}
