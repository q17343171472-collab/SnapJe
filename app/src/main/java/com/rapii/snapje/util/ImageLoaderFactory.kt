package com.rapii.snapje.util

import android.content.Context
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy

/**
 * Factory for creating optimized Coil ImageLoaders.
 * Provides consistent image caching configuration across the app.
 * 
 * Uses constants from Constants.kt for easy configuration.
 */
object ImageLoaderFactory {

    /**
     * Creates a standard image loader for thumbnail/grid loading.
     * Optimized for fast loading with moderate quality.
     */
    fun createStandardLoader(context: Context): ImageLoader {
        L.d("ImageLoaderFactory", "Creating standard image loader")
        
        return ImageLoader.Builder(context)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .memoryCache {
                MemoryCache.Builder(context)
                    .maxSizePercent(Constants.IMAGE_MEMORY_CACHE_PERCENT)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("coil_standard"))
                    .maxSizeBytes(Constants.IMAGE_MAX_DISK_CACHE_SIZE_MB * 1024 * 1024)
                    .build()
            }
            .crossfade(false)
            .respectCacheHeaders(false)
            .build()
    }

    /**
     * Creates a fullscreen image loader for high-quality viewing.
     * Optimized for maximum quality with larger cache.
     */
    fun createFullscreenLoader(context: Context): ImageLoader {
        L.d("ImageLoaderFactory", "Creating fullscreen image loader")
        
        return ImageLoader.Builder(context)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .memoryCache {
                MemoryCache.Builder(context)
                    .maxSizePercent(Constants.IMAGE_FULLSCREEN_MEMORY_CACHE_PERCENT)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("coil_fullscreen"))
                    .maxSizeBytes(Constants.IMAGE_MAX_DISK_CACHE_SIZE_MB * 1024 * 1024)
                    .build()
            }
            .crossfade(false) // Disable for instant full-res load
            .respectCacheHeaders(false)
            .build()
    }

    /**
     * Creates a thumbnail image loader for category/album grids.
     * Optimized for fast loading with aggressive caching.
     */
    fun createThumbnailLoader(context: Context): ImageLoader {
        L.d("ImageLoaderFactory", "Creating thumbnail image loader")
        
        return ImageLoader.Builder(context)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .memoryCache {
                MemoryCache.Builder(context)
                    .maxSizePercent(Constants.IMAGE_THUMBNAIL_MEMORY_CACHE_PERCENT)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("coil_thumbnail"))
                    .maxSizeBytes(Constants.IMAGE_THUMBNAIL_DISK_CACHE_SIZE_MB * 1024 * 1024)
                    .build()
            }
            .crossfade(false)
            .respectCacheHeaders(false)
            .build()
    }

    /**
     * Creates an image loader for trash/restored photos.
     * Uses cache paths when available.
     */
    fun createTrashLoader(context: Context): ImageLoader {
        L.d("ImageLoaderFactory", "Creating trash image loader")
        
        return ImageLoader.Builder(context)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .memoryCache {
                MemoryCache.Builder(context)
                    .maxSizePercent(Constants.IMAGE_TRASH_MEMORY_CACHE_PERCENT)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("coil_trash"))
                    .maxSizeBytes(Constants.IMAGE_TRASH_DISK_CACHE_SIZE_MB * 1024 * 1024)
                    .build()
            }
            .crossfade(false)
            .respectCacheHeaders(false)
            .build()
    }

    /**
     * 保险库专用加载器：只启用内存缓存，禁用磁盘缓存。
     *
     * 保险库照片的展示源是「解密后的临时文件」，若再被 Coil 写入磁盘缓存，
     * 会在 coil_* 目录留下不受管控的明文副本，违背保险库的隐私模型。
     */
    fun createVaultLoader(context: Context): ImageLoader {
        return ImageLoader.Builder(context)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.DISABLED)
            .memoryCache {
                MemoryCache.Builder(context)
                    .maxSizePercent(Constants.IMAGE_MEMORY_CACHE_PERCENT)
                    .build()
            }
            .crossfade(false)
            .respectCacheHeaders(false)
            .build()
    }

    /**
     * 清空全部 Coil 磁盘缓存（含默认单例的 image_cache），用于上锁 / 启动时清理明文残留。
     */
    fun clearAllDiskCaches(context: Context) {
        val cacheDir = context.cacheDir
        listOf(
            "coil_standard",
            "coil_fullscreen",
            "coil_thumbnail",
            "coil_trash",
            "image_cache"
        ).forEach { name ->
            runCatching { java.io.File(cacheDir, name).deleteRecursively() }
        }
    }
}
