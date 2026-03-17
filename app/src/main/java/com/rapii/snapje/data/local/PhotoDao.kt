package com.rapii.snapje.data.local

import androidx.paging.PagingSource
import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for Photo entities.
 * Provides CRUD operations and reactive flows for photo data.
 */
@Dao
interface PhotoDao {

    /**
     * Get all photos ordered by date taken (newest first).
     */
    @Query("SELECT * FROM photos ORDER BY dateTaken DESC")
    fun getAllPhotos(): Flow<List<PhotoEntity>>

    /**
     * Get all photos as PagingSource for pagination.
     */
    @Query("SELECT * FROM photos ORDER BY dateTaken DESC")
    fun getAllPhotosPaging(): PagingSource<Int, PhotoEntity>

    /**
     * Get photos for a specific bucket/category.
     */
    @Query("SELECT * FROM photos WHERE bucketId = :bucketId ORDER BY dateTaken DESC")
    fun getPhotosByBucket(bucketId: Long): Flow<List<PhotoEntity>>

    /**
     * Get photos for a specific bucket as PagingSource.
     */
    @Query("SELECT * FROM photos WHERE bucketId = :bucketId ORDER BY dateTaken DESC")
    fun getPhotosByBucketPaging(bucketId: Long): PagingSource<Int, PhotoEntity>

    /**
     * Get a specific photo by ID.
     */
    @Query("SELECT * FROM photos WHERE id = :id")
    suspend fun getPhotoById(id: Long): PhotoEntity?

    /**
     * Get photos by date range.
     */
    @Query("SELECT * FROM photos WHERE dateTaken BETWEEN :startDate AND :endDate ORDER BY dateTaken DESC")
    fun getPhotosByDateRange(startDate: Long, endDate: Long): Flow<List<PhotoEntity>>

    /**
     * Get photos by size range.
     */
    @Query("SELECT * FROM photos WHERE size BETWEEN :minSize AND :maxSize ORDER BY size DESC")
    fun getPhotosBySizeRange(minSize: Long, maxSize: Long): Flow<List<PhotoEntity>>

    /**
     * Search photos by display name.
     */
    @Query("SELECT * FROM photos WHERE displayName LIKE :query ORDER BY dateTaken DESC")
    fun searchPhotos(query: String): Flow<List<PhotoEntity>>

    /**
     * Insert or update a photo.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPhoto(photo: PhotoEntity)

    /**
     * Insert or update multiple photos.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPhotos(photos: List<PhotoEntity>)

    /**
     * Insert or update multiple photos and return inserted IDs.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPhotosAndGetIds(photos: List<PhotoEntity>): List<Long>

    /**
     * Delete a specific photo.
     */
    @Query("DELETE FROM photos WHERE id = :id")
    suspend fun deletePhoto(id: Long)

    /**
     * Delete photos by bucket ID.
     */
    @Query("DELETE FROM photos WHERE bucketId = :bucketId")
    suspend fun deletePhotosByBucket(bucketId: Long)

    /**
     * Delete photos older than a specific date.
     */
    @Query("DELETE FROM photos WHERE dateTaken < :cutoffDate")
    suspend fun deletePhotosOlderThan(cutoffDate: Long)

    /**
     * Delete all photos.
     */
    @Query("DELETE FROM photos")
    suspend fun deleteAllPhotos()

    /**
     * Get photo count for a specific bucket.
     */
    @Query("SELECT COUNT(*) FROM photos WHERE bucketId = :bucketId")
    fun getPhotoCountForBucket(bucketId: Long): Flow<Int>

    /**
     * Get total photo count.
     */
    @Query("SELECT COUNT(*) FROM photos")
    fun getTotalPhotoCount(): Flow<Int>

    /**
     * Get total storage used by photos.
     */
    @Query("SELECT SUM(size) FROM photos")
    fun getTotalStorageUsed(): Flow<Long?>

    /**
     * Get oldest photo date.
     */
    @Query("SELECT MIN(dateTaken) FROM photos")
    fun getOldestPhotoDate(): Flow<Long?>

    /**
     * Get newest photo date.
     */
    @Query("SELECT MAX(dateTaken) FROM photos")
    fun getNewestPhotoDate(): Flow<Long?>

    /**
     * Update photo metadata.
     */
    @Query("UPDATE photos SET displayName = :name, lastModified = :lastModified WHERE id = :id")
    suspend fun updatePhotoMetadata(id: Long, name: String, lastModified: Long)

    /**
     * Get duplicate photos (same size and date).
     */
    @Query("SELECT * FROM photos WHERE size = :size AND dateTaken = :dateTaken AND id != :excludeId")
    suspend fun findDuplicates(size: Long, dateTaken: Long, excludeId: Long): List<PhotoEntity>
}
