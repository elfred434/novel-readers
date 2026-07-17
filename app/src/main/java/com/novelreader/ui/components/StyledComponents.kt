package com.novelreader.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novelreader.ui.theme.StatusCancelled
import com.novelreader.ui.theme.StatusCompleted
import com.novelreader.ui.theme.StatusHiatus
import com.novelreader.ui.theme.StatusOngoing
import com.novelreader.data.model.NovelStatus

/**
 * Badge de statut stylisé pour les novels.
 * Utilise un petit indicateur rond + texte.
 */
@Composable
fun StatusBadge(status: NovelStatus, modifier: Modifier = Modifier) {
    val (text, color) = when (status) {
        NovelStatus.ONGOING -> "En cours" to StatusOngoing
        NovelStatus.COMPLETED -> "Terminé" to StatusCompleted
        NovelStatus.HIATUS -> "En pause" to StatusHiatus
        NovelStatus.CANCELLED -> "Abandonné" to StatusCancelled
        NovelStatus.UNKNOWN -> "" to Color.Transparent
    }
    if (text.isBlank()) return

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(color))
        Text(
            text,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.3.sp
            ),
            color = color
        )
    }
}

/**
 * Badge de note avec étoile.
 */
@Composable
fun RatingBadge(rating: Double, modifier: Modifier = Modifier) {
    if (rating <= 0) return
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
            .padding(horizontal = 6.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text("★", fontSize = 12.sp, color = Color(0xFFD4A853))
        Text(
            "%.1f".format(rating),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Petit séparateur de section avec label.
 */
@Composable
fun SectionLabel(text: String, count: Int? = null, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
        )
        if (count != null) {
            Text(
                "$count",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}

/**
 * Ligne horizontale subtile.
 */
@Composable
fun SubtleDivider(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant)
    )
}

/**
 * Indicateur de statut texte pour les détails (plus visible que le badge).
 */
@Composable
fun StatusChip(status: NovelStatus, modifier: Modifier = Modifier) {
    val (text, color) = when (status) {
        NovelStatus.ONGOING -> "En cours" to StatusOngoing
        NovelStatus.COMPLETED -> "Terminé" to StatusCompleted
        NovelStatus.HIATUS -> "En pause" to StatusHiatus
        NovelStatus.CANCELLED -> "Abandonné" to StatusCancelled
        NovelStatus.UNKNOWN -> "?" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = color
        )
    }
}
