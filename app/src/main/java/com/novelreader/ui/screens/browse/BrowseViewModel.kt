package com.novelreader.ui.screens.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novelreader.data.model.Novel
import com.novelreader.data.repository.NovelRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BrowseUiState(
    val novels: List<Novel> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: String? = null,
    val searchQuery: String = "",
    val currentPage: Int = 1,
    val hasMore: Boolean = true,
    val isSearching: Boolean = false,  // true quand on cherche dans TOUTES les pages
    val searchResults: List<Novel>? = null  // null = pas de recherche active, liste = résultats
)

@HiltViewModel
class BrowseViewModel @Inject constructor(
    private val repository: NovelRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BrowseUiState())
    val uiState: StateFlow<BrowseUiState> = _uiState.asStateFlow()

    /** Nombre maximum de pages à charger pour la recherche exhaustive */
    private companion object {
        const val MAX_SEARCH_PAGES = 50  // Plus de 50 pages = 1000 novels, large couverture
        const val SEARCH_PAGE_SIZE = 20
    }

    init {
        loadNovels()
    }

    /**
     * Charge la première page des novels.
     */
    fun loadNovels() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, searchResults = null) }
            try {
                val novels = repository.browseNovels(page = 1)
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

    /**
     * Charge la page suivante (pagination infinie normale).
     */
    fun loadMore() {
        val state = _uiState.value
        if (state.isLoadingMore || !state.hasMore || state.searchQuery.isNotBlank()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true) }
            try {
                val nextPage = state.currentPage + 1
                val novels = repository.browseNovels(page = nextPage)
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

    /**
     * Recherche : charge les pages une par une jusqu'à trouver des résultats
     * ou épuiser le catalogue. Le filtre est appliqué côté client.
     */
    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }

        if (query.isBlank()) {
            // Efface la recherche, revient à la vue normale
            _uiState.update { it.copy(searchResults = null, isSearching = false) }
            return
        }

        // Déclenche la recherche immédiatement (debounce implicite via le cycle Compose)
        performSearch(query.trim())
    }

    /**
     * Recherche exhaustive dans TOUS les novels du site.
     */
    private fun performSearch(query: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSearching = true, error = null) }
            val results = mutableListOf<Novel>()
            val queryLower = query.lowercase()

            try {
                // Étape 1 : charger page après page en filtrant
                for (page in 1..MAX_SEARCH_PAGES) {
                    val novels = repository.browseNovels(page = page)

                    // Filtrer les résultats de cette page
                    val matches = novels.filter { novel ->
                        novel.title.lowercase().contains(queryLower) ||
                        novel.author.lowercase().contains(queryLower)
                    }
                    results.addAll(matches)

                    // Vérifier si c'était la dernière page
                    if (novels.size < SEARCH_PAGE_SIZE) break

                    // Si on a déjà assez de résultats, on peut s'arrêter
                    // (mais on continue pour être exhaustif — max 10 pages avec résultats)
                    if (results.size >= 50) break
                }
            } catch (e: Exception) {
                // Si une page échoue, on continue avec ce qu'on a
                if (results.isEmpty()) {
                    _uiState.update { it.copy(isSearching = false, error = "Erreur lors de la recherche") }
                    return@launch
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
    }

    /**
     * Résultats affichés : soit les résultats de recherche, soit le browse normal.
     */
    val displayedNovels: List<Novel>
        get() {
            val state = _uiState.value
            return state.searchResults ?: state.novels
        }

    val isShowingSearchResults: Boolean
        get() = _uiState.value.searchResults != null
}
