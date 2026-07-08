package com.novelreader.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entité Room pour la table "novels".
 * Stocke les novels que l'utilisateur a ajoutés à sa bibliothèque.
 *
 * Le champ [genres] est une List<String> convertie automatiquement
 * en JSON par le TypeConverter [com.novelreader.data.local.Converters].
 *
 * @property slug Identifiant unique du novel (ex: "omniscient-readers-viewpoint")
 * @property title Titre du novel
 * @property author Nom de l'auteur
 * @property coverImageUrl URL complète de l'image de couverture
 * @property synopsis Résumé / description du novel
 * @property status État : "ONGOING" | "COMPLETED" | "UNKNOWN"
 * @property rating Note moyenne (0.0 - 5.0)
 * @property genres Liste des genres (sérialisée JSON via TypeConverter)
 * @property sourceUrl URL de la page du novel sur la source
 * @property addedAt Timestamp d'ajout à la bibliothèque
 * @property lastChapterRead Dernier numéro de chapitre lu
 * @property unreadChapterCount Nombre de nouveaux chapitres non lus
 */
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
    val genres: List<String> = emptyList(),  // TypeConverter gère List<String> <-> JSON
    val sourceUrl: String,
    val addedAt: Long = System.currentTimeMillis(),
    val lastChapterRead: Int? = null,
    val unreadChapterCount: Int = 0
)
