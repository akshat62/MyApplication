package com.reelbot.mobile.work

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

object PipelineScheduler {
    fun enqueue(context: Context, importId: String) {
        val request = OneTimeWorkRequestBuilder<ReelPipelineWorker>()
            .setInputData(Data.Builder().putString(ReelPipelineWorker.KEY_IMPORT_ID, importId).build())
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "reel_pipeline",
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            request
        )
    }
}
