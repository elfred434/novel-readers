package com.novelreader.ui.screens.extensions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novelreader.data.extension.ExtensionInfo
import com.novelreader.data.extension.ExtensionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
        viewModelScope.launch {
            extensionManager.sources.collect {
                _uiState.update { state -> state.copy(extensions = extensionManager.getExtensionInfos()) }
            }
        }
    }

    fun toggleExtension(id: Long) {
        val ext = _uiState.value.extensions.find { it.id == id } ?: return
        extensionManager.toggleSource(id, !ext.isEnabled)
    }
}
