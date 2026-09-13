package com.reelbot.mobile.ui.analytics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.reelbot.mobile.data.db.entity.ReelJobEntity

@Composable
fun AnalyticsScreen(viewModel: AnalyticsViewModel) {
    val state by viewModel.uiState.collectAsState()

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    MetricRow("Generated", state.generated)
                    MetricRow("Approved", state.approved)
                    MetricRow("Rejected", state.rejected)
                    MetricRow("Posted", state.posted)
                    MetricRow("Failed", state.failed)
                }
            }
        }
        item {
            Text(
                "These are local processing statistics. Instagram reach and plays are not available in this build.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        item { HorizontalDivider() }
        item { Text("History", style = MaterialTheme.typography.titleMedium) }
        items(state.history, key = { it.id }) { job -> HistoryRow(job) }
    }
}

@Composable
private fun MetricRow(label: String, value: Int) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Text(value.toString(), style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun HistoryRow(job: ReelJobEntity) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(job.hook ?: job.id.take(8), style = MaterialTheme.typography.bodyMedium)
        Text(job.status.name.replace('_', ' '), style = MaterialTheme.typography.labelSmall)
    }
}
