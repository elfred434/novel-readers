package com.novelreader.ui.screens.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.novelreader.data.model.NovelStatus
import com.novelreader.ui.components.ErrorView
import com.novelreader.ui.components.LoadingIndicator
import com.novelreader.ui.theme.RatingGold

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    onBack: () -> Unit, onChapterClick: (String) -> Unit,
    viewModel: DetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val downloaded = uiState.downloadedChapters
    val downloading = uiState.downloadingChapters

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(uiState.novel?.title ?: "Détail", maxLines = 1, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleLarge)
                        if (uiState.isOffline) {
                            Spacer(Modifier.width(6.dp))
                            Icon(Icons.Default.CloudOff, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent, titleContentColor = MaterialTheme.colorScheme.onSurface)
            )
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
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
                                // Cover
                                AsyncImage(
                                    model = novel.coverImageUrl, contentDescription = novel.title,
                                    modifier = Modifier.width(110.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(12.dp)),
                                    contentScale = ContentScale.Crop
                                )
                                // Infos
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(novel.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                    Text(novel.author, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        val (statText, statColor) = when (novel.status) {
                                            NovelStatus.COMPLETED -> "Terminé" to MaterialTheme.colorScheme.tertiary
                                            NovelStatus.ONGOING -> "En cours" to MaterialTheme.colorScheme.primary
                                            NovelStatus.UNKNOWN -> "?" to MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                        Text(statText, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = statColor))
                                        if (novel.rating > 0) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                                Text("★", style = MaterialTheme.typography.labelLarge, color = RatingGold)
                                                Text("%.1f".format(novel.rating), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
                                            }
                                        }
                                    }
                                    Text("${novel.chapterCount} chapitres · ${downloaded.size} téléchargés", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                                    // Genres chips
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        novel.genres.take(3).forEach { g ->
                                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(6.dp)) {
                                                Text(g, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                                            }
                                        }
                                        if (novel.genres.size > 3) Text("+${novel.genres.size - 3}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                                    }

                                    Spacer(Modifier.height(6.dp))

                                    // Actions row
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        // Suivre — style différent selon état (primaire uniquement si pas encore suivi)
                                        val isInLib = uiState.isInLibrary
                                        FilledTonalButton(
                                            onClick = viewModel::toggleLibrary,
                                            colors = ButtonDefaults.filledTonalButtonColors(
                                                containerColor = if (isInLib) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primary,
                                                contentColor = if (isInLib) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onPrimary
                                            ),
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier.height(38.dp)
                                        ) {
                                            Icon(if (isInLib) Icons.Default.Bookmark else Icons.Default.BookmarkBorder, null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(5.dp))
                                            Text(if (isInLib) "Suivi" else "Suivre", style = MaterialTheme.typography.labelLarge)
                                        }
                                        // Tout télécharger
                                        FilledTonalButton(
                                            onClick = viewModel::downloadAllChapters,
                                            colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier.height(38.dp)
                                        ) {
                                            Icon(Icons.Default.Download, null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(5.dp))
                                            Text("Télécharger", style = MaterialTheme.typography.labelLarge, maxLines = 1)
                                        }
                                    }
                                }
                            }

                            // Synopsis
                            if (novel.synopsis.isNotBlank()) {
                                Spacer(Modifier.height(16.dp))
                                Text("Synopsis", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
                                Spacer(Modifier.height(4.dp))
                                Text(novel.synopsis, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 6, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }

                    item(key = "ch") {
                        Text("Chapitres (${uiState.chapters.size})", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp))
                    }

                    items(uiState.chapters, key = { "${it.novelSlug}_${it.chapterNumber}" }) { ch ->
                        val isDl = ch.chapterNumber in downloaded
                        val isDling = ch.chapterNumber in downloading
                        Card(
                            onClick = { onChapterClick(ch.url) },
                            colors = CardDefaults.cardColors(containerColor = if (isDl) MaterialTheme.colorScheme.primary.copy(alpha = 0.06f) else MaterialTheme.colorScheme.surfaceVariant),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp)
                        ) {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("#${ch.chapterNumber}", style = MaterialTheme.typography.titleMedium, color = if (isDl) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.width(40.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(ch.title.ifBlank { "Chapitre ${ch.chapterNumber}" }, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                when {
                                    isDling -> CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                    isDl -> Icon(Icons.Default.DownloadDone, "Téléchargé", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.tertiary)
                                    else -> IconButton(onClick = { viewModel.downloadChapter(ch) }, modifier = Modifier.size(28.dp)) {
                                        Icon(Icons.Default.Download, "Télécharger", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                IconButton(onClick = { viewModel.markChapterAsUnread(ch) }, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Default.Close, "Non lu", modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
