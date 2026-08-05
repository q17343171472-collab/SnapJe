package com.rapii.snapje.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.rapii.snapje.data.PhotoSortOption

/**
 * Sort menu for photo grids.
 * Allows users to change the sort order of displayed photos.
 */
@Composable
fun PhotoSortMenu(
    currentSort: PhotoSortOption,
    onSortSelected: (PhotoSortOption) -> Unit,
    expanded: Boolean,
    onDismiss: () -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss
    ) {
        PhotoSortOption.entries.forEach { option ->
            DropdownMenuItem(
                text = { Text(option.displayName) },
                onClick = {
                    onSortSelected(option)
                    onDismiss()
                },
                leadingIcon = {
                    if (currentSort == option) {
                        Icon(
                            imageVector = Icons.Outlined.CheckCircle,
                            contentDescription = "已选择"
                        )
                    }
                }
            )
        }
    }
}

/**
 * Remembers the sort menu expanded state.
 * Use this to manage menu visibility.
 */
@Composable
fun rememberSortMenuState(): SortMenuState {
    var expanded by remember { mutableStateOf(false) }
    return SortMenuState(expanded = expanded, onExpand = { expanded = true }, onDismiss = { expanded = false })
}

/**
 * State holder for sort menu.
 */
data class SortMenuState(
    val expanded: Boolean,
    val onExpand: () -> Unit,
    val onDismiss: () -> Unit
)
