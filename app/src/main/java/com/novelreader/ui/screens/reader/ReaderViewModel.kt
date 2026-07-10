package com.novelreader.ui.screens.reader

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novelreader.data.download.StoredDownload
import com.novelreader.data.model.ChapterContent
import com.novelreader.data.model.Paragraph
import com.novelreader.data.repository.NovelRepository
import com.novelreader.data.storage.StorageManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
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
    val isMarkedRead: Boolean = false,
    val isOffline: Boolean = false
)

@HiltViewModel
class ReaderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: NovelRepository,
    private val storageManager: StorageManager
) : ViewModel() {

    private val json = Json { ignoreUnknownKeys = true }
    private val _uiState = MutableStateFlow(ReaderUiState())
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    init {
        val encoded = savedStateHandle.get<String>("chapterUrlEncoded") ?: ""
        val chapterUrl = if (encoded.isNotBlank()) java.net.URLDecoder.decode(encoded, "UTF-8") else ""
        if (chapterUrl.isNotBlank()) loadChapter(chapterUrl)
    }

    fun loadChapter(chapterUrl: String) {
        val slug = extractNovelSlug(chapterUrl)
        val chapterNumber = extractChapterNumber(chapterUrl)
        val chapterId = NovelRepository.chapterId(slug, chapterNumber)

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, currentChapterUrl = chapterUrl) }

            // 1. Essayer le fichier (stockage persistant — interne ou SAF)
            val jsonStr = withContext(Dispatchers.IO) { storageManager.loadChapterFile(slug, chapterNumber) }
            if (jsonStr != null) {
                try {
                    val stored = json.decodeFromString<StoredDownload>(jsonStr)
                    val fromFile = ChapterContent(
                        chapterTitle = stored.chapterTitle,
                        novelTitle = stored.novelTitle,
                        paragraphs = stored.paragraphs.map { Paragraph(it.index, it.htmlContent) },
                        prevChapterUrl = stored.prevChapterUrl,
                        nextChapterUrl = stored.nextChapterUrl
                    )
                    _uiState.update { it.copy(chapterContent = fromFile, isLoading = false, novelSlug = slug,
                        chapterNumber = chapterNumber, hasPrevChapter = fromFile.prevChapterUrl != null,
                        hasNextChapter = fromFile.nextChapterUrl != null, isOffline = true) }
                    markRead(slug, chapterNumber)
                    return@launch
                } catch (_: Exception) {}
            }

            // 2. Cache DB
            val fromDb = repository.getCachedChapter(chapterId)
            if (fromDb != null) {
                _uiState.update { it.copy(chapterContent = fromDb, isLoading = false, novelSlug = slug,
                    chapterNumber = chapterNumber, hasPrevChapter = fromDb.prevChapterUrl != null,
                    hasNextChapter = fromDb.nextChapterUrl != null, isOffline = true) }
                markRead(slug, chapterNumber)
                return@launch
            }

            // 3. Réseau
            try {
                val content = repository.getChapterContent(chapterUrl)
                _uiState.update { it.copy(chapterContent = content, isLoading = false, novelSlug = slug,
                    chapterNumber = chapterNumber, hasPrevChapter = content.prevChapterUrl != null,
                    hasNextChapter = content.nextChapterUrl != null, isOffline = false) }
                markRead(slug, chapterNumber)
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = "Connexion nécessaire. Télécharge le chapitre d'abord.") }
            }
        }
    }

    private suspend fun markRead(slug: String, chapterNumber: Int) {
        try { repository.markChapterAsRead(NovelRepository.chapterId(slug, chapterNumber)); _uiState.update { it.copy(isMarkedRead = true) } } catch (_: Exception) {}
    }

    fun goToNextChapter() { _uiState.value.chapterContent?.nextChapterUrl?.let { loadChapter(it) } }
    fun goToPrevChapter() { _uiState.value.chapterContent?.prevChapterUrl?.let { loadChapter(it) } }
    fun toggleSettings() { _uiState.update { it.copy(showSettings = !it.showSettings) } }
    fun hideSettings() { _uiState.update { it.copy(showSettings = false) } }
    fun updateFontSize(s: Int) { _uiState.update { it.copy(settings = it.settings.copy(fontSizeSp = s.coerceIn(12, 32))) } }
    fun updateFont(f: ReaderFont) { _uiState.update { it.copy(settings = it.settings.copy(fontFamily = f)) } }
    fun updateTheme(t: ReaderTheme) { _uiState.update { it.copy(settings = it.settings.copy(readerTheme = t)) } }
    fun updateLineHeight(h: Float) { _uiState.update { it.copy(settings = it.settings.copy(lineHeightMultiplier = h.coerceIn(1.2f, 2.5f))) } }
    fun updatePadding(p: Int) { _uiState.update { it.copy(settings = it.settings.copy(horizontalPaddingDp = p.coerceIn(12, 40))) } }
    fun togglePaginationMode() { _uiState.update { it.copy(settings = it.settings.copy(paginationMode = !it.settings.paginationMode)) } }
    fun saveScrollPosition(pos: Int) { _uiState.update { it.copy(scrollPosition = pos) } }

    suspend fun persistScrollPosition() {
        val s = _uiState.value
        if (s.scrollPosition > 0 && s.novelSlug.isNotBlank()) repository.saveScrollPosition(NovelRepository.chapterId(s.novelSlug, s.chapterNumber), s.scrollPosition)
    }

    private fun extractNovelSlug(url: String) = Regex("/novel/([^/]+)/").find(url)?.groupValues?.getOrNull(1) ?: ""
    private fun extractChapterNumber(url: String) = Regex("chapter-(\\d+)(?:/)?$").find(url)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
}
