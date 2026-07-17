package com.novelreader.data.download

import com.novelreader.data.local.preferences.PreferencesManager
import com.novelreader.data.model.ChapterContent
import com.novelreader.data.network.NetworkStateManager
import com.novelreader.data.repository.NovelRepository
import com.novelreader.data.storage.StorageManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
 *
 * CORRECTIONS AUDIT (v2) :
 * - Annulation RÉELLE : chaque téléchargement a un [Job] annulable (cancel/cancelAll).
 * - processQueue() sérialisé par [Mutex] → plus de race condition sur le parallélisme.
 * - La préférence « WiFi uniquement » est APPLIQUÉE (pause des téléchargements
 *   hors WiFi, reprise automatique au retour du WiFi).
 * - Les préférences (WiFi-only, simultanéité, mode haute vitesse) sont
 *   collectées directement depuis DataStore : plus de synchronisation manuelle.
 */
@Singleton
class DownloadManager @Inject constructor(
    private val repository: NovelRepository,
    private val storageManager: StorageManager,
    private val networkManager: NetworkStateManager,
    private val prefs: PreferencesManager
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _queue = MutableStateFlow<List<DownloadItem>>(emptyList())
    val queue: StateFlow<List<DownloadItem>> = _queue.asStateFlow()

    /** Parallélisme configuré par l'utilisateur (mode économie). */
    private var userMaxConcurrent = 2

    /** Parallélisme actif (augmenté sur WiFi si mode haute vitesse activé). */
    var maxConcurrent = 2
        private set

    /** Télécharger uniquement en WiFi (préférence utilisateur). */
    @Volatile
    private var wifiOnly = true

    /** Mode haute vitesse (5 téléchargements simultanés en WiFi). */
    @Volatile
    private var highDataModeEnabled = true

    /** Jobs actifs par chapterId — permet l'annulation réelle. */
    private val jobs = mutableMapOf<String, Job>()

    /** Sérialise processQueue() (appelé depuis plusieurs threads). */
    private val queueMutex = Mutex()

    var maxRetries = 3

    init {
        // Préférences utilisateur (auto-synchronisées via DataStore)
        scope.launch { prefs.downloadOnWifiOnly.collect { wifiOnly = it; processQueue() } }
        scope.launch { prefs.downloadMaxConcurrent.collect { userMaxConcurrent = it; updateMaxConcurrent() } }
        scope.launch { prefs.wifiHighDataMode.collect { highDataModeEnabled = it; updateMaxConcurrent() } }

        // Réagir aux bascules réseau (reprise auto au retour du WiFi)
        scope.launch { networkManager.isOnWifi.collect { updateMaxConcurrent() } }
    }

    /**
     * En mode WiFi + haute vitesse : jusqu'à 5 téléchargements simultanés.
     * Sinon : valeur utilisateur (2 par défaut).
     */
    private fun updateMaxConcurrent() {
        maxConcurrent = if (networkManager.isCurrentlyOnWifi() && highDataModeEnabled) 5 else userMaxConcurrent
        processQueue()
    }

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

    /** Annule un téléchargement — interrompt réellement le Job en cours. */
    fun cancel(chapterId: String) {
        synchronized(jobs) { jobs.remove(chapterId) }?.cancel()
        _queue.update { current -> current.map { if (it.chapterId == chapterId) it.copy(status = DownloadStatus.CANCELLED) else it } }
        processQueue()
    }

    fun cancelAll() {
        synchronized(jobs) {
            jobs.values.forEach { it.cancel() }
            jobs.clear()
        }
        _queue.update { current ->
            current.map {
                if (it.status == DownloadStatus.QUEUED || it.status == DownloadStatus.DOWNLOADING) {
                    it.copy(status = DownloadStatus.CANCELLED)
                } else it
            }
        }
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

    /**
     * Lance des téléchargements dans la limite du parallélisme.
     * Sérialisé par Mutex ; respecte la préférence « WiFi uniquement ».
     */
    private fun processQueue() {
        scope.launch {
            queueMutex.withLock {
                if (wifiOnly && !networkManager.isCurrentlyOnWifi()) return@withLock

                val pending = _queue.value.filter { it.status == DownloadStatus.QUEUED }
                val activeCount = synchronized(jobs) { jobs.size }
                val slots = maxConcurrent - activeCount
                if (slots <= 0 || pending.isEmpty()) return@withLock

                pending.take(slots).forEach { item ->
                    _queue.update { current -> current.map { if (it.chapterId == item.chapterId) it.copy(status = DownloadStatus.DOWNLOADING) else it } }
                    val job = scope.launch { downloadItem(item) }
                    synchronized(jobs) { jobs[item.chapterId] = job }
                }
            }
        }
    }

    private suspend fun downloadItem(item: DownloadItem) {
        try {
            _queue.update { current -> current.map { if (it.chapterId == item.chapterId && it.status == DownloadStatus.DOWNLOADING) it.copy(progress = 0.1f) else it } }
            val content: ChapterContent = repository.getChapterContent(item.url)
            _queue.update { current -> current.map { if (it.chapterId == item.chapterId && it.status == DownloadStatus.DOWNLOADING) it.copy(progress = 0.5f) else it } }

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

            // Ne pas écraser un statut CANCELLED posé entre-temps
            _queue.update { current -> current.map { if (it.chapterId == item.chapterId && it.status == DownloadStatus.DOWNLOADING) it.copy(status = DownloadStatus.COMPLETED, progress = 1f) else it } }
        } catch (e: CancellationException) {
            // Annulé par l'utilisateur : le statut CANCELLED a déjà été posé par cancel()
            throw e
        } catch (e: Exception) {
            val newCount = item.retryCount + 1
            if (newCount < maxRetries) {
                _queue.update { current -> current.map { if (it.chapterId == item.chapterId) it.copy(status = DownloadStatus.QUEUED, retryCount = newCount, error = "Tentative $newCount/$maxRetries: ${e.message}") else it } }
            } else {
                _queue.update { current -> current.map { if (it.chapterId == item.chapterId) it.copy(status = DownloadStatus.FAILED, error = e.message ?: "Erreur") else it } }
            }
        } finally {
            synchronized(jobs) { jobs.remove(item.chapterId) }
            processQueue()
        }
    }

    fun clearAll() {
        synchronized(jobs) {
            jobs.values.forEach { it.cancel() }
            jobs.clear()
        }
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
