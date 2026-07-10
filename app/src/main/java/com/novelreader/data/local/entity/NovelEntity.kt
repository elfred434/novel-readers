package com.novelreader.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "novels")
data class NovelEntity(
    @PrimaryKey
    val slug: String,
    val title: String,
    val author: String,
    val coverImageUrl: String,
    val synopsis: String,
    val status: String,
    val rating: Double,
    val genres: List<String> = emptyList(),
    val sourceUrl: String,
    val addedAt: Long = System.currentTimeMillis(),
    val lastChapterRead: Int? = null,
    val unreadChapterCount: Int = 0,
    val storageFolderName: String = ""  // Nom du dossier de stockage (basé sur le slug, stable)
)
