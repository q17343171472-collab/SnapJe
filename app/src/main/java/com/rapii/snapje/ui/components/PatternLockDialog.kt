package com.rapii.snapje.ui.components

import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.fragment.app.FragmentActivity
import com.rapii.snapje.util.BiometricAuthManager

/**
 * 生物识别确认对话框（指纹 / 面部）。
 *
 * 替换了原来的硬编码 PIN（\"1234\"）锁：删除等敏感操作前要求指纹 / 面部验证。
 * [correctPin] 参数为兼容旧调用保留，不再使用。
 */
@Composable
fun PatternLockDialog(
    onDismiss: () -> Unit,
    onUnlock: () -> Unit,
    title: String = "验证指纹以确认",
    @Suppress("UNUSED_PARAMETER") correctPin: String = "1234"
) {
    val context = LocalContext.current
    val fragmentActivity = remember { context as? FragmentActivity }

    var isAuthenticating by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun startAuthentication() {
        val activity = fragmentActivity ?: return
        isAuthenticating = true
        errorMessage = null
        BiometricAuthManager.authenticate(
            activity = activity,
            title = title,
            subtitle = "验证指纹或面部以继续",
            onSuccess = {
                isAuthenticating = false
                onUnlock()
            },
            onError = { errorCode, message ->
                isAuthenticating = false
                if (errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                    errorCode == BiometricPrompt.ERROR_USER_CANCELED
                ) {
                    errorMessage = "验证已取消"
                } else if (errorCode == -1) {
                    errorMessage = "验证失败，请重试"
                } else {
                    errorMessage = message
                }
            }
        )
    }

    // 打开即自动弹出生物识别
    LaunchedEffect(fragmentActivity) {
        startAuthentication()
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .width(300.dp)
                .padding(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Fingerprint,
                    contentDescription = "Fingerprint",
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Text(
                    text = "验证指纹或面部以继续",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                errorMessage?.let { message ->
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                }

                Button(
                    onClick = { startAuthentication() },
                    enabled = !isAuthenticating,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (isAuthenticating) "验证中…" else "重新验证")
                }

                Spacer(modifier = Modifier.height(8.dp))

                TextButton(onClick = onDismiss) {
                    Text("取消")
                }
            }
        }
    }
}
