package com.novelreader.data.download

import com.novelreader.data.model.ChapterContent
import com.novelreader.data.repository.NovelRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * État d'un téléchargement dans la queue.
 */
enum class DownloadStatus {
    QUEUED, DOWNLOADING, COMPLETED, FAILED, CANCELLED
}

data class DownloadItem(
    val chapterId: String,
    val novelSlug: String,
    val chapterNumber: Int,
    val novelTitle: String = "",
    val chapterTitle: String = "",
    val url: String,
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val progress: Float = 0f,
    val error: String? = null,
    val retryCount: Int = 0
)

/**
 * Gestionnaire de téléchargements avec queue, priorité et retry.
 * Inspiré du DownloadManager de Mihon.
 *
 * @property maxConcurrent Nombre max de téléchargements simultanés
 * @property maxRetries Nombre max de tentatives par chapitre
 */
@Singleton
class DownloadManager @Inject constructor(
    private val repository: NovelRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _queue = MutableStateFlow<List<DownloadItem>>(emptyList())
    val queue: StateFlow<List<DownloadItem>> = _queue.asStateFlow()

    /** Nombre de téléchargements simultanés */
    var maxConcurrent = 2

    /** Nombre maximum de tentatives */
    var maxRetries = 3

    /** Compteur de téléchargements actifs */
    private var activeCount = 0

    /**
     * Ajoute un chapitre à la queue de téléchargement.
     */
    fun enqueue(
        chapterId: String,
        novelSlug: String,
        chapterNumber: Int,
        url: String,
        novelTitle: String = "",
        chapterTitle: String = "",
        priority: Boolean = false
    ) {
        // Éviter les doublons
        if (_queue.value.any { it.chapterId == chapterId && it.status != DownloadStatus.FAILED }) return

        val item = DownloadItem(
            chapterId = chapterId,
            novelSlug = novelSlug,
            chapterNumber = chapterNumber,
            url = url,
            novelTitle = novelTitle,
            chapterTitle = chapterTitle,
            status = DownloadStatus.QUEUED
        )

        _queue.update { current ->
            if (priority) listOf(item) + current else current + item
        }
        processQueue()
    }

    /**
     * Ajoute plusieurs chapitres à la fois (batch download).
     */
    fun enqueueAll(items: List<DownloadItem>, priority: Boolean = false) {
        val newItems = items.filter { item ->
            _queue.value.none { it.chapterId == item.chapterId && it.status != DownloadStatus.FAILED }
        }
        if (newItems.isEmpty()) return
        _queue.update { current ->
            if (priority) newItems + current else current + newItems
        }
        processQueue()
    }

    /**
     * Retire un chapitre de la queue.
     */
    fun cancel(chapterId: String) {
        _queue.update { current ->
            current.map { if (it.chapterId == chapterId) it.copy(status = DownloadStatus.CANCELLED) else it }
        }
        processQueue()
    }

    /**
     * Réessaie tous les téléchargements en échec.
     */
    fun retryAllFailed() {
        _queue.update { current ->
            current.map { if (it.status == DownloadStatus.FAILED) it.copy(status = DownloadStatus.QUEUED, retryCount = 0, error = null) else it }
        }
        processQueue()
    }

    /**
     * Réessaie un téléchargement spécifique.
     */
    fun retry(chapterId: String) {
        _queue.update { current ->
            current.map { if (it.chapterId == chapterId) it.copy(status = DownloadStatus.QUEUED, error = null) else it }
        }
        processQueue()
    }

    /**
     * Traite la queue : lance les téléchargements en attente.
     */
    private fun processQueue() {
        val pending = _queue.value.filter { it.status == DownloadStatus.QUEUED }
        val slots = maxConcurrent - activeCount

        if (slots <= 0 || pending.isEmpty()) return

        pending.take(slots).forEach { item ->
            activeCount++
            _queue.update { current ->
                current.map { if (it.chapterId == item.chapterId) it.copy(status = DownloadStatus.DOWNLOADING) else it }
            }
            scope.launch {
                downloadItem(item)
            }
        }
    }

    /**
     * Télécharge un item de la queue avec retry.
     */
    private suspend fun downloadItem(item: DownloadItem) {
        try {
            _queue.update { current ->
                current.map { if (it.chapterId == item.chapterId) it.copy(progress = 0.1f) else it }
            }
            val content: ChapterContent = repository.getChapterContent(item.url)

            _queue.update { current ->
                current.map { if (it.chapterId == item.chapterId) it.copy(progress = 0.5f) else it }
            }
            repository.downloadChapter(item.chapterId, content)

            _queue.update { current ->
                current.map { if (it.chapterId == item.chapterId) it.copy(status = DownloadStatus.COMPLETED, progress = 1f) else it }
            }
        } catch (e: Exception) {
            val newRetryCount = item.retryCount + 1
            if (newRetryCount < maxRetries) {
                _queue.update { current ->
                    current.map {
                        if (it.chapterId == item.chapterId) it.copy(
                            status = DownloadStatus.QUEUED,
                            retryCount = newRetryCount,
                            error = "Tentative $newRetryCount/$maxRetries: ${e.message}"
                        ) else it
                    }
                }
            } else {
                _queue.update { current ->
                    current.map {
                        if (it.chapterId == item.chapterId) it.copy(
                            status = DownloadStatus.FAILED,
                            error = e.message ?: "Erreur inconnue"
                        ) else it
                    }
                }
            }
        } finally {
            activeCount--
            processQueue()
        }
    }

    /**
     * Vide la queue et supprime les chapitres téléchargés.
     */
    fun clearAll() {
        _queue.value = emptyList()
        scope.launch { repository.clearCache() }
    }

    /** Nombre de téléchargements en cours. */
    val activeDownloads: Int get() = _queue.value.count { it.status == DownloadStatus.DOWNLOADING }

    /** Nombre de téléchargements en échec. */
    val failedDownloads: Int get() = _queue.value.count { it.status == DownloadStatus.FAILED }

    /** Nombre de téléchargements en attente. */
    val queuedDownloads: Int get() = _queue.value.count { it.status == DownloadStatus.QUEUED }
}
