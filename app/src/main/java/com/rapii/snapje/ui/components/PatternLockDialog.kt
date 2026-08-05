package com.rapii.snapje.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

/**
 * 操作确认对话框（删除照片等敏感操作前二次确认）。
 *
 * 原为生物识别（指纹/面部）验证，已按要求改为普通确认框，
 * 点击"确定"即回调 [onUnlock] 继续执行。
 */
@Composable
fun PatternLockDialog(
    onDismiss: () -> Unit,
    onUnlock: () -> Unit,
    title: String = "确认操作",
    @Suppress("UNUSED_PARAMETER") correctPin: String = "1234"
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text("此操作无法撤销，确定继续吗？") },
        confirmButton = {
            TextButton(onClick = { onUnlock() }) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
