package com.rapii.snapje.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rapii.snapje.data.PhotoInfo

/**
 * Dialog showing photo information
 */
@Composable
fun PhotoInfoDialog(
    photoInfo: PhotoInfo,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "照片信息")
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                InfoRow(label = "名称", value = photoInfo.name)
                InfoRow(label = "路径", value = photoInfo.path)
                InfoRow(label = "大小", value = photoInfo.size)
                InfoRow(label = "尺寸", value = photoInfo.dimensions)
                InfoRow(label = "拍摄日期", value = photoInfo.dateTaken)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
