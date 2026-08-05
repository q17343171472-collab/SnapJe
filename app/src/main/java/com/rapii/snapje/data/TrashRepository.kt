package com.rapii.snapje.data

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import com.rapii.snapje.util.Constants
import com.rapii.snapje.util.L
import com.rapii.snapje.util.sortedByOption
import java.util.Date
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Data class representing a deleted photo in trash.
 */
data class TrashedPhoto(
    val id: Long,
    val originalUri: Uri,
    val displayName: String,
    val dateTaken: Long,
    val dateDeleted: Long,
    val size: Long,
    val mimeType: String,
    val originalPath: String? = null,
    val cachePath: String? = null
) {
    val daysRemaining: Int
        get() {
            val daysSinceDeleted = TimeUnit.MILLISECONDS.toDays(Date().time - dateDeleted)
            return (RETENTION_DAYS - daysSinceDeleted).coerceAtLeast(0).toInt()
        }

    val isExpired: Boolean
        get() = daysRemaining <= 0

    val thumbnailUri: Uri
        get() = if (!cachePath.isNullOrEmpty() && File(cachePath).exists()) {
            Uri.fromFile(File(cachePath))
        } else {
            originalUri
        }

    companion object {
        const val RETENTION_DAYS = Constants.TRASH_RETENTION_DAYS
    }
}

/**
 * Sort options for photos.
 */
enum class PhotoSortOption(val displayName: String) {
    DATE_TAKEN_DESC("最新优先"),
    DATE_TAKEN_ASC("最旧优先"),
    NAME_ASC("名称 (A-Z)"),
    NAME_DESC("名称 (Z-A)"),
    SIZE_DESC("最大优先"),
    SIZE_ASC("最小优先");

    companion object {
        val DEFAULT = DATE_TAKEN_DESC
    }
}

/**
 * Repository for managing trashed photos.
 * Uses application context to avoid memory leaks.
 *
 * DELETE FLOW FOR ANDROID 11+:
 * 1. Call moveToTrash() → returns NeedsPermission with PendingIntent
 * 2. Launch the PendingIntent via ActivityResultLauncher
 * 3. On RESULT_OK → call confirmTrash() to update local tracking
 * 4. On RESULT_CANCELED → call cancelTrash() to clean up
 */
