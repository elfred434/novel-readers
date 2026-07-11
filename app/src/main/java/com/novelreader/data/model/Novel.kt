package com.novelreader.data.model

/**
 * Modèle de données pour un roman/novel (utilisé par l'interface source et le repository).
 * C'est la représentation "domaine", indépendante de la couche de persistance (Room).
 */
data class Novel(
    val id: String = "",
    val slug: String,
    val title: String,
    val author: String,
    val translatorName: String? = null,
    val coverImageUrl: String,
    val synopsis: String,
    val status: NovelStatus,
    val rating: Double,
    val ratingCount: Int = 0,
    val views: Int = 0,
    val bookmarkCount: Int = 0,
    val type: String = "",
    val year: Int? = null,
    val alternativeTitles: String = "",
    val genres: List<String>,
    val chapterCount: Int,
    val sourceUrl: String,
    val firstChapterSlug: String? = null,
    val allTimeRank: Int? = null,
    val totalViews: Int? = null,
    val tags: List<String> = emptyList()
)

enum class NovelStatus {
    ONGOING,
    COMPLETED,
    UNKNOWN;

    companion object {
        fun fromString(value: String): NovelStatus = when (value.uppercase()) {
            "ONGOING", "EN COURS" -> ONGOING
            "COMPLETED", "TERMINÉ", "TERMINE" -> COMPLETED
            else -> UNKNOWN
        }
    }
}
