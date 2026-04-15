package com.rapii.snapje.data

import android.app.PendingIntent
import android.app.RecoverableSecurityException
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.content.FileProvider
import com.rapii.snapje.util.Constants
import com.rapii.snapje.util.L
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Result of a file operation.
 */
sealed class FileOperationResult {
    data class Success(val message: String) : FileOperationResult()
    data class Error(val message: String) : FileOperationResult()
    data class NeedsPermission(val pendingIntent: PendingIntent) : FileOperationResult()
}

/**
 * Types of file operations available.
 */
enum class FileOperationType {
    DELETE, COPY, RENAME, MOVE, SHARE, INFO, CROP, HIDE
}

/**
 * Photo information data class.
 */
data class PhotoInfo(
    val name: String,
    val path: String,
    val size: String,
    val dimensions: String,
    val dateTaken: String
)

/**
 * Manager for file operations on photos.
 * Uses application context to avoid memory leaks.
 */
class FileOperations(context: Context) {

    private val context = context.applicationContext
    private val contentResolver: ContentResolver = this.context.contentResolver

    /**
     * Delete a photo - handles Android 10+ permission requirements.
     */
    suspend fun deletePhoto(photo: PhotoItem, isRetry: Boolean = false): FileOperationResult {
        val uri = photo.uri

        // On API 30+, if this is a retry and we're still getting errors,
        // the permission might not have been granted.
        if (isRetry && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return FileOperationResult.Error("Delete permission required. Please grant permission in the dialog.")
        }

        // Proactive check: Try to open the file for write to see if we have permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (!checkWriteAccess(uri)) {
                return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    createDeleteRequest(photo)
                } else {
                    createDeleteRequestApi29(photo)
                }
            }
        }

        return try {
            val result = runInterruptible(Dispatchers.IO) {
                contentResolver.delete(uri, null, null)
            }

            when {
                result > 0 -> FileOperationResult.Success("Photo deleted successfully")
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> createDeleteRequest(photo)
                Build.VERSION.SDK_INT == Build.VERSION_CODES.Q -> createDeleteRequestApi29(photo)
                else -> FileOperationResult.Error("Failed to delete photo")
            }
        } catch (e: SecurityException) {
            handleSecurityException(e, photo)
        } catch (e: RuntimeException) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && e is RecoverableSecurityException) {
                FileOperationResult.NeedsPermission(e.userAction.actionIntent)
            } else if (e.javaClass.name.contains("RecoverableSecurityException")) {
                tryHandleAsRecoverableSecurityException(e)
            } else {
                FileOperationResult.Error("Error: ${e.message}")
            }
        } catch (e: Exception) {
            FileOperationResult.Error("Error: ${e.message}")
        }
    }

    /**
     * Check if we have write access to a URI.
     */
    private fun checkWriteAccess(uri: Uri): Boolean {
        return try {
            contentResolver.openFileDescriptor(uri, "w")?.use { pfd ->
                pfd.close()
                true
            } ?: false
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Try to handle RecoverableSecurityException via reflection.
     */
    private fun tryHandleAsRecoverableSecurityException(e: RuntimeException): FileOperationResult {
        return try {
            val userActionMethod = e.javaClass.getMethod("getUserAction")
            val userAction = userActionMethod.invoke(e)
            val actionIntentMethod = userAction?.javaClass?.getMethod("getActionIntent")
            val actionIntent = actionIntentMethod?.invoke(userAction)
            
            // Explicit type check instead of unsafe cast
            if (actionIntent is PendingIntent) {
                FileOperationResult.NeedsPermission(actionIntent)
            } else {
                L.e("FileOperations", "RecoverableSecurityException handling failed: unexpected type")
                FileOperationResult.Error("Permission handling failed. Please try again.")
            }
        } catch (reflEx: Exception) {
            L.e("FileOperations", "Reflection failed for RecoverableSecurityException", reflEx)
            FileOperationResult.Error("Permission handling not available on this device")
        }
    }

    /**
     * Handle SecurityException based on API level.
     */
    private fun handleSecurityException(e: SecurityException, photo: PhotoItem): FileOperationResult {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> createDeleteRequest(photo)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && e is RecoverableSecurityException -> {
                FileOperationResult.NeedsPermission(e.userAction.actionIntent)
            }
            else -> FileOperationResult.Error("Permission denied. Grant storage access in Settings.")
        }
    }

    /**
     * Create a delete request for Android 11+ (API 30+).
     */
    private fun createDeleteRequest(photo: PhotoItem): FileOperationResult {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return try {
                val pendingIntent = MediaStore.createDeleteRequest(contentResolver, listOf(photo.uri))
                FileOperationResult.NeedsPermission(pendingIntent)
            } catch (e: Exception) {
                FileOperationResult.Error("Delete not available: ${e.message}")
            }
        } else if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
            return createDeleteRequestApi29(photo)
        }
        return FileOperationResult.Error("Delete not supported on this Android version")
    }

    /**
     * Create a write request for Android 11+ (API 30+).
     */
    private fun createWriteRequest(uris: List<Uri>): FileOperationResult {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return try {
                val pendingIntent = MediaStore.createWriteRequest(contentResolver, uris)
                FileOperationResult.NeedsPermission(pendingIntent)
            } catch (e: Exception) {
                FileOperationResult.Error("Write request failed: ${e.message}")
            }
        }
        return FileOperationResult.Error("Write request not supported on this API level")
    }

    /**
     * Create a delete request for API 29 (Android 10).
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun createDeleteRequestApi29(photo: PhotoItem): FileOperationResult {
        return try {
            contentResolver.openFileDescriptor(photo.uri, "w")?.use { pfd ->
                pfd.close()
                val rows = contentResolver.delete(photo.uri, null, null)
                if (rows > 0) {
                    FileOperationResult.Success("Photo deleted")
                } else {
                    FileOperationResult.Error("Delete returned 0 rows")
                }
            } ?: FileOperationResult.Error("Could not open file")
        } catch (e: SecurityException) {
            if (e is RecoverableSecurityException) {
                FileOperationResult.NeedsPermission(e.userAction.actionIntent)
            } else {
                FileOperationResult.Error("Permission denied")
            }
        } catch (e: Exception) {
            FileOperationResult.Error("Delete not available on this device")
        }
    }

    /**
     * Rename a photo.
     */
    suspend fun renamePhoto(photo: PhotoItem, newName: String): FileOperationResult = withContext(Dispatchers.IO) {
        try {
            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, newName)
            }

            val rowsUpdated = contentResolver.update(photo.uri, contentValues, null, null)

            if (rowsUpdated > 0) {
                FileOperationResult.Success("Renamed to $newName")
            } else {
                FileOperationResult.Error("Failed to rename photo")
            }
        } catch (e: SecurityException) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                createWriteRequest(listOf(photo.uri))
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && e is RecoverableSecurityException) {
                FileOperationResult.NeedsPermission(e.userAction.actionIntent)
            } else {
                FileOperationResult.Error("Permission denied for rename")
            }
        } catch (e: RuntimeException) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && e is RecoverableSecurityException) {
                FileOperationResult.NeedsPermission(e.userAction.actionIntent)
            } else if (e.javaClass.name.contains("RecoverableSecurityException")) {
                tryHandleAsRecoverableSecurityException(e)
            } else {
                FileOperationResult.Error("Error: ${e.message}")
            }
        } catch (e: Exception) {
            FileOperationResult.Error("Error: ${e.message}")
        }
    }

    /**
     * Copy a photo to a destination folder.
     */
    suspend fun copyPhoto(photo: PhotoItem, destinationBucketId: Long): FileOperationResult = withContext(Dispatchers.IO) {
        try {
            val sourceUri = photo.uri

            if (sourceUri.scheme != "content" && sourceUri.scheme != "file") {
                return@withContext FileOperationResult.Error(
                    "Unsupported URI type: ${sourceUri.scheme}"
                )
            }

            val projection = arrayOf(
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.MIME_TYPE
            )

            var fileName = "copy_${photo.displayName}"
            var mimeType = "image/jpeg"

            contentResolver.query(sourceUri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME)
                    val mimeIndex = cursor.getColumnIndex(MediaStore.Images.Media.MIME_TYPE)

                    if (nameIndex >= 0) cursor.getString(nameIndex)?.let { fileName = "copy_$it" }
                    if (mimeIndex >= 0) cursor.getString(mimeIndex)?.let { mimeType = it }
                }
            }

            // Get the destination bucket's relative path
            val destProjection = arrayOf(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
            val destSelection = "${MediaStore.Images.Media.BUCKET_ID} = ?"
            val destSelectionArgs = arrayOf(destinationBucketId.toString())
            
            var destBucketName = "Pictures"
            contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                destProjection,
                destSelection,
                destSelectionArgs,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val bucketIndex = cursor.getColumnIndex(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
                    if (bucketIndex >= 0) {
                        destBucketName = cursor.getString(bucketIndex) ?: "Pictures"
                    }
                }
            }

            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, mimeType)
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/$destBucketName")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }

            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }

            val destUri = contentResolver.insert(collection, contentValues)
                ?: return@withContext FileOperationResult.Error("Failed to create destination file")

            val copyResult = try {
                contentResolver.openInputStream(sourceUri)?.use { input ->
                    contentResolver.openOutputStream(destUri)?.use { output ->
                        input.copyTo(output)
                    }
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                    contentResolver.update(destUri, contentValues, null, null)
                }

                FileOperationResult.Success("Photo copied to $destBucketName")
            } catch (e: Exception) {
                contentResolver.delete(destUri, null, null)
                FileOperationResult.Error("Failed to copy data: ${e.message}")
            }

            copyResult
        } catch (e: Exception) {
            FileOperationResult.Error("Error: ${e.message}")
        }
    }

    /**
     * Move a photo.
     */
    suspend fun movePhoto(photo: PhotoItem, destinationBucketId: Long): FileOperationResult {
        val copyResult = copyPhoto(photo, destinationBucketId)
        return if (copyResult is FileOperationResult.Success) {
            deletePhoto(photo)
        } else {
            copyResult
        }
    }

    /**
     * Share a photo.
     */
    fun sharePhoto(photo: PhotoItem) {
        try {
            // Copy photo to cache for sharing via FileProvider
            val cacheFile = File(context.cacheDir, "shared_${photo.id}_${photo.displayName}")
            
            // Copy photo to cache file
            contentResolver.openInputStream(photo.uri)?.use { input ->
                cacheFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            
            // Get FileProvider URI
            val shareUri = androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                cacheFile
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = photo.mimeType.ifEmpty { "image/*" }
                putExtra(Intent.EXTRA_STREAM, shareUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(shareIntent, "Share photo").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(chooser)
        } catch (e: Exception) {
            L.e("FileOperations", "Failed to share photo: ${e.message}", e)
            // Show error toast on Main thread
            runCatching {
                Toast.makeText(context.applicationContext, "Failed to share: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * Get photo info/details.
     */
    suspend fun getPhotoInfo(photo: PhotoItem): PhotoInfo = withContext(Dispatchers.IO) {
        var info = PhotoInfo(
            name = photo.displayName,
            path = photo.uri.toString(),
            size = "Unknown",
            dimensions = "Unknown",
            dateTaken = photo.dateTaken.toString()
        )

        try {
            val projection = arrayOf(
                MediaStore.Images.Media.SIZE,
                MediaStore.Images.Media.WIDTH,
                MediaStore.Images.Media.HEIGHT,
                MediaStore.Images.Media.DATA
            )

            contentResolver.query(photo.uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val size = cursor.getLong(0)
                    val width = cursor.getInt(1)
                    val height = cursor.getInt(2)
                    val path = cursor.getString(3)

                    info = info.copy(
                        size = formatFileSize(size),
                        dimensions = "$width x $height",
                        path = path ?: photo.uri.toString()
                    )
                }
            }
        } catch (e: Exception) {
            // Use defaults
        }

        info
    }

    /**
     * Format file size to human readable.
     */
    private fun formatFileSize(size: Long): String {
        return when {
            size < Constants.KB -> "$size B"
            size < Constants.MB -> "${size / Constants.KB} KB"
            size < Constants.GB -> "${size / Constants.MB} MB"
            else -> "${size / Constants.GB} GB"
        }
    }
}
