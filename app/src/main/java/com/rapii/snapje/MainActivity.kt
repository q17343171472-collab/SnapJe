package com.rapii.snapje

import android.os.Bundle
import android.view.Window
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.compose.rememberNavController
import com.rapii.snapje.navigation.GalleryNavGraph
import com.rapii.snapje.ui.theme.GalleryXTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Main entry point for GalleryX application.
 * Optimized for smooth transitions with hardware acceleration.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private var contentLoaded by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        // Install splash screen and keep it visible until content is loaded
        val splashScreen = installSplashScreen()

        // Keep the splash screen on-screen until the content is properly loaded
        // This prevents the "StandaloneCoroutine was cancelled" dialog
        splashScreen.setKeepOnScreenCondition { !contentLoaded }

        super.onCreate(savedInstanceState)
        
        // CRITICAL: Enable hardware acceleration for smooth transitions
        window.setFlags(
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        )
        
        // Enable edge-to-edge with proper transition support
        enableEdgeToEdge()

        setContent {
            contentLoaded = true
            GalleryXTheme {
                val navController = rememberNavController()
                GalleryNavGraph(navController = navController)
            }
        }
    }
}
