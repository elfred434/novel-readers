package com.novelreader.data.download

import com.novelreader.data.model.ChapterContent
import com.novelreader.data.repository.NovelRepository
import com.novelreader.data.storage.StorageManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

enum class DownloadStatus { QUEUED, DOWNLOADING, COMPLETED, FAILED, CANCELLED }

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
 * Gestionnaire de téléchargements.
 * Sauvegarde les chapitres dans le dossier choisi via [StorageManager].
 * Supporte stockage interne (filesDir) ET externe (SAF).
 */
@Singleton
class DownloadManager @Inject constructor(
    private val repository: NovelRepository,
    private val storageManager: StorageManager
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _queue = MutableStateFlow<List<DownloadItem>>(emptyList())
    val queue: StateFlow<List<DownloadItem>> = _queue.asStateFlow()

    var maxConcurrent = 2
    var maxRetries = 3
    private var activeCount = 0

    fun enqueue(
        chapterId: String, novelSlug: String, chapterNumber: Int, url: String,
        novelTitle: String = "", chapterTitle: String = "", priority: Boolean = false
    ) {
        if (_queue.value.any { it.chapterId == chapterId && it.status != DownloadStatus.FAILED }) return
        val item = DownloadItem(chapterId = chapterId, novelSlug = novelSlug, chapterNumber = chapterNumber,
            url = url, novelTitle = novelTitle, chapterTitle = chapterTitle, status = DownloadStatus.QUEUED)
        _queue.update { if (priority) listOf(item) + it else it + item }
        processQueue()
    }

    fun enqueueAll(items: List<DownloadItem>, priority: Boolean = false) {
        val newItems = items.filter { item ->
            _queue.value.none { it.chapterId == item.chapterId && it.status != DownloadStatus.FAILED }
        }
        if (newItems.isEmpty()) return
        _queue.update { if (priority) newItems + it else it + newItems }
        processQueue()
    }

    fun cancel(chapterId: String) {
        _queue.update { current -> current.map { if (it.chapterId == chapterId) it.copy(status = DownloadStatus.CANCELLED) else it } }
        processQueue()
    }

    fun retryAllFailed() {
        _queue.update { current -> current.map { if (it.status == DownloadStatus.FAILED) it.copy(status = DownloadStatus.QUEUED, retryCount = 0, error = null) else it } }
        processQueue()
    }

    fun retry(chapterId: String) {
        _queue.update { current -> current.map { if (it.chapterId == chapterId) it.copy(status = DownloadStatus.QUEUED, error = null) else it } }
        processQueue()
    }

    private fun processQueue() {
        val pending = _queue.value.filter { it.status == DownloadStatus.QUEUED }
        val slots = maxConcurrent - activeCount
        if (slots <= 0 || pending.isEmpty()) return
        pending.take(slots).forEach { item ->
            activeCount++
            _queue.update { current -> current.map { if (it.chapterId == item.chapterId) it.copy(status = DownloadStatus.DOWNLOADING) else it } }
            scope.launch { downloadItem(item) }
        }
    }

    private suspend fun downloadItem(item: DownloadItem) {
        try {
            _queue.update { current -> current.map { if (it.chapterId == item.chapterId) it.copy(progress = 0.1f) else it } }
            val content: ChapterContent = repository.getChapterContent(item.url)
            _queue.update { current -> current.map { if (it.chapterId == item.chapterId) it.copy(progress = 0.5f) else it } }

            // 1. Sauvegarder dans la DB (métadonnées + historique)
            repository.downloadChapter(item.chapterId, content)

            // 2. Sauvegarder dans le STOCKAGE UTILISATEUR (interne ou SAF)
            val data = StoredDownload(
                chapterTitle = content.chapterTitle,
                novelTitle = content.novelTitle,
                paragraphs = content.paragraphs.map { StoredDownloadPara(it.index, it.htmlContent) },
                prevChapterUrl = content.prevChapterUrl,
                nextChapterUrl = content.nextChapterUrl
            )
            val jsonStr = json.encodeToString(data)
            storageManager.saveChapterFile(item.novelSlug, item.chapterNumber, jsonStr)

            _queue.update { current -> current.map { if (it.chapterId == item.chapterId) it.copy(status = DownloadStatus.COMPLETED, progress = 1f) else it } }
        } catch (e: Exception) {
            val newCount = item.retryCount + 1
            if (newCount < maxRetries) {
                _queue.update { current -> current.map { if (it.chapterId == item.chapterId) it.copy(status = DownloadStatus.QUEUED, retryCount = newCount, error = "Tentative $newCount/$maxRetries: ${e.message}") else it } }
            } else {
                _queue.update { current -> current.map { if (it.chapterId == item.chapterId) it.copy(status = DownloadStatus.FAILED, error = e.message ?: "Erreur") else it } }
            }
        } finally {
            activeCount--
            processQueue()
        }
    }

    fun clearAll() {
        _queue.value = emptyList()
        scope.launch {
            repository.clearCache()
            storageManager.deleteAllFiles()
        }
    }

    val activeDownloads: Int get() = _queue.value.count { it.status == DownloadStatus.DOWNLOADING }
    val failedDownloads: Int get() = _queue.value.count { it.status == DownloadStatus.FAILED }
    val queuedDownloads: Int get() = _queue.value.count { it.status == DownloadStatus.QUEUED }
}

@Serializable
data class StoredDownload(
    val chapterTitle: String,
    val novelTitle: String,
    val paragraphs: List<StoredDownloadPara>,
    val prevChapterUrl: String? = null,
    val nextChapterUrl: String? = null
)

@Serializable
data class StoredDownloadPara(val index: Int, val htmlContent: String)
