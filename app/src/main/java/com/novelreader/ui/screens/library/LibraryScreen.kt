package com.novelreader.ui.screens.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.novelreader.data.local.entity.NovelEntity
import com.novelreader.ui.components.EmptyView
import com.novelreader.ui.components.LoadingIndicator
import com.novelreader.ui.components.NovelGridItem

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(
    onNovelClick: (String) -> Unit,
    onSettingsClick: () -> Unit = {},
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var contextMenuSlug by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        val title = if (uiState.selectedCategoryId != null)
                            uiState.categories.find { it.id == uiState.selectedCategoryId }?.name ?: "Catégorie"
                        else "Bibliothèque"
                        Text(title, style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold))
                        if (!uiState.isLoading && uiState.novels.isNotEmpty())
                            Text("${uiState.novels.size} novel${if (uiState.novels.size > 1) "s" else ""}${if (uiState.selectedCategoryId != null) " dans cette catégorie" else " au total"}",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::toggleViewMode) {
                        Icon(if (uiState.viewMode == ViewMode.GRID) Icons.Default.ViewList else Icons.Default.GridView, "Vue", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (uiState.selectedCategoryId != null)
                        IconButton(onClick = { viewModel.selectCategory(null) }) { Icon(Icons.Default.Close, "Tous", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                    IconButton(onClick = onSettingsClick) { Icon(Icons.Default.Settings, "Paramètres", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent, titleContentColor = MaterialTheme.colorScheme.onSurface)
            )
        }
    ) { padding ->
        when {
            uiState.isLoading -> LoadingIndicator(Modifier.padding(padding))
            uiState.novels.isEmpty() && uiState.selectedCategoryId == null -> EmptyView(
                "Ta bibliothèque est vide.\nAjoute des novels depuis Découverte.", Modifier.padding(padding))
            uiState.novels.isEmpty() && uiState.selectedCategoryId != null -> EmptyView(
                "Aucun novel dans cette catégorie.", Modifier.padding(padding))
            else -> {
                LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), modifier = Modifier.fillMaxSize().padding(padding)) {
                    if (uiState.selectedCategoryId == null) {
                        uiState.continueReading?.let { ch ->
                            item(key = "continue") {
                                Card(onClick = { onNovelClick(ch.novelSlug) }, shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                        Box(Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                                            Icon(Icons.Default.PlayArrow, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                                        }
                                        Column(Modifier.weight(1f)) {
                                            Text("Continuer", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary))
                                            Text(ch.novelTitle.ifBlank { ch.novelSlug }, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text("Chapitre ${ch.chapterNumber}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item(key = "cats") {
                        Column(Modifier.padding(bottom = 8.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("Catégories", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
                                IconButton(onClick = viewModel::showNewCategoryDialog, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Default.Add, "Nouvelle", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                item { FilterChip("Tous", uiState.selectedCategoryId == null) { viewModel.selectCategory(null) } }
                                items(uiState.categories, key = { it.id }) { cat ->
                                    FilterChip(cat.name, uiState.selectedCategoryId == cat.id) { viewModel.selectCategory(cat.id) }
                                }
                            }
                        }
                    }

                    if (uiState.viewMode == ViewMode.GRID) {
                        uiState.novels.chunked(2).forEach { pair ->
                            item(key = "g_${pair.first().slug}") {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    pair.forEach { n ->
                                        Box(Modifier.weight(1f)) {
                                            Box {
                                                NovelGridItem(n, onClick = { onNovelClick(n.slug) }, unreadCount = n.unreadChapterCount,
                                                    onLongClick = { contextMenuSlug = n.slug })
                                                NovelContextMenu(
                                                    expanded = contextMenuSlug == n.slug,
                                                    onDismiss = { contextMenuSlug = null },
                                                    onTransfer = { viewModel.showNovelCategoryDialog(n) },
                                                    onRemove = { viewModel.showRemoveFromLibraryDialog(n.slug, n.title) }
                                                )
                                            }
                                        }
                                    }
                                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                                }
                                Spacer(Modifier.height(10.dp))
                            }
                        }
                    } else {
                        items(uiState.novels, key = { it.slug }) { n ->
                            Box {
                                ListItem(novel = n, onClick = { onNovelClick(n.slug) }, onLongClick = { contextMenuSlug = n.slug })
                                NovelContextMenu(
                                    expanded = contextMenuSlug == n.slug,
                                    onDismiss = { contextMenuSlug = null },
                                    onTransfer = { viewModel.showNovelCategoryDialog(n) },
                                    onRemove = { viewModel.showRemoveFromLibraryDialog(n.slug, n.title) }
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                        }
                    }
                }
            }
        }

        // Dialog nouvelle catégorie
        if (uiState.showNewCategoryDialog) {
            AlertDialog(
                onDismissRequest = viewModel::hideNewCategoryDialog,
                containerColor = MaterialTheme.colorScheme.surface,
                title = { Text("Nouvelle catégorie", fontWeight = FontWeight.Bold) },
                text = { OutlinedTextField(uiState.newCategoryName, viewModel::onNewCategoryNameChange,
                    placeholder = { Text("Nom") }, singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary, cursorColor = MaterialTheme.colorScheme.primary,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface, unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant, unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant)) },
                confirmButton = { Button(onClick = viewModel::createCategory, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) { Text("Créer") } },
                dismissButton = { TextButton(onClick = viewModel::hideNewCategoryDialog) { Text("Annuler", color = MaterialTheme.colorScheme.onSurfaceVariant) } }
            )
        }

        // Dialog transfert catégorie
        if (uiState.showNovelCategoryDialog) {
            AlertDialog(
                onDismissRequest = viewModel::hideNovelCategoryDialog,
                containerColor = MaterialTheme.colorScheme.surface,
                title = { Column { Text("Transférer dans…", fontWeight = FontWeight.Bold); Text(uiState.selectedNovelTitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
                text = { Column {
                    uiState.categories.forEach { cat ->
                        Row(Modifier.fillMaxWidth().clickable { viewModel.toggleNovelCategory(cat.id) }.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Checkbox(checked = cat.id in uiState.selectedNovelCategoryIds, onCheckedChange = { viewModel.toggleNovelCategory(cat.id) }, colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary))
                            Text(cat.name, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    if (uiState.categories.isEmpty()) Text("Aucune catégorie. Crées-en une !", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } },
                confirmButton = { Button(onClick = viewModel::saveNovelCategories, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) { Text("Enregistrer") } },
                dismissButton = { TextButton(onClick = viewModel::hideNovelCategoryDialog) { Text("Annuler", color = MaterialTheme.colorScheme.onSurfaceVariant) } }
            )
        }

        // Dialog suppression de la bibliothèque
        if (uiState.showRemoveDialog) {
            AlertDialog(
                onDismissRequest = viewModel::hideRemoveFromLibraryDialog,
                containerColor = MaterialTheme.colorScheme.surface,
                title = { Text("Retirer de la bibliothèque ?", fontWeight = FontWeight.Bold) },
                text = { Text("« ${uiState.removeTitle} » sera retiré de ta bibliothèque.\nLes chapitres téléchargés seront conservés sur l'appareil.", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                confirmButton = { Button(onClick = viewModel::confirmRemoveFromLibrary, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Retirer") } },
                dismissButton = { TextButton(onClick = viewModel::hideRemoveFromLibraryDialog) { Text("Annuler", color = MaterialTheme.colorScheme.onSurfaceVariant) } }
            )
        }
    }
}

@Composable
private fun NovelContextMenu(expanded: Boolean, onDismiss: () -> Unit, onTransfer: () -> Unit, onRemove: () -> Unit) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss, offset = DpOffset(8.dp, 0.dp)) {
        DropdownMenuItem(text = { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.DriveFileMove, null, modifier = Modifier.size(18.dp)); Text("Transférer dans…") }},
            onClick = { onDismiss(); onTransfer() })
        DropdownMenuItem(text = { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.Delete, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error); Text("Retirer de la bibliothèque", color = MaterialTheme.colorScheme.error) }},
            onClick = { onDismiss(); onRemove() })
    }
}

@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(Modifier.clip(RoundedCornerShape(100.dp)).background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
        .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal),
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ListItem(novel: NovelEntity, onClick: () -> Unit, onLongClick: () -> Unit) {
    Card(modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AsyncImage(model = novel.coverImageUrl, contentDescription = novel.title, modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
            Column(Modifier.weight(1f)) {
                Text(novel.title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(novel.author, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                if (novel.unreadChapterCount > 0) Text("${novel.unreadChapterCount} nouveau${if (novel.unreadChapterCount > 1) "x" else ""}", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary))
            }
            if (novel.unreadChapterCount > 0) Box(Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
        }
    }
}
