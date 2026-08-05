package com.rapii.snapje.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.rapii.snapje.R

/**
 * Default TopAppBar for CategoryDetailScreen.
 * Shows category name with refresh and back actions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryDetailTopAppBar(
    categoryName: String,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    TopAppBar(
        title = {
            Text(
                text = categoryName,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.back)
                )
            }
        },
        actions = {
            IconButton(onClick = onRefresh) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "刷新"
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    )
}

/**
 * Selection mode TopAppBar for CategoryDetailScreen.
 * Shows selected count with cancel and delete actions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryDetailSelectionTopAppBar(
    selectedCount: Int,
    canDelete: Boolean,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
    onSave: (() -> Unit)? = null,
    onMove: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    TopAppBar(
        title = {
            Text(
                text = "已选择 $selectedCount 项",
                style = MaterialTheme.typography.titleLarge
            )
        },
        navigationIcon = {
            IconButton(onClick = onCancel) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "取消"
                )
            }
        },
        actions = {
            // 保存到系统相册（保险库照片）
            if (onSave != null) {
                IconButton(
                    onClick = onSave,
                    enabled = canDelete
                ) {
                    Icon(
                        imageVector = Icons.Default.SaveAlt,
                        contentDescription = "保存到相册",
                        tint = if (canDelete) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        }
                    )
                }
            }
            // 移动到其他分组（保险库照片）
            if (onMove != null) {
                IconButton(
                    onClick = onMove,
                    enabled = canDelete
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.DriveFileMove,
                        contentDescription = "移动到其他分组",
                        tint = if (canDelete) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        }
                    )
                }
            }
            IconButton(
                onClick = onDelete,
                enabled = canDelete
            ) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = "删除",
                    tint = if (canDelete) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    }
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    )
}
