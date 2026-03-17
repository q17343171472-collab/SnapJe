package com.rapii.snapje.util

/**
 * Application-wide constants.
 */
object Constants {
    
    // Trash/Recently Deleted
    const val TRASH_RETENTION_DAYS = 30
    
    // Photo Gallery Zoom
    const val ZOOM_MAX_SCALE = 5f
    const val ZOOM_MIN_SCALE = 1f
    const val ZOOM_THRESHOLD = 1.01f
    const val ZOOM_PAN_LIMIT = 500f
    
    // Grid Layout
    const val PHOTO_GRID_COLUMNS_PORTRAIT = 3
    const val PHOTO_GRID_COLUMNS_LANDSCAPE = 4
    const val CATEGORY_GRID_COLUMNS = 2
    
    // Photo Item Defaults
    const val DEFAULT_ASPECT_RATIO = 1f
    
    // Time Calculations (in milliseconds)
    const val MINUTE_MS = 60_000L
    const val HOUR_MS = 3_600_000L
    const val DAY_MS = 86_400_000L
    const val WEEK_MS = 604_800_000L
    
    // Cache
    const val CATEGORY_CACHE_VALIDITY_MS = 30_000L // 30 seconds

    // Image Loading (Coil)
    const val IMAGE_MEMORY_CACHE_PERCENT = 0.25 // 25% of available memory
    const val IMAGE_DISK_CACHE_PERCENT = 0.10 // 10% of disk cache
    const val IMAGE_FULLSCREEN_MEMORY_CACHE_PERCENT = 0.30 // 30% for fullscreen
    const val IMAGE_FULLSCREEN_DISK_CACHE_PERCENT = 0.15 // 15% for fullscreen
    const val IMAGE_MAX_DISK_CACHE_SIZE_MB = 100L // 100MB max disk cache
    const val IMAGE_THUMBNAIL_MEMORY_CACHE_PERCENT = 0.15 // 15% for thumbnails
    const val IMAGE_THUMBNAIL_DISK_CACHE_SIZE_MB = 50L // 50MB for thumbnails
    const val IMAGE_TRASH_MEMORY_CACHE_PERCENT = 0.10 // 10% for trash
    const val IMAGE_TRASH_DISK_CACHE_SIZE_MB = 30L // 30MB for trash

    // File Size Formatting
    const val KB = 1024L
    const val MB = 1024L * 1024L
    const val GB = 1024L * 1024L * 1024L
}
