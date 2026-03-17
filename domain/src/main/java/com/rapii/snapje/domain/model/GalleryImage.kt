package com.rapii.snapje.domain.model

import android.net.Uri

data class GalleryImage(
    val id: Long,
    val uri: Uri,
    val thumbnailUri: Uri,
    val displayName: String,
    val isVideo: Boolean = false
)
