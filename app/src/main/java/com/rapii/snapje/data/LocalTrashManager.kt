package com.rapii.snapje.data

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import com.rapii.snapje.util.Constants
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Date
import java.util.concurrent.TimeUnit

/**
 * Local trash manager that stores trashed photo info in SharedPreferences.
 * This is more reliable than system trash which has inconsistent behavior across devices.
 */
class LocalTrashManager(context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "trash_manager", 
        Context.MODE_PRIVATE
    )
    
    private val _trashedPhotos = MutableStateFlow<List<TrashedPhotoItem>>(emptyList())
    val trashedPhotos: StateFlow<List<TrashedPhotoItem>> = _trashedPhotos
    
    // Sort preference
    private val sortPrefs = context.getSharedPreferences("sort_prefs", Context.MODE_PRIVATE)
    
    fun getSortOption(): PhotoSortOption {
        val saved = sortPrefs.getString("sort_option", null)
        return saved?.let { PhotoSortOption.valueOf(it) } ?: PhotoSortOption.DEFAULT
    }
    
    fun setSortOption(option: PhotoSortOption) {
        sortPrefs.edit().putString("sort_option", option.name).apply()
    }
    
    init {
        loadTrashedPhotos()
    }
    
    data class TrashedPhotoItem(
        val id: Long,
        val uri: String,
        val displayName: String,
        val dateTaken: Long,
        val dateDeleted: Long,
        val size: Long,
        val mimeType: String,
        val cachePath: String? = null  // Path to cached thumbnail in app storage
    ) {
        val daysRemaining: Int
            get() {
                val daysSinceDeleted = TimeUnit.MILLISECONDS.toDays(Date().time - dateDeleted)
                return (Constants.TRASH_RETENTION_DAYS - daysSinceDeleted).coerceAtLeast(0).toInt()
            }
        
        fun toTrashedPhoto(): TrashedPhoto {
            return TrashedPhoto(
                id = id,
                originalUri = Uri.parse(uri),
                displayName = displayName,
                dateTaken = dateTaken,
                dateDeleted = dateDeleted,
                size = size,
                mimeType = mimeType,
                cachePath = cachePath
            )
        }
    }
    
    /**
     * Add a photo to the local trash tracking.
     * @param cachePath Path to cached thumbnail in app storage (needed for thumbnail after MediaStore deletion)
     */
    fun addToTrash(photo: PhotoItem, cachePath: String? = null) {
        val editor = prefs.edit()
        val key = "trash_${photo.id}"
        // Store cachePath as 8th field for backward compatibility
        val value = "${photo.id}|${photo.uri}|${photo.displayName}|${photo.dateTaken}|${System.currentTimeMillis()}|${photo.size}|${photo.mimeType}|${cachePath ?: ""}"
        editor.putString(key, value)
        editor.apply()
        
        loadTrashedPhotos()
        android.util.Log.d("LocalTrashManager", "Added to trash: ${photo.displayName}, cache: $cachePath")
    }
    
    /**
     * Remove a photo from trash tracking.
     */
    fun removeFromTrash(photoId: Long) {
        val editor = prefs.edit()
        editor.remove("trash_$photoId")
        editor.apply()
        
        loadTrashedPhotos()
        android.util.Log.d("LocalTrashManager", "Removed from trash: $photoId")
    }
    
    /**
     * Get all trashed photos.
     */
    fun getTrashedPhotos(): List<TrashedPhotoItem> {
        return _trashedPhotos.value
    }
    
    /**
     * Load trashed photos from SharedPreferences.
     */
    private fun loadTrashedPhotos() {
        val photos = mutableListOf<TrashedPhotoItem>()
        
        prefs.all.forEach { (key, value) ->
            if (key.startsWith("trash_") && value is String) {
                try {
                    val parts = value.split("|")
                    if (parts.size >= 7) {
                        photos.add(
                            TrashedPhotoItem(
                                id = parts[0].toLong(),
                                uri = parts[1],
                                displayName = parts[2],
                                dateTaken = parts[3].toLong(),
                                dateDeleted = parts[4].toLong(),
                                size = parts[5].toLong(),
                                mimeType = parts[6],
                                cachePath = if (parts.size >= 8 && parts[7].isNotEmpty()) parts[7] else null
                            )
                        )
                    }
                } catch (e: Exception) {
                    android.util.Log.e("LocalTrashManager", "Error parsing trash entry: $value", e)
                }
            }
        }
        
        // Sort by date deleted (newest first)
        photos.sortByDescending { it.dateDeleted }
        
        _trashedPhotos.value = photos
        android.util.Log.d("LocalTrashManager", "Loaded ${photos.size} trashed photos")
    }
    
    /**
     * Clean up expired items (older than 30 days).
     * @param onExpired Callback for each expired item (for cache cleanup)
     */
    fun cleanupExpired(onExpired: ((TrashedPhotoItem) -> Unit)? = null): List<Long> {
        val now = System.currentTimeMillis()
        val expiredIds = mutableListOf<Long>()
        val expiredPhotos = mutableListOf<TrashedPhotoItem>()
        
        _trashedPhotos.value.forEach { photo ->
            val daysSinceDeleted = TimeUnit.MILLISECONDS.toDays(now - photo.dateDeleted)
            if (daysSinceDeleted >= 30) {
                expiredIds.add(photo.id)
                expiredPhotos.add(photo)
            }
        }
        
        // Notify callback for cache cleanup
        expiredPhotos.forEach { onExpired?.invoke(it) }
        
        // Remove expired from prefs
        val editor = prefs.edit()
        expiredIds.forEach { id ->
            editor.remove("trash_$id")
        }
        editor.apply()
        
        if (expiredIds.isNotEmpty()) {
            loadTrashedPhotos()
        }
        
        return expiredIds
    }
    
    /**
     * Clear all trash entries.
     */
    fun clearAll() {
        val editor = prefs.edit()
        prefs.all.keys.filter { it.startsWith("trash_") }.forEach { key ->
            editor.remove(key)
        }
        editor.apply()
        
        _trashedPhotos.value = emptyList()
    }
    
    /**
     * Get total size of trashed items.
     */
    fun getTotalSize(): Long {
        return _trashedPhotos.value.sumOf { it.size }
    }
    
    /**
     * Get count of trashed items.
     */
    fun getCount(): Int {
        return _trashedPhotos.value.size
    }
}
