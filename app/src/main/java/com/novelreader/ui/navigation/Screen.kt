package com.novelreader.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Update
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Définit toutes les routes de navigation de l'application.
 *
 * La route Reader encode l'URL du chapitre en paramètre (encodé URL)
 * pour éviter les conflits avec le système de navigation Compose.
 */
sealed class Screen(
    val route: String,
    val title: String = "",
    val icon: ImageVector? = null
) {
    // Bottom navigation — 4 tabs principaux
    data object Library : Screen(
        route = "library",
        title = "Bibliothèque",
        icon = Icons.Default.Bookmark
    )

    data object Browse : Screen(
        route = "browse",
        title = "Découverte",
        icon = Icons.Default.Explore
    )

    data object Updates : Screen(
        route = "updates",
        title = "Mises à jour",
        icon = Icons.Default.Update
    )

    data object History : Screen(
        route = "history",
        title = "Historique",
        icon = Icons.Default.History
    )

    // Écrans secondaires
    data object Detail : Screen(
        route = "novel/{slug}",
        title = "Détail"
    ) {
        fun createRoute(slug: String) = "novel/$slug"
    }

    data object Reader : Screen(
        route = "reader/{chapterUrlEncoded}",
        title = "Lecture"
    ) {
        fun createRoute(chapterUrl: String): String {
            val encoded = java.net.URLEncoder.encode(chapterUrl, "UTF-8")
            return "reader/$encoded"
        }
    }

    data object Settings : Screen(
        route = "settings",
        title = "Paramètres"
    )

    data object Extensions : Screen(
        route = "extensions",
        title = "Extensions"
    )

    data object Downloads : Screen(
        route = "downloads",
        title = "Téléchargements"
    )

    companion object {
        /** Liste des écrans affichés dans la BottomNavBar. */
        val bottomNavItems = listOf(Library, Browse, Updates, History)
    }
}
