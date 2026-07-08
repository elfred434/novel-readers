package com.novelreader.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * Table de jonction pour la relation many-to-many entre NovelEntity et CategoryEntity.
 *
 * Un novel peut être dans plusieurs catégories (ex: "Favoris" + "En cours").
 * Une catégorie peut contenir plusieurs novels.
 *
 * CASCADE sur les deux FK : si un novel ou une catégorie est supprimé,
 * les lignes de jonction correspondantes sont automatiquement nettoyées.
 */
@Entity(
    tableName = "novel_category_cross_ref",
    primaryKeys = ["novelSlug", "categoryId"],
    foreignKeys = [
        ForeignKey(
            entity = NovelEntity::class,
            parentColumns = ["slug"],
            childColumns = ["novelSlug"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("novelSlug"),
        Index("categoryId")
    ]
)
data class NovelCategoryCrossRef(
    val novelSlug: String,
    val categoryId: Long
)
