package com.novelreader.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
 *   v5 : Ajout addedAt dans ChapterEntity (timestamp d'ajout par mise à jour)
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

        /** v4 → v5 : colonne addedAt dans chapters (0 = jamais marqué comme mise à jour). */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE chapters ADD COLUMN addedAt INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}

