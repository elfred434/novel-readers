package com.novelreader.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entité Room pour la table "chapters".
 * Stocke la liste des chapitres d'un novel suivi, avec l'état de lecture.
 */
@Entity(
    tableName = "chapters",
    foreignKeys = [
        ForeignKey(
            entity = NovelEntity::class,
            parentColumns = ["slug"],
            childColumns = ["novelSlug"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("novelSlug"), Index("chapterNumber")]
)
data class ChapterEntity(
    @PrimaryKey
    val id: String,                 // ID unique (ex: "omniscient-readers-viewpoint_42")
    val novelSlug: String,          // Slug du novel parent
    val novelTitle: String = "",    // Titre lisible du novel (pour l'historique)
    val chapterNumber: Int,
    val title: String,
    val url: String,                // URL complète du chapitre
    val publishedAt: String?,       // Date de publication (format ISO)
    val isRead: Boolean = false,    // Marqué comme lu ?
    val readAt: Long? = null,       // Timestamp de lecture
    val isDownloaded: Boolean = false,  // Téléchargé en local ?
    val scrollPosition: Int = 0     // Position de scroll sauvegardée (en pixels)
)
