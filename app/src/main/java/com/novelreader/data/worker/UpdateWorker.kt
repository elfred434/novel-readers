package com.novelreader.data.worker

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.novelreader.data.local.dao.ChapterDao
import com.novelreader.data.local.dao.NovelDao
import com.novelreader.data.local.preferences.PreferencesManager
import com.novelreader.data.repository.NovelRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * WorkManager principal qui déclenche la vérification des mises à jour.
 * Pour chaque novel dans la bibliothèque, il vérifie les nouveaux chapitres.
 *
 * CORRECTIONS AUDIT (v2) :
 * - unreadChapterCount = VRAI nombre de chapitres non lus (getUnreadCount),
 *   pas le nombre de nouveaux chapitres détectés.
 * - Poste une notification « nouveaux chapitres » (canal novel_updates)
 *   si la préférence notifications est activée.
 * - cacheChapters() préserve désormais l'état de lecture (voir NovelRepository).
 *
 * AMÉLIORATION possible : traiter chaque novel dans un worker séparé pour
 * paralléliser. Pour le MVP, traitement séquentiel avec erreur individuelle.
 */
@HiltWorker
class UpdateWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: NovelRepository,
    private val novelDao: NovelDao,
    private val chapterDao: ChapterDao,
    private val prefs: PreferencesManager
) : CoroutineWorker(context, params) {

    companion object {
        private const val WORK_NAME = "novel_update_check"
        private const val TAG_NOVEL_PREFIX = "novel_update_"
        private const val CHANNEL_UPDATES = "novel_updates"
        private const val NOTIFICATION_ID = 1002

        /** Planifie le worker périodique (UPDATE met à jour l'intervalle si changé). */
        fun schedule(workManager: WorkManager, intervalHours: Long = 12) {
            val request = PeriodicWorkRequestBuilder<UpdateWorker>(intervalHours, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .addTag("novel_updates_periodic")
                .build()
            workManager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        }

        /** Planifie un worker unique pour un novel spécifique (parallélisable). */
        fun scheduleNovelUpdate(workManager: WorkManager, novelSlug: String) {
            val request = androidx.work.OneTimeWorkRequestBuilder<SingleNovelUpdateWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .addTag(TAG_NOVEL_PREFIX + novelSlug)
                .setInputData(workDataOf("novel_slug" to novelSlug))
                .build()
            workManager.enqueueUniqueWork(
                TAG_NOVEL_PREFIX + novelSlug,
                androidx.work.ExistingWorkPolicy.REPLACE,
                request
            )
        }

        fun cancel(workManager: WorkManager) { workManager.cancelUniqueWork(WORK_NAME) }
    }

    override suspend fun doWork(): Result {
        return try {
            val novels = novelDao.getAllNovelsOnce()
            var totalNew = 0
            var failures = 0

            for (novel in novels) {
                try {
                    totalNew += checkNovelUpdates(novel.slug, novel.title)
                } catch (_: Exception) {
                    failures++
                }
            }

            // Notification récapitulative si nouveaux chapitres + préférence activée
            if (totalNew > 0) {
                val notifEnabled = runCatching { prefs.notificationsEnabled.first() }.getOrDefault(true)
                if (notifEnabled) showUpdateNotification(totalNew)
            }

            if (failures == novels.size && novels.isNotEmpty()) Result.retry()
            else Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    /**
     * Vérifie les mises à jour pour un novel spécifique.
     * Met à jour le compteur avec le VRAI nombre de chapitres non lus.
     */
    private suspend fun checkNovelUpdates(slug: String, title: String): Int {
        val remoteChapters = repository.getChapterList(slug)
        val localChapters = chapterDao.getChaptersForNovelOnce(slug)
        val localNumbers = localChapters.map { it.chapterNumber }.toSet()
        val newChapters = remoteChapters.filter { it.chapterNumber !in localNumbers }

        if (newChapters.isNotEmpty()) {
            // cacheChapters préserve isRead/scrollPosition/isDownloaded (upsert)
            repository.cacheChapters(slug, remoteChapters, title)
            // Compteur = vrai total des non-lus (inclut les nouveaux insérés)
            novelDao.updateUnreadCount(slug, chapterDao.getUnreadCount(slug))
        }
        return newChapters.size
    }

    /** Poste une notification « nouveaux chapitres » sur le canal dédié. */
    private fun showUpdateNotification(newCount: Int) {
        // Permission runtime requise depuis Android 13
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            applicationContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val notificationManager =
            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_UPDATES)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("Nouveaux chapitres disponibles")
            .setContentText("$newCount nouveau(x) chapitre(s) dans ta bibliothèque")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
}

/**
 * Worker individuel pour mettre à jour un seul novel.
 * Peut être lancé en parallèle avec d'autres (un par novel).
 */
@HiltWorker
class SingleNovelUpdateWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: NovelRepository,
    private val novelDao: NovelDao,
    private val chapterDao: ChapterDao
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val slug = inputData.getString("novel_slug") ?: return Result.failure()
        return try {
            val novel = novelDao.getNovelBySlug(slug) ?: return Result.failure()
            val remoteChapters = repository.getChapterList(slug)
            val localChapters = chapterDao.getChaptersForNovelOnce(slug)
            val localNumbers = localChapters.map { it.chapterNumber }.toSet()
            val newChapters = remoteChapters.filter { it.chapterNumber !in localNumbers }

            if (newChapters.isNotEmpty()) {
                repository.cacheChapters(slug, remoteChapters, novel.title)
                novelDao.updateUnreadCount(slug, chapterDao.getUnreadCount(slug))
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
