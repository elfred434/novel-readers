package com.novelreader.ui.screens.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.novelreader.ui.components.EmptyView
import com.novelreader.ui.components.ErrorView
import com.novelreader.ui.components.LoadingIndicator
import com.novelreader.ui.components.NovelGridItem
import com.novelreader.ui.components.SearchBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseScreen(onNovelClick: (String) -> Unit, viewModel: BrowseViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val gridState = rememberLazyGridState()

    val shouldLoadMore by remember {
        derivedStateOf {
            val last = gridState.layoutInfo.visibleItemsInfo.lastOrNull()
            last != null && last.index >= gridState.layoutInfo.totalItemsCount - 6 &&
                !uiState.isLoadingMore && uiState.hasMore && uiState.searchQuery.isBlank()
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) viewModel.loadMore()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Découvrir",
                            style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold)
                        )
                        if (!uiState.isLoading && uiState.novels.isNotEmpty() && !viewModel.isShowingSearchResults) {
                            Text(
                                "${uiState.novels.size} novels",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)
        ) {
            SearchBar(
                query = uiState.searchQuery,
                onQueryChange = viewModel::onSearchQueryChanged,
                placeholder = "Rechercher titre ou auteur…",
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // Filtres et tri (cachés pendant la recherche)
            if (uiState.searchQuery.isBlank()) {
                Column(modifier = Modifier.padding(bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Ligne 1 : Statut + Tri
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StatusFilter.entries.forEach { filter ->
                            FilterChip(
                                label = filter.label,
                                selected = uiState.statusFilter == filter,
                                onClick = { viewModel.setStatusFilter(filter) }
                            )
                        }
                        Box(Modifier.width(1.dp).height(20.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)))
                        SortOption.entries.forEach { option ->
                            FilterChip(
                                label = option.label,
                                selected = uiState.sortOption == option,
                                onClick = { viewModel.setSortOption(option) }
                            )
                        }
                    }

                    // Ligne 2 : Genres (P3) — extraits des résultats
                    if (uiState.availableGenres.isNotEmpty() && uiState.genreSlug == null) {
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            uiState.availableGenres.take(15).forEach { genre ->
                                FilterChip(
                                    label = genre.name,
                                    selected = uiState.genreSlug == genre.slug,
                                    onClick = { viewModel.setGenreFilter(genre.slug) }
                                )
                            }
                        }
                    }
                    // Si un genre est sélectionné, afficher seulement lui
                    if (uiState.genreSlug != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val selected = uiState.availableGenres.find { it.slug == uiState.genreSlug }
                            if (selected != null) {
                                FilterChip(label = selected.name, selected = true, onClick = { viewModel.setGenreFilter(null) })
                            }
                        }
                    }
                }
            }

            when {
                uiState.isLoading -> LoadingIndicator()
                uiState.error != null && uiState.novels.isEmpty() ->
                    ErrorView(uiState.error ?: "Erreur", viewModel::loadNovels)
                uiState.isSearching -> LoadingIndicator(message = "Recherche…")
                uiState.searchQuery.isNotBlank() && !uiState.isSearching &&
                    viewModel.isShowingSearchResults && viewModel.displayedNovels.isEmpty() ->
                    EmptyView("Aucun résultat pour « ${uiState.searchQuery} ».")
                else -> {
                    val novels = viewModel.displayedNovels
                    Column {
                        if (viewModel.isShowingSearchResults) {
                            Text(
                                "${novels.size} résultat${if (novels.size > 1) "s" else ""}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }

                        LazyVerticalGrid(
                            state = gridState,
                            columns = GridCells.Adaptive(minSize = 150.dp),
                            contentPadding = PaddingValues(bottom = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(novels, key = { it.slug }) { novel ->
                                NovelGridItem(novel, onClick = { onNovelClick(novel.slug) })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(100.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
            ),
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
