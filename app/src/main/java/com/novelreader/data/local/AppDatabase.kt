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
 * Base de données Room.
 *
 * VERSIONS :
 *   v1 : NovelEntity, ChapterEntity, ChapterContentEntity
 *   v2 : Ajout novelTitle dans ChapterEntity
 *   v3 : Ajout CategoryEntity + NovelCategoryCrossRef
 *   v4 : Ajout storageFolderName dans NovelEntity
 *   v5 : Retrait storageFolderName (champ mort — le nom de dossier est
 *        recalculé à la volée par StorageManager.sanitizeFolderName)
 */
@Database(
    entities = [
        NovelEntity::class,
        ChapterEntity::class,
        ChapterContentEntity::class,
        CategoryEntity::class,
        NovelCategoryCrossRef::class
    ],
    version = 5,
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
