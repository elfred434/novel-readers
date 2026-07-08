package com.novelreader.ui.screens.updates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novelreader.data.model.ChapterPreview
import com.novelreader.data.repository.NovelRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class UpdatesUiState(
    val updates: List<ChapterPreview> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class UpdatesViewModel @Inject constructor(
    private val repository: NovelRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(UpdatesUiState())
    val uiState: StateFlow<UpdatesUiState> = _uiState.asStateFlow()

    init {
        loadUpdates()
    }

    fun loadUpdates() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val updates = repository.getLatestUpdates(page = 1)
                _uiState.update {
                    it.copy(updates = updates, isLoading = false)
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
}
