package com.novelreader.data.download

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import javax.inject.Inject

/**
 * Foreground Service pour les téléchargements en arrière-plan.
 *
 * Rôle :
 * - Empêche Android de tuer le process pendant les téléchargements
 * - Affiche une notification persistante avec la progression
 * - S'arrête automatiquement quand tous les téléchargements sont terminés
 *
 * Démarrage automatique :
 *   Appelé par DetailViewModel.downloadChapter() / downloadAllChapters()
 *   via DownloadService.start(context)
 *
 * Arrêt automatique :
 *   Quand la queue n'a plus d'items actifs → stopForeground + stopSelf()
 */
@AndroidEntryPoint
class DownloadService : Service() {

    @Inject lateinit var downloadManager: DownloadManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var notificationJob: Job? = null
    private var isRunning = false

    companion object {
        const val CHANNEL_ID = "novel_downloads"
        const val NOTIFICATION_ID = 1001
        const val ACTION_CANCEL_ALL = "com.novelreader.CANCEL_ALL"
        const val ACTION_UPDATE = "com.novelreader.UPDATE"

        /** Démarre le service de téléchargement en arrière-plan. */
        fun start(context: Context) {
            val intent = Intent(context, DownloadService::class.java)
            intent.action = ACTION_UPDATE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /** Arrête le service de téléchargement. */
        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, DownloadService::class.java))
            } catch (_: Exception) {}
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CANCEL_ALL -> {
                downloadManager.cancelAll()
                return START_NOT_STICKY
            }
            ACTION_UPDATE -> {
                if (!isRunning) startDownloadMonitoring()
                return START_NOT_STICKY
            }
        }
        return START_NOT_STICKY
    }

    /**
     * Lance le monitoring de la queue de téléchargement.
     * Met à jour la notification en temps réel et arrête le service
     * automatiquement quand tout est terminé.
     */
    private fun startDownloadMonitoring() {
        isRunning = true

        // Notification initiale
        val notification = buildNotification(
            title = "Téléchargements",
            message = "Préparation…",
            progress = 0,
            max = 0,
            showCancel = false
        )
        startForeground(NOTIFICATION_ID, notification)

        notificationJob = scope.launch {
            downloadManager.queue.collect { items ->
                val active = items.filter {
                    it.status == DownloadStatus.QUEUED || it.status == DownloadStatus.DOWNLOADING
                }
                val completed = items.count { it.status == DownloadStatus.COMPLETED }
                val failed = items.count { it.status == DownloadStatus.FAILED }
                val cancelled = items.count { it.status == DownloadStatus.CANCELLED }
                val total = items.size

                if (total == 0 || (active.isEmpty() && total == cancelled)) {
                    // Rien à télécharger ou tout annulé → on arrête
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    isRunning = false
                    notificationJob?.cancel()
                    return@collect
                }

                if (active.isEmpty() && completed + failed > 0) {
                    // Tout est terminé
                    val allSuccess = failed == 0
                    val message = if (allSuccess) {
                        "$completed chapitre(s) téléchargé(s)"
                    } else {
                        "$completed réussi(s), $failed échec(s)"
                    }

                    val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                    val finalNotification = NotificationCompat.Builder(this@DownloadService, CHANNEL_ID)
                        .setContentTitle(if (allSuccess) "Téléchargements terminés" else "Téléchargements avec erreurs")
                        .setContentText(message)
                        .setSmallIcon(if (allSuccess) android.R.drawable.stat_sys_download_done else android.R.drawable.stat_notify_error)
                        .setAutoCancel(true)
                        .setOngoing(false)
                        .build()
                    notificationManager.notify(NOTIFICATION_ID, finalNotification)

                    delay(2000) // Laisser la notification visible 2s
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    isRunning = false
                    notificationJob?.cancel()
                    return@collect
                }

                // Mise à jour de progression
                val downloadingCount = items.count { it.status == DownloadStatus.DOWNLOADING }
                val queuedCount = items.count { it.status == DownloadStatus.QUEUED }

                val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                val progressNotification = buildNotification(
                    title = "$downloadingCount en cours · $queuedCount en attente",
                    message = "$completed/$total · $failed échec(s)",
                    progress = completed,
                    max = total,
                    showCancel = true
                )
                notificationManager.notify(NOTIFICATION_ID, progressNotification)
            }
        }
    }

    override fun onDestroy() {
        notificationJob?.cancel()
        scope.cancel()
        isRunning = false
        super.onDestroy()
    }

    // ── Construction de la notification ──

    private fun buildNotification(
        title: String,
        message: String,
        progress: Int,
        max: Int,
        showCancel: Boolean
    ): Notification {
        val cancelIntent = Intent(this, DownloadService::class.java).apply {
            action = ACTION_CANCEL_ALL
        }
        val cancelPendingIntent = PendingIntent.getService(
            this, 0, cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setProgress(max, progress, max == 0)
            .apply {
                if (showCancel) {
                    addAction(android.R.drawable.ic_menu_close_clear_cancel, "Annuler", cancelPendingIntent)
                }
            }
            .build()
    }
}
