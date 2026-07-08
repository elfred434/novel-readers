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
    val selectedGenre: String? = null
)

@HiltViewModel
class BrowseViewModel @Inject constructor(
    private val repository: NovelRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BrowseUiState())
    val uiState: StateFlow<BrowseUiState> = _uiState.asStateFlow()

    init {
        loadNovels()
    }

    /**
     * Charge la première page des novels.
     */
    fun loadNovels() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val novels = repository.browseNovels(
                    page = 1,
                    genre = _uiState.value.selectedGenre
                )
                _uiState.update {
                    it.copy(
                        novels = novels,
                        isLoading = false,
                        currentPage = 1,
                        hasMore = novels.size >= 20
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Erreur de chargement"
                    )
                }
            }
        }
    }

    /**
     * Charge la page suivante (pagination infinie).
     */
    fun loadMore() {
        val state = _uiState.value
        if (state.isLoadingMore || !state.hasMore) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true) }
            try {
                val nextPage = state.currentPage + 1
                val novels = repository.browseNovels(
                    page = nextPage,
                    genre = state.selectedGenre
                )
                _uiState.update {
                    it.copy(
                        novels = it.novels + novels,
                        isLoadingMore = false,
                        currentPage = nextPage,
                        hasMore = novels.size >= 20
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoadingMore = false) }
            }
        }
    }

    /**
     * Met à jour la requête de recherche et filtre côté client.
     * Note : l'API /api/novels?search= ne filtre pas côté serveur,
     * on filtre donc localement sur le titre.
     */
    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    /**
     * Résultats filtrés selon la requête de recherche.
     */
    val filteredNovels: List<Novel>
        get() {
            val state = _uiState.value
            val query = state.searchQuery.trim().lowercase()
            if (query.isBlank()) return state.novels
            return state.novels.filter { novel ->
                novel.title.lowercase().contains(query) ||
                novel.author.lowercase().contains(query)
            }
        }

}
