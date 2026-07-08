package com.novelreader.data.model

/**
 * Modèle de données pour un chapitre (utilisé par l'interface source).
 * La représentation légère affichée dans les listes.
 */
data class ChapterPreview(
    val id: String = "",
    val novelSlug: String,
    val chapterNumber: Int,
    val title: String,
    val url: String,
    val publishedAt: String? = null,
    val novelTitle: String? = null  // Utile pour le flux "derniers mises à jour"
)

/**
 * Contenu complet d'un chapitre (retourné par la source).
 */
data class ChapterContent(
    val chapterTitle: String,
    val novelTitle: String,
    val paragraphs: List<Paragraph>,
    val prevChapterUrl: String? = null,
    val nextChapterUrl: String? = null
)

/**
 * Un paragraphe du chapitre.
 * Le contenu peut contenir du HTML basique (<i>, <b>, etc.) pour la mise en forme.
 */
data class Paragraph(
    val index: Int,
    val htmlContent: String
)
