package com.novelreader.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.novelreader.data.model.NovelStatus
import com.novelreader.ui.theme.*

/**
 * Badge de statut avec dégradé et icône. Style luxe.
 */
@Composable
fun StyledStatusBadge(
    status: NovelStatus,
    modifier: Modifier = Modifier
) {
    val gradient: Brush
    val text: String

    when (status) {
        NovelStatus.COMPLETED -> {
            gradient = Brush.horizontalGradient(listOf(StatusCompleted, Color(0xFF1D4ED8)))
            text = "Terminé"
        }
        NovelStatus.ONGOING -> {
            gradient = Brush.horizontalGradient(listOf(StatusOngoing, Color(0xFF15803D)))
            text = "En cours"
        }
        NovelStatus.UNKNOWN -> {
            gradient = Brush.horizontalGradient(listOf(OnSurfaceDarkTertiary, OnSurfaceDarkTertiary))
            text = "?"
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(100.dp))
            .background(gradient)
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                color = Color.White,
                letterSpacing = 0.5.sp
            )
        )
    }
}

/**
 * Barre de progression stylisée avec dégradé.
 */
@Composable
fun ProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    gradient: Brush = Brush.horizontalGradient(listOf(Primary, Secondary))
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(500)
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(animatedProgress)
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(gradient)
        )
    }
}
