package com.novelreader.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.novelreader.data.local.entity.CategoryEntity
import com.novelreader.data.local.entity.NovelCategoryCrossRef
import com.novelreader.data.local.entity.NovelEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO pour les catégories et la relation many-to-many avec les novels.
 */
@Dao
interface CategoryDao {

    // ===================== Catégories =====================

    @Query("SELECT * FROM categories ORDER BY position ASC, name ASC")
    fun getAllCategories(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories ORDER BY position ASC, name ASC")
    suspend fun getAllCategoriesOnce(): List<CategoryEntity>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getCategoryById(id: Long): CategoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: CategoryEntity): Long

    @Update
    suspend fun updateCategory(category: CategoryEntity)

    @Delete
    suspend fun deleteCategory(category: CategoryEntity)

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun deleteCategoryById(id: Long)

    // ===================== Jonction novel ↔ catégorie =====================

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addNovelToCategory(crossRef: NovelCategoryCrossRef)

    @Query("DELETE FROM novel_category_cross_ref WHERE novelSlug = :novelSlug AND categoryId = :categoryId")
    suspend fun removeNovelFromCategory(novelSlug: String, categoryId: Long)

    @Query("DELETE FROM novel_category_cross_ref WHERE novelSlug = :novelSlug")
    suspend fun removeNovelFromAllCategories(novelSlug: String)

    @Query("""
        SELECT c.* FROM categories c
        INNER JOIN novel_category_cross_ref r ON c.id = r.categoryId
        WHERE r.novelSlug = :novelSlug
        ORDER BY c.position ASC
    """)
    suspend fun getCategoriesForNovel(novelSlug: String): List<CategoryEntity>

    @Query("""
        SELECT r.categoryId FROM novel_category_cross_ref r
        WHERE r.novelSlug = :novelSlug
    """)
    suspend fun getCategoryIdsForNovel(novelSlug: String): List<Long>

    @Query("""
        SELECT * FROM novels n
        INNER JOIN novel_category_cross_ref r ON n.slug = r.novelSlug
        WHERE r.categoryId = :categoryId
        ORDER BY n.addedAt DESC
    """)
    fun getNovelsInCategory(categoryId: Long): Flow<List<NovelEntity>>

    // ===================== Transaction : bulk assign =====================

    /**
     * Remplace toutes les catégories d'un novel par la liste fournie.
     * Supprime les anciennes assignations, insère les nouvelles.
     */
    @Transaction
    suspend fun setCategoriesForNovel(novelSlug: String, categoryIds: List<Long>) {
        removeNovelFromAllCategories(novelSlug)
        for (catId in categoryIds) {
            addNovelToCategory(NovelCategoryCrossRef(novelSlug, catId))
        }
    }
}
