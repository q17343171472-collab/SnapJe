package com.rapii.snapje.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rapii.snapje.util.ValidationUtils

/**
 * Dialog for renaming a photo with input validation.
 * Prevents path traversal attacks and invalid filenames.
 */
@Composable
fun RenameDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var newName by remember { mutableStateOf(currentName) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "重命名照片")
        },
        text = {
            Column {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { 
                        newName = it
                        showError = false  // Clear error when user types
                    },
                    label = { Text("新名称") },
                    singleLine = true,
                    isError = showError,
                    supportingText = if (showError) {
                        { Text(errorMessage ?: "名称无效") }
                    } else null,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    // Validate the new name before confirming
                    val result = ValidationUtils.validateFilename(newName)
                    if (result.isValid) {
                        onConfirm(newName)
                    } else {
                        errorMessage = result.errorMessage
                        showError = true
                    }
                },
                enabled = newName.isNotBlank() && newName != currentName && !showError
            ) {
                Text("重命名")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
