package com.novelreader.ui.screens.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novelreader.data.model.Novel
import com.novelreader.data.repository.NovelRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SortOption(val label: String, val sort: String?, val order: String?) {
    DEFAULT("Par défaut", null, null),
    POPULARITY("Popularité", "views", "desc"),
    RATING("Meilleure note", "rating", "desc"),
    NEW("Nouveautés", "createdAt", "desc"),
    UPDATED("Mise à jour", "updatedAt", "desc")
}

enum class StatusFilter(val label: String, val apiValue: String?) {
    ALL("Tous", null),
    ONGOING("En cours", "ONGOING"),
    COMPLETED("Terminé", "COMPLETED")
}

data class BrowseUiState(
    val novels: List<Novel> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: String? = null,
    val searchQuery: String = "",
    val currentPage: Int = 1,
    val hasMore: Boolean = true,
    val isSearching: Boolean = false,
    val searchResults: List<Novel>? = null,
    val sortOption: SortOption = SortOption.DEFAULT,
    val statusFilter: StatusFilter = StatusFilter.ALL,
    val genreSlug: String? = null,
    val availableGenres: List<GenreFilterOption> = emptyList()
)

data class GenreFilterOption(val name: String, val slug: String, val count: Int = 0)

@HiltViewModel
class BrowseViewModel @Inject constructor(
    private val repository: NovelRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BrowseUiState())
    val uiState: StateFlow<BrowseUiState> = _uiState.asStateFlow()

    private companion object {
        const val MAX_SEARCH_PAGES = 3   // 3 pages de 20 = 60 résultats max
        const val SEARCH_PAGE_SIZE = 20
        const val SEARCH_DEBOUNCE_MS = 300L
    }

    private var searchJob: Job? = null

    init {
        loadNovels()
    }

    fun loadNovels() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, searchResults = null) }
            try {
                val state = _uiState.value
                val novels = repository.browseNovels(
                    page = 1,
                    sort = state.sortOption.sort,
                    order = state.sortOption.order,
                    status = state.statusFilter.apiValue,
                    genre = state.genreSlug
                )

                // Extraire les genres disponibles (P3)
                val genres = novels.flatMap { n ->
                    n.genres.map { g -> g.lowercase().replace(" ", "-") to g }
                }.distinctBy { it.first }.map { (slug, name) ->
                    GenreFilterOption(name = name, slug = slug)
                }

                _uiState.update {
                    it.copy(
                        novels = novels,
                        isLoading = false,
                        currentPage = 1,
                        hasMore = novels.size >= SEARCH_PAGE_SIZE,
                        availableGenres = genres.take(20) // max 20 genres en chips
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "Erreur de chargement") }
            }
        }
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.isLoadingMore || !state.hasMore || state.searchQuery.isNotBlank()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true) }
            try {
                val nextPage = state.currentPage + 1
                val novels = repository.browseNovels(
                    page = nextPage,
                    sort = state.sortOption.sort,
                    order = state.sortOption.order,
                    status = state.statusFilter.apiValue,
                    genre = state.genreSlug
                )
                _uiState.update {
                    it.copy(
                        novels = it.novels + novels,
                        isLoadingMore = false,
                        currentPage = nextPage,
                        hasMore = novels.size >= SEARCH_PAGE_SIZE
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoadingMore = false) }
            }
        }
    }

    fun setSortOption(option: SortOption) {
        _uiState.update { it.copy(sortOption = option) }
        loadNovels()
    }

    fun setStatusFilter(filter: StatusFilter) {
        _uiState.update { it.copy(statusFilter = filter) }
        loadNovels()
    }

    fun setGenreFilter(genreSlug: String?) {
        _uiState.update { it.copy(genreSlug = genreSlug) }
        loadNovels()
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }

        if (query.isBlank()) {
            searchJob?.cancel()
            _uiState.update { it.copy(searchResults = null, isSearching = false) }
            return
        }

        // Debounce : on attend 300 ms après la dernière frappe avant d'interroger
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            performSearch(query.trim())
        }
    }

    /** Recherche via l'endpoint dédié /api/search (le paramètre search de /api/novels est ignoré par le serveur). */
    private suspend fun performSearch(query: String) {
        if (query.isBlank()) return
        _uiState.update { it.copy(isSearching = true, error = null) }
        val results = mutableListOf<Novel>()

        try {
            for (page in 1..MAX_SEARCH_PAGES) {
                val novels = repository.searchNovels(query, page)
                results.addAll(novels)
                if (novels.size < SEARCH_PAGE_SIZE) break
            }
        } catch (e: Exception) {
            if (results.isEmpty()) {
                _uiState.update { it.copy(isSearching = false, error = "Erreur lors de la recherche") }
                return
            }
        }

        _uiState.update {
            it.copy(
                searchResults = results,
                isSearching = false,
                error = null
            )
        }
    }

    val displayedNovels: List<Novel>
        get() {
            val state = _uiState.value
            return state.searchResults ?: state.novels
        }

    val isShowingSearchResults: Boolean
        get() = _uiState.value.searchResults != null
}
