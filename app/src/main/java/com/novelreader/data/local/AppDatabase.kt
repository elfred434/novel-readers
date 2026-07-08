package com.novelreader.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.novelreader.data.local.dao.CategoryDao
import com.novelreader.data.local.dao.ChapterContentDao
import com.novelreader.data.local.dao.ChapterDao
import com.novelreader.data.local.dao.NovelDao
import com.novelreader.data.local.entity.CategoryEntity
import com.novelreader.data.local.entity.ChapterContentEntity
import com.novelreader.data.local.entity.ChapterEntity
import com.novelreader.data.local.entity.NovelCategoryCrossRef
import com.novelreader.data.local.entity.NovelEntity

/**
 * Base de données Room principale de l'application.
 *
 * ENTITÉS (conformes Section 4 du CDC) :
 * - NovelEntity : novels dans la bibliothèque de l'utilisateur
 * - ChapterEntity : chapitres d'un novel suivi
 * - ChapterContentEntity : cache local des chapitres téléchargés
 * - CategoryEntity : catégories personnalisées pour organiser la bibliothèque
 * - NovelCategoryCrossRef : table de jonction many-to-many novels ↔ catégories
 *
 * VERSIONS :
 *   v1 (Phase 1) : NovelEntity, ChapterEntity, ChapterContentEntity
 *   v2 (Phase 2) : Ajout novelTitle dans ChapterEntity
 *   v3 (Phase 4) : Ajout CategoryEntity + NovelCategoryCrossRef
 *
 * @see NovelEntity
 * @see ChapterEntity
 * @see ChapterContentEntity
 * @see CategoryEntity
 * @see NovelCategoryCrossRef
 */
@Database(
    entities = [
        NovelEntity::class,
        ChapterEntity::class,
        ChapterContentEntity::class,
        CategoryEntity::class,
        NovelCategoryCrossRef::class
    ],
    version = 3,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun novelDao(): NovelDao
    abstract fun chapterDao(): ChapterDao
    abstract fun chapterContentDao(): ChapterContentDao
    abstract fun categoryDao(): CategoryDao

    companion object {
        const val DATABASE_NAME = "novel_reader.db"
    }
}
