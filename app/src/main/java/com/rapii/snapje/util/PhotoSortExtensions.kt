package com.rapii.snapje.util

import com.rapii.snapje.data.PhotoItem
import com.rapii.snapje.data.PhotoSortOption

/**
 * Extension function to sort PhotoItems based on sort option.
 */
fun List<PhotoItem>.sortedByOption(option: PhotoSortOption): List<PhotoItem> {
    return when (option) {
        PhotoSortOption.DATE_TAKEN_DESC -> sortedByDescending { it.dateTaken }
        PhotoSortOption.DATE_TAKEN_ASC -> sortedBy { it.dateTaken }
        PhotoSortOption.NAME_ASC -> sortedBy { it.displayName.lowercase() }
        PhotoSortOption.NAME_DESC -> sortedByDescending { it.displayName.lowercase() }
        PhotoSortOption.SIZE_DESC -> sortedByDescending { it.size }
        PhotoSortOption.SIZE_ASC -> sortedBy { it.size }
    }
}
