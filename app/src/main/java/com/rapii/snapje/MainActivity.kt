package com.rapii.snapje

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.navigation.compose.rememberNavController
import com.rapii.snapje.navigation.GalleryNavGraph
import com.rapii.snapje.ui.screens.AuthScreen
import com.rapii.snapje.ui.theme.GalleryXTheme
import com.rapii.snapje.util.ImageLoaderFactory
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Main entry point for SnapJe! (private vault gallery).
 *
 * 生物识别门（覆盖式上锁）：
 * - 导航树始终保持在组合中（这样 ActivityResult Launcher 的 key 不会丢失，
 *   系统相册选择 / 相机返回结果才能正常送达）。
 * - App 启动或退到后台（onStop 且非配置变更）时 [isUnlocked] 置 false，
 *   [AuthScreen] 作为全屏不透明层盖在最上层；指纹 / 面部验证通过后移除。
 * - FLAG_SECURE：禁止截图与最近任务预览，防止内容外泄。
 */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    private var contentLoaded by mutableStateOf(false)

    /** 是否已通过生物识别解锁（false 时 AuthScreen 全屏覆盖） */
    private var isUnlocked by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        // Install splash screen and keep it visible until content is loaded
        val splashScreen = installSplashScreen()

        // Keep the splash screen on-screen until the content is properly loaded
        splashScreen.setKeepOnScreenCondition { !contentLoaded }

        super.onCreate(savedInstanceState)

        // CRITICAL: Enable hardware acceleration for smooth transitions
        window.setFlags(
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        )

        // 隐私保护：禁止截图 / 最近任务缩略图显示保险库内容
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        // Enable edge-to-edge with proper transition support
        enableEdgeToEdge()

        setContent {
            contentLoaded = true
            GalleryXTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    // 导航树始终组合，保证 ActivityResult / 导入状态不丢失
                    val navController = rememberNavController()
                    GalleryNavGraph(navController = navController)

                    // 上锁时用全屏 AuthScreen 覆盖（盖在最上层）
                    if (!isUnlocked) {
                        AuthScreen(
                            onUnlocked = { isUnlocked = true },
                            onExit = { finish() }
                        )
                    }
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // App 退到后台即重新上锁（配置变更除外，避免旋转屏幕时重新验证）
        if (!isChangingConfigurations) {
            isUnlocked = false
            // 清理 Coil 磁盘缓存中的可能明文残留（保险库加载器本身已禁用磁盘缓存）
            runCatching { ImageLoaderFactory.clearAllDiskCaches(this) }
        }
    }
}
