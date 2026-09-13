package com.reelbot.mobile.ui.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reelbot.mobile.data.db.entity.ReelJobEntity
import com.reelbot.mobile.data.model.JobStatus
import com.reelbot.mobile.data.repository.ReelRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class AnalyticsUiState(
    val generated: Int = 0,
    val approved: Int = 0,
    val rejected: Int = 0,
    val posted: Int = 0,
    val failed: Int = 0,
    val history: List<ReelJobEntity> = emptyList()
    // Real Instagram insights (reach, plays, likes) are added once Meta Graph API
    // insights calls are wired up — intentionally absent until then. Per spec:
    // "Never invent unavailable analytics."
)

private data class Counts(
    val generated: Int,
    val approved: Int,
    val rejected: Int,
    val posted: Int,
    val failed: Int
)

class AnalyticsViewModel(repository: ReelRepository) : ViewModel() {

    private val counts: Flow<Counts> = combine(
        repository.observeTotalCount(),
        repository.observeCount(JobStatus.APPROVED),
        repository.observeCount(JobStatus.REJECTED),
        repository.observeCount(JobStatus.POSTED),
        repository.observeCount(JobStatus.FAILED)
    ) { generated, approved, rejected, posted, failed ->
        Counts(generated, approved, rejected, posted, failed)
    }

    val uiState: StateFlow<AnalyticsUiState> = combine(
        counts,
        repository.observeAllJobs()
    ) { c, history ->
        AnalyticsUiState(
            generated = history.count { it.outputFilePath != null },
            approved = c.approved,
            rejected = c.rejected,
            posted = c.posted,
            failed = c.failed,
            history = history
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AnalyticsUiState())
}
