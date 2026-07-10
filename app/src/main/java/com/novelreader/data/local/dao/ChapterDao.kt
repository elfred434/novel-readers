package com.novelreader.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.novelreader.data.local.entity.ChapterEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO pour la table "chapters".
 */
@Dao
interface ChapterDao {

    @Query("SELECT * FROM chapters WHERE novelSlug = :novelSlug ORDER BY chapterNumber DESC")
    fun getChaptersForNovel(novelSlug: String): Flow<List<ChapterEntity>>

    @Query("SELECT * FROM chapters WHERE novelSlug = :novelSlug ORDER BY chapterNumber DESC")
    suspend fun getChaptersForNovelOnce(novelSlug: String): List<ChapterEntity>

    @Query("SELECT * FROM chapters WHERE id = :id")
    suspend fun getChapterById(id: String): ChapterEntity?

    @Query("SELECT * FROM chapters WHERE novelSlug = :novelSlug AND chapterNumber = :number")
    suspend fun getChapterByNumber(novelSlug: String, number: Int): ChapterEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChapter(chapter: ChapterEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChapters(chapters: List<ChapterEntity>)

    @Update
    suspend fun updateChapter(chapter: ChapterEntity)

    @Delete
    suspend fun deleteChapter(chapter: ChapterEntity)

    @Query("DELETE FROM chapters WHERE novelSlug = :novelSlug")
    suspend fun deleteChaptersForNovel(novelSlug: String)

    @Query("UPDATE chapters SET isRead = 1, readAt = :readAt WHERE id = :id")
    suspend fun markAsRead(id: String, readAt: Long = System.currentTimeMillis())

    @Query("UPDATE chapters SET isRead = 0, readAt = NULL WHERE id = :id")
    suspend fun markAsUnread(id: String)

    @Query("UPDATE chapters SET scrollPosition = :position WHERE id = :id")
    suspend fun updateScrollPosition(id: String, position: Int)

    @Query("UPDATE chapters SET isDownloaded = 0")
    suspend fun resetAllDownloadedFlags()

    @Query("UPDATE chapters SET isDownloaded = 0 WHERE id IN (:chapterIds)")
    suspend fun resetDownloadedFlags(chapterIds: List<String>)

    @Query("SELECT * FROM chapters WHERE isRead = 1 ORDER BY readAt DESC LIMIT :limit")
    fun getRecentHistory(limit: Int = 30): Flow<List<ChapterEntity>>

    @Query("SELECT * FROM chapters WHERE novelSlug = :novelSlug AND isRead = 0 ORDER BY chapterNumber DESC")
    fun getUnreadChapters(novelSlug: String): Flow<List<ChapterEntity>>

    @Query("SELECT COUNT(*) FROM chapters WHERE novelSlug = :novelSlug AND isRead = 0")
    suspend fun getUnreadCount(novelSlug: String): Int
}
