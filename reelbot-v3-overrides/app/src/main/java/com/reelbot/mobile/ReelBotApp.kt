package com.reelbot.mobile

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.util.Log
import com.reelbot.mobile.data.db.ReelBotDatabase
import com.reelbot.mobile.data.repository.ReelRepository
import java.io.File
import kotlin.system.exitProcess

class ReelBotApp : Application() {

    val modelManager by lazy { com.reelbot.mobile.ai.ModelManager(this) }

    val database: ReelBotDatabase by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        ReelBotDatabase.getInstance(this)
    }

    val repository: ReelRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        ReelRepository.getInstance(database)
    }

    override fun onCreate() {
        super.onCreate()
        installCrashRecorder()
        // Notification setup must never prevent the app from opening on an OEM build.
        runCatching { createProcessingNotificationChannel() }
            .onFailure { Log.e("ReelBotApp", "Notification channel setup failed", it) }
    }

    private fun installCrashRecorder() {
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("ReelBotCrash", "Uncaught exception on ${thread.name}", throwable)
            runCatching {
                File(filesDir, LAST_CRASH_FILE).writeText(Log.getStackTraceString(throwable))
            }
            if (previousHandler != null) {
                previousHandler.uncaughtException(thread, throwable)
            } else {
                android.os.Process.killProcess(android.os.Process.myPid())
                exitProcess(10)
            }
        }
    }

    private fun createProcessingNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                PROCESSING_CHANNEL_ID,
                getString(R.string.notif_channel_processing),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notif_channel_processing_desc)
            }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    companion object {
        const val PROCESSING_CHANNEL_ID = "reel_processing"
        const val PROCESSING_NOTIFICATION_ID = 1001
        const val LAST_CRASH_FILE = "last_crash.txt"
    }
}
