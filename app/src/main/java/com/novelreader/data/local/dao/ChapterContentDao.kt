package com.novelreader.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.novelreader.data.local.entity.ChapterContentEntity

/**
 * DAO pour la table "chapter_content" — cache des chapitres téléchargés.
 */
@Dao
interface ChapterContentDao {

    @Query("SELECT * FROM chapter_content WHERE chapterId = :chapterId")
    suspend fun getChapterContent(chapterId: String): ChapterContentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChapterContent(content: ChapterContentEntity)

    @Query("DELETE FROM chapter_content WHERE chapterId = :chapterId")
    suspend fun deleteChapterContent(chapterId: String)

    @Query("DELETE FROM chapter_content WHERE chapterId IN (:chapterIds)")
    suspend fun deleteMultipleChapterContents(chapterIds: List<String>)

    @Query("DELETE FROM chapter_content WHERE downloadedAt < :beforeTimestamp")
    suspend fun deleteOldContent(beforeTimestamp: Long)

    @Query("SELECT COUNT(*) FROM chapter_content")
    suspend fun getCachedChapterCount(): Int
}
