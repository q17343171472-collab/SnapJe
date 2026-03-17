package com.rapii.snapje.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity representing a cached category/folder.
 * Used for offline caching and faster app startup.
 */
@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val id: Long,
    val name: String,
    val path: String,
    val photoCount: Int,
    val coverUri: String?,
    val isPinned: Boolean = false,
    val lastModified: Long = System.currentTimeMillis(),
    val sortBy: String = "RECENT"
) {
    /**
     * Convert to domain Category model.
     */
    fun toCategory(): com.rapii.snapje.data.Category {
        return com.rapii.snapje.data.Category(
            id = id,
            name = name,
            path = path,
            coverUris = listOfNotNull(coverUri?.let { android.net.Uri.parse(it) }),
            itemCount = photoCount,
            lastModified = lastModified,
            isPinned = isPinned
        )
    }

    companion object {
        /**
         * Create from domain Category model.
         */
        fun fromCategory(category: com.rapii.snapje.data.Category): CategoryEntity {
            return CategoryEntity(
                id = category.id,
                name = category.name,
                path = category.path,
                photoCount = category.itemCount,
                coverUri = category.coverUris.firstOrNull()?.toString(),
                isPinned = category.isPinned,
                lastModified = category.lastModified,
                sortBy = "RECENT"
            )
        }
    }
}
