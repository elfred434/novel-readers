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
import com.novelreader.data.local.dao.ChapterDao
import com.novelreader.data.local.dao.NovelDao
import com.novelreader.data.repository.NovelRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * WorkManager qui vérifie périodiquement les nouveaux chapitres
 * pour tous les novels de la bibliothèque.
 *
 * Planifié toutes les 12h par défaut dans NovelReaderApp.
 * Met à jour le compteur unreadChapterCount sur chaque novel.
 */
@HiltWorker
class UpdateWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: NovelRepository,
    private val novelDao: NovelDao,
    private val chapterDao: ChapterDao
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val novels = novelDao.getAllNovelsOnce()

            for (novel in novels) {
                try {
                    val remoteChapters = repository.getChapterList(novel.slug)
                    val localChapters = chapterDao.getChaptersForNovelOnce(novel.slug)
                    val localNumbers = localChapters.map { it.chapterNumber }.toSet()
                    val newChapters = remoteChapters.filter { it.chapterNumber !in localNumbers }

                    if (newChapters.isNotEmpty()) {
                        novelDao.updateUnreadCount(novel.slug, newChapters.size)
                        repository.cacheChapters(novel.slug, remoteChapters, novel.title)
                    }
                } catch (_: Exception) {
                    // Échec pour ce novel, on continue avec les suivants
                }
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "novel_update_check"

        fun schedule(workManager: WorkManager, intervalHours: Long = 12) {
            val request = PeriodicWorkRequestBuilder<UpdateWorker>(intervalHours, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()

            workManager.enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

        fun cancel(workManager: WorkManager) {
            workManager.cancelUniqueWork(WORK_NAME)
        }
    }
}
