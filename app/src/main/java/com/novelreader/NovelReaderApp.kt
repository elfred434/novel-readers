package com.novelreader

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.WorkManager
import com.novelreader.data.download.DownloadService
import com.novelreader.data.storage.StorageManager
import com.novelreader.data.worker.UpdateWorker
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Application class.
 * Initialise WorkManager, les canaux de notification, et le dossier de stockage.
 */
@HiltAndroidApp
class NovelReaderApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var storageManager: StorageManager

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        autoCreateStorage()
        scheduleUpdates()
    }

    /**
     * Crée automatiquement le dossier de téléchargement au premier lancement.
     * Utilise le stockage interne (filesDir) — aucune permission requise.
     * L'utilisateur pourra changer vers un dossier SAF depuis les paramètres.
     */
    private fun autoCreateStorage() {
        scope.launch {
            if (!storageManager.hasStorageLocation()) {
                val created = storageManager.autoCreateStorageLocation()
                if (created) {
                    android.util.Log.i("NovelReader", "Dossier de stockage auto-créé")
                } else {
                    android.util.Log.w("NovelReader", "Impossible de créer le dossier de stockage")
                }
            }
        }
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
