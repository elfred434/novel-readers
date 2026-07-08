package com.novelreader.data.worker

import android.content.Context
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
import com.novelreader.data.repository.NovelRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * WorkManager principal qui déclenche la vérification des mises à jour.
 * Pour chaque novel dans la bibliothèque, il vérifie les nouveaux chapitres.
 *
 * AMÉLIORATION : Pour un traitement vraiment parallélisé, chaque novel
 * pourrait être traité dans un worker séparé. Pour le MVP, on traite
 * en séquence avec gestion d'erreur individuelle.
 */
@HiltWorker
class UpdateWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: NovelRepository,
    private val novelDao: NovelDao,
    private val chapterDao: ChapterDao
) : CoroutineWorker(context, params) {

    companion object {
        private const val WORK_NAME = "novel_update_check"
        private const val TAG_NOVEL_PREFIX = "novel_update_"

        /** Planifie le worker périodique. */
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

            if (failures == novels.size && novels.isNotEmpty()) Result.retry()
            else Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    /**
     * Vérifie les mises à jour pour un novel spécifique.
     * Mutex-free car chaque novel est traité séquentiellement ici.
     */
    private suspend fun checkNovelUpdates(slug: String, title: String): Int {
        val remoteChapters = repository.getChapterList(slug)
        val localChapters = chapterDao.getChaptersForNovelOnce(slug)
        val localNumbers = localChapters.map { it.chapterNumber }.toSet()
        val newChapters = remoteChapters.filter { it.chapterNumber !in localNumbers }

        if (newChapters.isNotEmpty()) {
            novelDao.updateUnreadCount(slug, newChapters.size)
            repository.cacheChapters(slug, remoteChapters, title)
        }
        return newChapters.size
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
                novelDao.updateUnreadCount(slug, newChapters.size)
                repository.cacheChapters(slug, remoteChapters, novel.title)
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
