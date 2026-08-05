package com.rapii.snapje.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rapii.snapje.data.Category

/**
 * Dialog for selecting destination folder for copy/move operations
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CopyToDialog(
    categories: List<Category>,
    currentCategoryId: Long,
    isLoading: Boolean = false,
    title: String = "复制到",
    onDismiss: () -> Unit,
    onCategorySelected: (Category) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title) },
        text = {
            when {
                isLoading -> {
                    // Show loading state
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(48.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "加载中...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                categories.isEmpty() -> {
                    // Show empty state with message
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "没有可用的文件夹。\n请确认已授予存储权限。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 400.dp)
                    ) {
                        items(categories.filter { it.id != currentCategoryId }) { category ->
                            CategoryListItem(
                                category = category,
                                onClick = { onCategorySelected(category) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
private fun CategoryListItem(
    category: Category,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { Text(text = category.displayName) },
        supportingContent = { Text(text = category.formattedItemCount) },
        leadingContent = {
            Icon(
                imageVector = Icons.Default.Folder,
                contentDescription = null
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    )
}
