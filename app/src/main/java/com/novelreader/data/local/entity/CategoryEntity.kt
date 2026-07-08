package com.novelreader.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entité Room pour la table "categories".
 * Permet à l'utilisateur d'organiser sa bibliothèque en catégories personnalisées
 * (ex: "En cours", "Terminés", "Favoris", "À lire", etc.).
 *
 * Relation many-to-many avec NovelEntity via NovelCategoryCrossRef.
 *
 * @property id Identifiant unique auto-généré
 * @property name Nom de la catégorie (ex: "Favoris", "À lire")
 * @property position Ordre d'affichage (0 = première)
 * @property createdAt Timestamp de création
 */
@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val position: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)
