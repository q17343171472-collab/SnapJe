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
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.rapii.snapje.data.SettingsManager
import com.rapii.snapje.navigation.GalleryNavGraph
import com.rapii.snapje.ui.screens.AuthScreen
import com.rapii.snapje.ui.theme.GalleryXTheme
import com.rapii.snapje.util.ImageLoaderFactory
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Main entry point for SnapJe! (private vault gallery).
 *
 * PIN 密码门（覆盖式上锁）：
 * - 首次使用先设置 4-6 位数字密码，之后启动 / 从后台返回需输入密码解锁。
 * - 导航树始终保持在组合中（这样 ActivityResult Launcher 的 key 不会丢失，
 *   系统相册选择 / 相机返回结果才能正常送达）。
 * - [AuthScreen] 作为全屏不透明层盖在最上层；密码验证通过后移除。
 * - FLAG_SECURE：禁止截图与最近任务预览，防止内容外泄。
 */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    private var contentLoaded by mutableStateOf(false)

    /** 是否已解锁（false 时 AuthScreen 全屏覆盖） */
    private var isUnlocked by mutableStateOf(false)

    /** 是否已从本地读取到 PIN 状态（读取完成前不显示验证页，避免闪烁） */
    private var authReady by mutableStateOf(false)

    /** 是否已设置过 PIN 密码 */
    private var hasPin by mutableStateOf(false)

    /** 已设置的密码位数 */
    private var pinLength by mutableStateOf(4)

    /** 是否启用启动密码验证（默认关闭；设置中可开启） */
    private var pinEnabled by mutableStateOf(false)

    @Inject
    lateinit var settingsManager: SettingsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        // Install splash screen and keep it visible until content is loaded
        val splashScreen = installSplashScreen()

        // Keep the splash screen on-screen until the content is properly loaded
        splashScreen.setKeepOnScreenCondition { !contentLoaded }

        super.onCreate(savedInstanceState)

        // 读取 PIN 设置状态：是否已设置密码、密码位数、是否启用验证
        lifecycleScope.launch {
            hasPin = settingsManager.hasPin()
            pinLength = settingsManager.pinLength()
            pinEnabled = settingsManager.isPinEnabled()
            authReady = true
            // 用户已关闭密码验证：直接进入，不显示验证页
            if (!pinEnabled) {
                isUnlocked = true
            }
        }

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
                Box(modifier = Modifier.fillMaxSize()) {
                    // 导航树始终组合，保证 ActivityResult / 导入状态不丢失
                    val navController = rememberNavController()
                    GalleryNavGraph(navController = navController)

                    // 上锁时用全屏 AuthScreen 覆盖（盖在最上层）
                    if (!isUnlocked && authReady && pinEnabled) {
                        AuthScreen(
                            isFirstTimeSetup = !hasPin,
                            pinLength = pinLength,
                            onPinSet = { newPin ->
                                // 首次设置密码：保存并解锁
                                lifecycleScope.launch {
                                    settingsManager.setPin(newPin)
                                    pinLength = newPin.length
                                    hasPin = true
                                }
                                isUnlocked = true
                            },
                            onVerifyPin = { entered ->
                                settingsManager.verifyPin(entered)
                            },
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
        // App 退到后台即重新上锁（配置变更除外，避免旋转屏幕时重新验证）。
        // 仅当启用密码验证时上锁；关闭验证后不再弹密码。
        if (pinEnabled && !isChangingConfigurations) {
            isUnlocked = false
            // 清理 Coil 磁盘缓存中的可能明文残留（保险库加载器本身已禁用磁盘缓存）
            runCatching { ImageLoaderFactory.clearAllDiskCaches(this) }
        }
    }
}
