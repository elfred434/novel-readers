package com.novelreader.ui.screens.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.novelreader.data.local.entity.CategoryEntity
import com.novelreader.data.local.entity.NovelEntity
import com.novelreader.ui.components.EmptyView
import com.novelreader.ui.components.LoadingIndicator
import com.novelreader.ui.components.NovelGridItem
import com.novelreader.ui.components.StyledStatusBadge
import com.novelreader.ui.theme.*

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
                title = {
                    Column {
                        Text("Bibliothèque", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold))
                        if (!uiState.isLoading && uiState.novels.isNotEmpty()) {
                            Text("${uiState.novels.size} novels", style = MaterialTheme.typography.bodySmall.copy(color = OnSurfaceDarkSecondary))
                        }
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::toggleViewMode) {
                        Icon(
                            if (uiState.viewMode == ViewMode.GRID) Icons.Default.ViewList else Icons.Default.ViewList,
                            contentDescription = "Vue liste",
                            tint = OnSurfaceDark
                        )
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = "Paramètres", tint = OnSurfaceDark)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = OnSurfaceDark
                )
            )
        },
        containerColor = SurfaceDark
    ) { paddingValues ->
        when {
            uiState.isLoading -> LoadingIndicator(modifier = Modifier.padding(paddingValues))
            uiState.novels.isEmpty() -> EmptyView(
                message = "Ta bibliothèque est vide.\nAjoute des novels depuis Découverte.",
                modifier = Modifier.padding(paddingValues)
            )
            else -> {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize().padding(paddingValues)
                ) {
                    // === HERO: Continuer la lecture ===
                    uiState.continueReading?.let { ch ->
                        item(key = "continue") {
                            ContinueReadingCard(
                                novelSlug = ch.novelSlug,
                                novelTitle = ch.novelTitle.ifBlank { ch.novelSlug },
                                chapterNumber = ch.chapterNumber,
                                onClick = { onNovelClick(ch.novelSlug) }
                            )
                        }
                    }

                    // === CATÉGORIES ===
                    item(key = "categories") {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Catégories", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                                IconButton(onClick = viewModel::showNewCategoryDialog, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Default.Add, contentDescription = "Nouvelle", tint = Primary, modifier = Modifier.size(20.dp))
                                }
                            }
                            Spacer(Modifier.height(8.dp))

                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                item { CategoryChip(name = "Tous", selected = uiState.selectedCategoryId == null, onClick = { viewModel.selectCategory(null) }) }
                                items(uiState.categories, key = { it.id }) { cat ->
                                    CategoryChip(
                                        name = cat.name,
                                        selected = uiState.selectedCategoryId == cat.id,
                                        onClick = { viewModel.selectCategory(cat.id) },
                                        onDelete = { viewModel.showDeleteCategory(cat.id) }
                                    )
                                }
                            }
                        }
                    }

                    // === GRILLE DES NOVELS ===
                    val novels = uiState.novels

                    if (uiState.viewMode == ViewMode.GRID) {
                        val rows = novels.chunked(2)
                        rows.forEach { pair ->
                            item(key = "row_${pair.first().slug}") {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    pair.forEach { novel ->
                                        Box(modifier = Modifier.weight(1f)) {
                                            NovelGridItem(novel = novel, onClick = { onNovelClick(novel.slug) }, unreadCount = novel.unreadChapterCount)
                                        }
                                    }
                                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    } else {
                        items(items = novels, key = { it.slug }) { novel ->
                            LibraryListRow(novel = novel, onClick = { onNovelClick(novel.slug) })
                        }
                    }
                }
            }
        }

        // Dialogue création catégorie
        if (uiState.showNewCategoryDialog) {
            AlertDialog(
                onDismissRequest = viewModel::hideNewCategoryDialog,
                containerColor = SurfaceDarkCard,
                titleContentColor = OnSurfaceDark,
                textContentColor = OnSurfaceDarkSecondary,
                title = { Text("Nouvelle catégorie", fontWeight = FontWeight.Bold) },
                text = {
                    OutlinedTextField(
                        value = uiState.newCategoryName,
                        onValueChange = viewModel::onNewCategoryNameChange,
                        placeholder = { Text("Nom") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Primary,
                            cursorColor = Primary,
                            focusedTextColor = OnSurfaceDark,
                            unfocusedTextColor = OnSurfaceDark,
                            focusedContainerColor = SurfaceDarkElevated,
                            unfocusedContainerColor = SurfaceDarkElevated
                        )
                    )
                },
                confirmButton = { Button(onClick = viewModel::createCategory, colors = ButtonDefaults.buttonColors(containerColor = Primary)) { Text("Créer") } },
                dismissButton = { TextButton(onClick = viewModel::hideNewCategoryDialog) { Text("Annuler", color = OnSurfaceDarkSecondary) } }
            )
        }

        // Dialogue suppression catégorie
        uiState.deleteCategoryId?.let { id ->
            AlertDialog(
                onDismissRequest = viewModel::hideDeleteCategory,
                containerColor = SurfaceDarkCard,
                title = { Text("Supprimer ?", fontWeight = FontWeight.Bold) },
                text = { Text("La catégorie sera supprimée.", color = OnSurfaceDarkSecondary) },
                confirmButton = { Button(onClick = { viewModel.deleteCategory(id) }, colors = ButtonDefaults.buttonColors(containerColor = Error)) { Text("Supprimer") } },
                dismissButton = { TextButton(onClick = viewModel::hideDeleteCategory) { Text("Annuler", color = OnSurfaceDarkSecondary) } }
            )
        }
    }
}

