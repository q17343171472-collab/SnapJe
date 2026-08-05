package com.rapii.snapje.ui

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.rapii.snapje.R
import com.rapii.snapje.data.FileOperationResult
import com.rapii.snapje.data.TrashedPhoto
import kotlinx.coroutines.flow.StateFlow

/**
 * Recently Deleted Screen - Displays trashed photos with restore and delete options.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecentlyDeletedScreen(
    onBack: () -> Unit,
    viewModel: TrashViewModel = hiltViewModel(),
    onPhotoRestored: ((TrashedPhoto) -> Unit)? = null  // Callback for when photo is restored
) {
    val context = LocalContext.current
    val uiState = viewModel.uiState.collectAsState()

    // Track photo pending restore
    var pendingRestorePhoto by remember { mutableStateOf<TrashedPhoto?>(null) }

    // Activity result launcher for restore permission
    val restorePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            // Permission granted - confirm restore
            pendingRestorePhoto?.let { photo ->
                viewModel.confirmRestore(photo)
                Toast.makeText(context, "照片已恢复", Toast.LENGTH_SHORT).show()
                // CRITICAL: Notify parent that photo was restored so category can update
                onPhotoRestored?.invoke(photo)
            }
        } else {
            // Permission denied - cancel restore
            pendingRestorePhoto?.let { photo ->
                viewModel.cancelRestore(photo)
            }
            Toast.makeText(context, "恢复已取消", Toast.LENGTH_SHORT).show()
        }
        pendingRestorePhoto = null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.recently_deleted)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (uiState.value.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (uiState.value.photos.isEmpty()) {
                Text(
                    text = stringResource(R.string.trash_empty),
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 100.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(uiState.value.photos) { photo ->
                        TrashedPhotoItem(
                            photo = photo,
                            onRestore = {
                                // Start restore flow - may need permission
                                viewModel.startRestore(photo) { result ->
                                    when (result) {
                                        is FileOperationResult.Success -> {
                                            // Restore completed immediately (no permission needed)
                                            Toast.makeText(context, "照片已恢复", Toast.LENGTH_SHORT).show()
                                            // CRITICAL: Notify parent that photo was restored
                                            onPhotoRestored?.invoke(photo)
                                        }
                                        is FileOperationResult.NeedsPermission -> {
                                            // Permission needed - launch dialog
                                            pendingRestorePhoto = photo
                                            val intentSenderRequest = androidx.activity.result.IntentSenderRequest.Builder(
                                                result.pendingIntent.intentSender
                                            ).build()
                                            restorePermissionLauncher.launch(intentSenderRequest)
                                        }
                                        is FileOperationResult.Error -> {
                                            Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            },
                            onDelete = {
                                viewModel.permanentDelete(photo) { result ->
                                    val message = when (result) {
                                        is FileOperationResult.Success -> context.getString(R.string.permanently_deleted_toast)
                                        is FileOperationResult.Error -> result.message
                                        else -> "操作失败"
                                    }
                                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TrashedPhotoItem(
    photo: TrashedPhoto,
    onRestore: () -> Unit,
    onDelete: () -> Unit
) {
    // CRITICAL: Use trash-optimized image loader for cached thumbnails
    val context = LocalContext.current
    val trashLoader = remember {
        com.rapii.snapje.util.ImageLoaderFactory.createTrashLoader(context)
    }
    
    // Use cache path if available, otherwise use original URI
    val imageUri = if (!photo.cachePath.isNullOrEmpty() && java.io.File(photo.cachePath).exists()) {
        android.net.Uri.fromFile(java.io.File(photo.cachePath))
    } else {
        photo.originalUri
    }
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(4.dp)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
        ) {
            androidx.compose.foundation.Image(
                painter = coil.compose.rememberAsyncImagePainter(
                    model = imageUri,
                    imageLoader = trashLoader
                ),
                contentDescription = photo.displayName,
                modifier = Modifier.fillMaxSize(),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            IconButton(onClick = onRestore) {
                Icon(
                    imageVector = Icons.Default.Restore,
                    contentDescription = "恢复",
                    modifier = Modifier.size(24.dp)
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "删除",
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
