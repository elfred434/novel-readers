package com.novelreader.ui.screens.settings

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novelreader.data.download.DownloadManager
import com.novelreader.data.download.DownloadService
import com.novelreader.data.download.DownloadStatus
import com.novelreader.data.extension.ExtensionManager
import com.novelreader.data.local.preferences.PreferencesManager
import com.novelreader.data.network.NetworkStateManager
import com.novelreader.data.repository.NovelRepository
import com.novelreader.data.storage.StorageManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class SettingsUiState(
    val themeType: Int = 0,
    val updateIntervalHours: Int = 12,
    val notificationsEnabled: Boolean = true,
    val downloadMaxConcurrent: Int = 2,
    val downloadOnWifiOnly: Boolean = true,
    val wifiHighDataMode: Boolean = true,
    val cachedChapterCount: Int = 0,
    val clearingCache: Boolean = false,
    val activeDownloads: Int = 0,
    val failedDownloads: Int = 0,
    val extensionCount: Int = 1,
    val hasStorageLocation: Boolean = false,
    val storagePath: String = "Non configuré",
    val storageUsed: String = "0 Mo",
    val downloadCountOnDisk: Int = 0,
    val isOnline: Boolean = false,
    val isOnWifi: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: PreferencesManager,
    private val repository: NovelRepository,
    private val downloadManager: DownloadManager,
    private val extensionManager: ExtensionManager,
    private val storageManager: StorageManager,
    private val networkManager: NetworkStateManager,
    private val app: Application
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch { prefs.themeType.collect { v -> _uiState.update { it.copy(themeType = v) } } }
        viewModelScope.launch { prefs.updateIntervalHours.collect { v -> _uiState.update { it.copy(updateIntervalHours = v) } } }
        viewModelScope.launch { prefs.notificationsEnabled.collect { v -> _uiState.update { it.copy(notificationsEnabled = v) } } }
        viewModelScope.launch { prefs.downloadMaxConcurrent.collect { v -> _uiState.update { it.copy(downloadMaxConcurrent = v) } } }
        viewModelScope.launch { prefs.downloadOnWifiOnly.collect { v -> _uiState.update { it.copy(downloadOnWifiOnly = v) } } }
        viewModelScope.launch { prefs.wifiHighDataMode.collect { v ->
            _uiState.update { it.copy(wifiHighDataMode = v) }
            downloadManager.highDataModeEnabled = v
        } }
        viewModelScope.launch { extensionManager.sources.collect { sources -> _uiState.update { it.copy(extensionCount = sources.size) } } }
        viewModelScope.launch {
            downloadManager.queue.collect { queue ->
                _uiState.update { it.copy(
                    activeDownloads = queue.count { q -> q.status == DownloadStatus.DOWNLOADING || q.status == DownloadStatus.QUEUED },
                    failedDownloads = queue.count { q -> q.status == DownloadStatus.FAILED }
                )}
            }
        }
        viewModelScope.launch { _uiState.update { it.copy(cachedChapterCount = repository.getCachedCount()) } }

        // Observer l'état réseau
        viewModelScope.launch {
            networkManager.isOnline.collect { online ->
                _uiState.update { it.copy(isOnline = online) }
            }
        }
        viewModelScope.launch {
            networkManager.isOnWifi.collect { wifi ->
                _uiState.update { it.copy(isOnWifi = wifi) }
            }
        }

        loadStorageInfo()
    }

    private fun loadStorageInfo() {
        viewModelScope.launch {
            val hasStorage = prefs.hasAnyStorage()
            val displayPath = storageManager.getStorageDisplayPath()
            val count = withContext(Dispatchers.IO) { if (hasStorage) storageManager.countDownloadedChapters() else 0 }
            val bytes = withContext(Dispatchers.IO) { if (hasStorage) storageManager.getStorageSizeBytes() else 0L }
            val sizeStr = when {
                bytes < 1024 -> "$bytes o"
                bytes < 1024 * 1024 -> "${bytes / 1024} Ko"
                else -> "%.1f Mo".format(bytes.toDouble() / (1024 * 1024))
            }
            _uiState.update { it.copy(hasStorageLocation = hasStorage, downloadCountOnDisk = count, storageUsed = sizeStr, storagePath = displayPath) }
        }
    }

    fun refreshStorageInfo() { loadStorageInfo() }

    fun setSafUri(uri: String) {
        viewModelScope.launch { prefs.setSafTreeUri(uri); loadStorageInfo() }
    }

    fun setThemeType(type: Int) { viewModelScope.launch { prefs.setThemeType(type) } }
    fun setUpdateInterval(h: Int) { viewModelScope.launch { prefs.setUpdateIntervalHours(h) } }
    fun toggleNotifications() { viewModelScope.launch { prefs.setNotificationsEnabled(!_uiState.value.notificationsEnabled) } }
    fun setDownloadMaxConcurrent(n: Int) {
        viewModelScope.launch {
            prefs.setDownloadMaxConcurrent(n)
            downloadManager.userMaxConcurrent = n
            // Recalcule immédiat
            val onWifi = networkManager.isCurrentlyOnWifi()
            downloadManager.highDataModeEnabled = _uiState.value.wifiHighDataMode
        }
    }
    fun setDownloadOnWifiOnly(e: Boolean) { viewModelScope.launch { prefs.setDownloadOnWifiOnly(e) } }
    fun toggleWifiHighDataMode() {
        viewModelScope.launch {
            val newVal = !_uiState.value.wifiHighDataMode
            prefs.setWifiHighDataMode(newVal)
        }
    }
    fun retryFailedDownloads() {
        downloadManager.retryAllFailed()
        DownloadService.start(app)
    }

    fun clearCache() {
        viewModelScope.launch {
            _uiState.update { it.copy(clearingCache = true) }
            downloadManager.clearAll()
            _uiState.update { it.copy(clearingCache = false, cachedChapterCount = 0) }
        }
    }
}
