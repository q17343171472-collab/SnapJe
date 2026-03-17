package com.rapii.snapje.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity representing a cached photo.
 * Used for offline caching and faster photo grid loading.
 * 
 * Indexed by bucketId for fast category filtering.
 */
@Entity(
    tableName = "photos",
    indices = [
        Index(value = ["bucketId"]),
        Index(value = ["dateTaken"]),
        Index(value = ["bucketId", "dateTaken"])
    ]
)
data class PhotoEntity(
    @PrimaryKey val id: Long,
    val uri: String,
    val displayName: String,
    val dateTaken: Long,
    val size: Long,
    val mimeType: String,
    val bucketId: Long,
    val bucketName: String?,
    val width: Int = 0,
    val height: Int = 0,
    val lastModified: Long = System.currentTimeMillis()
) {
    /**
     * Convert to domain PhotoItem model.
     */
    fun toPhotoItem(): com.rapii.snapje.data.PhotoItem {
        return com.rapii.snapje.data.PhotoItem(
            id = id,
            uri = android.net.Uri.parse(uri),
            displayName = displayName,
            dateTaken = dateTaken,
            size = size,
            mimeType = mimeType,
            bucketId = bucketId,
            bucketName = bucketName,
            width = width,
            height = height
        )
    }

    companion object {
        /**
         * Create from domain PhotoItem model.
         */
        fun fromPhotoItem(photo: com.rapii.snapje.data.PhotoItem): PhotoEntity {
            return PhotoEntity(
                id = photo.id,
                uri = photo.uri.toString(),
                displayName = photo.displayName,
                dateTaken = photo.dateTaken,
                size = photo.size,
                mimeType = photo.mimeType,
                bucketId = photo.bucketId ?: 0L,
                bucketName = photo.bucketName,
                width = photo.width,
                height = photo.height,
                lastModified = System.currentTimeMillis()
            )
        }
    }
}
