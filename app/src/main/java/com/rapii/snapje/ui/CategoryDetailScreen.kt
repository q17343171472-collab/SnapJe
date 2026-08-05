package com.rapii.snapje.ui

import android.app.Activity
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.rapii.snapje.R
import com.rapii.snapje.data.Category
import com.rapii.snapje.data.FileOperationResult
import com.rapii.snapje.data.FileOperationType
import com.rapii.snapje.data.FileOperations
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.rapii.snapje.data.PhotoInfo
import com.rapii.snapje.data.PhotoItem
import com.rapii.snapje.data.Result
import com.rapii.snapje.util.L
import com.rapii.snapje.data.PhotoSortOption
import com.rapii.snapje.data.TrashRepository
import com.rapii.snapje.data.TrashedPhoto
import com.rapii.snapje.ui.components.CategoryDetailDialogs
import com.rapii.snapje.ui.components.CategoryDetailSelectionTopAppBar
import com.rapii.snapje.ui.components.CategoryDetailTopAppBar
import com.rapii.snapje.ui.components.CategoryPhotoGridItemWithLongPress
import com.rapii.snapje.ui.components.PhotoGridWithOperations
import com.rapii.snapje.ui.components.PhotoSortMenu
import com.rapii.snapje.ui.components.SortMenuState
import com.rapii.snapje.ui.components.rememberSortMenuState
import kotlinx.coroutines.launch
import java.io.File

