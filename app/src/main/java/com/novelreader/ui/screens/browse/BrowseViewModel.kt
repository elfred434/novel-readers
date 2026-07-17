package com.novelreader.ui.screens.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novelreader.data.model.Novel
import com.novelreader.data.repository.NovelRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Options de tri alignées sur l'API réelle de NovelFrance.
 * Seules les valeurs "popular" et "rating" sont effectives côté serveur
 * (vérifié le 17/07/2026) ; toute autre valeur est ignorée et retombe
 * sur le tri par défaut (dernières mises à jour).
 */
enum class SortOption(val label: String, val sort: String?, val order: String?) {
    DEFAULT("Dernière mise à jour", null, null),
    POPULARITY("Les plus populaires", "popular", "desc"),
    RATING("Mieux notés", "rating", "desc")
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

    /** Job de recherche courant — annulé à chaque nouvelle frappe. */
    private var searchJob: Job? = null

    private companion object {
        const val SEARCH_PAGE_SIZE = 20
        const val SEARCH_DEBOUNCE_MS = 300L
    }

    init {
        loadGenres()
        loadNovels()
    }

    /**
     * Charge la liste complète des genres depuis l'API (/api/genres) —
     * avec les compteurs réels — au lieu de les extraire des 20 premiers
     * novels de la page courante.
     */
    private fun loadGenres() {
        viewModelScope.launch {
            try {
                val genres = repository.getSourceGenres()
                    .filter { it.novelCount > 0 }
                    .sortedByDescending { it.novelCount }
                    .map { GenreFilterOption(name = it.name, slug = it.slug, count = it.novelCount) }
                if (genres.isNotEmpty()) {
                    _uiState.update { it.copy(availableGenres = genres) }
                }
            } catch (e: Exception) {
                // Non bloquant : les chips de genre ne seront simplement pas affichés
            }
        }
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

                _uiState.update {
                    it.copy(
                        novels = novels,
                        isLoading = false,
                        currentPage = 1,
                        hasMore = novels.size >= SEARCH_PAGE_SIZE
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

    /**
     * Recherche avec DEBOUNCE : attend 300 ms après la dernière frappe avant
     * d'appeler l'API. La recherche précédente est ANNULÉE à chaque frappe
     * (plus de course entre résultats).
     */
    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        searchJob?.cancel()

        if (query.isBlank()) {
            _uiState.update { it.copy(searchResults = null, isSearching = false) }
            return
        }

        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            performSearch(query.trim())
        }
    }

    /**
     * Recherche via l'endpoint dédié du site (/api/search?q=…) —
     * 1 requête réseau au lieu de télécharger jusqu'à 50 pages de catalogue.
     * NB : le paramètre `search` de /api/novels est ignoré par le serveur,
     * c'est bien /api/search qui effectue la recherche plein-texte.
     */
    private suspend fun performSearch(query: String) {
        _uiState.update { it.copy(isSearching = true, error = null) }
        try {
            val results = repository.searchNovels(query, page = 1)
            _uiState.update {
                it.copy(
                    searchResults = results,
                    isSearching = false,
                    error = null
                )
            }
        } catch (e: CancellationException) {
            // Recherche remplacée par une frappe plus récente — ne pas toucher l'état
            throw e
        } catch (e: Exception) {
            _uiState.update { it.copy(isSearching = false, error = "Erreur lors de la recherche") }
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
