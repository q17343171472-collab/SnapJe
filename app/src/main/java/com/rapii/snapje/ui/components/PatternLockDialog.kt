package com.rapii.snapje.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

/**
 * Simple pattern/PIN lock dialog for authentication before sensitive operations.
 * Supports 4-digit PIN entry.
 */
@Composable
fun PatternLockDialog(
    onDismiss: () -> Unit,
    onUnlock: () -> Unit,
    title: String = "Enter PIN to confirm",
    correctPin: String = "1234" // In production, this should come from secure storage
) {
    var enteredPin by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }
    
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
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 24.dp)
                )
                
                // PIN display
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(bottom = 24.dp)
                ) {
                    repeat(4) { index ->
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(
                                    if (index < enteredPin.length) {
                                        if (isError) MaterialTheme.colorScheme.error 
                                        else MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (index < enteredPin.length) {
                                Text(
                                    text = "•",
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
                
                if (isError) {
                    Text(
                        text = "Incorrect PIN",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                }
                
                // Numeric keypad
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    repeat(3) { row ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.padding(horizontal = 8.dp)
                        ) {
                            repeat(3) { col ->
                                val number = row * 3 + col + 1
                                NumberKeyButton(
                                    number = number.toString(),
                                    onClick = {
                                        if (enteredPin.length < 4) {
                                            enteredPin += number
                                            isError = false
                                            
                                            // Check if PIN is complete
                                            if (enteredPin.length == 4) {
                                                if (enteredPin == correctPin) {
                                                    onUnlock()
                                                } else {
                                                    isError = true
                                                    enteredPin = ""
                                                }
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                    
                    // Bottom row: Clear, 0, Back
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.padding(horizontal = 8.dp)
                    ) {
                        NumberKeyButton(
                            number = "Clear",
                            onClick = {
                                enteredPin = ""
                                isError = false
                            },
                            isAction = true
                        )
                        
                        NumberKeyButton(
                            number = "0",
                            onClick = {
                                if (enteredPin.length < 4) {
                                    enteredPin += "0"
                                    isError = false
                                    
                                    if (enteredPin.length == 4) {
                                        if (enteredPin == correctPin) {
                                            onUnlock()
                                        } else {
                                            isError = true
                                            enteredPin = ""
                                        }
                                    }
                                }
                            }
                        )
                        
                        NumberKeyButton(
                            number = "⌫",
                            onClick = {
                                if (enteredPin.isNotEmpty()) {
                                    enteredPin = enteredPin.dropLast(1)
                                    isError = false
                                }
                            },
                            isAction = true
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    }
}

@Composable
private fun NumberKeyButton(
    number: String,
    onClick: () -> Unit,
    isAction: Boolean = false
) {
    Button(
        onClick = onClick,
        modifier = Modifier.size(64.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isAction) {
                MaterialTheme.colorScheme.surfaceVariant
            } else {
                MaterialTheme.colorScheme.primaryContainer
            },
            contentColor = if (isAction) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onPrimaryContainer
            }
        ),
        shape = CircleShape,
        contentPadding = PaddingValues(0.dp)
    ) {
        Text(
            text = number,
            style = MaterialTheme.typography.titleLarge
        )
    }
}
