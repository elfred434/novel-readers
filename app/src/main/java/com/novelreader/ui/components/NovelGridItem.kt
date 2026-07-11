package com.novelreader.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.novelreader.ui.theme.RatingGold

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NovelGridItem(
    coverUrl: String, title: String, author: String,
    status: NovelStatus, rating: Double,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    unreadCount: Int = 0,
    isDownloaded: Boolean = false,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box {
            Column {
                // Couverture
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(2f / 3f)
                        .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                        .background(Color(0xFF0A0A0A))
                ) {
                    if (coverUrl.isNotBlank()) {
                        AsyncImage(
                            model = coverUrl, contentDescription = title,
                            modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                title.take(2).uppercase(),
                                style = MaterialTheme.typography.headlineLarge,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                            )
                        }
                    }

                    // Badge note
                    if (rating > 0) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(6.dp)
                                .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text("★", fontSize = 11.sp, color = RatingGold)
                                Text("%.1f".format(rating), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = Color.White))
                            }
                        }
                    }

                    // Badge téléchargé
                    if (isDownloaded) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(6.dp)
                                .size(22.dp)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.9f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("✓", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }

                // Infos
                Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                    Text(
                        title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        author, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Badge non lus
            if (unreadCount > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset(x = (-4).dp, y = (-4).dp)
                        .size(24.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (unreadCount > 99) "99+" else "$unreadCount",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = Color.White, fontSize = 9.sp)
                    )
                }
            }
        }
    }
}

@Composable
fun NovelGridItem(novel: NovelEntity, onClick: () -> Unit, modifier: Modifier = Modifier, unreadCount: Int = novel.unreadChapterCount, onLongClick: () -> Unit = {}, isDownloaded: Boolean = false) =
    NovelGridItem(coverUrl = novel.coverImageUrl, title = novel.title, author = novel.author,
        status = NovelStatus.fromString(novel.status), rating = novel.rating,
        onClick = onClick, onLongClick = onLongClick, unreadCount = unreadCount, modifier = modifier, isDownloaded = isDownloaded)

@Composable
fun NovelGridItem(novel: Novel, onClick: () -> Unit, modifier: Modifier = Modifier) =
    NovelGridItem(coverUrl = novel.coverImageUrl, title = novel.title, author = novel.author,
        status = novel.status, rating = novel.rating, onClick = onClick, modifier = modifier)
