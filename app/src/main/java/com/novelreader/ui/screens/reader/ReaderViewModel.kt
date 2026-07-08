package com.novelreader.ui.screens.reader

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novelreader.data.model.ChapterContent
import com.novelreader.data.repository.NovelRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ReaderUiState(
    val chapterContent: ChapterContent? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
    val novelSlug: String = "",
    val currentChapterUrl: String = "",
    val chapterNumber: Int = 0,
    val hasPrevChapter: Boolean = false,
    val hasNextChapter: Boolean = false,
    val settings: ReaderSettings = ReaderSettings(),
    val showSettings: Boolean = false,
    val scrollPosition: Int = 0,
    val isMarkedRead: Boolean = false
)

@HiltViewModel
class ReaderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: NovelRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReaderUiState())
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    init {
        val encoded = savedStateHandle.get<String>("chapterUrlEncoded") ?: ""
        val chapterUrl = if (encoded.isNotBlank()) {
            java.net.URLDecoder.decode(encoded, "UTF-8")
        } else ""
        if (chapterUrl.isNotBlank()) {
            loadChapter(chapterUrl)
        }
    }

    fun loadChapter(chapterUrl: String) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(isLoading = true, error = null, currentChapterUrl = chapterUrl, scrollPosition = 0)
            }
            try {
                val content = repository.getChapterContent(chapterUrl)
                val slug = extractNovelSlug(chapterUrl)
                val chapterNumber = extractChapterNumber(chapterUrl)

                _uiState.update {
                    it.copy(
                        chapterContent = content,
                        isLoading = false,
                        novelSlug = slug,
                        chapterNumber = chapterNumber,
                        hasPrevChapter = content.prevChapterUrl != null,
                        hasNextChapter = content.nextChapterUrl != null
                    )
                }

                val chapterId = NovelRepository.chapterId(slug, chapterNumber)
                repository.markChapterAsRead(chapterId)
                _uiState.update { it.copy(isMarkedRead = true) }

            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = e.message ?: "Impossible de charger le chapitre")
                }
            }
        }
    }

    fun goToNextChapter() {
        _uiState.value.chapterContent?.nextChapterUrl?.let { loadChapter(it) }
    }

    fun goToPrevChapter() {
        _uiState.value.chapterContent?.prevChapterUrl?.let { loadChapter(it) }
    }

    fun toggleSettings() { _uiState.update { it.copy(showSettings = !it.showSettings) } }
    fun hideSettings() { _uiState.update { it.copy(showSettings = false) } }

    fun updateFontSize(size: Int) {
        _uiState.update { it.copy(settings = it.settings.copy(fontSizeSp = size.coerceIn(12, 32))) }
    }
    fun updateFont(font: ReaderFont) {
        _uiState.update { it.copy(settings = it.settings.copy(fontFamily = font)) }
    }
    fun updateTheme(theme: ReaderTheme) {
        _uiState.update { it.copy(settings = it.settings.copy(readerTheme = theme)) }
    }
    fun updateLineHeight(multiplier: Float) {
        _uiState.update { it.copy(settings = it.settings.copy(lineHeightMultiplier = multiplier.coerceIn(1.2f, 2.5f))) }
    }
    fun updatePadding(padding: Int) {
        _uiState.update { it.copy(settings = it.settings.copy(horizontalPaddingDp = padding.coerceIn(12, 40))) }
    }
    fun togglePaginationMode() {
        _uiState.update { it.copy(settings = it.settings.copy(paginationMode = !it.settings.paginationMode)) }
    }

    fun saveScrollPosition(position: Int) {
        _uiState.update { it.copy(scrollPosition = position) }
    }

    suspend fun persistScrollPosition() {
        val state = _uiState.value
        if (state.scrollPosition > 0 && state.novelSlug.isNotBlank()) {
            val chapterId = NovelRepository.chapterId(state.novelSlug, state.chapterNumber)
            repository.saveScrollPosition(chapterId, state.scrollPosition)
        }
    }

    private fun extractNovelSlug(url: String): String {
        val regex = Regex("/novel/([^/]+)/")
        return regex.find(url)?.groupValues?.getOrNull(1) ?: ""
    }

    private fun extractChapterNumber(url: String): Int {
        val regex = Regex("chapter-(\\d+)(?:/)?$")
        return regex.find(url)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
    }
}
