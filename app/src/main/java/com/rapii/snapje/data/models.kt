package com.rapii.snapje.data

import android.net.Uri

data class PhotoItem(
    val id: Long,
    val uri: Uri,
    val displayName: String,
    val dateTaken: Long,
    val bucketId: Long? = null,
    val bucketName: String? = null,
    val size: Long = 0,
    val mimeType: String = "image/jpeg",
    val width: Int = 0,
    val height: Int = 0
) {
    val aspectRatio: Float
        get() = if (height > 0) width.toFloat() / height.toFloat() else 1f
}

data class Album(
    val id: Long,
    val name: String,
    val coverPhotoUri: Uri? = null,
    val photoCount: Int = 0,
    val photos: List<PhotoItem> = emptyList()
) {
    val displayName: String
        get() = name.ifEmpty { "Unnamed Album" }
}

/**
 * Result of a search operation.
 */
data class SearchResult(
    val query: String,
    val photos: List<PhotoItem>
)
