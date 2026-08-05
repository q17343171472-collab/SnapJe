package com.rapii.snapje.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * 相册分组投影（用于首页分组展示）。
 */
data class VaultBucket(
    val bucketId: Long,
    val bucketName: String
)

/**
 * Data Access Object for vault (encrypted) photos.
 */
@Dao
interface VaultPhotoDao {

    /**
     * 获取全部加密照片（最新在前）。
     */
    @Query("SELECT * FROM vault_photos ORDER BY dateTaken DESC")
    fun getAllPhotos(): Flow<List<VaultPhotoEntity>>

    /**
     * 获取某个保险库相册的照片。
     */
    @Query("SELECT * FROM vault_photos WHERE bucketId = :bucketId ORDER BY dateTaken DESC")
    fun getPhotosByBucket(bucketId: Long): Flow<List<VaultPhotoEntity>>

    /**
     * 按名称搜索加密照片。
     */
    @Query("SELECT * FROM vault_photos WHERE originalName LIKE '%' || :query || '%' ORDER BY dateTaken DESC")
    fun searchPhotos(query: String): Flow<List<VaultPhotoEntity>>

    /**
     * 按 ID 获取单张照片。
     */
    @Query("SELECT * FROM vault_photos WHERE id = :id")
    suspend fun getPhotoById(id: String): VaultPhotoEntity?

    /**
     * 获取所有相册分组。
     */
    @Query("SELECT DISTINCT bucketId, bucketName FROM vault_photos")
    suspend fun getBuckets(): List<VaultBucket>

    /**
     * 获取全部照片（一次性，用于分组统计）。
     */
    @Query("SELECT * FROM vault_photos")
    suspend fun getAllPhotosOnce(): List<VaultPhotoEntity>

    /**
     * 插入或更新加密照片。
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPhoto(photo: VaultPhotoEntity)

    /**
     * 删除单张加密照片。
     */
    @Query("DELETE FROM vault_photos WHERE id = :id")
    suspend fun deletePhoto(id: String)

    /**
     * 批量删除加密照片。
     */
    @Query("DELETE FROM vault_photos WHERE id IN (:ids)")
    suspend fun deletePhotos(ids: List<String>)

    /**
     * 重命名（仅更新显示名，不改动加密文件）。
     */
    @Query("UPDATE vault_photos SET originalName = :newName WHERE id = :id")
    suspend fun renamePhoto(id: String, newName: String)

    /**
     * 批量移动照片到另一个分组（更新 bucketId / bucketName，加密文件不动）。
     */
    @Query("UPDATE vault_photos SET bucketId = :newBucketId, bucketName = :newBucketName WHERE id IN (:ids)")
    suspend fun movePhotos(ids: List<String>, newBucketId: Long, newBucketName: String)
}
