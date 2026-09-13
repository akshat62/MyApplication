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

    val recentVideos = repository.observeRecentVideos().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val uiState: StateFlow<HomeUiState> = combine(
        repository.observeAllJobs(),
        (context.applicationContext as com.reelbot.mobile.ReelBotApp).modelManager.state
    ) { jobs, model ->
        val session = tokenVault.currentSession()
        HomeUiState(
            totalProcessed = jobs.filter { it.outputFilePath != null }.map { it.sourceImportId }.distinct().size,
            reelsGenerated = jobs.count { it.outputFilePath != null },
            awaitingApproval = jobs.count { it.status == JobStatus.READY_FOR_REVIEW },
            published = jobs.count { it.status == JobStatus.POSTED },
            failed = jobs.count { it.status == JobStatus.FAILED },
            instagramConnected = session != null,
            instagramUsername = session?.igUsername,
            modelReady = model in listOf(com.reelbot.mobile.data.model.ModelState.INSTALLED, com.reelbot.mobile.data.model.ModelState.READY)
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUiState())
}
