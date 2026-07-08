package com.novelreader.ui.screens.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novelreader.data.download.DownloadManager
import com.novelreader.data.model.ChapterPreview
import com.novelreader.data.model.Novel
import com.novelreader.data.repository.NovelRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DetailUiState(
    val novel: Novel? = null,
    val chapters: List<ChapterPreview> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val isInLibrary: Boolean = false,
    val lastReadChapterNumber: Int? = null
)

@HiltViewModel
class DetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: NovelRepository,
    private val downloadManager: DownloadManager
) : ViewModel() {

    private val slug: String = savedStateHandle["slug"] ?: ""

    /** Observed by DetailScreen to show download status per chapter */
    val downloadQueue = downloadManager.queue

    private val _uiState = MutableStateFlow(DetailUiState())
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()

    init { loadNovelDetails() }

    fun loadNovelDetails() {
        if (slug.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val novel = repository.getNovelDetails(slug)
                val chapters = repository.getChapterList(slug)
                val inLibrary = repository.isNovelInLibrary(slug)
                _uiState.update { it.copy(novel = novel, chapters = chapters, isLoading = false, isInLibrary = inLibrary) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "Erreur") }
            }
        }
    }

    fun toggleLibrary() {
        val novel = _uiState.value.novel ?: return
        viewModelScope.launch {
            if (_uiState.value.isInLibrary) {
                repository.removeNovelFromLibrary(slug)
                _uiState.update { it.copy(isInLibrary = false) }
            } else {
                repository.addNovelToLibrary(novel)
                repository.cacheChapters(slug, _uiState.value.chapters, novel.title)
                _uiState.update { it.copy(isInLibrary = true) }
            }
        }
    }

    /** Download single chapter via the queue manager */
    fun downloadChapter(chapter: ChapterPreview) {
        val novel = _uiState.value.novel ?: return
        downloadManager.enqueue(
            chapterId = NovelRepository.chapterId(slug, chapter.chapterNumber),
            novelSlug = slug, chapterNumber = chapter.chapterNumber, url = chapter.url,
            novelTitle = novel.title, chapterTitle = chapter.title
        )
    }

    /** Enqueue all chapters for download */
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
