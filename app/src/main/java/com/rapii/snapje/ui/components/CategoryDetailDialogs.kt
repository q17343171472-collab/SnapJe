package com.rapii.snapje.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.rapii.snapje.data.Category
import com.rapii.snapje.data.PhotoInfo

/**
 * Holder for dialog states in CategoryDetailScreen.
 * Centralizes dialog visibility management.
 */
data class CategoryDialogState(
    val showRenameDialog: Boolean = false,
    val showInfoDialog: Boolean = false,
    val showCopyDialog: Boolean = false,
    val showMoveDialog: Boolean = false,
    val showOperationsMenu: Boolean = false
)

/**
 * Dialogs for CategoryDetailScreen operations.
 * Extracted to reduce complexity of main screen.
 */
object CategoryDetailDialogs {

    /**
     * Rename dialog for photos.
     */
    @Composable
    fun RenameDialog(
        currentName: String,
        onDismiss: () -> Unit,
        onConfirm: (String) -> Unit
    ) {
        com.rapii.snapje.ui.components.RenameDialog(
            currentName = currentName,
            onDismiss = onDismiss,
            onConfirm = onConfirm
        )
    }

    /**
     * Photo info dialog showing metadata.
     */
    @Composable
    fun PhotoInfoDialog(
        photoInfo: PhotoInfo,
        onDismiss: () -> Unit
    ) {
        com.rapii.snapje.ui.components.PhotoInfoDialog(
            photoInfo = photoInfo,
            onDismiss = onDismiss
        )
    }

    /**
     * Copy to category dialog.
     */
    @Composable
    fun CopyToDialog(
        categories: List<Category>,
        currentCategoryId: Long,
        title: String,
        onDismiss: () -> Unit,
        onCategorySelected: (Category) -> Unit
    ) {
        com.rapii.snapje.ui.components.CopyToDialog(
            categories = categories,
            currentCategoryId = currentCategoryId,
            isLoading = false,
            title = title,
            onDismiss = onDismiss,
            onCategorySelected = onCategorySelected
        )
    }

    /**
     * File operations bottom sheet menu.
     */
    @Composable
    fun FileOperationsMenu(
        photoName: String,
        onDismiss: () -> Unit,
        onOperation: (com.rapii.snapje.data.FileOperationType) -> Unit
    ) {
        com.rapii.snapje.ui.components.FileOperationsBottomSheet(
            photoName = photoName,
            onDismiss = onDismiss,
            onOperation = onOperation
        )
    }
}
