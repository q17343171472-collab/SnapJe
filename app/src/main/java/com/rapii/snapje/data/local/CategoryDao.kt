package com.rapii.snapje.data.local

import androidx.paging.PagingSource
import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for Category entities.
 * Provides CRUD operations and reactive flows for category data.
 */
@Dao
interface CategoryDao {

    /**
     * Get all categories ordered by pinned status and name.
     */
    @Query("SELECT * FROM categories ORDER BY isPinned DESC, name ASC")
    fun getAllCategories(): Flow<List<CategoryEntity>>

    /**
     * Get all categories as PagingSource for pagination.
     */
    @Query("SELECT * FROM categories ORDER BY isPinned DESC, name ASC")
    fun getAllCategoriesPaging(): PagingSource<Int, CategoryEntity>

    /**
     * Get a specific category by ID.
     */
    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getCategoryById(id: Long): CategoryEntity?

    /**
     * Get a specific category by ID as Flow.
     */
    @Query("SELECT * FROM categories WHERE id = :id")
    fun getCategoryByIdFlow(id: Long): Flow<CategoryEntity?>

    /**
     * Get pinned categories only.
     */
    @Query("SELECT * FROM categories WHERE isPinned = 1 ORDER BY name ASC")
    fun getPinnedCategories(): Flow<List<CategoryEntity>>

    /**
     * Get categories with photo count greater than zero.
     */
    @Query("SELECT * FROM categories WHERE photoCount > 0 ORDER BY isPinned DESC, name ASC")
    fun getNonEmptyCategories(): Flow<List<CategoryEntity>>

    /**
     * Insert or update a category.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: CategoryEntity)

    /**
     * Insert or update multiple categories.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategories(categories: List<CategoryEntity>)

    /**
     * Update category pin status.
     */
    @Query("UPDATE categories SET isPinned = :isPinned WHERE id = :id")
    suspend fun updateCategoryPin(id: Long, isPinned: Boolean)

    /**
     * Update category sort order.
     */
    @Query("UPDATE categories SET sortBy = :sortBy WHERE id = :id")
    suspend fun updateCategorySort(id: Long, sortBy: String)

    /**
     * Delete a specific category.
     */
    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun deleteCategory(id: Long)

    /**
     * Delete all categories.
     */
    @Query("DELETE FROM categories")
    suspend fun deleteAllCategories()

    /**
     * Get total category count.
     */
    @Query("SELECT COUNT(*) FROM categories")
    fun getCategoryCount(): Flow<Int>

    /**
     * Get total photo count across all categories.
     */
    @Query("SELECT SUM(photoCount) FROM categories")
    fun getTotalPhotoCount(): Flow<Int?>

    /**
     * Search categories by name.
     */
    @Query("SELECT * FROM categories WHERE name LIKE :query ORDER BY name ASC")
    fun searchCategories(query: String): Flow<List<CategoryEntity>>
}
