package com.novelreader

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.WorkManager
import com.novelreader.data.download.DownloadService
import com.novelreader.data.worker.UpdateWorker
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Application class.
 * Initialise WorkManager, les canaux de notification, et les services.
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
        createNotificationChannels()
        scheduleUpdates()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NotificationManager::class.java)

            // Canal pour les mises à jour (nouveaux chapitres)
            val updatesChannel = NotificationChannel(
                "novel_updates",
                "Nouveaux chapitres",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications quand de nouveaux chapitres sont disponibles"
            }
            notificationManager.createNotificationChannel(updatesChannel)

            // Canal pour les téléchargements en cours (importance faible — ne pas déranger)
            val downloadsChannel = NotificationChannel(
                DownloadService.CHANNEL_ID,
                "Téléchargements",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Progression des téléchargements de chapitres"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(downloadsChannel)
        }
    }

    private fun scheduleUpdates() {
        val workManager = WorkManager.getInstance(this)
        UpdateWorker.schedule(workManager, intervalHours = 12)
    }
}
