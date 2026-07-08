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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.novelreader.ui.components.EmptyView
import com.novelreader.ui.components.ErrorView
import com.novelreader.ui.components.LoadingIndicator
import com.novelreader.ui.components.NovelGridItem
import com.novelreader.ui.components.SearchBar
import com.novelreader.ui.theme.OnSurfaceDark
import com.novelreader.ui.theme.OnSurfaceDarkSecondary
import com.novelreader.ui.theme.SurfaceDark

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseScreen(
    onNovelClick: (String) -> Unit,
    viewModel: BrowseViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val gridState = rememberLazyGridState()

    val shouldLoadMore by remember {
        derivedStateOf {
            val last = gridState.layoutInfo.visibleItemsInfo.lastOrNull()
            last != null && last.index >= gridState.layoutInfo.totalItemsCount - 6 &&
                !uiState.isLoadingMore && uiState.hasMore && uiState.searchQuery.isBlank()
        }
    }

    LaunchedEffect(shouldLoadMore) { if (shouldLoadMore) viewModel.loadMore() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Découverte", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold))
                        if (!uiState.isLoading && uiState.novels.isNotEmpty()) {
                            Text("${uiState.novels.size} novels disponibles", style = MaterialTheme.typography.bodySmall.copy(color = OnSurfaceDarkSecondary))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent, titleContentColor = OnSurfaceDark)
            )
        },
        containerColor = SurfaceDark
    ) { paddingValues ->
        Column(
            modifier = Modifier.fillMaxSize().padding(paddingValues).padding(horizontal = 16.dp)
        ) {
            SearchBar(
                query = uiState.searchQuery,
                onQueryChange = viewModel::onSearchQueryChanged,
                placeholder = "Rechercher titre ou auteur…",
                modifier = Modifier.padding(bottom = 12.dp)
            )

            when {
                uiState.isLoading -> LoadingIndicator()
                uiState.error != null && uiState.novels.isEmpty() -> {
                    ErrorView(message = uiState.error ?: "Erreur", onRetry = viewModel::loadNovels)
                }
                uiState.isSearching -> LoadingIndicator(message = "Recherche en cours…")
                uiState.searchQuery.isNotBlank() && !uiState.isSearching &&
                    viewModel.isShowingSearchResults && viewModel.displayedNovels.isEmpty() -> {
                    EmptyView(message = "Aucun résultat pour \"${uiState.searchQuery}\".")
                }
                else -> {
                    val novels = viewModel.displayedNovels
                    val label = if (viewModel.isShowingSearchResults) "${novels.size} résultat${if (novels.size > 1) "s" else ""}" else null

                    Column {
                        label?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = OnSurfaceDarkSecondary, modifier = Modifier.padding(bottom = 4.dp))
                        }
                        LazyVerticalGrid(
                            state = gridState,
                            columns = GridCells.Adaptive(minSize = 150.dp),
                            contentPadding = PaddingValues(bottom = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(items = novels, key = { it.slug }) { novel ->
                                NovelGridItem(novel = novel, onClick = { onNovelClick(novel.slug) })
                            }
                        }
                    }
                }
            }
        }
    }
}
