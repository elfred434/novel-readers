package com.novelreader.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novelreader.data.download.DownloadManager
import com.novelreader.data.extension.ExtensionManager
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
    val themeType: Int = 0,
    val updateIntervalHours: Int = 12,
    val notificationsEnabled: Boolean = true,
    val downloadMaxConcurrent: Int = 2,
    val downloadOnWifiOnly: Boolean = true,
    val cachedChapterCount: Int = 0,
    val clearingCache: Boolean = false,
    val activeDownloads: Int = 0,
    val failedDownloads: Int = 0,
    val extensionCount: Int = 1
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: PreferencesManager,
    private val repository: NovelRepository,
    private val downloadManager: DownloadManager,
    private val extensionManager: ExtensionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            prefs.themeType.collect { v -> _uiState.update { it.copy(themeType = v) } }
        }
        viewModelScope.launch {
            prefs.updateIntervalHours.collect { v -> _uiState.update { it.copy(updateIntervalHours = v) } }
        }
        viewModelScope.launch {
            prefs.notificationsEnabled.collect { v -> _uiState.update { it.copy(notificationsEnabled = v) } }
        }
        viewModelScope.launch {
            prefs.downloadMaxConcurrent.collect { v -> _uiState.update { it.copy(downloadMaxConcurrent = v) } }
        }
        viewModelScope.launch {
            prefs.downloadOnWifiOnly.collect { v -> _uiState.update { it.copy(downloadOnWifiOnly = v) } }
        }
        viewModelScope.launch {
            extensionManager.sources.collect { sources ->
                _uiState.update { it.copy(extensionCount = sources.size) }
            }
        }
        viewModelScope.launch {
            downloadManager.queue.collect { queue ->
                _uiState.update {
                    it.copy(
                        activeDownloads = queue.count { q -> q.status == com.novelreader.data.download.DownloadStatus.DOWNLOADING || q.status == com.novelreader.data.download.DownloadStatus.QUEUED },
                        failedDownloads = queue.count { q -> q.status == com.novelreader.data.download.DownloadStatus.FAILED }
                    )
                }
            }
        }
        viewModelScope.launch {
            _uiState.update { it.copy(cachedChapterCount = repository.getCachedCount()) }
        }
    }

    fun setThemeType(type: Int) { viewModelScope.launch { prefs.setThemeType(type) } }
    fun setUpdateInterval(h: Int) { viewModelScope.launch { prefs.setUpdateIntervalHours(h) } }
    fun toggleNotifications() { viewModelScope.launch { prefs.setNotificationsEnabled(!_uiState.value.notificationsEnabled) } }
    fun setDownloadMaxConcurrent(n: Int) { viewModelScope.launch { prefs.setDownloadMaxConcurrent(n); downloadManager.maxConcurrent = n } }
    fun setDownloadOnWifiOnly(e: Boolean) { viewModelScope.launch { prefs.setDownloadOnWifiOnly(e) } }
    fun retryFailedDownloads() { downloadManager.retryAllFailed() }

    fun clearCache() {
        viewModelScope.launch {
            _uiState.update { it.copy(clearingCache = true) }
            downloadManager.clearAll()
            _uiState.update { it.copy(clearingCache = false, cachedChapterCount = 0) }
        }
    }
}
