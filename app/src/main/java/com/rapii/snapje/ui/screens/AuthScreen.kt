package com.rapii.snapje.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rapii.snapje.R
import com.rapii.snapje.util.L

/**
 * PIN 密码锁屏页。
 *
 * - 首次使用（isFirstTimeSetup=true）：设置 4-6 位数字密码（输入两次确认），完成后回调 [onPinSet]。
 * - 之后启动 / 从后台返回：输入密码验证，输入满 [pinLength] 位自动校验，
 *   正确回调 [onUnlocked]，错误提示并清空重输。
 * - 密码以加盐哈希存储于本地（见 SettingsManager），不保存明文。
 */
@Composable
fun AuthScreen(
    isFirstTimeSetup: Boolean,
    pinLength: Int = 4,
    onPinSet: (String) -> Unit,
    onVerifyPin: suspend (String) -> Boolean,
    onUnlocked: () -> Unit,
    onExit: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var isConfirming by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val title = when {
        !isFirstTimeSetup -> "输入密码"
        isConfirming -> "再次输入密码"
        else -> "设置密码"
    }
    val subtitle = when {
        !isFirstTimeSetup -> "请输入 $pinLength 位数字密码"
        else -> "请设置 $pinLength 位数字密码"
    }

    fun currentInput(): String = if (isFirstTimeSetup && isConfirming) confirmPin else pin

    fun setCurrentInput(value: String) {
        if (isFirstTimeSetup && isConfirming) confirmPin = value else pin = value
    }

    fun onDigit(digit: Char) {
        errorMessage = null
        val cur = currentInput()
        if (cur.length >= pinLength) return
        setCurrentInput(cur + digit)
    }

    fun onDelete() {
        errorMessage = null
        setCurrentInput(currentInput().dropLast(1))
    }

    // 首次设置：第一步输入满 pinLength 位后进入确认步骤
    LaunchedEffect(pin) {
        if (isFirstTimeSetup && !isConfirming && pin.length >= pinLength) {
            isConfirming = true
        }
    }

    // 首次设置：确认输入满位后比对
    LaunchedEffect(confirmPin) {
        if (isFirstTimeSetup && isConfirming && confirmPin.length >= pinLength) {
            if (confirmPin == pin) {
                L.d("AuthScreen", "PIN setup confirmed")
                onPinSet(confirmPin)
            } else {
                errorMessage = "两次输入的密码不一致，请重新设置"
                pin = ""
                confirmPin = ""
                isConfirming = false
            }
        }
    }

    // 验证模式：输入满 pinLength 位自动校验
    LaunchedEffect(pin) {
        if (!isFirstTimeSetup && pin.length >= pinLength) {
            if (onVerifyPin(pin)) {
                L.d("AuthScreen", "PIN verified, unlocked")
                onUnlocked()
            } else {
                errorMessage = "密码错误，请重试"
                pin = ""
            }
        }
    }

    // 锁定态按返回键直接退出 App
    BackHandler { onExit() }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            // 圆点指示器
            PinDots(count = currentInput().length, total = pinLength)

            // 错误提示
            errorMessage?.let { message ->
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(48.dp))

            // 数字键盘
            PinKeypad(
                pinLength = pinLength,
                onDigit = ::onDigit,
                onDelete = ::onDelete
            )

            Spacer(modifier = Modifier.height(24.dp))
            TextButton(onClick = onExit) {
                Text("退出")
            }
        }
    }
}

/**
 * 密码位数圆点指示器。
 */
@Composable
private fun PinDots(count: Int, total: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        repeat(total) { i ->
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .background(
                        color = if (i < count) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        shape = CircleShape
                    )
            )
        }
    }
}

/**
 * 数字键盘：1-9、0、退格。
 */
@Composable
private fun PinKeypad(
    pinLength: Int,
    onDigit: (Char) -> Unit,
    onDelete: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        listOf("123", "456", "789").forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                row.forEach { c ->
                    KeypadButton(text = c) { onDigit(c) }
                }
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 占位，保持键盘对称
            Spacer(modifier = Modifier.size(72.dp))
            KeypadButton(text = '0') { onDigit('0') }
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clickable(onClick = onDelete),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Backspace,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * 单个数字键。
 */
@Composable
private fun KeypadButton(text: Char, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text.toString(),
            fontSize = 28.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