@Singleton
class TrashRepository @Inject constructor(
    @ApplicationContext context: Context
) {

    private val context = context.applicationContext
    private val contentResolver: ContentResolver = this.context.contentResolver
    private val localTrashManager = LocalTrashManager(this.context)

    private val trashCacheDir: File by lazy {
        File(context.cacheDir, "trash_thumbnails").apply {
            if (!exists()) mkdirs()
        }
    }

    var currentSortOption: PhotoSortOption
        get() = localTrashManager.getSortOption()
        set(value) = localTrashManager.setSortOption(value)

    /**
     * STEP 1: Move a photo to trash.
     * For Android 11+ (API 30+): Uses MediaStore.createTrashRequest().
     * For Android 10 and below: Performs immediate delete.
     * 
     * Returns:
     * - NeedsPermission (API 30+): Caller must launch the pending intent
     * - Success (API 29-): Photo moved to trash immediately
     * - Error: Operation failed
     */
    suspend fun moveToTrash(photo: PhotoItem): FileOperationResult {
        return withContext(Dispatchers.IO) {
            try {
                // Copy to cache first (for thumbnail preview in trash)
                val cachePath = copyToCache(photo)
                
                // Log for debugging
                L.d("TrashRepository", "moveToTrash: URI=${photo.uri}, URI string=${photo.uri.toString()}, cachePath=$cachePath")

                // Android 11+ (API 30+): Use system trash with permission dialog
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val pendingIntent = MediaStore.createTrashRequest(
                        contentResolver,
                        listOf(photo.uri),
                        true // true = move to trash
                    )
                    // Cache the photo info - will be confirmed on RESULT_OK
                    pendingTrashCache[photo.uri.toString()] = cachePath
                    L.d("TrashRepository", "moveToTrash: Cached pending trash for URI=${photo.uri.toString()}, cache size=${pendingTrashCache.size}")
                    return@withContext FileOperationResult.NeedsPermission(pendingIntent)
                }

                // Android 10 and below: Immediate delete (no system trash)
                val result = contentResolver.delete(photo.uri, null, null)

                if (result > 0) {
                    localTrashManager.addToTrash(photo, cachePath)
                    FileOperationResult.Success("Photo moved to trash")
                } else {
                    cachePath?.let { File(it).delete() }
                    FileOperationResult.Error("Failed to move to trash")
                }
            } catch (e: Exception) {
                L.e("TrashRepository", "moveToTrash failed: ${e.message}", e)
                FileOperationResult.Error("Error: ${e.message}")
            }
        }
    }

    // Temporary cache for pending trash operations
    private val pendingTrashCache = mutableMapOf<String, String?>()

    /**
     * STEP 1: Move MULTIPLE photos to trash in a SINGLE operation.
     * CRITICAL: Pass ALL URIs to createTrashRequest at once - do NOT call individually.
     * This is required for Android 11+ batch delete to work correctly.
     *
     * For Android 11+ (API 30+): Uses MediaStore.createTrashRequest() with ALL URIs.
     * For Android 10 and below: Falls back to individual deletes.
     *
     * Returns:
     * - NeedsPermission (API 30+): Caller must launch the pending intent for ALL photos
     * - Success (API 29-): All photos moved to trash immediately
     * - Error: Operation failed
     */
    suspend fun moveToTrashBatch(photos: List<PhotoItem>): FileOperationResult {
        return withContext(Dispatchers.IO) {
            if (photos.isEmpty()) {
                return@withContext FileOperationResult.Error("No photos to delete")
            }

            try {
                // Copy all photos to cache first (for thumbnail preview in trash)
                val cachePaths = mutableMapOf<String, String?>()
                photos.forEach { photo ->
                    cachePaths[photo.uri.toString()] = copyToCache(photo)
                }

                // Android 11+ (API 30+): Use system trash with SINGLE permission dialog for ALL photos
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    // CRITICAL: Pass ALL URIs in ONE call - this is the root cause fix
                    val allUris = photos.map { it.uri }
                    val pendingIntent = MediaStore.createTrashRequest(
                        contentResolver,
                        allUris,  // ALL URIs passed at once
                        true // true = move to trash
                    )
                    // Cache ALL photo info - will be confirmed on RESULT_OK
                    photos.forEach { photo ->
                        pendingTrashCache[photo.uri.toString()] = cachePaths[photo.uri.toString()]
                    }
                    return@withContext FileOperationResult.NeedsPermission(pendingIntent)
                }

                // Android 10 and below: Individual deletes (no system trash)
                var successCount = 0
                var failedCount = 0

                photos.forEach { photo ->
                    val result = contentResolver.delete(photo.uri, null, null)
                    if (result > 0) {
                        localTrashManager.addToTrash(photo, cachePaths[photo.uri.toString()])
                        successCount++
                    } else {
                        cachePaths[photo.uri.toString()]?.let { File(it).delete() }
                        failedCount++
                    }
                }

                when {
                    successCount == photos.size -> FileOperationResult.Success("$successCount photos moved to trash")
                    failedCount == photos.size -> FileOperationResult.Error("Failed to move photos to trash")
                    else -> FileOperationResult.Error("$successCount moved, $failedCount failed")
                }
            } catch (e: Exception) {
                FileOperationResult.Error("Error: ${e.message}")
            }
        }
    }

    /**
     * STEP 2: Confirm trash operation after user grants permission (RESULT_OK).
     * Adds photo to local trash tracking.
     */
    fun confirmTrash(photo: PhotoItem) {
        val cachePath = pendingTrashCache.remove(photo.uri.toString())
        localTrashManager.addToTrash(photo, cachePath)
    }

    /**
     * STEP 2: Confirm BATCH trash operation after user grants permission (RESULT_OK).
     * Adds ALL photos to local trash tracking.
     */
    fun confirmTrashBatch(photos: List<PhotoItem>) {
        L.d("TrashRepository", "confirmTrashBatch: ${photos.size} photos")
        photos.forEach { photo ->
            val uriString = photo.uri.toString()
            val cachePath = pendingTrashCache.remove(uriString)
            L.d("TrashRepository", "confirmTrashBatch: URI=$uriString, cachePath=$cachePath, cache was found=${cachePath != null}")
            if (cachePath != null) {
                localTrashManager.addToTrash(photo, cachePath)
                L.d("TrashRepository", "confirmTrashBatch: Added to local trash: ${photo.displayName}")
            } else {
                L.e("TrashRepository", "confirmTrashBatch: No cache found for URI=$uriString, photo NOT added to trash!")
            }
        }
        L.d("TrashRepository", "confirmTrashBatch: Remaining cache size=${pendingTrashCache.size}")
    }

    /**
     * STEP 2b: Cancel trash operation (RESULT_CANCELED).
     * Cleans up cached thumbnail.
     */
    fun cancelTrash(photo: PhotoItem) {
        val cachePath = pendingTrashCache.remove(photo.uri.toString())
        cachePath?.let { File(it).delete() }
    }

    /**
     * STEP 2b: Cancel BATCH trash operation (RESULT_CANCELED).
     * Cleans up ALL cached thumbnails.
     */
    fun cancelTrashBatch(photos: List<PhotoItem>) {
        photos.forEach { photo ->
            val cachePath = pendingTrashCache.remove(photo.uri.toString())
            cachePath?.let { File(it).delete() }
        }
    }

    /**
     * Copy the photo to app cache before deletion.
     */
    private fun copyToCache(photo: PhotoItem): String? {
        return try {
            val cacheFile = File(trashCacheDir, "trash_${photo.id}_${photo.displayName}")

            contentResolver.openInputStream(photo.uri)?.use { input ->
                FileOutputStream(cacheFile).use { output ->
                    input.copyTo(output)
                }
            }

            if (cacheFile.exists() && cacheFile.length() > 0) {
                cacheFile.absolutePath
            } else null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Restore a photo from trash.
     */
    suspend fun restoreFromTrash(photo: TrashedPhoto): FileOperationResult {
        return withContext(Dispatchers.IO) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val pendingIntent = MediaStore.createTrashRequest(
                        contentResolver,
                        listOf(photo.originalUri),
                        false // false = restore from trash
                    )
                    return@withContext FileOperationResult.NeedsPermission(pendingIntent)
                } else {
                    localTrashManager.removeFromTrash(photo.id)
                    return@withContext FileOperationResult.Success("Photo restored")
                }
            } catch (e: Exception) {
                return@withContext FileOperationResult.Error("Error restoring: ${e.message}")
            }
        }
    }

    /**
     * Confirm restore after permission granted.
     */
    fun confirmRestore(photo: TrashedPhoto) {
        localTrashManager.removeFromTrash(photo.id)
    }

    /**
     * Cancel restore (user denied permission).
     */
    fun cancelRestore(photo: TrashedPhoto) {
        // No action needed - photo remains in trash
    }

    /**
     * Permanently delete a photo from trash.
     */
    suspend fun permanentDelete(photo: TrashedPhoto): FileOperationResult {
        return withContext(Dispatchers.IO) {
            try {
                photo.cachePath?.let { path ->
                    try {
                        File(path).delete()
                    } catch (e: Exception) {
                        L.w("TrashRepository", "Failed to delete cache file: ${e.message}")
                    }
                }

                try {
                    contentResolver.delete(photo.originalUri, null, null)
                } catch (e: Exception) {
                    L.w("TrashRepository", "Failed to delete from MediaStore (may already be deleted): ${e.message}")
                }

                localTrashManager.removeFromTrash(photo.id)
                FileOperationResult.Success("Photo permanently deleted")
            } catch (e: Exception) {
                FileOperationResult.Error("Error: ${e.message}")
            }
        }
    }

    /**
     * Empty the trash.
     */
    suspend fun emptyTrash(): FileOperationResult {
        return withContext(Dispatchers.IO) {
            try {
                val photos = localTrashManager.getTrashedPhotos()
                var deletedCount = 0

                photos.forEach { photo ->
                    try {
                        photo.cachePath?.let { path ->
                            try {
                                File(path).delete()
                            } catch (e: Exception) {
                                L.w("TrashRepository", "Failed to delete cache file: ${e.message}")
                            }
                        }

                        try {
                            contentResolver.delete(Uri.parse(photo.uri), null, null)
                        } catch (e: Exception) {
                            L.w("TrashRepository", "Failed to delete from MediaStore: ${e.message}")
                        }
                        deletedCount++
                    } catch (e: Exception) {
                        L.e("TrashRepository", "Error deleting photo ${photo.id}: ${e.message}")
                    }
                }

                localTrashManager.clearAll()
                FileOperationResult.Success("$deletedCount items deleted")
            } catch (e: Exception) {
                FileOperationResult.Error("Error emptying trash: ${e.message}")
            }
        }
    }

    /**
     * Get all trashed photos.
     */
    suspend fun loadTrashedPhotos(): List<TrashedPhoto> {
        return withContext(Dispatchers.IO) {
            cleanupExpiredItems()
            localTrashManager.getTrashedPhotos().map { it.toTrashedPhoto() }
        }
    }

    /**
     * Clean up expired items (older than 30 days).
     */
    suspend fun cleanupExpiredItems(): Int {
        return withContext(Dispatchers.IO) {
            localTrashManager.cleanupExpired { expiredPhoto ->
                expiredPhoto.cachePath?.let { path ->
                    try {
                        File(path).delete()
                    } catch (e: Exception) {
                        L.w("TrashRepository", "Failed to delete expired cache file: ${e.message}")
                    }
                }
            }.size
        }
    }

    fun getTrashSize(): Long = localTrashManager.getTotalSize()
    fun getTrashCount(): Int = localTrashManager.getCount()
}
