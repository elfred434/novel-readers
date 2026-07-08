package com.novelreader.ui.screens.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novelreader.data.local.entity.ChapterEntity
import com.novelreader.data.repository.NovelRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HistoryUiState(
    val chapters: List<ChapterEntity> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val repository: NovelRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.getRecentHistory().collect { chapters ->
                _uiState.update {
                    it.copy(chapters = chapters, isLoading = false)
                }
            }
        }
    }
}
