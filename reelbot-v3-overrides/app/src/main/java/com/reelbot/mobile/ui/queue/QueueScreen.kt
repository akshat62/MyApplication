package com.reelbot.mobile.ui.queue

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.reelbot.mobile.data.db.entity.ReelJobEntity
import com.reelbot.mobile.data.model.JobStatus

@Composable
fun QueueScreen(viewModel: QueueViewModel, onOpenApproval: (String) -> Unit) {
    val jobs by viewModel.jobs.collectAsState()

    if (jobs.isEmpty()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("Nothing in the queue", style = MaterialTheme.typography.titleMedium)
            Text(
                "Reels you generate will show up here for review.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(jobs, key = { it.id }) { job ->
            ReelQueueCard(
                job = job,
                onApprove = { viewModel.approve(job.id) },
                onReject = { viewModel.reject(job.id) },
                onOpen = { onOpenApproval(job.id) },
                onPostNow = { viewModel.postNow(job.id) }
            )
        }
    }
}

@Composable
private fun ReelQueueCard(
    job: ReelJobEntity,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    onOpen: () -> Unit,
    onPostNow: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    job.hook ?: "Untitled clip",
                    style = MaterialTheme.typography.titleMedium
                )
                StatusChip(job.status)
            }

            Text(
                durationLabel(job),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            job.highlightScore?.let {
                Text(
                    "Highlight score: ${it.toInt()}/100",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            job.caption?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onReject, enabled = job.status == JobStatus.READY_FOR_REVIEW) { Text("Reject") }
                TextButton(onClick = onOpen) { Text("Preview") }
                OutlinedButton(onClick = onApprove, enabled = job.status == JobStatus.READY_FOR_REVIEW) {
                    Text("Approve")
                }
                if (job.status == JobStatus.APPROVED) {
                    OutlinedButton(onClick = onPostNow, modifier = Modifier.padding(start = 4.dp)) {
                        Text("Post Now")
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusChip(status: JobStatus) {
    AssistChip(onClick = {}, label = { Text(status.name.replace('_', ' ')) })
}

private fun durationLabel(job: ReelJobEntity): String {
    val start = job.startMs ?: return "Processing…"
    val end = job.endMs ?: return "Processing…"
    val seconds = (end - start) / 1000
    return "%d:%02d clip".format(seconds / 60, seconds % 60)
}
