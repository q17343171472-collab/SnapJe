package com.rapii.snapje

import android.app.Application
import com.rapii.snapje.util.L
import dagger.hilt.android.HiltAndroidApp

/**
 * Application class for SnapJe!
 * Initializes app-wide components and logging.
 */
@HiltAndroidApp
class GalleryXApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // Initialize logging for debug/release builds
        L.init()
        L.d("GalleryXApplication", "App started - version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
    }
}
