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
    val coverImageUrl: String,
    val synopsis: String,
    val status: NovelStatus,
    val rating: Double,
    val genres: List<String>,
    val chapterCount: Int,
    val sourceUrl: String
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
