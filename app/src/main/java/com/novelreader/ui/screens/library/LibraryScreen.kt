package com.novelreader.ui.screens.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.novelreader.data.local.entity.NovelEntity
import com.novelreader.ui.components.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(
    onNovelClick: (String) -> Unit,
    onSettingsClick: () -> Unit = {},
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var contextMenuSlug by remember { mutableStateOf<String?>(null) }
    var categoryExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            if (uiState.selectedCategoryId != null)
                                uiState.categories.find { it.id == uiState.selectedCategoryId }?.name ?: "Catégorie"
                            else "Bibliothèque",
                            style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold)
                        )
                        if (!uiState.isLoading && uiState.novels.isNotEmpty())
                            Text(
                                "${uiState.novels.size} novel${if (uiState.novels.size > 1) "s" else ""}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::toggleViewMode) {
                        Icon(
                            if (uiState.viewMode == ViewMode.GRID) Icons.Default.ViewList else Icons.Default.GridView,
                            "Vue",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    if (uiState.selectedCategoryId != null) {
                        IconButton(onClick = { viewModel.selectCategory(null) }) {
                            Icon(Icons.Default.Close, "Tous", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, "Paramètres", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        when {
            uiState.isLoading -> LoadingIndicator(Modifier.padding(padding))
            uiState.novels.isEmpty() && uiState.selectedCategoryId == null ->
                EmptyView("Ta bibliothèque est vide.\nAjoute des novels depuis Découvrir.", Modifier.padding(padding))
            uiState.novels.isEmpty() && uiState.selectedCategoryId != null ->
                EmptyView("Aucun novel dans cette catégorie.", Modifier.padding(padding))
            else -> {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    modifier = Modifier.fillMaxSize().padding(padding),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    // ── Carte "Continuer la lecture" ──
                    if (uiState.selectedCategoryId == null) {
                        uiState.continueReading?.let { ch ->
                            item(key = "continue") {
                                ContinueReadingCard(
                                    novelTitle = ch.novelTitle.ifBlank { ch.novelSlug },
                                    chapterNumber = ch.chapterNumber,
                                    onClick = { onNovelClick(ch.novelSlug) }
                                )
                                Spacer(Modifier.height(16.dp))
                            }
                        }
                    }

                    // ── Catégories ──
                    item(key = "categories") {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Catégories",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    letterSpacing = 0.5.sp
                                )
                                IconButton(
                                    onClick = viewModel::showNewCategoryDialog,
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Add,
                                        "Nouvelle",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                item {
                                    CategoryChip(
                                        label = "Tous",
                                        selected = uiState.selectedCategoryId == null,
                                        onClick = { viewModel.selectCategory(null) }
                                    )
                                }
                                items(uiState.categories, key = { it.id }) { cat ->
                                    CategoryChip(
                                        label = cat.name,
                                        selected = uiState.selectedCategoryId == cat.id,
                                        onClick = { viewModel.selectCategory(cat.id) }
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                    }

                    // ── Liste des novels ──
                    if (uiState.viewMode == ViewMode.GRID) {
                        uiState.novels.chunked(2).forEach { pair ->
                            item(key = "row_${pair.first().slug}") {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    pair.forEach { n ->
                                        Box(Modifier.weight(1f)) {
                                            NovelGridItem(
                                                novel = n,
                                                onClick = { onNovelClick(n.slug) },
                                                unreadCount = n.unreadChapterCount,
                                                onLongClick = { contextMenuSlug = n.slug }
                                            )
                                            NovelContextMenu(
                                                expanded = contextMenuSlug == n.slug,
                                                onDismiss = { contextMenuSlug = null },
                                                onTransfer = { viewModel.showNovelCategoryDialog(n) },
                                                onRemove = { viewModel.showRemoveFromLibraryDialog(n.slug, n.title) }
                                            )
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
                                LibraryListItem(
                                    novel = n,
                                    onClick = { onNovelClick(n.slug) },
                                    onLongClick = { contextMenuSlug = n.slug }
                                )
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

                    // Bottom spacer
                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }

        // ── Dialog : Nouvelle catégorie ──
        if (uiState.showNewCategoryDialog) {
            AlertDialog(
                onDismissRequest = viewModel::hideNewCategoryDialog,
                containerColor = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(20.dp),
                title = { Text("Nouvelle catégorie", fontWeight = FontWeight.Bold) },
                text = {
                    OutlinedTextField(
                        uiState.newCategoryName,
                        viewModel::onNewCategoryNameChange,
                        placeholder = { Text("Nom de la catégorie") },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            cursorColor = MaterialTheme.colorScheme.primary
                        )
                    )
                },
                confirmButton = {
                    Button(
                        onClick = viewModel::createCategory,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(10.dp)
                    ) { Text("Créer") }
                },
                dismissButton = {
                    TextButton(onClick = viewModel::hideNewCategoryDialog) {
                        Text("Annuler", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            )
        }

        // ── Dialog : Transférer dans catégorie ──
        if (uiState.showNovelCategoryDialog) {
            AlertDialog(
                onDismissRequest = viewModel::hideNovelCategoryDialog,
                containerColor = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(20.dp),
                title = {
                    Column {
                        Text("Transférer dans…", fontWeight = FontWeight.Bold)
                        Text(
                            uiState.selectedNovelTitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                text = {
                    Column {
                        uiState.categories.forEach { cat ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.toggleNovelCategory(cat.id) }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Checkbox(
                                    checked = cat.id in uiState.selectedNovelCategoryIds,
                                    onCheckedChange = { viewModel.toggleNovelCategory(cat.id) },
                                    colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                                )
                                Text(cat.name, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        if (uiState.categories.isEmpty())
                            Text(
                                "Aucune catégorie. Crées-en une !",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = viewModel::saveNovelCategories,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(10.dp)
                    ) { Text("Enregistrer") }
                },
                dismissButton = {
                    TextButton(onClick = viewModel::hideNovelCategoryDialog) {
                        Text("Annuler", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            )
        }

        // ── Dialog : Retirer de la bibliothèque ──
        if (uiState.showRemoveDialog) {
            AlertDialog(
                onDismissRequest = viewModel::hideRemoveFromLibraryDialog,
                containerColor = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(20.dp),
                icon = {
                    Box(
                        Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.error.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                },
                title = { Text("Retirer de la bibliothèque ?", fontWeight = FontWeight.Bold) },
                text = {
                    Text(
                        "« ${uiState.removeTitle} » sera retiré de ta bibliothèque.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                confirmButton = {
                    Button(
                        onClick = viewModel::confirmRemoveFromLibrary,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        shape = RoundedCornerShape(10.dp)
                    ) { Text("Retirer") }
                },
                dismissButton = {
                    TextButton(onClick = viewModel::hideRemoveFromLibraryDialog) {
                        Text("Annuler", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// COMPOSANTS PRIVÉS
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun ContinueReadingCard(novelTitle: String, chapterNumber: Int, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation)
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.PlayArrow,
                    null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    "Continuer la lecture",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    ),
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    novelTitle,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "Chapitre $chapterNumber",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                Icons.Default.ChevronRight,
                null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun CategoryChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(100.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
            ),
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LibraryListItem(novel: NovelEntity, onClick: () -> Unit, onLongClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Mini couverture
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF0A0A0A))
            ) {
                if (novel.coverImageUrl.isNotBlank()) {
                    AsyncImage(
                        model = novel.coverImageUrl,
                        contentDescription = novel.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            novel.title.take(2).uppercase(),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                        )
                    }
                }
            }

            Column(Modifier.weight(1f)) {
                Text(
                    novel.title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    novel.author,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
                if (novel.unreadChapterCount > 0) {
                    Text(
                        "${novel.unreadChapterCount} nouveau${if (novel.unreadChapterCount > 1) "x" else ""}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }

            if (novel.unreadChapterCount > 0) {
                Box(
                    Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary)
                )
            }
        }
    }
}

@Composable
private fun NovelContextMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onTransfer: () -> Unit,
    onRemove: () -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        offset = DpOffset(8.dp, 0.dp),
        shape = RoundedCornerShape(12.dp),
    ) {
        DropdownMenuItem(
            text = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        Icons.Default.DriveFileMove,
                        null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                    Text("Transférer dans…")
                }
            },
            onClick = { onDismiss(); onTransfer() }
        )
        DropdownMenuItem(
            text = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                    Text("Retirer de la bibliothèque", color = MaterialTheme.colorScheme.error)
                }
            },
            onClick = { onDismiss(); onRemove() }
        )
    }
}


