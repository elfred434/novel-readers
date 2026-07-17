package com.novelreader.ui.screens.extensions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novelreader.data.extension.ExtensionInfo
import com.novelreader.data.extension.ExtensionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ExtensionsUiState(
    val extensions: List<ExtensionInfo> = emptyList()
)

@HiltViewModel
class ExtensionsViewModel @Inject constructor(
    private val extensionManager: ExtensionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExtensionsUiState())
    val uiState: StateFlow<ExtensionsUiState> = _uiState.asStateFlow()

    init {
        // Recalcule la liste dès que les sources OU leur état d'activation changent
        // (avant : seul `sources` était observé → le toggle ne rafraîchissait pas l'UI)
        viewModelScope.launch {
            combine(
                extensionManager.sources,
                extensionManager.disabledSourceIds
            ) { _, _ -> extensionManager.getExtensionInfos() }
                .collect { infos ->
                    _uiState.update { it.copy(extensions = infos) }
                }
        }
    }

    fun toggleExtension(id: Long) {
        val ext = _uiState.value.extensions.find { it.id == id } ?: return
        extensionManager.toggleSource(id, !ext.isEnabled)
    }
}
