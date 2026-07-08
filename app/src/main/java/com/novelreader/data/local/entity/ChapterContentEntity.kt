package com.novelreader.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entité Room pour la table "chapter_content".
 * Cache local du contenu texte des chapitres téléchargés pour lecture hors-ligne.
 */
@Entity(
    tableName = "chapter_content",
    foreignKeys = [
        ForeignKey(
            entity = ChapterEntity::class,
            parentColumns = ["id"],
            childColumns = ["chapterId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("chapterId")]
)
data class ChapterContentEntity(
    @PrimaryKey
    val chapterId: String,
    val paragraphsJson: String,     // JSON array des paragraphes : [{"index":0, "htmlContent":"..."}]
    val downloadedAt: Long = System.currentTimeMillis()
)