@Composable
private fun ContinueReadingCard(novelSlug: String, novelTitle: String, chapterNumber: Int, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        listOf(Primary.copy(alpha = 0.2f), Primary.copy(alpha = 0.05f))
                    )
                )
                .padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .background(Primary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Continuer la lecture", style = MaterialTheme.typography.labelLarge.copy(color = Primary, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp))
                    Spacer(Modifier.height(4.dp))
                    Text(novelTitle, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("Chapitre $chapterNumber", style = MaterialTheme.typography.bodySmall.copy(color = OnSurfaceDarkSecondary))
                }
            }
        }
    }
}

@Composable
private fun CategoryChip(name: String, selected: Boolean, onClick: () -> Unit, onDelete: (() -> Unit)? = null) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(100.dp))
            .background(if (selected) Brush.horizontalGradient(listOf(Primary, PrimaryVariant)) else SurfaceDarkCard)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(name, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold, color = if (selected) Color.White else OnSurfaceDarkSecondary))
            if (onDelete != null) {
                IconButton(onClick = onDelete, modifier = Modifier.size(16.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Supprimer", tint = if (selected) Color.White else OnSurfaceDarkTertiary, modifier = Modifier.size(12.dp))
                }
            }
        }
    }
}

@Composable
private fun LibraryListRow(novel: NovelEntity, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDarkCard),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            AsyncImage(
                model = novel.coverImageUrl, contentDescription = novel.title,
                modifier = Modifier.size(64.dp).clip(RoundedCornerShape(10.dp)),
                contentScale = ContentScale.Crop
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(novel.title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(novel.author, style = MaterialTheme.typography.bodySmall.copy(color = OnSurfaceDarkSecondary), maxLines = 1)
                if (novel.unreadChapterCount > 0) {
                    Text("${novel.unreadChapterCount} nouveau${if (novel.unreadChapterCount > 1) "x" else ""}", style = MaterialTheme.typography.labelSmall.copy(color = Primary, fontWeight = FontWeight.Bold))
                }
            }
            if (novel.unreadChapterCount > 0) {
                Box(modifier = Modifier.size(8.dp).background(Primary, CircleShape))
            }
        }
    }
}
