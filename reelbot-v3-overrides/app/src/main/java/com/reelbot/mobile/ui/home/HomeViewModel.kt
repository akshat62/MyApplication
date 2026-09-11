package com.reelbot.mobile.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reelbot.mobile.data.model.JobStatus
import com.reelbot.mobile.data.repository.ReelRepository
import com.reelbot.mobile.instagram.TokenVault
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class HomeUiState(
    val totalProcessed: Int = 0,
    val reelsGenerated: Int = 0,
    val awaitingApproval: Int = 0,
    val published: Int = 0,
    val failed: Int = 0,
    val instagramConnected: Boolean = false,
    val instagramUsername: String? = null,
    val modelReady: Boolean = false
)

class HomeViewModel(
    private val repository: ReelRepository,
    context: Context
) : ViewModel() {

    private val tokenVault = TokenVault(context.applicationContext)

    val uiState: StateFlow<HomeUiState> = combine(
        repository.observeTotalCount(),
        repository.observeCount(JobStatus.READY_FOR_REVIEW),
        repository.observeCount(JobStatus.POSTED),
        repository.observeCount(JobStatus.FAILED)
    ) { total, awaiting, posted, failed ->
        val session = runCatching { tokenVault.currentSession() }.getOrNull()
        HomeUiState(
            totalProcessed = total,
            reelsGenerated = total,
            awaitingApproval = awaiting,
            published = posted,
            failed = failed,
            instagramConnected = session != null,
            instagramUsername = session?.igUsername,
            modelReady = false
        )
    }
        .catch { emit(HomeUiState()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUiState())
}
