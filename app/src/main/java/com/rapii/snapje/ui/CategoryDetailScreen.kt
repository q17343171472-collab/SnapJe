package com.rapii.snapje.ui

import android.app.Activity
import android.media.MediaScannerConnection
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
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
import com.rapii.snapje.util.L
import com.rapii.snapje.data.PhotoRepository
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
            allCategories = allCategories
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
                    Toast.makeText(context, "${photosToConfirm.size} photos moved to trash", Toast.LENGTH_SHORT).show()
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
                Toast.makeText(context, "${photosToConfirm.size} photos moved to trash", Toast.LENGTH_SHORT).show()
            }

            // Handle Cancellation
            if (pendingRename != null) {
                Toast.makeText(context, "Rename cancelled", Toast.LENGTH_SHORT).show()
                pendingRename = null
            } else {
                val photosToCancel = if (pendingBatchDeletePhotos.isNotEmpty()) {
                    pendingBatchDeletePhotos
                } else {
                    pendingDeletePhoto?.let { listOf(it) } ?: emptyList()
                }

                trashRepository.cancelTrashBatch(photosToCancel.map { it.photo })

                if (photosToCancel.isNotEmpty()) {
                    Toast.makeText(context, "Delete cancelled", Toast.LENGTH_SHORT).show()
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
                Toast.makeText(context, "Photo moved to trash", Toast.LENGTH_SHORT).show()
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

        when (operation) {
            FileOperationType.COPY -> { showOperationsMenu = false; showCopyDialog = true; return }
            FileOperationType.MOVE -> { showOperationsMenu = false; showMoveDialog = true; return }
            else -> { }
        }

        scope.launch {
            try {
                when (operation) {
                    FileOperationType.DELETE -> {
                        showOperationsMenu = false
                        selectedPhoto = null
                        val result = withContext(Dispatchers.IO) {
                            trashRepository.moveToTrash(photo)
                        }
                        withContext(Dispatchers.Main) {
                            when (result) {
                                is FileOperationResult.Success -> {
                                    snackbarHostState.showSnackbar("Photo moved to trash")
                                    viewModel.removePhotoFromList(photo)
                                }
                                is FileOperationResult.Error -> {
                                    Toast.makeText(context.applicationContext, result.message, Toast.LENGTH_LONG).show()
                                }
                                is FileOperationResult.NeedsPermission -> {
                                    L.d("CategoryDetailScreen", "NeedsPermission: Setting pendingDeletePhoto and launching intent")
                                    pendingDeletePhoto = PendingDelete(photo)
                                    val intentSenderRequest = androidx.activity.result.IntentSenderRequest.Builder(
                                        result.pendingIntent.intentSender
                                    ).build()
                                    L.d("CategoryDetailScreen", "Launching intentSenderLauncher")
                                    intentSenderLauncher.launch(intentSenderRequest)
                                }
                            }
                        }
                    }
                    FileOperationType.RENAME -> { showRenameDialog = true; return@launch }
                    FileOperationType.SHARE -> {
                        showOperationsMenu = false
                        selectedPhoto = null
                        // Share doesn't need IO dispatcher as it just launches an Intent
                        try {
                            fileOperations.sharePhoto(photo)
                        } catch (e: Exception) {
                            Toast.makeText(context.applicationContext, "Share failed: ${e.message}", Toast.LENGTH_LONG).show()
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
                Toast.makeText(context.applicationContext, "Operation failed: ${e.message}", Toast.LENGTH_LONG).show()
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
                            Toast.makeText(context.applicationContext, "Copied to ${destCategory.displayName}", Toast.LENGTH_SHORT).show()
                            MediaScannerConnection.scanFile(context.applicationContext, arrayOf(destCategory.path), null, null)
                            // Refresh current category to show any changes
                            viewModel.loadPhotos()
                        }
                        is FileOperationResult.Error -> {
                            Toast.makeText(context.applicationContext, result.message, Toast.LENGTH_LONG).show()
                        }
                        is FileOperationResult.NeedsPermission -> {
                            Toast.makeText(context.applicationContext, "Permission needed", Toast.LENGTH_SHORT).show()
                        }
                    }
                    showCopyDialog = false
                    selectedPhoto = null
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context.applicationContext, "Copy failed: ${e.message}", Toast.LENGTH_LONG).show()
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
                            Toast.makeText(context.applicationContext, "Moved to ${destCategory.displayName}", Toast.LENGTH_SHORT).show()
                            MediaScannerConnection.scanFile(context.applicationContext, arrayOf(destCategory.path), null, null)
                            viewModel.loadPhotos()
                        }
                        is FileOperationResult.Error -> {
                            Toast.makeText(context.applicationContext, result.message, Toast.LENGTH_LONG).show()
                        }
                        is FileOperationResult.NeedsPermission -> {
                            Toast.makeText(context.applicationContext, "Permission needed", Toast.LENGTH_SHORT).show()
                        }
                    }
                    showMoveDialog = false
                    selectedPhoto = null
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context.applicationContext, "Move failed: ${e.message}", Toast.LENGTH_LONG).show()
                    showMoveDialog = false
                    selectedPhoto = null
                }
            }
        }
    }

    // Function to handle batch delete of selected photos
    fun handleBatchDelete() {
        scope.launch {
            when (val result = trashRepository.moveToTrashBatch(selectedPhotos.toList())) {
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
                    categoryName = uiState.category?.displayName ?: "Category",
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
                                Text(text = "No photos in this folder", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = "Take some photos or move images here",
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
            title = "Copy to",
            onDismiss = { showCopyDialog = false; selectedPhoto = null },
            onCategorySelected = { handleCopyTo(it) }
        )
    }

    // Move To Dialog
    if (showMoveDialog && selectedPhoto != null) {
        CategoryDetailDialogs.CopyToDialog(
            categories = allCategories,
            currentCategoryId = 0L,
            title = "Move to",
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
                                Toast.makeText(context.applicationContext, "Rename failed: ${e.message}", Toast.LENGTH_LONG).show()
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
}
