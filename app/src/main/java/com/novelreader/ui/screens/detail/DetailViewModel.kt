package com.novelreader.ui.screens.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novelreader.data.download.DownloadManager
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
    val downloadedChapters: Set<Int> = emptySet(), // numéros de chapitres téléchargés
    val downloadingChapters: Set<Int> = emptySet(),
    val lastReadChapterNumber: Int? = null
)

@HiltViewModel
class DetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: NovelRepository,
    private val downloadManager: DownloadManager,
    private val storageManager: StorageManager
) : ViewModel() {

    private val slug: String = savedStateHandle["slug"] ?: ""

    private val _uiState = MutableStateFlow(DetailUiState())
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()

    init {
        loadNovelDetails()
        // Observer les téléchargements en cours
        viewModelScope.launch {
            downloadManager.queue.collect { items ->
                val downloading = items.filter { it.novelSlug == slug }
                    .map { it.chapterNumber }
                    .toSet()
                _uiState.update { it.copy(downloadingChapters = downloading) }
            }
        }
    }

    fun loadNovelDetails() {
        if (slug.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            // 1. Toujours charger la liste des téléchargés (hors-ligne d'abord)
            val downloadedNums = withContext(Dispatchers.IO) {
                storageManager.getDownloadedChapterNumbers(slug).toSet()
            }

            try {
                // Tentative réseau
                val novel = repository.getNovelDetails(slug)
                val chapters = repository.getChapterList(slug)
                val inLibrary = repository.isNovelInLibrary(slug)
                _uiState.update { it.copy(
                    novel = novel, chapters = chapters, isLoading = false,
                    isInLibrary = inLibrary, isOffline = false,
                    downloadedChapters = downloadedNums
                )}
            } catch (e: Exception) {
                // PAS DE RÉSEAU → charger local
                try {
                    val localNovel = repository.getLocalNovelBySlug(slug)
                    if (localNovel != null) {
                        val localChapters = repository.getChaptersFromDb(slug)
                        val novel = Novel(
                            id = "", slug = localNovel.slug, title = localNovel.title,
                            author = localNovel.author, coverImageUrl = localNovel.coverImageUrl,
                            synopsis = localNovel.synopsis, status = NovelStatus.fromString(localNovel.status),
                            rating = localNovel.rating, genres = localNovel.genres,
                            chapterCount = localNovel.unreadChapterCount, sourceUrl = localNovel.sourceUrl
                        )
                        val previews = localChapters.map { ch ->
                            ChapterPreview(id = ch.id, novelSlug = ch.novelSlug,
                                chapterNumber = ch.chapterNumber, title = ch.title,
                                url = ch.url, publishedAt = ch.publishedAt)
                        }
                        _uiState.update { it.copy(novel = novel, chapters = previews,
                            isLoading = false, isInLibrary = true, isOffline = true,
                            downloadedChapters = downloadedNums)}
                    } else {
                        _uiState.update { it.copy(isLoading = false, downloadedChapters = downloadedNums,
                            error = "Novel non trouvé en local. Vérifie ta connexion.")}
                    }
                } catch (e2: Exception) {
                    _uiState.update { it.copy(isLoading = false, downloadedChapters = downloadedNums,
                        error = "Impossible de charger le novel. Vérifie ta connexion.")}
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
    }

    fun downloadAllChapters() {
        val novel = _uiState.value.novel ?: return
        val items = _uiState.value.chapters.map { ch ->
            com.novelreader.data.download.DownloadItem(
                chapterId = NovelRepository.chapterId(slug, ch.chapterNumber),
                novelSlug = slug, chapterNumber = ch.chapterNumber, url = ch.url,
                novelTitle = novel.title, chapterTitle = ch.title
            )
        }
        downloadManager.enqueueAll(items)
    }

    fun markChapterAsUnread(chapter: ChapterPreview) {
        viewModelScope.launch {
            repository.markChapterAsUnread(NovelRepository.chapterId(slug, chapter.chapterNumber))
        }
    }
}
