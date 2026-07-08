package com.novelreader.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import coil.compose.AsyncImage
import com.novelreader.data.local.entity.NovelEntity
import com.novelreader.data.model.Novel
import com.novelreader.data.model.NovelStatus
import com.novelreader.ui.theme.*

/**
 * Carte novel au design luxe — inspirée des apps de streaming.
 * Avec dégradé, badge, note et effet de profondeur.
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
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = SurfaceDarkCard
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 8.dp,
            pressedElevation = 2.dp
        )
    ) {
        Box {
            Column {
                // Image avec dégradé overlay
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(2f / 3f)
                ) {
                    AsyncImage(
                        model = coverUrl,
                        contentDescription = title,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
                        contentScale = ContentScale.Crop
                    )

                    // Dégradé vers le bas
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color.Black.copy(alpha = 0.6f)
                                    ),
                                    startY = 200f
                                )
                            )
                    )

                    // Badge statut
                    StyledStatusBadge(
                        status = status,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                    )

                    // Note en bas de l'image
                    if (rating > 0) {
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text(
                                text = "★",
                                fontSize = 14.sp,
                                color = RatingGold
                            )
                            Text(
                                text = "%.1f".format(rating),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            )
                        }
                    }
                }

                // Infos texte
                Column(
                    modifier = Modifier.padding(12.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = OnSurfaceDark,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = author,
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurfaceDarkSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Badge cerise "nouveaux chapitres"
            if (unreadCount > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset(x = (-4).dp, y = (-4).dp)
                        .size(26.dp)
                        .background(Primary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (unreadCount > 9) "N" else "$unreadCount",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 10.sp
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun NovelGridItem(
    novel: NovelEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    unreadCount: Int = novel.unreadChapterCount
) = NovelGridItem(
    coverUrl = novel.coverImageUrl, title = novel.title, author = novel.author,
    status = NovelStatus.fromString(novel.status), rating = novel.rating,
    onClick = onClick, unreadCount = unreadCount, modifier = modifier
)

@Composable
fun NovelGridItem(
    novel: Novel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) = NovelGridItem(
    coverUrl = novel.coverImageUrl, title = novel.title, author = novel.author,
    status = novel.status, rating = novel.rating, onClick = onClick, modifier = modifier
)
