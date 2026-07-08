package com.novelreader.data.local

import androidx.room.TypeConverter
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

/**
 * TypeConverters Room pour stocker des types complexes dans SQLite.
 *
 * Le champ NovelEntity.genres est une List<String> sérialisée en JSON.
 * Ce converter permet d'utiliser Room.transaction et les requêtes
 * sans avoir à sérialiser manuellement à chaque appel.
 *
 * Utilisation : @TypeConverters(Converters::class) sur AppDatabase
 */
class Converters {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Convertit une liste de genres (type Kotlin) en string JSON (type SQLite).
     */
    @TypeConverter
    fun fromGenreList(genres: List<String>): String {
        return json.encodeToString(genres)
    }

    /**
     * Convertit une string JSON (SQLite) en liste de genres (Kotlin).
     */
    @TypeConverter
    fun toGenreList(genresJson: String): List<String> {
        return try {
            json.decodeFromString(genresJson)
        } catch (e: Exception) {
            emptyList()
        }
    }
}
