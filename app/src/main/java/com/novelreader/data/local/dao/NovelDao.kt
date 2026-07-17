package com.novelreader.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.novelreader.data.local.entity.NovelEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO pour la table "novels" — bibliothèque locale de l'utilisateur.
 */
@Dao
interface NovelDao {

    @Query("SELECT * FROM novels ORDER BY addedAt DESC")
    fun getAllNovels(): Flow<List<NovelEntity>>

    @Query("SELECT * FROM novels ORDER BY addedAt DESC")
    suspend fun getAllNovelsOnce(): List<NovelEntity>

    @Query("SELECT * FROM novels WHERE slug = :slug")
    suspend fun getNovelBySlug(slug: String): NovelEntity?

    @Query("SELECT * FROM novels WHERE slug = :slug")
    fun getNovelBySlugFlow(slug: String): Flow<NovelEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNovel(novel: NovelEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNovels(novels: List<NovelEntity>)

    @Query("DELETE FROM novels WHERE slug = :slug")
    suspend fun deleteNovelBySlug(slug: String)

    @Query("SELECT COUNT(*) FROM novels")
    suspend fun getNovelCount(): Int

    @Query("UPDATE novels SET unreadChapterCount = :count WHERE slug = :slug")
    suspend fun updateUnreadCount(slug: String, count: Int)

    @Query("UPDATE novels SET lastChapterRead = :chapterNumber WHERE slug = :slug")
    suspend fun updateLastChapterRead(slug: String, chapterNumber: Int)
}
