package com.novelreader

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.WorkManager
import com.novelreader.data.worker.UpdateWorker
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Application class.
 * Initialise WorkManager et les canaux de notification.
 */
@HiltAndroidApp
class NovelReaderApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        scheduleUpdates()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "novel_updates",
                "Nouveaux chapitres",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications quand de nouveaux chapitres sont disponibles"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun scheduleUpdates() {
        val workManager = WorkManager.getInstance(this)
        UpdateWorker.schedule(workManager, intervalHours = 12)
    }
}
