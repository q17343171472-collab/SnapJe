package com.rapii.snapje.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.rapii.snapje.data.FileOperationType

/**
 * Bottom sheet menu for file operations
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileOperationsBottomSheet(
    photoName: String,
    onDismiss: () -> Unit,
    onOperation: (FileOperationType) -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Header
            Text(
                text = photoName,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            HorizontalDivider()
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Operations
            FileOperationItem(
                icon = Icons.Default.Delete,
                label = "删除",
                tint = MaterialTheme.colorScheme.error,
                onClick = { onOperation(FileOperationType.DELETE) }
            )
            
            FileOperationItem(
                icon = Icons.Default.HideImage,
                label = "隐藏",
                onClick = { onOperation(FileOperationType.HIDE) }
            )
            
            FileOperationItem(
                icon = Icons.Default.Edit,
                label = "重命名",
                onClick = { onOperation(FileOperationType.RENAME) }
            )
            
            FileOperationItem(
                icon = Icons.Default.ContentCopy,
                label = "复制",
                onClick = { onOperation(FileOperationType.COPY) }
            )
            
            FileOperationItem(
                icon = Icons.AutoMirrored.Filled.DriveFileMove,
                label = "移动",
                onClick = { onOperation(FileOperationType.MOVE) }
            )
            
            FileOperationItem(
                icon = Icons.Default.Crop,
                label = "裁剪",
                onClick = { onOperation(FileOperationType.CROP) }
            )
            
            FileOperationItem(
                icon = Icons.Default.Share,
                label = "分享",
                onClick = { onOperation(FileOperationType.SHARE) }
            )
            
            FileOperationItem(
                icon = Icons.Default.Info,
                label = "信息",
                onClick = { onOperation(FileOperationType.INFO) }
            )
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun FileOperationItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: Color = LocalContentColor.current
) {
    ListItem(
        headlineContent = { Text(text = label) },
        leadingContent = {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tint
            )
        },
        modifier = Modifier.clickable { onClick() }
    )
}
