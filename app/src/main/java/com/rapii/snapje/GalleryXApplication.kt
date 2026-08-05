package com.rapii.snapje

import android.app.Application
import com.rapii.snapje.data.VaultRepository
import com.rapii.snapje.util.L
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Application class for SnapJe!
 * Initializes app-wide components and logging.
 */
@HiltAndroidApp
class GalleryXApplication : Application() {

    @Inject
    lateinit var vaultRepository: VaultRepository

    override fun onCreate() {
        super.onCreate()
        // Initialize logging for debug/release builds
        L.init()
        L.d("GalleryXApplication", "App started - version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")

        // 清理过期的临时解密文件（明文不长期落盘）
        runCatching { vaultRepository.cleanupStaleTempFiles() }
        // 清理 Coil 磁盘缓存中可能的历史明文残留
        runCatching { com.rapii.snapje.util.ImageLoaderFactory.clearAllDiskCaches(this) }
    }
}
