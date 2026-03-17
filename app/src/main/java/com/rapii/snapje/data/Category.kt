package com.rapii.snapje.data

import android.net.Uri
import com.rapii.snapje.util.Constants
import java.util.Date

/**
 * Represents a photo category (folder) with metadata.
 */
data class Category(
    val id: Long,                    // BUCKET_ID from MediaStore
    val name: String,                // BUCKET_DISPLAY_NAME or folder name
    val path: String,                // Full folder path
    val coverUris: List<Uri>,        // First 1-4 images for thumbnail collage
    val itemCount: Int,              // Number of photos in this folder
    val lastModified: Long,          // Last modified timestamp
    val isPinned: Boolean = false,   // Pinned to top
    val isHidden: Boolean = false    // Hidden from view
) {
    val displayName: String
        get() = name.ifEmpty { "Unnamed Folder" }
    
    val formattedItemCount: String
        get() = when (itemCount) {
            0 -> "Empty"
            1 -> "1 photo"
            else -> "$itemCount photos"
        }
    
    val formattedLastModified: String
        get() {
            val now = System.currentTimeMillis()
            val diff = now - lastModified
            
            return when {
                diff < Constants.MINUTE_MS -> "Just now"
                diff < Constants.HOUR_MS -> "${diff / Constants.MINUTE_MS} min ago"
                diff < Constants.DAY_MS -> "${diff / Constants.HOUR_MS} hours ago"
                diff < Constants.WEEK_MS -> "${diff / Constants.DAY_MS} days ago"
                else -> Date(lastModified).toString().substring(0, 10)
            }
        }
    
    companion object {
        /**
         * Common category names and their display names
         */
        val commonCategories = mapOf(
            "Camera" to "Camera",
            "Screenshots" to "Screenshots",
            "WhatsApp" to "WhatsApp",
            "Download" to "Downloads",
            "Instagram" to "Instagram",
            "Telegram" to "Telegram",
            "Facebook" to "Facebook",
            "DCIM" to "Camera Roll",
            "Pictures" to "Pictures",
            "Movies" to "Videos",
            "Videos" to "Videos"
        )
        
        /**
         * Get display name for a folder name
         */
        fun getDisplayName(folderName: String): String {
            return commonCategories[folderName] ?: folderName
        }
        
        /**
         * Sort categories by different criteria
         */
        fun sortCategories(
            categories: List<Category>,
            sortBy: SortBy = SortBy.RECENT
        ): List<Category> {
            return when (sortBy) {
                SortBy.RECENT -> categories.sortedByDescending { it.lastModified }
                SortBy.NAME -> categories.sortedBy { it.name.lowercase() }
                SortBy.COUNT -> categories.sortedByDescending { it.itemCount }
                SortBy.PINNED -> categories.sortedWith(
                    compareByDescending<Category> { it.isPinned }
                        .thenByDescending { it.lastModified }
                )
            }
        }
    }
}

enum class SortBy {
    RECENT,     // Sort by last modified (default)
    NAME,       // Sort alphabetically
    COUNT,      // Sort by item count
    PINNED      // Sort by pinned status
}