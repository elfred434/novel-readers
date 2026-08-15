package com.novelreader.ui.screens.updates

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.novelreader.data.local.entity.ChapterEntity
import com.novelreader.data.repository.NovelRepository
import com.novelreader.data.worker.UpdateWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class UpdatesUiState(
    /** Nouveaux chapitres des novels de la bibliothèque (du plus récent au plus ancien). */
    val updates: List<ChapterEntity> = emptyList(),
    val isLoading: Boolean = true,
    val checkingLibrary: Boolean = false,
    val libraryCheckDone: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class UpdatesViewModel @Inject constructor(
    private val repository: NovelRepository,
    private val app: Application
) : ViewModel() {

    private val _uiState = MutableStateFlow(UpdatesUiState())
    val uiState: StateFlow<UpdatesUiState> = _uiState.asStateFlow()

    init {
        // Flux réactif : se met à jour dès que les workers insèrent de nouveaux chapitres
        viewModelScope.launch {
            repository.getLibraryUpdates().collect { chapters ->
                _uiState.update { it.copy(updates = chapters, isLoading = false) }
            }
        }

        // Vérifie immédiatement les nouveaux chapitres à l'ouverture de la page
        checkLibraryUpdates()
    }

    /**
     * Lance une vérification des nouveaux chapitres pour tous les novels de la
     * bibliothèque (un worker par novel). Les résultats apparaissent dans la
     * liste automatiquement via le flux Room.
     */
    fun checkLibraryUpdates() {
        if (_uiState.value.checkingLibrary) return
        viewModelScope.launch {
            _uiState.update { it.copy(checkingLibrary = true, libraryCheckDone = false, error = null) }
            try {
                UpdateWorker.runNow(WorkManager.getInstance(app))
                _uiState.update { it.copy(checkingLibrary = false, libraryCheckDone = true) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(checkingLibrary = false, error = e.message ?: "Erreur lors du lancement de la vérification")
                }
            }
        }
    }
}
