package com.novelreader.ui.screens.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.novelreader.data.local.entity.NovelEntity
import com.novelreader.ui.components.EmptyView
import com.novelreader.ui.components.LoadingIndicator
import com.novelreader.ui.components.NovelGridItem
import com.novelreader.ui.theme.Primary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onNovelClick: (String) -> Unit,
    onSettingsClick: () -> Unit = {},
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Bibliothèque", style = MaterialTheme.typography.headlineMedium) },
                actions = {
                    IconButton(onClick = viewModel::toggleViewMode) {
                        Icon(
                            if (uiState.viewMode == ViewMode.GRID) Icons.Default.ViewList else Icons.Default.GridView,
                            contentDescription = "Changer de vue"
                        )
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = "Paramètres")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { paddingValues ->
        when {
            uiState.isLoading -> LoadingIndicator(modifier = Modifier.padding(paddingValues))

            uiState.novels.isEmpty() -> EmptyView(
                message = "Ta bibliothèque est vide.\nAjoute des novels depuis la section Découverte.",
                modifier = Modifier.padding(paddingValues)
            )

            else -> {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxSize().padding(paddingValues)
                ) {
                    // === "Continuer la lecture" ===
                    uiState.continueReading?.let { ch ->
                        item(key = "continue_reading") {
                            Card(
                                onClick = { onNovelClick(ch.novelSlug) },
                                colors = CardDefaults.cardColors(containerColor = Primary.copy(alpha = 0.12f)),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Primary, modifier = Modifier.size(28.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Continuer la lecture", style = MaterialTheme.typography.titleSmall, color = Primary)
                                        val title = ch.novelTitle.ifBlank { ch.novelSlug }
                                        Text("$title — Ch. ${ch.chapterNumber}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                    }
                                }
                            }
                        }
                    }

                    // === Catégories ===
                    if (uiState.categories.isNotEmpty()) {
                        item(key = "categories") {
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()).padding(bottom = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Toutes
                                FilterChip(
                                    label = "Tous",
                                    selected = uiState.selectedCategoryId == null,
                                    onClick = { viewModel.selectCategory(null) }
                                )
                                uiState.categories.forEach { cat ->
                                    FilterChip(
                                        label = cat.name,
                                        selected = uiState.selectedCategoryId == cat.id,
                                        onClick = { viewModel.selectCategory(cat.id) }
                                    )
                                }
                                // Bouton +
                                FilledTonalButton(
                                    onClick = viewModel::showNewCategoryDialog,
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "Nouvelle catégorie", modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }

                    // === Grille / Liste ===
                    val novels = uiState.novels

                    if (uiState.viewMode == ViewMode.GRID) {
                        // Pour la vue grille, on utilise items direct
                        val gridItems = novels.chunked(2)
                        gridItems.forEach { pair ->
                            item(key = "row_${pair.first().slug}") {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    pair.forEach { novel ->
                                        Box(modifier = Modifier.weight(1f)) {
                                            NovelGridItem(
                                                novel = novel,
                                                onClick = { onNovelClick(novel.slug) },
                                                unreadCount = novel.unreadChapterCount
                                            )
                                        }
                                    }
                                    if (pair.size == 1) {
                                        Spacer(Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    } else {
                        items(items = novels, key = { it.slug }) { novel ->
                            LibraryListItem(
                                coverUrl = novel.coverImageUrl,
                                title = novel.title,
                                author = novel.author,
                                unreadCount = novel.unreadChapterCount,
                                onClick = { onNovelClick(novel.slug) }
                            )
                        }
                    }
                }

                // Dialogue nouvelle catégorie
                if (uiState.showNewCategoryDialog) {
                    AlertDialog(
                        onDismissRequest = viewModel::hideNewCategoryDialog,
                        title = { Text("Nouvelle catégorie") },
                        text = {
                            OutlinedTextField(
                                value = uiState.newCategoryName,
                                onValueChange = viewModel::onNewCategoryNameChange,
                                placeholder = { Text("Nom de la catégorie") },
                                singleLine = true
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = viewModel::createCategory) { Text("Créer") }
                        },
                        dismissButton = {
                            TextButton(onClick = viewModel::hideNewCategoryDialog) { Text("Annuler") }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) Primary else MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun LibraryListItem(
    coverUrl: String, title: String, author: String, unreadCount: Int, onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AsyncImage(model = coverUrl, contentDescription = title,
                modifier = Modifier.size(52.dp).clip(RoundedCornerShape(6.dp)),
                contentScale = ContentScale.Crop)
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1)
                Text(author, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            if (unreadCount > 0) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(Primary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("$unreadCount", style = MaterialTheme.typography.labelSmall, color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