/**
 * Screen showing all photos in a specific category/folder.
 * Supports long-press for file operations.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryDetailScreen(
    categoryId: Long,
    allCategories: List<Category> = emptyList(),
    onBack: () -> Unit,
    onPhotoClick: (PhotoItem, List<PhotoItem>) -> Unit,
    onPhotoRestored: ((TrashedPhoto) -> Unit)? = null,
    refreshTrigger: Boolean = false
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Use Hilt ViewModel with SavedStateHandle for categoryId
    val viewModel: CategoryDetailViewModel = hiltViewModel()

    // FileOperations is now injected via Hilt into the ViewModel
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val fileOperations = viewModel.fileOperationsInstance
    val trashRepository = viewModel.trashRepositoryInstance

    // CRITICAL: Remember the LazyVerticalGrid state to preserve scroll position
    val gridState = rememberLazyGridState()

    // Photo gallery state
    var showPhotoGallery by remember { mutableStateOf(false) }
    var galleryPhotos by remember { mutableStateOf(listOf<PhotoItem>()) }
    var galleryInitialIndex by remember { mutableIntStateOf(0) }

    // CRITICAL: Track the TAPPED PHOTO index and scroll offset for proper restoration
    data class ScrollStateData(
        val tappedPhotoIndex: Int,
        val tappedPhotoOffset: Int,  // Offset of the tapped photo within viewport (always >= 0)
        val viewportHeight: Int,
        val totalItems: Int
    )
    var savedScrollState by remember { mutableStateOf<ScrollStateData?>(null) }

    // Trigger scroll restoration after returning from fullscreen
    var shouldRestoreScroll by remember { mutableStateOf(false) }

    // Function to save scroll state synchronously (called on photo tap)
    fun saveScrollState(tappedIndex: Int) {
        val visibleItems = gridState.layoutInfo.visibleItemsInfo
        val totalItems = gridState.layoutInfo.totalItemsCount
        val viewportHeight = gridState.layoutInfo.viewportSize.height
        
        // Find the tapped photo in visible items
        val tappedItem = visibleItems.find { it.index == tappedIndex }
        // Use the tapped photo's offset (always positive, relative to viewport top)
        val tappedPhotoOffset = tappedItem?.offset?.y?.toInt() ?: 0
        
        savedScrollState = ScrollStateData(
            tappedPhotoIndex = tappedIndex,
            tappedPhotoOffset = tappedPhotoOffset,
            viewportHeight = viewportHeight,
            totalItems = totalItems
        )
        
        L.d("CategoryDetail", "Saving scroll (sync): tappedIndex=$tappedIndex, tappedOffset=$tappedPhotoOffset, viewportHeight=$viewportHeight, totalItems=$totalItems")
    }

    // Restore scroll state when returning from fullscreen
    LaunchedEffect(showPhotoGallery, savedScrollState) {
        if (!showPhotoGallery && savedScrollState != null) {
            shouldRestoreScroll = true
            // 退出全屏后自动删除解密原图临时文件（保留网格缩略图缓存）
            viewModel.clearFullImageCache()
        }
    }

    if (shouldRestoreScroll && savedScrollState != null) {
        LaunchedEffect(shouldRestoreScroll) {
            val scrollData = savedScrollState!!
            val totalItems = scrollData.totalItems
            val tappedIndex = scrollData.tappedPhotoIndex.coerceIn(0, totalItems - 1)
            val tappedOffset = scrollData.tappedPhotoOffset
            val viewportHeight = scrollData.viewportHeight
            
            L.d("CategoryDetail", "Restoring scroll: tappedIndex=$tappedIndex, tappedOffset=$tappedOffset, viewportHeight=$viewportHeight, totalItems=$totalItems")
            
            try {
                // To restore the exact position, we need to calculate which item should be first visible
                // and what offset to use.
                // 
                // The tappedOffset is where the tapped item appears in the viewport (e.g., 812px from top)
                // We want to restore so the same items are visible in the same positions.
                //
                // Strategy: Scroll to the tapped item with offset 0, then calculate how much to offset
                // to get the tapped item back to its original position.
                //
                // But scrollToItem's offset parameter is the scroll offset from the item's top,
                // not the item's position in viewport.
                //
                // Better approach: Just scroll to the tapped item at position 0 (top of viewport)
                // This ensures the tapped item is visible, though not at the exact same position.
                //
                // For exact restoration, we'd need to know item heights, which is complex.
                // Let's try: scroll to tapped item with offset that puts it at the same position.
                
                // The offset in scrollToItem is how much to scroll PAST the item (negative = item below top)
                // So to have the item at offset X from top, we use -X
                val restoreOffset = -tappedOffset
                
                gridState.scrollToItem(tappedIndex, restoreOffset)
                
                L.d("CategoryDetail", "Scroll restored: index=$tappedIndex, offset=$restoreOffset (tapped at $tappedOffset from top)")
            } catch (e: Exception) {
                L.e("CategoryDetail", "Scroll restoration failed: ${e.message}")
                // Fallback: scroll to the tapped photo at top of screen
                gridState.scrollToItem(tappedIndex, 0)
                L.d("CategoryDetail", "Fallback scroll to index=$tappedIndex, offset=0")
            }

            shouldRestoreScroll = false
            savedScrollState = null
        }
    }

    // Photo Gallery Screen
    if (showPhotoGallery && galleryPhotos.isNotEmpty()) {
        PhotoGalleryScreen(
            photos = galleryPhotos,
            initialPhotoIndex = galleryInitialIndex,
            onBack = { showPhotoGallery = false },
            onShare = { photo -> viewModel.sharePhoto(photo) },
            allCategories = allCategories,
            vaultFullImageProvider = { photo -> viewModel.fullImageUri(photo) },
            vaultDeleteHandler = { photo ->
                scope.launch {
                    when (val result = viewModel.deleteVaultPhoto(photo)) {
                        is Result.Loading -> {}
                        is Result.Success -> {
                            Toast.makeText(
                                context,
                                context.getString(R.string.vault_photo_deleted),
                                Toast.LENGTH_SHORT
                            ).show()
                            viewModel.removePhotoFromList(photo)
                        }
                        is Result.Error -> {
                            Toast.makeText(context.applicationContext, result.message, Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        )
        return
    }

    // File operations state
    var selectedPhoto by remember { mutableStateOf<PhotoItem?>(null) }
    var showOperationsMenu by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showInfoDialog by remember { mutableStateOf(false) }
    var showCopyDialog by remember { mutableStateOf(false) }
    var showMoveDialog by remember { mutableStateOf(false) }
    var photoInfo by remember { mutableStateOf<PhotoInfo?>(null) }

    data class PendingDelete(val photo: PhotoItem)
    var pendingDeletePhoto by remember { mutableStateOf<PendingDelete?>(null) }
    var pendingBatchDeletePhotos by remember { mutableStateOf<List<PendingDelete>>(emptyList()) }
    
    data class PendingRename(val photo: PhotoItem, val newName: String)
    var pendingRename by remember { mutableStateOf<PendingRename?>(null) }
    
    // Pattern lock for delete confirmation
    var showPatternLockForDelete by remember { mutableStateOf(false) }
    var photoPendingDeleteAfterLock by remember { mutableStateOf<PhotoItem?>(null) }
    
    // Crop activity launcher
    var pendingCropOutputUri by remember { mutableStateOf<Uri?>(null) }
    
    val cropLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            pendingCropOutputUri?.let { outputUri ->
                try {
                    // Notify media scanner about the new cropped image
                    MediaScannerConnection.scanFile(
                        context,
                        arrayOf(outputUri.path),
                        arrayOf("image/jpeg"),
                        null
                    )
                    Toast.makeText(context, "照片裁剪成功", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    L.e("CropError", "Failed to process cropped image: ${e.message}")
                    Toast.makeText(context, "裁剪成功但保存失败", Toast.LENGTH_SHORT).show()
                }
            }
            pendingCropOutputUri = null
        } else {
            L.d("Crop", "Crop cancelled or failed")
            pendingCropOutputUri = null
        }
    }

    // Function to launch crop activity
    fun launchCropActivity(photo: PhotoItem) {
        try {
            val tempFile = File(context.cacheDir, "crop_temp_${System.currentTimeMillis()}.jpg")
            val outputUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                tempFile
            )

            val intent = Intent("com.android.camera.action.CROP").apply {
                setDataAndType(photo.uri, "image/*")
                putExtra("crop", "true")
                putExtra("aspectX", 1)
                putExtra("aspectY", 1)
                putExtra("outputX", 1024)
                putExtra("outputY", 1024)
                putExtra(MediaStore.EXTRA_OUTPUT, outputUri)
                putExtra("return-data", false)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }

            // Check if there's an activity to handle the crop intent
            if (intent.resolveActivity(context.packageManager) != null) {
                pendingCropOutputUri = outputUri
                cropLauncher.launch(intent)
            } else {
                Toast.makeText(context, "没有可用的裁剪应用", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            L.e("CropError", "Failed to launch crop: ${e.message}")
            Toast.makeText(context, "裁剪不可用：${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    // Multi-selection state
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedPhotos by remember { mutableStateOf<Set<PhotoItem>>(emptySet()) }
    var isRefreshing by remember { mutableStateOf(false) }

    // Snackbar state
    val snackbarHostState = remember { SnackbarHostState() }

    // Sort menu state
    val sortMenuState = rememberSortMenuState()

    // Activity result launcher for permissions (Delete, Rename, etc.)
    val intentSenderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        L.d("CategoryDetailScreen", "ActivityResult CALLBACK TRIGGERED: resultCode=${result.resultCode}, RESULT_OK=${Activity.RESULT_OK}, RESULT_CANCEL=${Activity.RESULT_CANCELED}")

        if (result.resultCode == Activity.RESULT_OK) {
            L.d("CategoryDetailScreen", "ActivityResult: RESULT_OK received")

            // Handle Pending Rename
            pendingRename?.let { pending ->
                L.d("CategoryDetailScreen", "ActivityResult: Handling pending rename")
                scope.launch {
                    when (val retryResult = fileOperations.renamePhoto(pending.photo, pending.newName)) {
                        is FileOperationResult.Success -> {
                            Toast.makeText(context, retryResult.message, Toast.LENGTH_SHORT).show()
                            viewModel.loadPhotos()
                        }
                        is FileOperationResult.Error -> {
                            Toast.makeText(context, retryResult.message, Toast.LENGTH_SHORT).show()
                        }
                        else -> {}
                    }
                    pendingRename = null
                }
            } ?: run {
                // Handle Pending Deletes
                L.d("CategoryDetailScreen", "ActivityResult: Handling pending deletes")
                L.d("CategoryDetailScreen", "ActivityResult: pendingBatchDeletePhotos.size=${pendingBatchDeletePhotos.size}")
                L.d("CategoryDetailScreen", "ActivityResult: pendingDeletePhoto=${pendingDeletePhoto?.photo?.displayName}")

                val photosToConfirm = if (pendingBatchDeletePhotos.isNotEmpty()) {
                    pendingBatchDeletePhotos
                } else {
                    pendingDeletePhoto?.let { listOf(it) } ?: emptyList()
                }

                L.d("CategoryDetailScreen", "ActivityResult: photosToConfirm.size=${photosToConfirm.size}")

                trashRepository.confirmTrashBatch(photosToConfirm.map { it.photo })
                photosToConfirm.forEach { pending ->
                    viewModel.removePhotoFromList(pending.photo)
                }

                if (photosToConfirm.isNotEmpty()) {
                    Toast.makeText(context, "${photosToConfirm.size} 张照片已移至回收站", Toast.LENGTH_SHORT).show()
                }

                pendingDeletePhoto = null
                pendingBatchDeletePhotos = emptyList()
            }
        } else {
            L.d("CategoryDetailScreen", "ActivityResult: RESULT_CANCELLED or other (${result.resultCode})")

            // For Android 11+, the system handles trash automatically
            // We still need to track it locally even if result is not OK
            val photosToConfirm = if (pendingBatchDeletePhotos.isNotEmpty()) {
                pendingBatchDeletePhotos
            } else {
                pendingDeletePhoto?.let { listOf(it) } ?: emptyList()
            }

            if (photosToConfirm.isNotEmpty()) {
                L.d("CategoryDetailScreen", "ActivityResult: Confirming trash anyway (Android 11+ auto-trash)")
                trashRepository.confirmTrashBatch(photosToConfirm.map { it.photo })
                photosToConfirm.forEach { pending ->
                    viewModel.removePhotoFromList(pending.photo)
                }
                Toast.makeText(context, "${photosToConfirm.size} 张照片已移至回收站", Toast.LENGTH_SHORT).show()
            }

            // Handle Cancellation
            if (pendingRename != null) {
                Toast.makeText(context, "重命名已取消", Toast.LENGTH_SHORT).show()
                pendingRename = null
            } else {
                val photosToCancel = if (pendingBatchDeletePhotos.isNotEmpty()) {
                    pendingBatchDeletePhotos
                } else {
                    pendingDeletePhoto?.let { listOf(it) } ?: emptyList()
                }

                trashRepository.cancelTrashBatch(photosToCancel.map { it.photo })

                if (photosToCancel.isNotEmpty()) {
                    Toast.makeText(context, "删除已取消", Toast.LENGTH_SHORT).show()
                }

                pendingDeletePhoto = null
                pendingBatchDeletePhotos = emptyList()
            }
        }

        isSelectionMode = false
        selectedPhotos = emptySet()
    }

    // Fallback: If ActivityResult callback doesn't fire (Android 11+ auto-trash), confirm after delay
    LaunchedEffect(pendingDeletePhoto) {
        if (pendingDeletePhoto != null) {
            L.d("CategoryDetailScreen", "LaunchedEffect: Waiting 1s for ActivityResult callback")
            kotlinx.coroutines.delay(1000)
            
            // If still pending after delay, confirm manually
            if (pendingDeletePhoto != null) {
                L.d("CategoryDetailScreen", "LaunchedEffect: Callback didn't fire, confirming trash manually")
                val photo = pendingDeletePhoto!!.photo
                trashRepository.confirmTrash(photo)
                viewModel.removePhotoFromList(photo)
                Toast.makeText(context, "照片已移至回收站", Toast.LENGTH_SHORT).show()
                pendingDeletePhoto = null
            }
        }
    }

    // Handle back gesture
    val lifecycleOwner = LocalLifecycleOwner.current
    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher

    DisposableEffect(backDispatcher) {
        val callback = object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isSelectionMode) {
                    isSelectionMode = false
                    selectedPhotos = emptySet()
                } else {
                    onBack()
                }
            }
        }
        backDispatcher?.addCallback(lifecycleOwner, callback)
        onDispose { callback.remove() }
    }

    // Pre-populate ViewModel with cached category
    LaunchedEffect(allCategories) {
        if (allCategories.isNotEmpty()) {
            val category = allCategories.find { it.id == categoryId }
            viewModel.setCachedCategory(category)
        }
    }

    // Load photos ONCE when screen is first shown
    LaunchedEffect(categoryId) {
        if (viewModel.uiState.value.photos.isEmpty()) {
            viewModel.loadPhotos()
        }
    }

    // Handle errors
    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            if (!error.contains("cancelled", ignoreCase = true)) {
                Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
            }
            viewModel.clearError()
        }
    }

    // Function to handle file operations
    fun handleFileOperation(operation: FileOperationType) {
        val photo = selectedPhoto ?: return

        // 保险库照片仅支持：删除 / 重命名 / 分享 / 信息（复制、移动、裁剪、隐藏仅适用于系统相册照片）
        if (photo.isVaultPhoto && operation in setOf(
                FileOperationType.COPY,
                FileOperationType.MOVE,
                FileOperationType.CROP,
                FileOperationType.HIDE
            )
        ) {
            Toast.makeText(
                context,
                context.getString(R.string.vault_op_not_supported),
                Toast.LENGTH_SHORT
            ).show()
            showOperationsMenu = false
            selectedPhoto = null
            return
        }

        when (operation) {
            FileOperationType.COPY -> { showOperationsMenu = false; showCopyDialog = true; return }
            FileOperationType.MOVE -> { showOperationsMenu = false; showMoveDialog = true; return }
            FileOperationType.CROP -> {
                showOperationsMenu = false
                selectedPhoto = null
                launchCropActivity(photo)
                return
            }
            FileOperationType.HIDE -> {
                showOperationsMenu = false
                selectedPhoto = null
                scope.launch {
                    withContext(Dispatchers.IO) {
                        // Hide photo by adding .nomedia file in the same directory
                        val uriPath = photo.uri.path ?: return@withContext
                        val photoDir = java.io.File(uriPath).parentFile
                        val noMediaFile = java.io.File(photoDir, ".nomedia")
                        if (!noMediaFile.exists()) {
                            noMediaFile.createNewFile()
                        }
                        // Also rename the photo to start with a dot
                        val currentName = photo.displayName
                        if (!currentName.startsWith(".")) {
                            val hiddenName = ".$currentName"
                            // Note: Actual renaming requires MediaStore operations
                        }
                    }
                    withContext(Dispatchers.Main) {
                        snackbarHostState.showSnackbar("照片已隐藏")
                        viewModel.removePhotoFromList(photo)
                    }
                }
                return
            }
            else -> { }
        }

        scope.launch {
            try {
                when (operation) {
                    FileOperationType.DELETE -> {
                        showOperationsMenu = false
                        // Show pattern lock before delete
                        photoPendingDeleteAfterLock = photo
                        selectedPhoto = null
                        showPatternLockForDelete = true
                        return@launch
                    }
                    FileOperationType.RENAME -> { showRenameDialog = true; return@launch }
                    FileOperationType.SHARE -> {
                        showOperationsMenu = false
                        selectedPhoto = null
                        // 保险库/系统照片统一走 ViewModel（保险库会解密原图后分享）
                        try {
                            viewModel.sharePhoto(photo)
                        } catch (e: Exception) {
                            Toast.makeText(context.applicationContext, "分享失败：${e.message}", Toast.LENGTH_LONG).show()
                        }
                        return@launch
                    }
                    FileOperationType.INFO -> {
                        val info = withContext(Dispatchers.IO) {
                            fileOperations.getPhotoInfo(photo)
                        }
                        photoInfo = info
                        showInfoDialog = true
                        return@launch
                    }
                    else -> {}
                }
                showOperationsMenu = false
                selectedPhoto = null
            } catch (e: Exception) {
                Toast.makeText(context.applicationContext, "操作失败：${e.message}", Toast.LENGTH_LONG).show()
                showOperationsMenu = false
                selectedPhoto = null
            }
        }
    }

    // Function to handle copy to selected category
    // CRITICAL: Use application context for Toast + proper coroutine dispatching
    fun handleCopyTo(destCategory: Category) {
        val photo = selectedPhoto ?: return
        scope.launch {
            try {
                // Run copy on IO thread
                val result = withContext(Dispatchers.IO) {
                    fileOperations.copyPhoto(photo, destCategory.id)
                }

                // Show result on Main thread with application context
                withContext(Dispatchers.Main) {
                    when (result) {
                        is FileOperationResult.Success -> {
                            Toast.makeText(context.applicationContext, "已复制到 ${destCategory.displayName}", Toast.LENGTH_SHORT).show()
                            MediaScannerConnection.scanFile(context.applicationContext, arrayOf(destCategory.path), null, null)
                            // Refresh current category to show any changes
                            viewModel.loadPhotos()
                        }
                        is FileOperationResult.Error -> {
                            Toast.makeText(context.applicationContext, result.message, Toast.LENGTH_LONG).show()
                        }
                        is FileOperationResult.NeedsPermission -> {
                            Toast.makeText(context.applicationContext, "需要权限", Toast.LENGTH_SHORT).show()
                        }
                    }
                    showCopyDialog = false
                    selectedPhoto = null
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context.applicationContext, "复制失败：${e.message}", Toast.LENGTH_LONG).show()
                    showCopyDialog = false
                    selectedPhoto = null
                }
            }
        }
    }

    // Function to handle move to selected category
    // CRITICAL: Use application context for Toast + proper coroutine dispatching
    fun handleMoveTo(destCategory: Category) {
        val photo = selectedPhoto ?: return
        scope.launch {
            try {
                // Run move on IO thread
                val result = withContext(Dispatchers.IO) {
                    fileOperations.movePhoto(photo, destCategory.id)
                }
                
                // Show result on Main thread with application context
                withContext(Dispatchers.Main) {
                    when (result) {
                        is FileOperationResult.Success -> {
                            Toast.makeText(context.applicationContext, "已移动到 ${destCategory.displayName}", Toast.LENGTH_SHORT).show()
                            MediaScannerConnection.scanFile(context.applicationContext, arrayOf(destCategory.path), null, null)
                            viewModel.loadPhotos()
                        }
                        is FileOperationResult.Error -> {
                            Toast.makeText(context.applicationContext, result.message, Toast.LENGTH_LONG).show()
                        }
                        is FileOperationResult.NeedsPermission -> {
                            Toast.makeText(context.applicationContext, "需要权限", Toast.LENGTH_SHORT).show()
                        }
                    }
                    showMoveDialog = false
                    selectedPhoto = null
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context.applicationContext, "移动失败：${e.message}", Toast.LENGTH_LONG).show()
                    showMoveDialog = false
                    selectedPhoto = null
                }
            }
        }
    }

    // Function to handle batch delete of selected photos
    fun handleBatchDelete() {
        scope.launch {
            val photosToDelete = selectedPhotos.toList()

            // 保险库照片：直接批量删除
            if (photosToDelete.isNotEmpty() && photosToDelete.all { it.isVaultPhoto }) {
                var failed = 0
                photosToDelete.forEach { photo ->
                    when (val result = viewModel.deleteVaultPhoto(photo)) {
                        is Result.Loading -> {}
                        is Result.Success -> viewModel.removePhotoFromList(photo)
                        is Result.Error -> failed++
                    }
                }
                snackbarHostState.showSnackbar(
                    context.getString(
                        R.string.vault_photos_deleted,
                        photosToDelete.size - failed
                    )
                )
                isSelectionMode = false
                selectedPhotos = emptySet()
                return@launch
            }

            when (val result = trashRepository.moveToTrashBatch(photosToDelete)) {
                is FileOperationResult.Success -> {
                    snackbarHostState.showSnackbar(result.message)
                    selectedPhotos.forEach { photo ->
                        viewModel.removePhotoFromList(photo)
                    }
                }
                is FileOperationResult.NeedsPermission -> {
                    pendingBatchDeletePhotos = selectedPhotos.toList().map { PendingDelete(it) }
                    val intentSenderRequest = androidx.activity.result.IntentSenderRequest.Builder(
                        result.pendingIntent.intentSender
                    ).build()
                    intentSenderLauncher.launch(intentSenderRequest)
                    return@launch
                }
                is FileOperationResult.Error -> {
                    Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                }
            }

            isSelectionMode = false
            selectedPhotos = emptySet()
        }
    }

    // Toggle photo selection
    fun togglePhotoSelection(photo: PhotoItem) {
        selectedPhotos = if (photo in selectedPhotos) {
            selectedPhotos - photo
        } else {
            selectedPhotos + photo
        }
        if (selectedPhotos.isEmpty()) {
            isSelectionMode = false
        }
    }

    val currentSort by viewModel.currentSort.collectAsStateWithLifecycle()

    // UI Rendering
    Scaffold(
        topBar = {
            if (isSelectionMode) {
                CategoryDetailSelectionTopAppBar(
                    selectedCount = selectedPhotos.size,
                    canDelete = selectedPhotos.isNotEmpty(),
                    onCancel = {
                        isSelectionMode = false
                        selectedPhotos = emptySet()
                    },
                    onDelete = { handleBatchDelete() }
                )
            } else {
                CategoryDetailTopAppBar(
                    categoryName = uiState.category?.displayName ?: "相册",
                    onBack = onBack,
                    onRefresh = {
                        isRefreshing = true
                        viewModel.loadPhotos()
                        isRefreshing = false
                    }
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = {
                    isRefreshing = true
                    viewModel.loadPhotos()
                    isRefreshing = false
                },
                modifier = Modifier.fillMaxSize()
            ) {
                when {
                    uiState.isLoading -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    uiState.photos.isEmpty() -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(text = stringResource(R.string.vault_empty), style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = stringResource(R.string.vault_empty_message),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    else -> {
                        PhotoGridWithOperations(
                            photos = uiState.photos,
                            isSelectionMode = isSelectionMode,
                            selectedPhotos = selectedPhotos,
                            onPhotoClick = { clickedPhoto, clickedIndex ->
                                if (isSelectionMode) {
                                    togglePhotoSelection(clickedPhoto)
                                } else {
                                    // CRITICAL: Save scroll state BEFORE opening fullscreen
                                    saveScrollState(clickedIndex)
                                    galleryPhotos = uiState.photos
                                    galleryInitialIndex = clickedIndex
                                    showPhotoGallery = true
                                }
                            },
                            onPhotoLongPress = { photo ->
                                if (!isSelectionMode) {
                                    isSelectionMode = true
                                    selectedPhotos = setOf(photo)
                                } else {
                                    togglePhotoSelection(photo)
                                }
                            },
                            onDragSelect = { photo ->
                                if (photo !in selectedPhotos) {
                                    selectedPhotos = selectedPhotos + photo
                                }
                            },
                            modifier = Modifier.fillMaxSize(),
                            state = gridState
                        )
                    }
                }
            }
        }
    }

    // Sort Menu
    if (sortMenuState.expanded) {
        PhotoSortMenu(
            currentSort = currentSort,
            onSortSelected = { viewModel.setSortOption(it) },
            expanded = true,
            onDismiss = { sortMenuState.onDismiss() }
        )
    }

    // File Operations Menu
    if (showOperationsMenu) {
        selectedPhoto?.let { photo ->
            CategoryDetailDialogs.FileOperationsMenu(
                photoName = photo.displayName,
                onDismiss = { showOperationsMenu = false; selectedPhoto = null },
                onOperation = { handleFileOperation(it) }
            )
        }
    }

    // Copy To Dialog
    if (showCopyDialog && selectedPhoto != null) {
        CategoryDetailDialogs.CopyToDialog(
            categories = allCategories,
            currentCategoryId = 0L,
            title = "复制到",
            onDismiss = { showCopyDialog = false; selectedPhoto = null },
            onCategorySelected = { handleCopyTo(it) }
        )
    }

    // Move To Dialog
    if (showMoveDialog && selectedPhoto != null) {
        CategoryDetailDialogs.CopyToDialog(
            categories = allCategories,
            currentCategoryId = 0L,
            title = "移动到",
            onDismiss = { showMoveDialog = false; selectedPhoto = null },
            onCategorySelected = { handleMoveTo(it) }
        )
    }

    // Rename Dialog
    if (showRenameDialog) {
        selectedPhoto?.let { photo ->
            CategoryDetailDialogs.RenameDialog(
                currentName = photo.displayName,
                onDismiss = { showRenameDialog = false; selectedPhoto = null },
                onConfirm = { newName ->
                    scope.launch {
                        // 保险库照片：直接改显示名
                        if (photo.isVaultPhoto) {
                            when (val result = viewModel.renameVaultPhoto(photo, newName)) {
                                is Result.Loading -> {}
                                is Result.Success -> {
                                    Toast.makeText(
                                        context.applicationContext,
                                        context.getString(R.string.vault_renamed),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    viewModel.updatePhotoName(photo, newName)
                                }
                                is Result.Error -> {
                                    Toast.makeText(context.applicationContext, result.message, Toast.LENGTH_LONG).show()
                                }
                            }
                            showRenameDialog = false
                            selectedPhoto = null
                            return@launch
                        }
                        try {
                            // Run rename on IO thread
                            val result = withContext(Dispatchers.IO) {
                                fileOperations.renamePhoto(photo, newName)
                            }

                            // Show result on Main thread with application context
                            withContext(Dispatchers.Main) {
                                when (result) {
                                    is FileOperationResult.Success -> {
                                        Toast.makeText(context.applicationContext, result.message, Toast.LENGTH_SHORT).show()
                                        // Refresh photos to show the renamed photo immediately
                                        viewModel.loadPhotos()
                                    }
                                    is FileOperationResult.Error -> {
                                        Toast.makeText(context.applicationContext, result.message, Toast.LENGTH_LONG).show()
                                    }
                                    is FileOperationResult.NeedsPermission -> {
                                        pendingRename = PendingRename(photo, newName)
                                        val intentSenderRequest = androidx.activity.result.IntentSenderRequest.Builder(
                                            result.pendingIntent.intentSender
                                        ).build()
                                        intentSenderLauncher.launch(intentSenderRequest)
                                    }
                                }
                                showRenameDialog = false
                                selectedPhoto = null
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context.applicationContext, "重命名失败：${e.message}", Toast.LENGTH_LONG).show()
                                showRenameDialog = false
                                selectedPhoto = null
                            }
                        }
                    }
                }
            )
        }
    }

    // Info Dialog
    val currentPhotoInfo = photoInfo
    if (showInfoDialog && currentPhotoInfo != null) {
        CategoryDetailDialogs.PhotoInfoDialog(
            photoInfo = currentPhotoInfo,
            onDismiss = { 
                showInfoDialog = false
                photoInfo = null
                selectedPhoto = null 
            }
        )
    }
    
    // Pattern lock dialog for delete confirmation
    if (showPatternLockForDelete) {
        com.rapii.snapje.ui.components.PatternLockDialog(
            onDismiss = {
                showPatternLockForDelete = false
                photoPendingDeleteAfterLock = null
            },
            onUnlock = {
                showPatternLockForDelete = false
                // Proceed with deletion after successful unlock
                photoPendingDeleteAfterLock?.let { photoToDelete ->
                    scope.launch {
                        // 保险库照片：直接删除（密文文件 + DB 记录）
                        if (photoToDelete.isVaultPhoto) {
                            when (val result = viewModel.deleteVaultPhoto(photoToDelete)) {
                                is Result.Loading -> {}
                                is Result.Success -> {
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.vault_photo_deleted),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    viewModel.removePhotoFromList(photoToDelete)
                                }
                                is Result.Error -> {
                                    Toast.makeText(context.applicationContext, result.message, Toast.LENGTH_LONG).show()
                                }
                            }
                            photoPendingDeleteAfterLock = null
                            return@launch
                        }
                        try {
                            val result = withContext(Dispatchers.IO) {
                                trashRepository.moveToTrash(photoToDelete)
                            }
                            withContext(Dispatchers.Main) {
                                when (result) {
                                    is FileOperationResult.Success -> {
                                        snackbarHostState.showSnackbar("照片已移至回收站")
                                        viewModel.removePhotoFromList(photoToDelete)
                                    }
                                    is FileOperationResult.Error -> {
                                        Toast.makeText(context.applicationContext, result.message, Toast.LENGTH_LONG).show()
                                    }
                                    is FileOperationResult.NeedsPermission -> {
                                        L.d("CategoryDetailScreen", "NeedsPermission: Setting pendingDeletePhoto and launching intent")
                                        pendingDeletePhoto = PendingDelete(photoToDelete)
                                        val intentSenderRequest = androidx.activity.result.IntentSenderRequest.Builder(
                                            result.pendingIntent.intentSender
                                        ).build()
                                        L.d("CategoryDetailScreen", "Launching intentSenderLauncher")
                                        intentSenderLauncher.launch(intentSenderRequest)
                                    }
                                }
                            }
                        } finally {
                            photoPendingDeleteAfterLock = null
                        }
                    }
                }
            },
            title = stringResource(R.string.delete_vault_photo)
        )
    }
}
