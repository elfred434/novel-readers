package com.novelreader.ui.screens.downloads

import com.novelreader.data.download.DownloadItem
import com.novelreader.data.download.DownloadManager
import com.novelreader.data.download.DownloadStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DownloadsUiState(
    val items: List<DownloadItem> = emptyList(),
    val activeCount: Int = 0,
    val queuedCount: Int = 0,
    val completedCount: Int = 0,
    val failedCount: Int = 0
)

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val downloadManager: DownloadManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(DownloadsUiState())
    val uiState: StateFlow<DownloadsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            downloadManager.queue.collect { items ->
                _uiState.update {
                    it.copy(
                        items = items,
                        activeCount = items.count { i -> i.status == DownloadStatus.DOWNLOADING },
                        queuedCount = items.count { i -> i.status == DownloadStatus.QUEUED },
                        completedCount = items.count { i -> i.status == DownloadStatus.COMPLETED },
                        failedCount = items.count { i -> i.status == DownloadStatus.FAILED }
                    )
                }
            }
        }
    }

    fun retryAll() = downloadManager.retryAllFailed()
    fun retry(chapterId: String) = downloadManager.retry(chapterId)
    fun cancel(chapterId: String) = downloadManager.cancel(chapterId)
    fun clearCompleted() {
        viewModelScope.launch {
            val completed = _uiState.value.items.filter { it.status == DownloadStatus.COMPLETED }
            completed.forEach { downloadManager.cancel(it.chapterId) }
        }
    }
}
