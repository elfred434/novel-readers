package com.novelreader.ui.screens.reader

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novelreader.data.download.StoredDownload
import com.novelreader.data.local.preferences.PreferencesManager
import com.novelreader.data.model.ChapterContent
import com.novelreader.data.model.Paragraph
import com.novelreader.data.network.NetworkStateManager
import com.novelreader.data.repository.NovelRepository
import com.novelreader.data.storage.StorageManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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
    /** Position de scroll restaurée depuis la DB au chargement du chapitre. */
    val initialScrollPosition: Int = 0,
    val isMarkedRead: Boolean = false,
    val isOffline: Boolean = false,
    val isPrefetchingNext: Boolean = false,
    val isOnWifi: Boolean = false
)

@HiltViewModel
class ReaderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: NovelRepository,
    private val storageManager: StorageManager,
    private val networkManager: NetworkStateManager,
    private val prefs: PreferencesManager
) : ViewModel() {

    private val json = Json { ignoreUnknownKeys = true }
    private val _uiState = MutableStateFlow(ReaderUiState())
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    init {
        val encoded = savedStateHandle.get<String>("chapterUrlEncoded") ?: ""
        val chapterUrl = if (encoded.isNotBlank()) java.net.URLDecoder.decode(encoded, "UTF-8") else ""
        if (chapterUrl.isNotBlank()) loadChapter(chapterUrl)

        // Restaurer les réglages du lecteur persistés (taille, police, thème…)
        viewModelScope.launch { restoreSettings() }

        // Observer le WiFi pour l'UI
        viewModelScope.launch {
            networkManager.isOnWifi.collect { wifi ->
                _uiState.update { it.copy(isOnWifi = wifi) }
            }
        }
    }

    /** Restaure les réglages du lecteur depuis DataStore. */
    private suspend fun restoreSettings() {
        val settings = ReaderSettings(
            fontSizeSp = prefs.readerFontSize.first(),
            lineHeightMultiplier = prefs.readerLineHeight.first(),
            fontFamily = ReaderFont.entries.getOrElse(prefs.readerFont.first()) { ReaderFont.DEFAULT },
            readerTheme = ReaderTheme.entries.getOrElse(prefs.readerTheme.first()) { ReaderTheme.DARK },
            horizontalPaddingDp = prefs.readerPadding.first(),
            paginationMode = prefs.readerPaginationMode.first()
        )
        _uiState.update { it.copy(settings = settings) }
    }

    fun loadChapter(chapterUrl: String) {
        val slug = extractNovelSlug(chapterUrl)
        val chapterNumber = extractChapterNumber(chapterUrl)
        val chapterId = NovelRepository.chapterId(slug, chapterNumber)

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, currentChapterUrl = chapterUrl, initialScrollPosition = 0, scrollPosition = 0) }

            // Position de scroll sauvegardée (restauration de la reprise de lecture)
            val savedScroll = withContext(Dispatchers.IO) { repository.getScrollPosition(chapterId) }

            suspend fun applyContent(content: ChapterContent, offline: Boolean) {
                _uiState.update { it.copy(chapterContent = content, isLoading = false, novelSlug = slug,
                    chapterNumber = chapterNumber, hasPrevChapter = content.prevChapterUrl != null,
                    hasNextChapter = content.nextChapterUrl != null, isOffline = offline,
                    initialScrollPosition = savedScroll, scrollPosition = savedScroll) }
                markRead(slug, chapterNumber)
                prefetchNextOnWifi(content.nextChapterUrl)
            }

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
                    applyContent(fromFile, offline = true)
                    return@launch
                } catch (_: Exception) {}
            }

            // 2. Cache DB
            val fromDb = repository.getCachedChapter(chapterId)
            if (fromDb != null) {
                applyContent(fromDb, offline = true)
                return@launch
            }

            // 3. Réseau
            try {
                val content = repository.getChapterContent(chapterUrl)
                applyContent(content, offline = false)
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = "Connexion nécessaire. Télécharge le chapitre d'abord.") }
            }
        }
    }

    /**
     * Précharge le chapitre suivant en arrière-plan si on est en WiFi.
     * Le contenu est mis en cache DB (Room) pour un accès instantané.
     */
    private fun prefetchNextOnWifi(nextChapterUrl: String?) {
        if (nextChapterUrl == null) return
        viewModelScope.launch {
            val onWifi = networkManager.isOnWifi.first()
            if (!onWifi) return@launch

            _uiState.update { it.copy(isPrefetchingNext = true) }
            try {
                // Vérifier si déjà en cache
                val slug = extractNovelSlug(nextChapterUrl)
                val chapterNumber = extractChapterNumber(nextChapterUrl)
                val chapterId = NovelRepository.chapterId(slug, chapterNumber)

                // Déjà sur le disque ou en cache ?
                val onDisk = withContext(Dispatchers.IO) { storageManager.isChapterDownloaded(slug, chapterNumber) }
                val inDb = repository.getCachedChapter(chapterId)
                if (onDisk || inDb != null) {
                    _uiState.update { it.copy(isPrefetchingNext = false) }
                    return@launch
                }

                // Précharger et mettre en cache DB
                val content = repository.getChapterContent(nextChapterUrl)
                repository.downloadChapter(chapterId, content)
            } catch (_: Exception) {
                // Silence — le préchargement est un bonus, pas bloquant
            } finally {
                _uiState.update { it.copy(isPrefetchingNext = false) }
            }
        }
    }

    private suspend fun markRead(slug: String, chapterNumber: Int) {
        try {
            repository.markChapterAsRead(NovelRepository.chapterId(slug, chapterNumber))
            _uiState.update { it.copy(isMarkedRead = true) }
        } catch (_: Exception) {}
    }

    fun goToNextChapter() { _uiState.value.chapterContent?.nextChapterUrl?.let { loadChapter(it) } }
    fun goToPrevChapter() { _uiState.value.chapterContent?.prevChapterUrl?.let { loadChapter(it) } }
    fun toggleSettings() { _uiState.update { it.copy(showSettings = !it.showSettings) } }
    fun hideSettings() { _uiState.update { it.copy(showSettings = false) } }

    // ── Réglages lecteur : mise à jour de l'état + PERSISTANCE DataStore ──

    fun updateFontSize(s: Int) {
        val v = s.coerceIn(12, 32)
        _uiState.update { it.copy(settings = it.settings.copy(fontSizeSp = v)) }
        viewModelScope.launch { prefs.setReaderFontSize(v) }
    }

    fun updateFont(f: ReaderFont) {
        _uiState.update { it.copy(settings = it.settings.copy(fontFamily = f)) }
        viewModelScope.launch { prefs.setReaderFont(f.ordinal) }
    }

    fun updateTheme(t: ReaderTheme) {
        _uiState.update { it.copy(settings = it.settings.copy(readerTheme = t)) }
        viewModelScope.launch { prefs.setReaderTheme(t.ordinal) }
    }

    fun updateLineHeight(h: Float) {
        val v = h.coerceIn(1.2f, 2.5f)
        _uiState.update { it.copy(settings = it.settings.copy(lineHeightMultiplier = v)) }
        viewModelScope.launch { prefs.setReaderLineHeight(v) }
    }

    fun updatePadding(p: Int) {
        val v = p.coerceIn(12, 40)
        _uiState.update { it.copy(settings = it.settings.copy(horizontalPaddingDp = v)) }
        viewModelScope.launch { prefs.setReaderPadding(v) }
    }

    fun togglePaginationMode() {
        val v = !_uiState.value.settings.paginationMode
        _uiState.update { it.copy(settings = it.settings.copy(paginationMode = v)) }
        viewModelScope.launch { prefs.setReaderPaginationMode(v) }
    }

    fun saveScrollPosition(pos: Int) { _uiState.update { it.copy(scrollPosition = pos) } }

    /**
     * Persiste la position de scroll courante en DB.
     * Lancée dans le viewModelScope (survit à la destruction de la vue,
     * contrairement au scope de composition — plus de perte à la sortie).
     */
    fun persistScrollPosition() {
        val s = _uiState.value
        if (s.scrollPosition > 0 && s.novelSlug.isNotBlank()) {
            viewModelScope.launch {
                repository.saveScrollPosition(NovelRepository.chapterId(s.novelSlug, s.chapterNumber), s.scrollPosition)
            }
        }
    }

    private fun extractNovelSlug(url: String) = Regex("/novel/([^/]+)/").find(url)?.groupValues?.getOrNull(1) ?: ""

    /**
     * Extrait le numéro de chapitre depuis une URL.
     * "…/chapter-42" → 42. Fallback : dernier groupe de chiffres du dernier
     * segment (tolère les slugs non standard comme "chapitre-42-final").
     */
    private fun extractChapterNumber(url: String): Int {
        Regex("chapter-(\\d+)(?:/)?$").find(url)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { return it }
        val lastSegment = url.trimEnd('/').substringAfterLast('/')
        return Regex("(\\d+)").find(lastSegment)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
    }
}
