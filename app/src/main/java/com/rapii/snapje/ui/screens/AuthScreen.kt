package com.rapii.snapje.ui.screens

import androidx.activity.compose.BackHandler
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import android.content.Intent
import android.provider.Settings
import com.rapii.snapje.R
import com.rapii.snapje.util.BiometricAuthManager
import com.rapii.snapje.util.L
import kotlinx.coroutines.delay

/**
 * 生物识别验证启动页。
 * App 启动 / 从后台返回时展示；只有指纹 / 面部验证通过后才回调 [onUnlocked] 放行。
 * 验证失败或用户取消则通过 [onExit] 退出 App。
 */
@Composable
fun AuthScreen(
    onUnlocked: () -> Unit,
    onSkip: () -> Unit = {},
    onExit: () -> Unit
) {
    val context = LocalContext.current
    val fragmentActivity = remember { context as? FragmentActivity }
    // 生物识别状态码：BIOMETRIC_SUCCESS=已录入可用；BIOMETRIC_ERROR_NONE_ENROLLED=有硬件但未录入；其他=无硬件/不可用
    val bioAvailability = remember(fragmentActivity) {
        fragmentActivity?.let { BiometricAuthManager.getAvailability(it) }
            ?: BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE
    }
    val hasEnrolledBiometric = bioAvailability == BiometricManager.BIOMETRIC_SUCCESS
    val hasHardwareButNotEnrolled = bioAvailability == BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED

    var isAuthenticating by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    // 防止冷启动时重复自动弹指纹（只自动尝试一次，之后由用户手动点按钮）
    var hasAutoAttempted by remember { mutableStateOf(false) }

    // 预解析字符串（局部函数 / 回调内不能调用 @Composable stringResource）
    val appName = stringResource(R.string.app_name)
    val authRequired = stringResource(R.string.auth_required)
    val authDescription = stringResource(R.string.auth_description)
    val authCancelled = stringResource(R.string.auth_cancelled)
    val authFailedRetry = stringResource(R.string.auth_failed_retry)

    fun startAuthentication() {
        val activity = fragmentActivity ?: return
        isAuthenticating = true
        errorMessage = null
        BiometricAuthManager.authenticate(
            activity = activity,
            title = appName,
            subtitle = authRequired,
            description = authDescription,
            onSuccess = {
                isAuthenticating = false
                L.d("AuthScreen", "Biometric authentication succeeded")
                onUnlocked()
            },
            onError = { errorCode, message ->
                isAuthenticating = false
                L.d("AuthScreen", "Biometric auth error: code=$errorCode msg=$message")
                if (errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                    errorCode == BiometricPrompt.ERROR_USER_CANCELED
                ) {
                    errorMessage = authCancelled
                } else if (errorCode == -1) {
                    errorMessage = authFailedRetry
                } else {
                    errorMessage = message
                }
            }
        )
    }

    // 安全地触发指纹：某些设备冷启动时 Fragment 未就绪，
    // 立即调用 BiometricPrompt 会抛 IllegalStateException 导致闪退，
    // 这里 catch 住并回退为手动按钮模式。
    fun safeAuthenticate() {
        runCatching { startAuthentication() }.onFailure { e ->
            isAuthenticating = false
            errorMessage = e.message ?: "指纹验证无法启动，请点击按钮重试"
            L.e("AuthScreen", "BiometricPrompt launch failed", e)
        }
    }

    // 锁定态按返回键直接退出 App
    BackHandler { onExit() }

    // 首次进入自动弹出生物识别（仅在前台 RESUMED 状态，避免退后台时触发）。
    // 延迟 600ms 等窗口/ Fragment 就绪后再弹，避免冷启动时崩溃。
    val lifecycleOwner = LocalLifecycleOwner.current
    val lifecycleState by lifecycleOwner.lifecycle.currentStateAsState()
    LaunchedEffect(lifecycleState, fragmentActivity, hasEnrolledBiometric) {
        if (lifecycleState == Lifecycle.State.RESUMED && hasEnrolledBiometric && !hasAutoAttempted) {
            hasAutoAttempted = true
            delay(600)
            safeAuthenticate()
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = appName,
                style = MaterialTheme.typography.headlineMedium
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (hasEnrolledBiometric) {
                Text(
                    text = authRequired,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(32.dp))

                Icon(
                    imageVector = Icons.Default.Fingerprint,
                    contentDescription = null,
                    modifier = Modifier.size(96.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(32.dp))

                errorMessage?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                }

                Button(
                    onClick = { safeAuthenticate() },
                    enabled = !isAuthenticating
                ) {
                    Text(
                        if (isAuthenticating) {
                            stringResource(R.string.auth_waiting)
                        } else {
                            stringResource(R.string.auth_retry)
                        }
                    )
                }
            } else {
                if (hasHardwareButNotEnrolled) {
                    // 手机有指纹/面部功能，但用户还没在系统里录入
                    Text(
                        text = "检测到您的手机支持指纹/面部解锁，\n但您还没有在手机系统里录入指纹或面部。\n\n请先点击下方按钮去录入，录入完成后\n返回本 App 即可使用验证功能。",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(onClick = {
                        // 打开系统生物识别录入界面
                        runCatching {
                            context.startActivity(Intent(Settings.ACTION_BIOMETRIC_ENROLL))
                        }.onFailure {
                            runCatching {
                                context.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS))
                            }
                        }
                    }) {
                        Text("去设置指纹/面部")
                    }
                } else {
                    // 设备完全没有指纹/面部硬件，或不可用
                    Text(
                        text = "当前设备不支持指纹/面部解锁，\n无法使用验证功能。",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(onClick = onSkip) {
                    Text("跳过验证，直接进入")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            TextButton(onClick = onExit) {
                Text(stringResource(R.string.exit_app))
            }
        }
    }
}
