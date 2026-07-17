package com.novelreader.ui.screens.detail

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novelreader.data.download.DownloadManager
import com.novelreader.data.download.DownloadService
import com.novelreader.data.download.DownloadStatus
import com.novelreader.data.storage.StorageManager
import com.novelreader.data.model.ChapterPreview
import com.novelreader.data.model.Novel
import com.novelreader.data.model.NovelStatus
import com.novelreader.data.repository.NovelRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class DetailUiState(
    val novel: Novel? = null,
    val chapters: List<ChapterPreview> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val isInLibrary: Boolean = false,
    val isOffline: Boolean = false,
    val downloadedChapters: Set<Int> = emptySet(),
    val downloadingChapters: Set<Int> = emptySet(),
    val lastReadChapterNumber: Int? = null,
    /** Numéros des chapitres marqués comme lus (depuis Room). */
    val readChapters: Set<Int> = emptySet(),
    val isSelectionMode: Boolean = false,
    val selectedChapters: Set<Int> = emptySet()
)

@HiltViewModel
class DetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: NovelRepository,
    private val downloadManager: DownloadManager,
    private val storageManager: StorageManager,
    private val app: Application
) : ViewModel() {

    private val slug: String = savedStateHandle["slug"] ?: ""
    private val _uiState = MutableStateFlow(DetailUiState())
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()

    // Stocke le dernier état des chapitres dans la queue pour détecter les transitions
    private var previousCompleted: Set<Int> = emptySet()

    init {
        loadNovelDetails()

        // Observer les chapitres locaux pour afficher l'état « lu » de chacun
        viewModelScope.launch {
            repository.getLocalChapters(slug).collect { entities ->
                _uiState.update {
                    it.copy(
                        readChapters = entities.filter { e -> e.isRead }.map { e -> e.chapterNumber }.toSet(),
                        lastReadChapterNumber = entities.filter { e -> e.isRead }.maxByOrNull { e -> e.readAt ?: 0L }?.chapterNumber
                            ?: it.lastReadChapterNumber
                    )
                }
            }
        }

        viewModelScope.launch {
            downloadManager.queue.collect { items ->
                val slugItems = items.filter { it.novelSlug == slug }

                // En cours (spinner)
                val downloading = slugItems
                    .filter { it.status == DownloadStatus.DOWNLOADING }
                    .map { it.chapterNumber }.toSet()

                // Vient de passer COMPLETED (transition détectée)
                val currentCompleted = slugItems
                    .filter { it.status == DownloadStatus.COMPLETED }
                    .map { it.chapterNumber }.toSet()
                val newCompleted = currentCompleted - previousCompleted
                previousCompleted = currentCompleted

                _uiState.update { it.copy(downloadingChapters = downloading) }

                // Rafraîchir downloadedChapters SEULEMENT si un nouveau chapitre vient de finir
                if (newCompleted.isNotEmpty()) {
                    val nums = withContext(Dispatchers.IO) {
                        storageManager.getDownloadedChapterNumbers(slug).toSet()
                    }
                    _uiState.update { it.copy(downloadedChapters = nums) }
                }
            }
        }
    }

    fun loadNovelDetails() {
        if (slug.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val downloadedNums = withContext(Dispatchers.IO) { storageManager.getDownloadedChapterNumbers(slug).toSet() }

            try {
                val novel = repository.getNovelDetails(slug)
                val chapters = repository.getChapterList(slug)
                val inLibrary = repository.isNovelInLibrary(slug)
                _uiState.update { it.copy(novel = novel, chapters = chapters, isLoading = false,
                    isInLibrary = inLibrary, isOffline = false, downloadedChapters = downloadedNums,
                    lastReadChapterNumber = repository.getLocalNovelBySlug(slug)?.lastChapterRead) }
            } catch (e: Exception) {
                try {
                    val localNovel = repository.getLocalNovelBySlug(slug)
                    if (localNovel != null) {
                        val localChapters = repository.getChaptersFromDb(slug)
                        val novel = Novel(id = "", slug = localNovel.slug, title = localNovel.title,
                            author = localNovel.author, coverImageUrl = localNovel.coverImageUrl,
                            synopsis = localNovel.synopsis, status = NovelStatus.fromString(localNovel.status),
                            rating = localNovel.rating, genres = localNovel.genres,
                            chapterCount = localChapters.size, sourceUrl = localNovel.sourceUrl)
                        val previews = localChapters.sortedBy { it.chapterNumber }.map { ch ->
                            ChapterPreview(id = ch.id, novelSlug = ch.novelSlug, chapterNumber = ch.chapterNumber,
                                title = ch.title, url = ch.url, publishedAt = ch.publishedAt)
                        }
                        _uiState.update { it.copy(novel = novel, chapters = previews,
                            isLoading = false, isInLibrary = true, isOffline = true, downloadedChapters = downloadedNums) }
                    } else {
                        _uiState.update { it.copy(isLoading = false, downloadedChapters = downloadedNums, error = "Novel non trouvé en local.") }
                    }
                } catch (e2: Exception) {
                    _uiState.update { it.copy(isLoading = false, downloadedChapters = downloadedNums, error = "Impossible de charger le novel.") }
                }
            }
        }
    }

    fun toggleLibrary() {
        val novel = _uiState.value.novel ?: return
        viewModelScope.launch {
            if (_uiState.value.isInLibrary) {
                repository.removeNovelFromLibrary(slug)
                storageManager.deleteNovelFiles(slug)
                _uiState.update { it.copy(isInLibrary = false, downloadedChapters = emptySet()) }
            } else {
                repository.addNovelToLibrary(novel)
                repository.cacheChapters(slug, _uiState.value.chapters, novel.title)
                _uiState.update { it.copy(isInLibrary = true) }
            }
        }
    }

    fun downloadChapter(chapter: ChapterPreview) {
        val novel = _uiState.value.novel ?: return
        downloadManager.enqueue(
            chapterId = NovelRepository.chapterId(slug, chapter.chapterNumber),
            novelSlug = slug, chapterNumber = chapter.chapterNumber, url = chapter.url,
            novelTitle = novel.title, chapterTitle = chapter.title
        )
        DownloadService.start(app)
    }

    fun downloadAllChapters() {
        val novel = _uiState.value.novel ?: return
        downloadManager.enqueueAll(_uiState.value.chapters.map { ch ->
            com.novelreader.data.download.DownloadItem(
                chapterId = NovelRepository.chapterId(slug, ch.chapterNumber),
                novelSlug = slug, chapterNumber = ch.chapterNumber, url = ch.url,
                novelTitle = novel.title, chapterTitle = ch.title
            )
        })
        DownloadService.start(app)
    }

    fun markChapterAsUnread(chapter: ChapterPreview) {
        viewModelScope.launch {
            repository.markChapterAsUnread(NovelRepository.chapterId(slug, chapter.chapterNumber))
        }
    }

    fun deleteDownloadedChapter(chapterNumber: Int) {
        viewModelScope.launch {
            storageManager.deleteChapterFile(slug, chapterNumber)
            repository.deleteDownloadedChapterData(slug, chapterNumber)
            val nums = withContext(Dispatchers.IO) { storageManager.getDownloadedChapterNumbers(slug).toSet() }
            _uiState.update { it.copy(downloadedChapters = nums) }
        }
    }

    // ── Sélection multiple ─────────────────────────────

    fun enterSelectionMode(chapterNumber: Int) {
        _uiState.update { it.copy(
            isSelectionMode = true,
            selectedChapters = setOf(chapterNumber)
        )}
    }

    fun exitSelectionMode() {
        _uiState.update { it.copy(isSelectionMode = false, selectedChapters = emptySet()) }
    }

    fun toggleChapterSelection(chapterNumber: Int) {
        _uiState.update { state ->
            val newSelection = if (chapterNumber in state.selectedChapters) {
                state.selectedChapters - chapterNumber
            } else {
                state.selectedChapters + chapterNumber
            }
            if (newSelection.isEmpty()) {
                state.copy(isSelectionMode = false, selectedChapters = emptySet())
            } else {
                state.copy(selectedChapters = newSelection)
            }
        }
    }

    fun selectAllDownloaded() {
        val downloaded = _uiState.value.downloadedChapters
        _uiState.update { it.copy(selectedChapters = downloaded, isSelectionMode = true) }
    }

    val selectedCount: Int get() = _uiState.value.selectedChapters.size
    val isSelectionActive: Boolean get() = _uiState.value.isSelectionMode

    fun deleteSelectedChapters() {
        val selected = _uiState.value.selectedChapters.toList()
        if (selected.isEmpty()) return
        viewModelScope.launch {
            storageManager.deleteMultipleChapterFiles(slug, selected)
            repository.deleteMultipleDownloadedChapters(slug, selected)
            val nums = withContext(Dispatchers.IO) { storageManager.getDownloadedChapterNumbers(slug).toSet() }
            _uiState.update { it.copy(
                downloadedChapters = nums,
                isSelectionMode = false,
                selectedChapters = emptySet()
            )}
        }
    }
}
