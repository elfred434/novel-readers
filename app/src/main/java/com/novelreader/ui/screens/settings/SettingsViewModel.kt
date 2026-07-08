package com.novelreader.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novelreader.data.local.preferences.PreferencesManager
import com.novelreader.data.repository.NovelRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val isDarkTheme: Boolean = true,
    val updateIntervalHours: Int = 12,
    val notificationsEnabled: Boolean = true,
    val cachedChapterCount: Int = 0,
    val clearingCache: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: PreferencesManager,
    private val repository: NovelRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            prefs.isDarkTheme.collect { isDark ->
                _uiState.update { it.copy(isDarkTheme = isDark) }
            }
        }
        viewModelScope.launch {
            prefs.updateIntervalHours.collect { hours ->
                _uiState.update { it.copy(updateIntervalHours = hours) }
            }
        }
        viewModelScope.launch {
            prefs.notificationsEnabled.collect { enabled ->
                _uiState.update { it.copy(notificationsEnabled = enabled) }
            }
        }
        viewModelScope.launch {
            _uiState.update { it.copy(cachedChapterCount = repository.getCachedCount()) }
        }
    }

    fun toggleDarkTheme() {
        viewModelScope.launch {
            prefs.setDarkTheme(!_uiState.value.isDarkTheme)
        }
    }

    fun setUpdateInterval(hours: Int) {
        viewModelScope.launch {
            prefs.setUpdateIntervalHours(hours)
        }
    }

    fun toggleNotifications() {
        viewModelScope.launch {
            prefs.setNotificationsEnabled(!_uiState.value.notificationsEnabled)
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            _uiState.update { it.copy(clearingCache = true) }
            repository.clearCache()
            _uiState.update {
                it.copy(clearingCache = false, cachedChapterCount = 0)
            }
        }
    }
}
