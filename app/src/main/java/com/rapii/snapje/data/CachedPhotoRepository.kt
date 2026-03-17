package com.rapii.snapje.data

import com.rapii.snapje.data.local.CategoryDao
import com.rapii.snapje.data.local.CategoryEntity
import com.rapii.snapje.data.local.PhotoDao
import com.rapii.snapje.data.local.PhotoEntity
import com.rapii.snapje.util.L
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for cached category and photo data.
 * Uses Room database for offline access and faster loading.
 * 
 * Caching strategy:
 * 1. Load from cache immediately (fast)
 * 2. Refresh from MediaStore in background
 * 3. Update cache with fresh data
 */
@Singleton
class CachedPhotoRepository @Inject constructor(
    private val categoryDao: CategoryDao,
    private val photoDao: PhotoDao,
    private val photoRepository: PhotoRepository
) {

    /**
     * Get cached categories as Flow.
     * Updates automatically when cache changes.
     */
    fun getCachedCategories(): Flow<List<Category>> {
        return categoryDao.getAllCategories()
            .map { entities -> entities.map { it.toCategory() } }
            .catch { e ->
                L.e("CachedPhotoRepository", "Error loading cached categories: ${e.message}", e)
                emit(emptyList())
            }
            .flowOn(Dispatchers.IO)
    }

    /**
     * Get cached photos for a category.
     */
    fun getCachedPhotos(bucketId: Long): Flow<List<PhotoItem>> {
        return photoDao.getPhotosByBucket(bucketId)
            .map { entities -> entities.map { it.toPhotoItem() } }
            .catch { e ->
                L.e("CachedPhotoRepository", "Error loading cached photos: ${e.message}", e)
                emit(emptyList())
            }
            .flowOn(Dispatchers.IO)
    }

    /**
     * Refresh categories from MediaStore and update cache.
     * Returns fresh data from MediaStore.
     */
    suspend fun refreshCategories(): Result<List<Category>> {
        return withContext(Dispatchers.IO) {
            try {
                // Get fresh data from MediaStore
                val freshCategories = photoRepository.getCategories()
                
                // Update cache
                val entities = freshCategories.map { CategoryEntity.fromCategory(it) }
                categoryDao.insertCategories(entities)
                
                L.d("CachedPhotoRepository", "Refreshed ${freshCategories.size} categories in cache")
                Result.success(freshCategories)
            } catch (e: Exception) {
                L.e("CachedPhotoRepository", "Error refreshing categories: ${e.message}", e)
                Result.failure(e)
            }
        }
    }

    /**
     * Refresh photos for a category from MediaStore and update cache.
     */
    suspend fun refreshPhotos(bucketId: Long): Result<List<PhotoItem>> {
        return withContext(Dispatchers.IO) {
            try {
                // Get fresh data from MediaStore
                val freshPhotos = photoRepository.getPhotosByAlbum(bucketId)
                
                // Update cache
                val entities = freshPhotos.map { PhotoEntity.fromPhotoItem(it) }
                photoDao.insertPhotos(entities)
                
                L.d("CachedPhotoRepository", "Refreshed ${freshPhotos.size} photos in cache for bucket $bucketId")
                Result.success(freshPhotos)
            } catch (e: Exception) {
                L.e("CachedPhotoRepository", "Error refreshing photos: ${e.message}", e)
                Result.failure(e)
            }
        }
    }

    /**
     * Get categories with cache-first strategy.
     * Returns cached data immediately. Call refreshCategories() separately to update.
     */
    suspend fun getCategoriesWithCache(): List<Category> {
        return withContext(Dispatchers.IO) {
            // Return cached data immediately
            val cached = categoryDao.getAllCategories().firstOrNull()?.map { it.toCategory() }

            if (cached != null && cached.isNotEmpty()) {
                cached
            } else {
                // No cache, load from MediaStore
                refreshCategories().getOrDefault(emptyList())
            }
        }
    }

    /**
     * Get photos with cache-first strategy.
     * Returns cached data immediately. Call refreshPhotos() separately to update.
     */
    suspend fun getPhotosWithCache(bucketId: Long): List<PhotoItem> {
        return withContext(Dispatchers.IO) {
            // Return cached data immediately
            val cached = photoDao.getPhotosByBucket(bucketId).firstOrNull()?.map { it.toPhotoItem() }

            if (cached != null && cached.isNotEmpty()) {
                cached
            } else {
                // No cache, load from MediaStore
                refreshPhotos(bucketId).getOrDefault(emptyList())
            }
        }
    }

    /**
     * Clear all cached data.
     */
    suspend fun clearCache() {
        withContext(Dispatchers.IO) {
            categoryDao.deleteAllCategories()
            photoDao.deleteAllPhotos()
            L.d("CachedPhotoRepository", "Cache cleared")
        }
    }

    /**
     * Get cache statistics.
     */
    suspend fun getCacheStats(): CacheStats {
        return withContext(Dispatchers.IO) {
            val categoryCount = categoryDao.getCategoryCount().firstOrNull() ?: 0
            val photoCount = photoDao.getTotalPhotoCount().firstOrNull() ?: 0
            val storageUsed = photoDao.getTotalStorageUsed().firstOrNull() ?: 0L
            
            CacheStats(
                categoryCount = categoryCount,
                photoCount = photoCount,
                storageUsedBytes = storageUsed
            )
        }
    }

    /**
     * Cache statistics data class.
     */
    data class CacheStats(
        val categoryCount: Int,
        val photoCount: Int,
        val storageUsedBytes: Long
    ) {
        val storageUsedMB: Double
            get() = storageUsedBytes / (1024.0 * 1024.0)
    }
}
