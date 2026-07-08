package com.novelreader.ui.screens.updates

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.novelreader.ui.components.EmptyView
import com.novelreader.ui.components.ErrorView
import com.novelreader.ui.components.LoadingIndicator

/**
 * Écran Mises à jour.
 * Affiche les derniers chapitres sortis sur le site.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdatesScreen(
    onNovelClick: (String) -> Unit,
    viewModel: UpdatesViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Mises à jour",
                        style = MaterialTheme.typography.headlineMedium
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { paddingValues ->
        when {
            uiState.isLoading -> {
                LoadingIndicator(modifier = Modifier.padding(paddingValues))
            }
            uiState.error != null -> {
                ErrorView(
                    message = uiState.error ?: "Erreur",
                    onRetry = viewModel::loadUpdates,
                    modifier = Modifier.padding(paddingValues)
                )
            }
            uiState.updates.isEmpty() -> {
                EmptyView(
                    message = "Aucune mise à jour récente.",
                    modifier = Modifier.padding(paddingValues)
                )
            }
            else -> {
                LazyColumn(
                    contentPadding = PaddingValues(
                        start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    items(
                        items = uiState.updates,
                        key = { "${it.novelSlug}_${it.chapterNumber}" }
                    ) { update ->
                        UpdateItem(
                            novelTitle = update.novelTitle ?: update.novelSlug,
                            chapterTitle = update.title,
                            chapterNumber = update.chapterNumber,
                            onClick = { onNovelClick(update.novelSlug) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Item d'une mise à jour.
 */
@Composable
private fun UpdateItem(
    novelTitle: String,
    chapterTitle: String,
    chapterNumber: Int,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = novelTitle,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                Text(
                    text = "Ch. $chapterNumber — $chapterTitle",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
            }
        }
    }
}
