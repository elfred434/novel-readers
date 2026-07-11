package com.novelreader.ui.screens.downloads

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.novelreader.data.download.DownloadItem
import com.novelreader.data.download.DownloadStatus
import com.novelreader.ui.components.EmptyView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    onBack: () -> Unit,
    viewModel: DownloadsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Téléchargements",
                        style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {

            val activeItems = uiState.items.filter {
                it.status == DownloadStatus.QUEUED ||
                it.status == DownloadStatus.DOWNLOADING ||
                it.status == DownloadStatus.FAILED
            }
            val hasActive = activeItems.isNotEmpty()
            val hasFiles = uiState.downloadedFromFiles.isNotEmpty()

            if (!hasActive && !hasFiles) {
                EmptyView(
                    message = "Aucun téléchargement en cours.\nTélécharge des chapitres depuis le détail d'un novel."
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    // SECTION : En cours
                    if (hasActive) {
                        item(key = "section_active") {
                            Row(
                                modifier = Modifier.padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    "En cours",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    "${uiState.activeCount + uiState.queuedCount}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .background(
                                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
                                            RoundedCornerShape(4.dp)
                                        )
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        items(items = activeItems, key = { "queue_${it.chapterId}" }) { item ->
                            QueueItemCard(
                                item = item,
                                onRetry = { viewModel.retry(item.chapterId) },
                                onCancel = { viewModel.cancel(item.chapterId) }
                            )
                        }

                        if (hasFiles) {
                            item(key = "divider") {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 12.dp)
                                        .height(1.dp)
                                        .background(MaterialTheme.colorScheme.outlineVariant)
                                )
                            }
                        }
                    }

                    // SECTION : Fichiers sauvegardés
                    if (hasFiles) {
                        item(key = "section_files") {
                            Row(
                                modifier = Modifier.padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    "Chapitres sauvegardés",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    "${uiState.downloadedFromFiles.sumOf { it.chapterCount }}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .background(
                                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
                                            RoundedCornerShape(4.dp)
                                        )
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        items(items = uiState.downloadedFromFiles, key = { "file_${it.novelSlug}" }) { info ->
                            DownloadedNovelCard(info = info)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QueueItemCard(item: DownloadItem, onRetry: () -> Unit, onCancel: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Icône de statut
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(
                        when (item.status) {
                            DownloadStatus.DOWNLOADING -> MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                            DownloadStatus.QUEUED -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
                            DownloadStatus.FAILED -> MaterialTheme.colorScheme.error.copy(alpha = 0.1f)
                            else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                when (item.status) {
                    DownloadStatus.DOWNLOADING -> CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                    DownloadStatus.QUEUED -> Icon(
                        Icons.Default.HourglassEmpty, null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    DownloadStatus.FAILED -> Icon(
                        Icons.Default.Error, null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                    else -> {}
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.novelTitle.ifBlank { item.novelSlug },
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "Ch. ${item.chapterNumber} — ${item.chapterTitle.ifBlank { "Chapitre ${item.chapterNumber}" }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (item.error != null) {
                    Text(
                        item.error,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 1
                    )
                }
            }

            when (item.status) {
                DownloadStatus.FAILED -> IconButton(onClick = onRetry, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Refresh, "Réessayer", modifier = Modifier.size(18.dp))
                }
                DownloadStatus.QUEUED, DownloadStatus.DOWNLOADING -> IconButton(onClick = onCancel, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, "Annuler", modifier = Modifier.size(18.dp))
                }
                else -> {}
            }
        }
    }
}

@Composable
private fun DownloadedNovelCard(info: DownloadedNovelInfo) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.DownloadDone, null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    info.novelTitle,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${info.chapterCount} chapitre${if (info.chapterCount > 1) "s" else ""} · stockés sur l'appareil",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                Icons.Default.Storage, null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
