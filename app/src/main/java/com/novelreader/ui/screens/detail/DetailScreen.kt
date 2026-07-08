package com.novelreader.ui.screens.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.novelreader.data.model.NovelStatus
import com.novelreader.ui.components.ErrorView
import com.novelreader.ui.components.LoadingIndicator
import com.novelreader.ui.components.StatusBadge
import com.novelreader.ui.theme.Primary
import com.novelreader.ui.theme.RatingGold
import com.novelreader.ui.theme.StatusCompleted
import com.novelreader.ui.theme.SurfaceVariant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    onBack: () -> Unit,
    onChapterClick: (String) -> Unit,
    viewModel: DetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(uiState.novel?.title ?: "Détail", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleLarge)
                        if (uiState.isOffline) {
                            Spacer(Modifier.width(6.dp))
                            Icon(Icons.Default.CloudOff, contentDescription = "Hors-ligne", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background, titleContentColor = MaterialTheme.colorScheme.onSurface)
            )
        }
    ) { paddingValues ->
        when {
            uiState.isLoading -> LoadingIndicator(modifier = Modifier.padding(paddingValues))
            uiState.error != null -> ErrorView(message = uiState.error ?: "Erreur", onRetry = viewModel::loadNovelDetails, modifier = Modifier.padding(paddingValues))
            uiState.novel == null -> ErrorView(message = "Novel introuvable", onRetry = viewModel::loadNovelDetails, modifier = Modifier.padding(paddingValues))
            else -> {
                val novel = uiState.novel!!
                val downloaded = uiState.downloadedChapters
                val downloading = uiState.downloadingChapters

                LazyColumn(contentPadding = PaddingValues(bottom = 32.dp), modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                    item(key = "header") {
                        HeaderSection(
                            coverUrl = novel.coverImageUrl, title = novel.title, author = novel.author,
                            status = novel.status, rating = novel.rating, genres = novel.genres,
                            chapterCount = novel.chapterCount, synopsis = novel.synopsis,
                            isInLibrary = uiState.isInLibrary, onToggleLibrary = viewModel::toggleLibrary,
                            onDownloadAll = viewModel::downloadAllChapters, dlCount = downloaded.size
                        )
                    }

                    item(key = "chapters_header") {
                        Text(
                            text = "Chapitres (${uiState.chapters.size})",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp)
                        )
                    }

                    items(items = uiState.chapters, key = { "${it.novelSlug}_${it.chapterNumber}" }) { chapter ->
                        val isDl = chapter.chapterNumber in downloaded
                        val isDling = chapter.chapterNumber in downloading

                        ChapterListItem(
                            chapterNumber = chapter.chapterNumber,
                            title = chapter.title,
                            isDownloaded = isDl,
                            isDownloading = isDling,
                            onChapterClick = { onChapterClick(chapter.url) },
                            onDownload = { viewModel.downloadChapter(chapter) },
                            onMarkUnread = { viewModel.markChapterAsUnread(chapter) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HeaderSection(
    coverUrl: String, title: String, author: String, status: NovelStatus, rating: Double,
    genres: List<String>, chapterCount: Int, synopsis: String,
    isInLibrary: Boolean, onToggleLibrary: () -> Unit, onDownloadAll: () -> Unit, dlCount: Int
) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            AsyncImage(model = coverUrl, contentDescription = title, modifier = Modifier.width(120.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.headlineMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Text(author, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusBadge(status = status)
                    if (rating > 0) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("\u2605", style = MaterialTheme.typography.labelLarge, color = RatingGold)
                            Text("%.1f".format(rating), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
                Text("$chapterCount chapitres ($dlCount téléchargés)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    genres.take(3).forEach { GenreChip(it) }
                    if (genres.size > 3) Text("+${genres.size - 3}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = onToggleLibrary, colors = ButtonDefaults.filledTonalButtonColors(containerColor = if (isInLibrary) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.primaryContainer)) {
                        Icon(if (isInLibrary) Icons.Default.Bookmark else Icons.Default.BookmarkBorder, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (isInLibrary) "Suivi" else "Suivre", style = MaterialTheme.typography.labelLarge)
                    }
                    FilledTonalButton(onClick = onDownloadAll, colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f))) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Tout DL", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
        if (synopsis.isNotBlank()) {
            Spacer(Modifier.height(16.dp))
            Text("Synopsis", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(synopsis, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 6, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun GenreChip(name: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(6.dp)) {
        Text(name, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
    }
}

@Composable
private fun ChapterListItem(
    chapterNumber: Int, title: String,
    isDownloaded: Boolean = false, isDownloading: Boolean = false,
    onChapterClick: () -> Unit, onDownload: () -> Unit, onMarkUnread: () -> Unit
) {
    Card(
        onClick = onChapterClick,
        colors = CardDefaults.cardColors(containerColor = if (isDownloaded) Primary.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("#$chapterNumber", style = MaterialTheme.typography.titleMedium, color = if (isDownloaded) Primary else MaterialTheme.colorScheme.primary, modifier = Modifier.width(44.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title.ifBlank { "Chapitre $chapterNumber" }, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            when {
                isDownloading -> CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                isDownloaded -> Icon(Icons.Default.DownloadDone, contentDescription = "Téléchargé", modifier = Modifier.size(18.dp), tint = StatusCompleted)
                else -> {
                    IconButton(onClick = onDownload, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Download, contentDescription = "Télécharger", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            IconButton(onClick = onMarkUnread, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Marquer non lu", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
