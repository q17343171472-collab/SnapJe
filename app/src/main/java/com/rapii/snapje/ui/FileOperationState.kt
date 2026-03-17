package com.rapii.snapje.ui

import com.rapii.snapje.data.PhotoItem

/**
 * Manages file operation state for CategoryDetailScreen.
 * Extracted to reduce state complexity in main composable.
 */
class FileOperationState {
    
    /**
     * Currently selected photo for operations.
     */
    var selectedPhoto: PhotoItem? = null
        private set
    
    /**
     * Pending photo for delete operation (waiting for permission).
     */
    data class PendingDelete(val photo: PhotoItem)
    
    /**
     * Photos pending batch delete.
     */
    private val _pendingBatchDeletes = mutableListOf<PendingDelete>()
    val pendingBatchDeletes: List<PendingDelete> get() = _pendingBatchDeletes.toList()
    
    /**
     * Set the currently selected photo.
     */
    fun setSelectedPhoto(photo: PhotoItem?) {
        selectedPhoto = photo
    }
    
    /**
     * Add photo to pending batch delete.
     */
    fun addPendingDelete(photo: PhotoItem) {
        _pendingBatchDeletes.add(PendingDelete(photo))
    }
    
    /**
     * Add all photos to pending batch delete.
     */
    fun addAllPendingDeletes(photos: List<PhotoItem>) {
        _pendingBatchDeletes.addAll(photos.map { PendingDelete(it) })
    }
    
    /**
     * Clear all pending deletes.
     */
    fun clearPendingDeletes() {
        _pendingBatchDeletes.clear()
    }
    
    /**
     * Get all photos to confirm (for batch operations).
     */
    fun getPhotosToConfirm(): List<PhotoItem> {
        return _pendingBatchDeletes.map { it.photo }
    }
    
    /**
     * Reset state.
     */
    fun reset() {
        selectedPhoto = null
        clearPendingDeletes()
    }
}
