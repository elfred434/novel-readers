package com.novelreader.ui.screens.detail

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.novelreader.ui.components.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DetailScreen(
    onBack: () -> Unit,
    onChapterClick: (String) -> Unit,
    viewModel: DetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val downloaded = uiState.downloadedChapters
    val downloading = uiState.downloadingChapters
    var deleteTarget by remember { mutableStateOf<Int?>(null) }
    var showBulkDeleteDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            if (uiState.isSelectionMode) {
                TopAppBar(
                    title = { Text("${uiState.selectedChapters.size} sélectionné(s)", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)) },
                    navigationIcon = { IconButton(onClick = viewModel::exitSelectionMode) { Icon(Icons.Default.Close, "Quitter") } },
                    actions = {
                        IconButton(onClick = viewModel::selectAllDownloaded) { Icon(Icons.Default.SelectAll, "Tout sélectionner", tint = MaterialTheme.colorScheme.onSurface) }
                        IconButton(onClick = { showBulkDeleteDialog = true }, enabled = uiState.selectedChapters.isNotEmpty()) {
                            Icon(Icons.Default.Delete, "Supprimer", tint = if (uiState.selectedChapters.isNotEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f), titleContentColor = MaterialTheme.colorScheme.onSurface)
                )
            } else {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(uiState.novel?.title ?: "Détail", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                            if (uiState.isOffline) { Spacer(Modifier.width(8.dp)); Icon(Icons.Default.CloudOff, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                    },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") } },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent, titleContentColor = MaterialTheme.colorScheme.onSurface)
                )
            }
        }
    ) { padding ->
        when {
            uiState.isLoading -> LoadingIndicator(Modifier.padding(padding))
            uiState.error != null -> ErrorView(uiState.error ?: "Erreur", viewModel::loadNovelDetails, Modifier.padding(padding))
            uiState.novel == null -> ErrorView("Novel introuvable", viewModel::loadNovelDetails, Modifier.padding(padding))
            else -> {
                val novel = uiState.novel!!
                LazyColumn(contentPadding = PaddingValues(bottom = 32.dp), modifier = Modifier.fillMaxSize().padding(padding)) {
                    item(key = "header") {
                        Column(Modifier.fillMaxWidth()) {
                            Box(modifier = Modifier.fillMaxWidth().height(260.dp)) {
                                if (novel.coverImageUrl.isNotBlank()) {
                                    AsyncImage(model = novel.coverImageUrl, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background.copy(alpha = 0.85f)))
                                }
                                Row(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.Bottom) {
                                    Box(modifier = Modifier.width(120.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(14.dp)).background(Color(0xFF0A0A0A)), contentAlignment = Alignment.Center) {
                                        if (novel.coverImageUrl.isNotBlank()) AsyncImage(model = novel.coverImageUrl, contentDescription = novel.title, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                        else Text(novel.title.take(2).uppercase(), style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                                    }
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(novel.title, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                        Text(novel.author, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            StatusChip(status = novel.status)
                                            if (novel.rating > 0) RatingBadge(rating = novel.rating)
                                        }
                                        Text("${novel.chapterCount} chapitres · ${downloaded.size} téléchargés", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            novel.genres.take(3).forEach { g ->
                                                Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)) {
                                                    Text(g, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                                                }
                                            }
                                        }
                                        Spacer(Modifier.height(6.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            val isInLib = uiState.isInLibrary
                                            FilledTonalButton(onClick = viewModel::toggleLibrary, shape = RoundedCornerShape(10.dp), modifier = Modifier.height(36.dp),
                                                colors = ButtonDefaults.filledTonalButtonColors(containerColor = if (isInLib) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primary, contentColor = if (isInLib) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onPrimary)) {
                                                Icon(if (isInLib) Icons.Default.Bookmark else Icons.Default.BookmarkBorder, null, modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.width(5.dp))
                                                Text(if (isInLib) "Suivi" else "Suivre", style = MaterialTheme.typography.labelLarge)
                                            }
                                            FilledTonalButton(onClick = viewModel::downloadAllChapters, shape = RoundedCornerShape(10.dp), modifier = Modifier.height(36.dp),
                                                colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                                                Icon(Icons.Default.Download, null, modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.width(5.dp)); Text("Télécharger", style = MaterialTheme.typography.labelLarge)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (novel.synopsis.isNotBlank()) {
                        item(key = "synopsis") {
                            Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                                Text("Synopsis", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(8.dp))
                                Text(novel.synopsis, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 6, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }

                    item(key = "ch_title") {
                        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Chapitres", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                            Text("${uiState.chapters.size}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                    }

                    items(uiState.chapters, key = { "${it.novelSlug}_${it.chapterNumber}" }) { ch ->
                        val isDl = ch.chapterNumber in downloaded
                        val isDling = ch.chapterNumber in downloading
                        val isSelected = ch.chapterNumber in uiState.selectedChapters
                        val isLastRead = ch.chapterNumber == uiState.lastReadChapterNumber

                        ChapterCard(
                            chapterNumber = ch.chapterNumber, title = ch.title,
                            isDownloaded = isDl, isDownloading = isDling, isSelected = isSelected,
                            isSelectionMode = uiState.isSelectionMode, isLastRead = isLastRead,
                            onClick = {
                                if (uiState.isSelectionMode) viewModel.toggleChapterSelection(ch.chapterNumber)
                                else onChapterClick(ch.url)
                            },
                            onLongClick = {
                                if (!uiState.isSelectionMode && isDl) viewModel.enterSelectionMode(ch.chapterNumber)
                            },
                            onDownload = { viewModel.downloadChapter(ch) },
                            onDelete = { deleteTarget = ch.chapterNumber },
                            onMarkUnread = { viewModel.markChapterAsUnread(ch) }
                        )
                    }
                }
            }
        }
    }

    // Dialog suppression individuelle
    deleteTarget?.let { chapterNumber ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(20.dp),
            title = { Text("Supprimer le téléchargement ?", fontWeight = FontWeight.Bold) },
            text = { Text("Le chapitre #$chapterNumber sera supprimé.", color = MaterialTheme.colorScheme.onSurfaceVariant) },
            confirmButton = {
                Button(onClick = { viewModel.deleteDownloadedChapter(chapterNumber); deleteTarget = null },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(10.dp)) { Text("Supprimer") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Annuler", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        )
    }

    // Dialog suppression groupée
    if (showBulkDeleteDialog) {
        val count = uiState.selectedChapters.size
        AlertDialog(
            onDismissRequest = { showBulkDeleteDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(20.dp),
            title = { Text("Supprimer $count téléchargements ?", fontWeight = FontWeight.Bold) },
            text = {
                val sorted = uiState.selectedChapters.sorted()
                val preview = if (sorted.size <= 5) sorted.joinToString(", ") { "#$it" }
                    else sorted.take(5).joinToString(", ") { "#$it" } + "… et ${sorted.size - 5} autres"
                Column {
                    Text("Ces chapitres seront supprimés :", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Text(preview, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                }
            },
            confirmButton = {
                Button(onClick = { viewModel.deleteSelectedChapters(); showBulkDeleteDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(10.dp)) { Text("Tout supprimer") }
            },
            dismissButton = {
                TextButton(onClick = { showBulkDeleteDialog = false }) { Text("Annuler", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChapterCard(
    chapterNumber: Int, title: String,
    isDownloaded: Boolean, isDownloading: Boolean, isSelected: Boolean,
    isSelectionMode: Boolean, isLastRead: Boolean,
    onClick: () -> Unit, onLongClick: () -> Unit,
    onDownload: () -> Unit, onDelete: () -> Unit, onMarkUnread: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp).combinedClickable(onClick = onClick, onLongClick = onLongClick),
        colors = CardDefaults.cardColors(containerColor = when {
            isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
            isDownloaded -> MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)
            else -> MaterialTheme.colorScheme.surfaceVariant
        }),
        shape = RoundedCornerShape(10.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (isSelectionMode) {
                Checkbox(checked = isSelected, onCheckedChange = { onClick() }, colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary), modifier = Modifier.size(20.dp))
            }
            Text("#$chapterNumber", style = MaterialTheme.typography.titleMedium, color = if (isDownloaded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, modifier = Modifier.width(40.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(title.ifBlank { "Chapitre $chapterNumber" }, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (isLastRead) {
                        Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)) {
                            Text("Lu", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 9.sp), modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp))
                        }
                    }
                }
            }
            if (!isSelectionMode) {
                when {
                    isDownloading -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    isDownloaded -> IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.Delete, "Supprimer", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error) }
                    else -> IconButton(onClick = onDownload, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.Download, "Télécharger", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                IconButton(onClick = onMarkUnread, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.Close, "Non lu", modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else if (isDownloaded) {
                Icon(Icons.Default.DownloadDone, "Téléchargé", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
            }
        }
    }
}
