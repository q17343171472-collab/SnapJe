package com.rapii.snapje.ui

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import coil.size.Size
import com.rapii.snapje.util.ImageLoaderFactory
import com.rapii.snapje.data.FileOperationResult
import com.rapii.snapje.data.FileOperationType
import com.rapii.snapje.data.FileOperations
import com.rapii.snapje.data.PhotoItem
import com.rapii.snapje.data.TrashRepository
import com.rapii.snapje.ui.components.CopyToDialog
import com.rapii.snapje.ui.components.FileOperationsBottomSheet
import com.rapii.snapje.ui.components.RenameDialog
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.log10
import kotlin.math.pow

/**
 * Photo Gallery Screen with smooth swipe navigation and pinch-to-zoom.
 *
 * @param vaultFullImageProvider 保险库照片的解密原图 URI 提供者（全屏显示时按需解密到临时文件）；
 *                               非保险库照片不使用。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PhotoGalleryScreen(
    photos: List<PhotoItem>,
    initialPhotoIndex: Int = 0,
    onBack: () -> Unit,
    onShare: (PhotoItem) -> Unit = {},
    allCategories: List<com.rapii.snapje.data.Category> = emptyList(),
    vaultFullImageProvider: suspend (PhotoItem) -> Uri? = { null },
    /** 保险库照片删除回调（生物识别确认后触发） */
    vaultDeleteHandler: ((PhotoItem) -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var categories by remember { mutableStateOf<List<com.rapii.snapje.data.Category>>(allCategories) }
    var isLoadingCategories by remember { mutableStateOf(false) }

    // 保险库照片删除确认（生物识别）
    var vaultPendingDelete by remember { mutableStateOf<PhotoItem?>(null) }

    val imageLoader = remember(context) {
        ImageLoaderFactory.createVaultLoader(context)
    }

    val fileOperations = remember { FileOperations(context) }
    val trashRepository = remember { TrashRepository(context) }

    val pagerState = rememberPagerState(
        initialPage = initialPhotoIndex.coerceIn(0, (photos.size - 1).coerceAtLeast(0)),
        pageCount = { photos.size }
    )

    var isPagingEnabled by remember { mutableStateOf(true) }

    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(backDispatcher) {
        val callback = object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                onBack()
            }
        }
        backDispatcher?.addCallback(lifecycleOwner, callback)
        onDispose { callback.remove() }
    }

    var showInfo by remember { mutableStateOf(false) }
    val currentPhoto = photos.getOrNull(pagerState.currentPage)

    // File operations state
    var showOperationsMenu by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showCopyDialog by remember { mutableStateOf(false) }
    var showMoveDialog by remember { mutableStateOf(false) }

    var pendingDeletePhoto by remember { mutableStateOf<PhotoItem?>(null) }
    
    // Crop activity launcher
    val cropLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            Toast.makeText(context, "Photo cropped successfully", Toast.LENGTH_SHORT).show()
        }
    }
    
    // Function to launch crop activity
    fun launchCropActivity(photo: PhotoItem) {
        try {
            val intent = Intent("com.android.camera.action.CROP").apply {
                setDataAndType(photo.uri, "image/*")
                putExtra("crop", "true")
                putExtra("aspectX", 0)
                putExtra("aspectY", 0)
                putExtra("outputX", 1024)
                putExtra("outputY", 1024)
                putExtra("return-data", false)
                putExtra(MediaStore.EXTRA_OUTPUT, photo.uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }
            cropLauncher.launch(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Crop not available on this device", Toast.LENGTH_SHORT).show()
        }
    }
    data class PendingRename(val photo: PhotoItem, val newName: String)
    var pendingRename by remember { mutableStateOf<PendingRename?>(null) }

    // Generic intent launcher for permissions
    val intentSenderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            pendingRename?.let { pending ->
                scope.launch {
                    when (val renameResult = fileOperations.renamePhoto(pending.photo, pending.newName)) {
                        is FileOperationResult.Success -> {
                            Toast.makeText(context, "Photo renamed", Toast.LENGTH_SHORT).show()
                        }
                        is FileOperationResult.Error -> {
                            Toast.makeText(context, renameResult.message, Toast.LENGTH_SHORT).show()
                        }
                        else -> {}
                    }
                    pendingRename = null
                }
            } ?: run {
                pendingDeletePhoto?.let { photo ->
                    scope.launch {
                        when (val deleteResult = trashRepository.moveToTrash(photo)) {
                            is FileOperationResult.Success -> {
                                Toast.makeText(context, "Photo moved to trash", Toast.LENGTH_SHORT).show()
                            }
                            is FileOperationResult.Error -> {
                                Toast.makeText(context, deleteResult.message, Toast.LENGTH_SHORT).show()
                            }
                            else -> {}
                        }
                        pendingDeletePhoto = null
                    }
                }
            }
        } else {
            Toast.makeText(context, "Operation cancelled", Toast.LENGTH_SHORT).show()
            pendingRename = null
            pendingDeletePhoto = null
        }
    }

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = currentPhoto?.displayName ?: "",
                            style = MaterialTheme.typography.titleMedium.copy(color = Color.White),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${pagerState.currentPage + 1} / ${photos.size}",
                            style = MaterialTheme.typography.labelSmall.copy(color = Color.White.copy(alpha = 0.7f))
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = { showInfo = !showInfo }) {
                        Icon(Icons.Default.Info, contentDescription = "Photo info", tint = Color.White)
                    }
                    currentPhoto?.let { photo ->
                        IconButton(onClick = { onShare(photo) }) {
                            Icon(Icons.Default.Share, contentDescription = "Share photo", tint = Color.White)
                        }
                    }
                    IconButton(onClick = { showOperationsMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More options", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black.copy(alpha = 0.6f))
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = isPagingEnabled,
                pageSpacing = 16.dp
            ) { pageIndex ->
                val isCurrentPage = pageIndex == pagerState.currentPage

                PhotoPage(
                    photo = photos[pageIndex],
                    imageLoader = imageLoader,
                    isCurrentPage = isCurrentPage,
                    vaultFullImageProvider = vaultFullImageProvider,
                    onZoomStateChanged = { zoomed ->
                        if (isCurrentPage) {
                            isPagingEnabled = !zoomed
                        }
                    }
                )
            }

            if (showInfo && currentPhoto != null) {
                Box(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp)) {
                    GalleryPhotoInfoOverlay(
                        photo = currentPhoto,
                        onClose = { showInfo = false }
                    )
                }
            }

            // File Operations Menu
            if (showOperationsMenu && currentPhoto != null) {
                FileOperationsBottomSheet(
                    photoName = currentPhoto.displayName,
                    onDismiss = { showOperationsMenu = false },
                    onOperation = { operation ->
                        // 保险库照片：复制 / 移动 / 裁剪 / 隐藏 / 重命名不支持
                        if (currentPhoto.isVaultPhoto && operation in setOf(
                                FileOperationType.RENAME,
                                FileOperationType.COPY,
                                FileOperationType.MOVE,
                                FileOperationType.CROP,
                                FileOperationType.HIDE
                            )
                        ) {
                            showOperationsMenu = false
                            Toast.makeText(context, "该操作仅支持系统相册照片", Toast.LENGTH_SHORT).show()
                            return@FileOperationsBottomSheet
                        }
                        when (operation) {
                            FileOperationType.DELETE -> {
                                showOperationsMenu = false
                                if (currentPhoto.isVaultPhoto) {
                                    vaultPendingDelete = currentPhoto
                                    return@FileOperationsBottomSheet
                                }
                                scope.launch {
                                    when (val result = trashRepository.moveToTrash(currentPhoto)) {
                                        is FileOperationResult.Success -> {
                                            Toast.makeText(context, "Photo moved to trash", Toast.LENGTH_SHORT).show()
                                        }
                                        is FileOperationResult.Error -> {
                                            Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
                                        }
                                        is FileOperationResult.NeedsPermission -> {
                                            pendingDeletePhoto = currentPhoto
                                            val intentSenderRequest = androidx.activity.result.IntentSenderRequest.Builder(
                                                result.pendingIntent.intentSender
                                            ).build()
                                            intentSenderLauncher.launch(intentSenderRequest)
                                        }
                                    }
                                }
                            }
                            FileOperationType.RENAME -> {
                                showOperationsMenu = false
                                showRenameDialog = true
                            }
                            FileOperationType.COPY -> {
                                showOperationsMenu = false
                                showCopyDialog = true
                            }
                            FileOperationType.MOVE -> {
                                showOperationsMenu = false
                                showMoveDialog = true
                            }
                            FileOperationType.CROP -> {
                                showOperationsMenu = false
                                // Launch crop activity for the current photo
                                launchCropActivity(currentPhoto)
                            }
                            FileOperationType.SHARE -> {
                                showOperationsMenu = false
                                onShare(currentPhoto)
                            }
                            FileOperationType.HIDE -> {
                                showOperationsMenu = false
                            }
                            FileOperationType.INFO -> {
                                showOperationsMenu = false
                                showInfo = true
                            }
                        }
                    }
                )
            }

            // 保险库照片删除确认（生物识别）
            vaultPendingDelete?.let { photo ->
                com.rapii.snapje.ui.components.PatternLockDialog(
                    onDismiss = { vaultPendingDelete = null },
                    onUnlock = {
                        val pending = vaultPendingDelete
                        vaultPendingDelete = null
                        pending?.let { vaultDeleteHandler?.invoke(it) }
                    },
                    title = "删除保险库照片"
                )
            }

            // Rename Dialog
            if (showRenameDialog && currentPhoto != null) {
                RenameDialog(
                    currentName = currentPhoto.displayName,
                    onDismiss = { showRenameDialog = false },
                    onConfirm = { newName ->
                        showRenameDialog = false
                        val photo = currentPhoto
                        scope.launch {
                            when (val result = fileOperations.renamePhoto(photo, newName)) {
                                is FileOperationResult.Success -> {
                                    Toast.makeText(context, "File renamed successfully", Toast.LENGTH_SHORT).show()
                                }
                                is FileOperationResult.Error -> {
                                    Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
                                }
                                is FileOperationResult.NeedsPermission -> {
                                    pendingRename = PendingRename(photo, newName)
                                    val intentSenderRequest = androidx.activity.result.IntentSenderRequest.Builder(
                                        result.pendingIntent.intentSender
                                    ).build()
                                    intentSenderLauncher.launch(intentSenderRequest)
                                }
                            }
                        }
                    }
                )
            }

            // Copy Dialog
            if (showCopyDialog && currentPhoto != null) {
                CopyToDialog(
                    categories = categories,
                    currentCategoryId = currentPhoto.bucketId ?: -1L,
                    isLoading = isLoadingCategories,
                    title = "Copy to",
                    onDismiss = { showCopyDialog = false },
                    onCategorySelected = { targetCategory ->
                        showCopyDialog = false
                        scope.launch {
                            val result = fileOperations.copyPhoto(currentPhoto, targetCategory.id)
                            if (result is FileOperationResult.Success) {
                                Toast.makeText(context, "File copied to ${targetCategory.displayName}", Toast.LENGTH_SHORT).show()
                            } else if (result is FileOperationResult.Error) {
                                Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                )
            }

            // Move Dialog
            if (showMoveDialog && currentPhoto != null) {
                CopyToDialog(
                    categories = categories,
                    currentCategoryId = currentPhoto.bucketId ?: -1L,
                    isLoading = isLoadingCategories,
                    title = "Move to",
                    onDismiss = { showMoveDialog = false },
                    onCategorySelected = { targetCategory ->
                        showMoveDialog = false
                        scope.launch {
                            val result = fileOperations.movePhoto(currentPhoto, targetCategory.id)
                            if (result is FileOperationResult.Success) {
                                Toast.makeText(context, "File moved to ${targetCategory.displayName}", Toast.LENGTH_SHORT).show()
                            } else if (result is FileOperationResult.Error) {
                                Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
                            } else if (result is FileOperationResult.NeedsPermission) {
                                pendingDeletePhoto = currentPhoto
                                val intentSenderRequest = androidx.activity.result.IntentSenderRequest.Builder(
                                    result.pendingIntent.intentSender
                                ).build()
                                intentSenderLauncher.launch(intentSenderRequest)
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun PhotoPage(
    photo: PhotoItem,
    imageLoader: ImageLoader,
    isCurrentPage: Boolean,
    onZoomStateChanged: (Boolean) -> Unit,
    vaultFullImageProvider: suspend (PhotoItem) -> Uri? = { null }
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var isZoomed by remember { mutableStateOf(false) }

    // 保险库照片：当前页可见时按需解密原图到临时文件（展示前先显示缩略图）
    var fullDisplayUri by remember(photo.vaultId) { mutableStateOf<Uri?>(null) }
    LaunchedEffect(isCurrentPage, photo.vaultId) {
        if (photo.isVaultPhoto && isCurrentPage) {
            val cached = fullDisplayUri
            // 临时文件可能被清理（上锁/过期），失效时重新解密
            if (cached == null || !java.io.File(cached.path.orEmpty()).exists()) {
                fullDisplayUri = runCatching { vaultFullImageProvider(photo) }.getOrNull()
            }
        }
    }
    val displayUri = fullDisplayUri ?: photo.uri
    
    // Smooth zoom animation using spring (less bouncy for smoother feel)
    val targetScale by animateFloatAsState(
        targetValue = scale,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
        ),
        label = "zoomScale"
    )

    LaunchedEffect(isCurrentPage) {
        if (!isCurrentPage) {
            scale = 1f
            offset = Offset.Zero
            isZoomed = false
            onZoomStateChanged(false)
        }
    }

    // Disable pager scrolling when zoomed
    LaunchedEffect(scale) {
        onZoomStateChanged(scale > 1f)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
            // Consume all pointer events to prevent pager interception
            .pointerInput(Unit) {
                awaitEachGesture {
                    var pinchStartDistance = 0f
                    var pinchZooming = false
                    val scaleAtPinchStart = scale

                    do {
                        val event = awaitPointerEvent()
                        val changes = event.changes

                        if (changes.size >= 2) {
                            if (!pinchZooming) {
                                // Start of pinch - record initial finger distance and current scale
                                pinchStartDistance = kotlin.math.hypot(
                                    changes[0].position.x - changes[1].position.x,
                                    changes[0].position.y - changes[1].position.y
                                )
                                pinchZooming = true
                            }

                            // Calculate current distance between fingers
                            val currentDistance = kotlin.math.hypot(
                                changes[0].position.x - changes[1].position.x,
                                changes[0].position.y - changes[1].position.y
                            )

                            // Calculate zoom factor
                            val zoomFactor = if (pinchStartDistance > 0) {
                                currentDistance / pinchStartDistance
                            } else 1f

                            // Apply zoom based on scale at start of pinch
                            val newScale = (scaleAtPinchStart * zoomFactor).coerceIn(1f, 5f)

                            if (newScale > 1.01f) {
                                scale = newScale
                                if (!isZoomed) {
                                    isZoomed = true
                                    onZoomStateChanged(true)
                                }
                            } else if (newScale <= 1f) {
                                scale = 1f
                                offset = Offset.Zero
                                if (isZoomed) {
                                    isZoomed = false
                                    onZoomStateChanged(false)
                                }
                            }

                            // Consume all pointer changes to prevent pager interception
                            changes.forEach { it.consume() }
                        } else if (changes.size == 1 && scale > 1f) {
                            // Single finger drag when zoomed - consume to prevent pager swipe
                            changes.forEach { it.consume() }
                        } else {
                            pinchZooming = false
                            pinchStartDistance = 0f
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
            .then(
                if (scale > 1f) {
                    Modifier.pointerInput(scale) {
                        detectDragGestures(
                            onDragStart = { },
                            onDragEnd = { },
                            onDragCancel = { },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                offset += dragAmount
                            }
                        )
                    }
                } else {
                    Modifier
                }
            )
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { tapOffset ->
                        if (scale > 1f) {
                            // Reset zoom on double tap when zoomed in
                            scale = 1f
                            offset = Offset.Zero
                            isZoomed = false
                            onZoomStateChanged(false)
                        } else {
                            // Zoom to 3x on double tap when not zoomed
                            scale = 3f
                            isZoomed = true
                            onZoomStateChanged(true)
                        }
                    },
                    onTap = { } // Consume single taps to prevent pager from handling them
                )
            }
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(displayUri)
                .crossfade(false)
                .size(Size.ORIGINAL)
                .bitmapConfig(Bitmap.Config.ARGB_8888)
                .build(),
            contentDescription = photo.displayName,
            imageLoader = imageLoader,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = targetScale
                    scaleY = targetScale
                    translationX = offset.x
                    translationY = offset.y
                },
            contentScale = ContentScale.Fit
        )
    }
}

@Composable
fun GalleryPhotoInfoOverlay(
    photo: PhotoItem,
    onClose: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth(0.9f)
            .padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.8f)),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Photo Details",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            InfoRow(label = "Filename", value = photo.displayName)
            InfoRow(label = "Uri", value = photo.uri.toString())

            // MediaStore DATE_TAKEN / VaultPhoto.dateTaken 均为毫秒时间戳，勿再乘 1000
            val dateText = if (photo.dateTaken > 0) {
                SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
                    .format(Date(photo.dateTaken))
            } else {
                "Unknown"
            }
            InfoRow(label = "Date", value = dateText)

            InfoRow(label = "Size", value = formatFileSize(photo.size))
            InfoRow(label = "Mime Type", value = photo.mimeType)
        }
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.6f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

fun formatFileSize(size: Long): String {
    if (size <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (log10(size.toDouble()) / log10(1024.0)).toInt()
    return String.format(Locale.getDefault(), "%.1f %s", size / 1024.0.pow(digitGroups.toDouble()), units[digitGroups])
}
