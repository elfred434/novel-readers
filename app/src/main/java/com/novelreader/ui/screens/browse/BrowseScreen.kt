package com.novelreader.ui.screens.browse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.novelreader.ui.components.EmptyView
import com.novelreader.ui.components.ErrorView
import com.novelreader.ui.components.LoadingIndicator
import com.novelreader.ui.components.NovelGridItem
import com.novelreader.ui.components.SearchBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseScreen(
    onNovelClick: (String) -> Unit,
    viewModel: BrowseViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val gridState = rememberLazyGridState()

    // Pagination normale (hors recherche)
    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisibleItem = gridState.layoutInfo.visibleItemsInfo.lastOrNull()
            lastVisibleItem != null &&
            lastVisibleItem.index >= gridState.layoutInfo.totalItemsCount - 6 &&
            !uiState.isLoadingMore &&
            uiState.hasMore &&
            uiState.searchQuery.isBlank()
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) viewModel.loadMore()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Découverte", style = MaterialTheme.typography.headlineMedium) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            SearchBar(
                query = uiState.searchQuery,
                onQueryChange = viewModel::onSearchQueryChanged,
                placeholder = "Rechercher un novel par titre ou auteur…",
                modifier = Modifier.padding(bottom = 12.dp)
            )

            when {
                // Chargement initial
                uiState.isLoading -> LoadingIndicator()

                // Erreur et rien d'affiché
                uiState.error != null && uiState.novels.isEmpty() -> {
                    ErrorView(
                        message = uiState.error ?: "Erreur inconnue",
                        onRetry = viewModel::loadNovels
                    )
                }

                // Recherche en cours (chargement de plusieurs pages)
                uiState.isSearching -> LoadingIndicator(message = "Recherche en cours…")

                // Résultats de recherche vides
                uiState.searchQuery.isNotBlank() && !uiState.isSearching &&
                    viewModel.isShowingSearchResults && viewModel.displayedNovels.isEmpty() -> {
                    EmptyView(
                        message = "Aucun résultat pour \"${uiState.searchQuery}\".\n" +
                            "Vérifie l'orthographe ou essaie un autre terme."
                    )
                }

                // Contenu normal ou résultats de recherche
                else -> {
                    val novels = viewModel.displayedNovels
                    val searchResultLabel = if (viewModel.isShowingSearchResults) {
                        "${novels.size} résultat${if (novels.size > 1) "s" else ""}"
                    } else null

                    Column {
                        // Indicateur de nombre de résultats
                        if (searchResultLabel != null) {
                            Text(
                                text = searchResultLabel,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }

                        LazyVerticalGrid(
                            state = gridState,
                            columns = GridCells.Adaptive(minSize = 140.dp),
                            contentPadding = PaddingValues(bottom = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(
                                items = novels,
                                key = { it.slug }
                            ) { novel ->
                                NovelGridItem(
                                    novel = novel,
                                    onClick = { onNovelClick(novel.slug) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
