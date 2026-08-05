package com.rapii.snapje.ui

import android.app.Activity
import android.content.ContentUris
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SaveAlt
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
import com.rapii.snapje.data.SettingsManager
import com.rapii.snapje.util.CameraLauncher
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
import kotlin.math.abs
import kotlin.math.hypot

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

    // 设置管理器（捏合列数持久化 + 自动删除原图开关）
    val settingsManager = remember { SettingsManager(context.applicationContext) }

    // ---------------------------------------------------------------------
    // 双指捏合缩放网格（苹果相册风格）：缩小→列数变多，放大→列数变少
    // ---------------------------------------------------------------------
    val minGridColumns = 2
    val maxGridColumns = 14
    // 当前列数（松手后持久化到全局设置，所有分组共享）
    var gridColumns by remember { mutableIntStateOf(3) }
    // 列数变化动画：丝滑过渡
    val animatedGridColumns by animateIntAsState(
        targetValue = gridColumns,
        animationSpec = tween(220),
        label = "gridColumns"
    )
    // 读取全局列数设置
    LaunchedEffect(Unit) {
        gridColumns = settingsManager.getGridColumns().coerceIn(minGridColumns, maxGridColumns)
    }
    // 捏合手势：两指距离变化 → 列数增减；松手后保存
    val pinchModifier = Modifier.pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            var lastSpan = -1f
            var gestureColumns = gridColumns
            var pinchChanged = false
            do {
                val event = awaitPointerEvent()
                val pressed = event.changes.filter { it.pressed }
                if (pressed.size >= 2) {
                    val span = hypot(
                        pressed[0].position.x - pressed[1].position.x,
                        pressed[0].position.y - pressed[1].position.y
                    )
                    if (lastSpan > 0f) {
                        val ratio = span / lastSpan
                        // 距离变化超过 10% 才调整一列（避免抖动）
                        if (ratio > 1.10f && gestureColumns > minGridColumns) {
                            gestureColumns--
                            lastSpan = span
                            pinchChanged = true
                        } else if (ratio < 0.90f && gestureColumns < maxGridColumns) {
                            gestureColumns++
                            lastSpan = span
                            pinchChanged = true
                        }
                        if (gestureColumns != gridColumns) {
                            gridColumns = gestureColumns
                        }
                    } else {
                        lastSpan = span
                    }
                } else {
                    lastSpan = -1f
                }
                // 捏合时消费事件，避免同时触发滚动/点击
                if (pinchChanged) {
                    event.changes.forEach { it.consume() }
                }
            } while (event.changes.any { it.pressed })
            // 松手：锁定当前列数并全局保存
            if (pinchChanged) {
                scope.launch { settingsManager.setGridColumns(gridColumns) }
            }
        }
    }

    // ---------------------------------------------------------------------
    // 分组内导入（右下角按钮）+ 导出到相册
    // ---------------------------------------------------------------------
    var showImportSheet by remember { mutableStateOf(false) }
    var pendingImportUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    var albumName by remember { mutableStateOf("") }
    var isImporting by remember { mutableStateOf(false) }

    // 删除原图相关（支持批量）
    var pendingDeleteOriginalUris by remember { mutableStateOf<List<Uri>>(emptyList()) }

    // 相机
    val cameraLauncher = remember { CameraLauncher() }

    // 删除相册原图（Android 11+ 弹系统确认框，支持批量）
    val deleteOriginalLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        pendingDeleteOriginalUris = emptyList()
        Toast.makeText(
            context,
            if (result.resultCode == Activity.RESULT_OK) R.string.original_deleted else R.string.original_kept,
            Toast.LENGTH_SHORT
        ).show()
    }

    fun cleanupCameraFile() {
        val file = cameraLauncher.getLastPhotoFile()
        if (file != null) runCatching { file.delete() }
        cameraLauncher.clearPhotoUri()
        pendingCameraUri = null
    }

    /** Photo Picker 地址转 MediaStore 标准地址（可删除） */
    fun toMediaStoreUri(uri: Uri): Uri {
        if (uri.scheme == "content" && uri.authority?.contains("media") == true &&
            !uri.path.orEmpty().contains("/picker/")
        ) {
            return uri
        }
        val id = runCatching {
            context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns._ID), null, null, null)
                ?.use { if (it.moveToFirst()) it.getLong(it.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)) else null }
        }.getOrNull() ?: uri.lastPathSegment?.toLongOrNull()
        return if (id != null) {
            // 按 MIME 区分图片/视频集合（视频 URI 用 Video 表，否则删除会失败）
            val mime = runCatching { context.contentResolver.getType(uri) }.getOrNull() ?: ""
            if (mime.startsWith("video/")) {
                ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
            } else {
                ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
            }
        } else {
            uri
        }
    }

    fun deleteOriginalsFromGallery(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val resolver = context.contentResolver
        val mediaUris = uris.map { toMediaStoreUri(it) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val pendingIntent = MediaStore.createDeleteRequest(resolver, mediaUris)
                val request = androidx.activity.result.IntentSenderRequest.Builder(pendingIntent).build()
                deleteOriginalLauncher.launch(request)
                return
            } catch (e: Exception) {
                L.e("CategoryDetail", "createDeleteRequest failed", e)
            }
        }
        // 低版本：逐个删除
        var deletedCount = 0
        mediaUris.forEach { mediaUri ->
            val deleted = runCatching { resolver.delete(mediaUri, null, null) }.getOrDefault(0)
            if (deleted > 0) deletedCount++
        }
        Toast.makeText(
            context,
            if (deletedCount > 0) R.string.original_deleted else R.string.original_delete_failed,
            if (deletedCount > 0) Toast.LENGTH_SHORT else Toast.LENGTH_LONG
        ).show()
        pendingDeleteOriginalUris = emptyList()
    }

    // 分组内导入：直接导入当前分组，不再弹确认框
    fun performImportBatch(uris: List<Uri>, album: String) {
        if (uris.isEmpty()) return
        if (isImporting) return
        isImporting = true
        scope.launch {
            var successCount = 0
            val galleryUris = mutableListOf<Uri>()
            uris.forEach { uri ->
                val result = viewModel.addPhotoToVault(uri, album)
                if (result.isSuccess) {
                    successCount++
                    if (uri != pendingCameraUri) galleryUris.add(uri)
                }
            }
            isImporting = false
            if (successCount > 0) {
                Toast.makeText(
                    context,
                    if (uris.size > 1) "已加密导入 $successCount 项到保险库" else context.getString(R.string.import_success),
                    Toast.LENGTH_SHORT
                ).show()
                viewModel.loadPhotos()
                // 系统相册导入的：按设置批量删除原图或询问（相机临时文件自动清理）
                if (galleryUris.isNotEmpty()) {
                    if (settingsManager.isAutoDeleteOriginal()) {
                        deleteOriginalsFromGallery(galleryUris)
                    } else {
                        pendingDeleteOriginalUris = galleryUris
                    }
                }
            } else {
                Toast.makeText(
                    context,
                    context.getString(R.string.import_failed, "导入失败"),
                    Toast.LENGTH_LONG
                ).show()
            }
            cleanupCameraFile()
        }
    }

    // 清除待导入状态
    fun clearPendingImport() {
        pendingImportUris = emptyList()
        cleanupCameraFile()
        showImportSheet = false
    }

    // 选择照片/视频（直接调起系统 Gallery，支持长按+滑动手势多选）
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data ?: return@rememberLauncherForActivityResult
            val uris = mutableListOf<Uri>()
            // 多选：从 clipData 取
            data.clipData?.let { clipData ->
                for (i in 0 until clipData.itemCount) {
                    uris.add(clipData.getItemAt(i).uri)
                }
            }
            // 单选：从 data 取
            data.data?.let { uris.add(it) }
            if (uris.isNotEmpty()) {
                pendingImportUris = uris
                // 分组内导入：直接导入当前分组，不再弹确认框
                albumName = uiState.category?.displayName ?: "我的保险库"
                performImportBatch(uris, albumName)
            }
        }
    }

    /**
     * 备用方案：ACTION_OPEN_DOCUMENT 的 launcher（通用文件选择器，直接返回 Uri 列表）。
     */
    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            pendingImportUris = uris
            // 分组内导入：直接导入当前分组，不再弹确认框
            albumName = uiState.category?.displayName ?: "我的保险库"
            performImportBatch(uris, albumName)
        }
    }

    // 相机结果：拍照后直接导入当前分组
    val cameraResultLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = cameraLauncher.getLastPhotoUri()
        if (result.resultCode == Activity.RESULT_OK && uri != null) {
            pendingCameraUri = uri
            // 分组内拍照：直接导入当前分组，不再弹确认框
            albumName = uiState.category?.displayName ?: "我的保险库"
            performImportBatch(listOf(uri), albumName)
        } else {
            cameraLauncher.clearPhotoUri()
        }
    }

    /**
     * 打开系统原生相册（ACTION_PICK + Images 表，绝大多数设备支持；仅图片，不含视频）。
     */
    fun launchSystemGallery() {
        // 用 Images 表：之前验证可正常启动，只是不含视频；
        // 若仍找不到处理者则回退到文件选择器，不闪退。
        val pickIntent = Intent(
            Intent.ACTION_PICK,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        ).apply {
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        if (pickIntent.resolveActivity(context.packageManager) != null) {
            galleryLauncher.launch(pickIntent)
        } else {
            Toast.makeText(context, "当前设备不支持系统相册，请改用文件选择器", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * 打开系统文件选择器（ACTION_OPEN_DOCUMENT，所有设备可用，勾选多选）。
     */
    fun launchFilePicker() {
        openDocumentLauncher.launch(arrayOf("image/*", "video/*"))
    }

    fun launchCamera() {
        runCatching {
            cameraResultLauncher.launch(cameraLauncher.createCaptureIntent(context))
        }.onFailure { e ->
            L.e("CategoryDetail", "Camera launch failed: ${e.message}")
            Toast.makeText(context, "无法打开相机", Toast.LENGTH_SHORT).show()
        }
    }

    // 导出单张保险库照片/视频到系统相册
    fun exportVaultPhoto(photo: PhotoItem) {
        scope.launch {
            when (val result = viewModel.exportVaultPhoto(photo)) {
                is Result.Success -> Toast.makeText(context, "已保存到相册", Toast.LENGTH_SHORT).show()
                is Result.Error -> Toast.makeText(context, "保存失败：${result.message}", Toast.LENGTH_LONG).show()
                is Result.Loading -> {}
            }
        }
    }

    // 播放保险库视频（解密后交给系统播放器）
    fun playVideo(photo: PhotoItem) {
        scope.launch {
            val uri = viewModel.videoUri(photo)
            if (uri == null) {
                Toast.makeText(context, "视频解密失败", Toast.LENGTH_SHORT).show()
                return@launch
            }
            val tempFile = File(uri.path ?: return@launch)
            val providerUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", tempFile)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(providerUri, "video/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            runCatching { context.startActivity(intent) }
                .onFailure { Toast.makeText(context, "没有可用的视频播放器", Toast.LENGTH_LONG).show() }
        }
    }

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
            },
            vaultExportHandler = { photo -> exportVaultPhoto(photo) },
            vaultPlayHandler = { photo -> playVideo(photo) }
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

    // 批量导出选中的保险库照片/视频到系统相册
    fun handleBatchExport() {
        val photos = selectedPhotos.toList()
        if (photos.isEmpty()) return
        scope.launch {
            val count = viewModel.exportVaultPhotos(photos)
            Toast.makeText(
                context,
                if (count > 0) "已保存 $count 项到相册" else "保存失败，请检查存储权限",
                Toast.LENGTH_LONG
            ).show()
            isSelectionMode = false
            selectedPhotos = emptySet()
        }
    }

    // ---- 移动到其他分组 ----
    var showMoveGroupDialog by remember { mutableStateOf(false) }
    var moveTargetBuckets by remember { mutableStateOf<List<com.rapii.snapje.data.local.VaultBucket>>(emptyList()) }

    // 打开"移动到分组"弹窗：加载除当前分组外的所有分组
    fun openMoveDialog() {
        val photos = selectedPhotos.toList()
        if (photos.isEmpty()) return
        scope.launch {
            val currentBucketId = uiState.category?.id
            val buckets = viewModel.getVaultBuckets()
                .filter { it.bucketId != currentBucketId }
            moveTargetBuckets = buckets
            showMoveGroupDialog = true
        }
    }

    // 执行移动到目标分组
    fun handleMoveToBucket(targetBucketId: Long, targetBucketName: String) {
        val photos = selectedPhotos.toList()
        if (photos.isEmpty()) return
        showMoveGroupDialog = false
        scope.launch {
            val count = viewModel.moveVaultPhotos(photos, targetBucketId, targetBucketName)
            if (count > 0) {
                Toast.makeText(context, "已移动 $count 项到「$targetBucketName」", Toast.LENGTH_SHORT).show()
                // 刷新当前分组列表（移走的照片会从当前分组消失）
                viewModel.loadPhotos()
                isSelectionMode = false
                selectedPhotos = emptySet()
            } else {
                Toast.makeText(context, "移动失败", Toast.LENGTH_LONG).show()
            }
        }
    }

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
                    onDelete = { handleBatchDelete() },
                    onSave = {
                        if (selectedPhotos.isNotEmpty()) handleBatchExport()
                    },
                    onMove = {
                        if (selectedPhotos.isNotEmpty()) openMoveDialog()
                    }
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            // 分组内导入：右下角按钮，导入的照片默认归入当前相册分组
            if (!isSelectionMode && !showPhotoGallery) {
                FloatingActionButton(
                    onClick = { showImportSheet = true },
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Icon(Icons.Default.Add, contentDescription = "导入到此相册")
                }
            }
        }
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
                            state = gridState,
                            // 捏合缩放：实时列数（带动画）+ 手势叠加
                            columns = animatedGridColumns,
                            pinchModifier = pinchModifier
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

    // ---- 分组内导入：选择来源（相册/相机） ----
    if (showImportSheet) {
        ModalBottomSheet(onDismissRequest = { showImportSheet = false }) {
            Column(modifier = Modifier.padding(vertical = 16.dp)) {
                // 从系统相册选择（支持长按+滑动手势多选）
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            showImportSheet = false
                            launchSystemGallery()
                        }
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text("从系统相册选择（手势多选）", style = MaterialTheme.typography.bodyLarge)
                }
                // 从文件选择器选择（勾选多选）
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            showImportSheet = false
                            launchFilePicker()
                        }
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text("从文件选择器选择（勾选多选）", style = MaterialTheme.typography.bodyLarge)
                }
                // 拍照
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            showImportSheet = false
                            launchCamera()
                        }
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text("拍照", style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }

    // ---- 导入后：询问是否删除相册原图（批量） ----
    pendingDeleteOriginalUris.takeIf { it.isNotEmpty() }?.let { originalUris ->
        AlertDialog(
            onDismissRequest = { pendingDeleteOriginalUris = emptyList() },
            title = { Text(stringResource(R.string.delete_original_title)) },
            text = { Text(stringResource(R.string.delete_original_message)) },
            confirmButton = {
                TextButton(onClick = { deleteOriginalsFromGallery(originalUris) }) {
                    Text(stringResource(R.string.delete_original_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    pendingDeleteOriginalUris = emptyList()
                    Toast.makeText(context, R.string.original_kept, Toast.LENGTH_SHORT).show()
                }) {
                    Text(stringResource(R.string.delete_original_keep))
                }
            }
        )
    }

    // ---- 移动到其他分组：选择目标分组 ----
    if (showMoveGroupDialog) {
        AlertDialog(
            onDismissRequest = { showMoveGroupDialog = false },
            title = { Text("移动到其他分组") },
            text = {
                if (moveTargetBuckets.isEmpty()) {
                    Text(
                        "没有其他分组可用。\n请先在首页创建其他相册分组。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Column {
                        moveTargetBuckets.forEach { bucket ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        handleMoveToBucket(bucket.bucketId, bucket.bucketName)
                                    }
                                    .padding(vertical = 12.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Folder,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = bucket.bucketName.ifBlank { "我的保险库" },
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                            HorizontalDivider()
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showMoveGroupDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

