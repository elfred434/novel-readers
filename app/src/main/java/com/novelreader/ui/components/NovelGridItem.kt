package com.novelreader.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novelreader.ui.theme.Primary
import coil.compose.AsyncImage
import com.novelreader.data.local.entity.NovelEntity
import com.novelreader.data.model.Novel
import com.novelreader.data.model.NovelStatus

/**
 * Carte d'un novel en vue grille (utilisée dans Browse et Library).
 * Affiche la couverture, le titre, l'auteur et le statut.
 */
@Composable
fun NovelGridItem(
    coverUrl: String,
    title: String,
    author: String,
    status: NovelStatus,
    rating: Double,
    onClick: () -> Unit,
    unreadCount: Int = 0,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box {
            Column {
                // Couverture
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(2f / 3f)
                        .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                        .background(MaterialTheme.colorScheme.surface)
                ) {
                    AsyncImage(
                        model = coverUrl,
                        contentDescription = title,
                        modifier = Modifier.fillMaxWidth(),
                        contentScale = ContentScale.Crop
                    )

                    StatusBadge(
                        status = status,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                    )
                }

                // Infos texte
                Column(
                    modifier = Modifier.padding(8.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = author,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (rating > 0) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = "★",
                                style = MaterialTheme.typography.labelSmall,
                                color = com.novelreader.ui.theme.RatingGold
                            )
                            Text(
                                text = "%.1f".format(rating),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Badge nouveaux chapitres
            if (unreadCount > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp)
                        .size(24.dp)
                        .background(color = Primary, shape = CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (unreadCount > 99) "99+" else "$unreadCount",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}

/**
 * Badge indiquant le statut du novel (Terminé / En cours).
 */
@Composable
fun StatusBadge(
    status: NovelStatus,
    modifier: Modifier = Modifier
) {
    val (text, color) = when (status) {
        NovelStatus.COMPLETED -> "Terminé" to com.novelreader.ui.theme.StatusCompleted
        NovelStatus.ONGOING -> "En cours" to com.novelreader.ui.theme.StatusOngoing
        NovelStatus.UNKNOWN -> "?" to MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = modifier
            .background(
                color = color.copy(alpha = 0.9f),
                shape = RoundedCornerShape(4.dp)
            )
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = com.novelreader.ui.theme.Surface
        )
    }
}

/**
 * Helper pour créer un NovelGridItem depuis un NovelEntity (bibliothèque locale).
 */
@Composable
fun NovelGridItem(
    novel: NovelEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    unreadCount: Int = novel.unreadChapterCount
) = NovelGridItem(
    coverUrl = novel.coverImageUrl,
    title = novel.title,
    author = novel.author,
    status = NovelStatus.fromString(novel.status),
    rating = novel.rating,
    onClick = onClick,
    unreadCount = unreadCount,
    modifier = modifier
)

/**
 * Helper pour créer un NovelGridItem depuis un Novel (modèle domaine).
 */
@Composable
fun NovelGridItem(
    novel: Novel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) = NovelGridItem(
    coverUrl = novel.coverImageUrl,
    title = novel.title,
    author = novel.author,
    status = novel.status,
    rating = novel.rating,
    onClick = onClick,
    modifier = modifier
)
