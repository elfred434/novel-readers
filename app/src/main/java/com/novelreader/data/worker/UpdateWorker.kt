package com.novelreader.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.novelreader.data.local.dao.NovelDao
import com.novelreader.data.repository.NovelRepository
import com.novelreader.data.update.NovelUpdateNotifier
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

private const val KEY_NOVEL_SLUG = "novel_slug"

/**
 * WorkManager principal qui déclenche la vérification des mises à jour.
 *
 * Ce worker ne fait plus le travail lui-même : il distribue un worker unique
 * par novel ([SingleNovelUpdateWorker]). Bénéfices : traitement parallèle,
 * isolation des erreurs et nouvelle tentative individuelle par novel
 * (backoff exponentiel WorkManager).
 */
@HiltWorker
class UpdateWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val novelDao: NovelDao
) : CoroutineWorker(context, params) {

    companion object {
        private const val WORK_NAME = "novel_update_check"
        private const val WORK_NOW_NAME = "novel_update_check_now"
        private const val TAG_NOVEL_PREFIX = "novel_update_"

        /** Planifie le worker périodique. */
        fun schedule(workManager: WorkManager, intervalHours: Long = 12) {
            val request = PeriodicWorkRequestBuilder<UpdateWorker>(intervalHours, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .addTag("novel_updates_periodic")
                .build()
            workManager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        }

        /** Lance une vérification immédiate (bouton « Vérifier » dans l'UI). */
        fun runNow(workManager: WorkManager) {
            val request = OneTimeWorkRequestBuilder<UpdateWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            workManager.enqueueUniqueWork(WORK_NOW_NAME, ExistingWorkPolicy.REPLACE, request)
        }

        /** Planifie un worker unique pour un novel spécifique (parallélisable). */
        fun scheduleNovelUpdate(workManager: WorkManager, novelSlug: String) {
            val request = OneTimeWorkRequestBuilder<SingleNovelUpdateWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .addTag(TAG_NOVEL_PREFIX + novelSlug)
                .setInputData(workDataOf(KEY_NOVEL_SLUG to novelSlug))
                .build()
            workManager.enqueueUniqueWork(
                TAG_NOVEL_PREFIX + novelSlug,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }

        fun cancel(workManager: WorkManager) {
            workManager.cancelUniqueWork(WORK_NAME)
            workManager.cancelUniqueWork(WORK_NOW_NAME)
        }
    }

    override suspend fun doWork(): Result {
        return try {
            val novels = novelDao.getAllNovelsOnce()
            if (novels.isNotEmpty()) {
                val wm = WorkManager.getInstance(applicationContext)
                novels.forEach { scheduleNovelUpdate(wm, it.slug) }
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}

/**
 * Worker individuel pour mettre à jour un seul novel.
 * Peut tourner en parallèle avec les autres (un par novel).
 *
 * Vérifie les nouveaux chapitres, met à jour le compteur non-lu et la liste
 * locale, puis notifie l'utilisateur si de nouveaux chapitres ont été trouvés.
 */
@HiltWorker
class SingleNovelUpdateWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: NovelRepository,
    private val novelDao: NovelDao,
    private val notifier: NovelUpdateNotifier
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val slug = inputData.getString(KEY_NOVEL_SLUG) ?: return Result.failure()
        return try {
            val novel = novelDao.getNovelBySlug(slug) ?: return Result.failure()
            val newChapters = repository.updateLibraryNovelChapters(slug, novel.title)
            if (newChapters.isNotEmpty()) {
                notifier.notifyNewChapters(slug, novel.title, newChapters)
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
