package com.rapii.snapje.data

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages delayed photo deletion with undo support.
 * Photos are marked for deletion and only actually deleted after a timeout.
 */
class TrashManager private constructor(context: Context) {

    private val context = context.applicationContext
    private val contentResolver: ContentResolver = this.context.contentResolver
    
    // Use SupervisorScope with proper exception handler to prevent cancellation dialogs
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main + CoroutineExceptionHandler { _, throwable ->
            // Silently handle cancellation exceptions to prevent dialog
            if (throwable !is CancellationException) {
                // Log other exceptions but don't show dialog
                android.util.Log.e("TrashManager", "Error in trash operation", throwable)
            }
        }
    )

    // Map of photo URIs to their pending deletion jobs
    private val pendingDeletions = ConcurrentHashMap<String, PendingDeletion>()
    
    data class PendingDeletion(
        val photo: PhotoItem,
        val job: Job,
        val callback: ((Boolean) -> Unit)? = null
    )
    
    companion object {
        @Volatile
        private var instance: TrashManager? = null
        
        fun getInstance(context: Context): TrashManager {
            return instance ?: synchronized(this) {
                instance ?: TrashManager(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }
    
    /**
     * Request deletion with undo support.
     * @param photo The photo to delete
     * @param timeoutMillis Time before actual deletion (default 5 seconds)
     * @param onDeleteResult Callback with result: true if deleted, false if undone
     * @return true if deletion was scheduled, false if immediate deletion failed
     */
    fun requestDelete(
        photo: PhotoItem,
        timeoutMillis: Long = 5000,
        onDeleteResult: ((Boolean) -> Unit)? = null
    ): Boolean {
        // Cancel any existing pending deletion for this photo
        cancelPendingDelete(photo.uri.toString())
        
        // Create delayed deletion job
        val job = scope.launch {
            delay(timeoutMillis)
            // Actually delete after timeout
            performActualDelete(photo, onDeleteResult)
        }
        
        pendingDeletions[photo.uri.toString()] = PendingDeletion(photo, job, onDeleteResult)
        return true
    }
    
    /**
     * Undo a pending deletion.
     * @param photoUri The URI of the photo to restore
     * @return true if undone successfully, false if not found or already deleted
     */
    fun undoDelete(photoUri: String): Boolean {
        val pending = pendingDeletions.remove(photoUri) ?: return false
        pending.job.cancel()
        pending.callback?.invoke(false) // Notify that deletion was undone
        return true
    }
    
    /**
     * Cancel all pending deletions.
     */
    fun cancelAllPending() {
        pendingDeletions.forEach { (_, pending) ->
            pending.job.cancel()
            pending.callback?.invoke(false)
        }
        pendingDeletions.clear()
    }
    
    /**
     * Check if a photo has a pending deletion.
     */
    fun hasPendingDelete(photoUri: String): Boolean {
        return pendingDeletions.containsKey(photoUri)
    }
    
    /**
     * Get count of pending deletions.
     */
    fun getPendingCount(): Int = pendingDeletions.size

    /**
     * Clean up resources when TrashManager is no longer needed.
     * Call this from Application.onTrimMemory() or similar lifecycle callback.
     */
    fun cleanup() {
        cancelAllPending()
        scope.cancel()
    }

    private suspend fun performActualDelete(
        photo: PhotoItem,
        callback: ((Boolean) -> Unit)?
    ) {
        withContext(Dispatchers.IO) {
            try {
                val result = contentResolver.delete(photo.uri, null, null)
                pendingDeletions.remove(photo.uri.toString())
                callback?.invoke(result > 0)
            } catch (e: Exception) {
                pendingDeletions.remove(photo.uri.toString())
                callback?.invoke(false)
            }
        }
    }
    
    private fun cancelPendingDelete(photoUri: String) {
        pendingDeletions.remove(photoUri)?.job?.cancel()
    }
    
    /**
     * For API 30+: Move to system trash instead of permanent delete.
     * This allows recovery from system trash later.
     */
    suspend fun moveToSystemTrash(photo: PhotoItem): FileOperationResult {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val pendingIntent = MediaStore.createTrashRequest(
                    contentResolver,
                    listOf(photo.uri),
                    true // Move to trash
                )
                FileOperationResult.NeedsPermission(pendingIntent)
            } catch (e: Exception) {
                FileOperationResult.Error("Cannot move to trash: ${e.message}")
            }
        } else {
            FileOperationResult.Error("System trash not available on this Android version")
        }
    }
}
