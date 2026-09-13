package com.reelbot.mobile.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import com.reelbot.mobile.data.repository.ReelRepository
import com.reelbot.mobile.ui.analytics.AnalyticsViewModel
import com.reelbot.mobile.ui.create.CreateViewModel
import com.reelbot.mobile.ui.home.HomeViewModel
import com.reelbot.mobile.ui.queue.QueueViewModel
import com.reelbot.mobile.ui.settings.SettingsViewModel

class ReelBotViewModelFactory(
    private val repository: ReelRepository,
    private val appContext: Context
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T = when (modelClass) {
        HomeViewModel::class.java -> HomeViewModel(repository, appContext) as T
        QueueViewModel::class.java -> QueueViewModel(repository, appContext) as T
        AnalyticsViewModel::class.java -> AnalyticsViewModel(repository) as T
        CreateViewModel::class.java -> CreateViewModel(repository, appContext) as T
        SettingsViewModel::class.java -> SettingsViewModel(appContext) as T
        else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
