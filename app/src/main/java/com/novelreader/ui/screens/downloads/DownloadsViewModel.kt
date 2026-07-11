package com.novelreader.ui.screens.downloads

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novelreader.data.download.DownloadItem
import com.novelreader.data.download.DownloadManager
import com.novelreader.data.download.DownloadService
import com.novelreader.data.download.DownloadStatus
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

data class DownloadsUiState(
    val items: List<DownloadItem> = emptyList(),
    val downloadedFromFiles: List<DownloadedNovelInfo> = emptyList(),
    val activeCount: Int = 0,
    val queuedCount: Int = 0,
    val completedCount: Int = 0,
    val failedCount: Int = 0
)

data class DownloadedNovelInfo(
    val novelSlug: String,
    val novelTitle: String,
    val chapterCount: Int,
    val chapters: List<Int>
)

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val downloadManager: DownloadManager,
    private val storageManager: StorageManager,
    private val app: Application
) : ViewModel() {

    private val _uiState = MutableStateFlow(DownloadsUiState())
    val uiState: StateFlow<DownloadsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            downloadManager.queue.collect { items ->
                _uiState.update { it.copy(items = items,
                    activeCount = items.count { i -> i.status == DownloadStatus.DOWNLOADING },
                    queuedCount = items.count { i -> i.status == DownloadStatus.QUEUED },
                    completedCount = items.count { i -> i.status == DownloadStatus.COMPLETED },
                    failedCount = items.count { i -> i.status == DownloadStatus.FAILED }) }
            }
        }

        viewModelScope.launch {
            val infos = withContext(Dispatchers.IO) { scanDownloadedFiles() }
            _uiState.update { it.copy(downloadedFromFiles = infos) }
        }
    }

    private suspend fun scanDownloadedFiles(): List<DownloadedNovelInfo> {
        val slugs = storageManager.getDownloadedNovelSlugs()
        return slugs.map { slug ->
            val chapters = storageManager.getDownloadedChapterNumbers(slug)
            DownloadedNovelInfo(
                novelSlug = slug,
                novelTitle = slug.replace("-", " ").split(" ").joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } },
                chapterCount = chapters.size,
                chapters = chapters
            )
        }
    }

    fun refreshFiles() {
        viewModelScope.launch {
            val infos = withContext(Dispatchers.IO) { scanDownloadedFiles() }
            _uiState.update { it.copy(downloadedFromFiles = infos) }
        }
    }

    fun retryAll() {
        downloadManager.retryAllFailed()
        DownloadService.start(app)
    }
    fun retry(chapterId: String) {
        downloadManager.retry(chapterId)
        DownloadService.start(app)
    }
    fun cancel(chapterId: String) = downloadManager.cancel(chapterId)
}
