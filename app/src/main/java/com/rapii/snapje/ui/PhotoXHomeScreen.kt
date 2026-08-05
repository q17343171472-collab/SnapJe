package com.rapii.snapje.ui

import android.app.Activity
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.rapii.snapje.R
import com.rapii.snapje.data.Category
import com.rapii.snapje.data.SortBy
import com.rapii.snapje.ui.components.CategoryGrid
import com.rapii.snapje.util.CameraLauncher
import com.rapii.snapje.util.L
import kotlinx.coroutines.launch

/**
 * 首页：展示保险库内的加密照片分组。
 *
 * - 数据源为 VaultRepository（加密保险库），不读取系统相册。
 * - FAB 提供"从系统相册选择 / 拍照"两种方式导入照片，导入时加密存储。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoXHomeScreen(
    viewModel: CategoryViewModel = hiltViewModel(),
    onCategoryClick: (Category) -> Unit,
    onNavigateToCamera: () -> Unit = {},
    onNavigateToSearch: () -> Unit,
    onNavigateToTrash: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onCategoriesLoaded: (List<Category>) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // ---- 导入状态 ----
    var showImportSheet by remember { mutableStateOf(false) }
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    var showAlbumDialog by remember { mutableStateOf(false) }
    var albumName by remember { mutableStateOf("") }
    var existingAlbums by remember { mutableStateOf(listOf<String>()) }

    // 系统相册选择（Photo Picker，无需存储权限）
    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            pendingImportUri = uri
            showAlbumDialog = true
        }
    }

    // 相机
    val cameraLauncher = remember { CameraLauncher() }
    val cameraResultLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = cameraLauncher.getLastPhotoUri()
        if (result.resultCode == Activity.RESULT_OK && uri != null) {
            pendingCameraUri = uri
            showAlbumDialog = true
        } else {
            cameraLauncher.clearPhotoUri()
        }
    }

    fun launchCamera() {
        runCatching {
            val intent = cameraLauncher.createCaptureIntent(context)
            cameraResultLauncher.launch(intent)
        }.onFailure { e ->
            L.e("PhotoXHome", "Camera launch failed: ${e.message}")
            Toast.makeText(context, "无法打开相机", Toast.LENGTH_SHORT).show()
        }
    }

    // 打开相册对话框时预填相册名
    LaunchedEffect(showAlbumDialog) {
        if (showAlbumDialog) {
            existingAlbums = viewModel.getAlbumNames()
            albumName = existingAlbums.firstOrNull() ?: context.getString(R.string.vault_default_album)
        }
    }

    // 删除相机拍摄的临时明文文件（所有退出路径都要调用）
    fun cleanupCameraFile() {
        val file = cameraLauncher.getLastPhotoFile()
        if (file != null) {
            runCatching { file.delete() }
        }
        cameraLauncher.clearPhotoUri()
        pendingCameraUri = null
    }

    // 执行导入（加密存储）
    fun performImport(uri: Uri, album: String) {
        scope.launch {
            val result = viewModel.addPhotoToVault(uri, album)
            if (result.isSuccess) {
                Toast.makeText(context, context.getString(R.string.import_success), Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(
                    context,
                    context.getString(
                        R.string.import_failed,
                        result.exceptionOrNull()?.message ?: "unknown"
                    ),
                    Toast.LENGTH_LONG
                ).show()
            }
            // 无论成败，清理相机临时原图（避免明文残留在外部存储）
            cleanupCameraFile()
            showAlbumDialog = false
        }
    }

    // 清除待导入状态
    fun clearPendingImport() {
        pendingImportUri = null
        cleanupCameraFile()
        showAlbumDialog = false
        showImportSheet = false
    }

    // Sort menu state
    var showSortMenu by remember { mutableStateOf(false) }

    // Snackbar for errors
    val snackbarHostState = remember { SnackbarHostState() }

    // Show error snackbar if there's an error
    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }

    // Notify parent when categories are loaded
    LaunchedEffect(uiState.categories) {
        if (uiState.categories.isNotEmpty()) {
            onCategoriesLoaded(uiState.categories)
        }
    }

    Scaffold(
        modifier = Modifier.statusBarsPadding(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
                ),
                actions = {
                    IconButton(onClick = onNavigateToSearch) {
                        Icon(
                            imageVector = Icons.Outlined.Search,
                            contentDescription = stringResource(R.string.search),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(onClick = onNavigateToTrash) {
                        Icon(
                            imageVector = Icons.Outlined.DeleteOutline,
                            contentDescription = "Recently Deleted",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Box {
                        IconButton(onClick = { showSortMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Settings") },
                                onClick = {
                                    onNavigateToSettings()
                                    showSortMenu = false
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Settings, contentDescription = null)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Recent activity") },
                                onClick = {
                                    viewModel.updateSortBy(SortBy.RECENT)
                                    showSortMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Name (A-Z)") },
                                onClick = {
                                    viewModel.updateSortBy(SortBy.NAME)
                                    showSortMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Item count") },
                                onClick = {
                                    viewModel.updateSortBy(SortBy.COUNT)
                                    showSortMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Pinned first") },
                                onClick = {
                                    viewModel.updateSortBy(SortBy.PINNED)
                                    showSortMenu = false
                                }
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showImportSheet = true },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                elevation = FloatingActionButtonDefaults.elevation(
                    defaultElevation = 2.dp,
                    pressedElevation = 4.dp
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.add_photo)
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                // Loading
                uiState.isLoading && uiState.categories.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                        )
                    }
                }

                // Empty state
                uiState.isEmpty -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(32.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.vault_empty),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.vault_empty_message),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                // Success - show vault categories
                else -> {
                    CategoryGrid(
                        categories = uiState.categories,
                        onCategoryClick = onCategoryClick,
                        onTogglePin = { categoryId: Long ->
                            viewModel.toggleCategoryPin(categoryId)
                        },
                        onHideCategory = { categoryId: Long ->
                            viewModel.hideCategory(categoryId)
                        },
                        modifier = Modifier.fillMaxSize(),
                        columns = 2
                    )
                }
            }
        }
    }

    // 导入方式选择底部弹层
    if (showImportSheet) {
        ModalBottomSheet(onDismissRequest = { showImportSheet = false }) {
            Column(modifier = Modifier.padding(vertical = 16.dp)) {
                ImportSourceItem(
                    icon = { Icon(Icons.Outlined.PhotoLibrary, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    text = stringResource(R.string.pick_from_gallery),
                    onClick = {
                        showImportSheet = false
                        photoPicker.launch(
                            ActivityResultContracts.PickVisualMediaRequest(
                                ActivityResultContracts.PickVisualMedia.ImageOnly
                            )
                        )
                    }
                )
                ImportSourceItem(
                    icon = { Icon(Icons.Default.CameraAlt, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    text = stringResource(R.string.take_photo),
                    onClick = {
                        showImportSheet = false
                        launchCamera()
                    }
                )
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // 相册命名对话框
    if (showAlbumDialog) {
        AlertDialog(
            onDismissRequest = { clearPendingImport() },
            title = { Text(stringResource(R.string.add_photo)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = albumName,
                        onValueChange = { albumName = it },
                        label = { Text(stringResource(R.string.vault_album_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (existingAlbums.size > 1) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = existingAlbums.joinToString(" / "),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val uri = pendingImportUri ?: pendingCameraUri
                        if (uri != null) {
                            performImport(uri, albumName)
                        }
                    }
                ) {
                    Text(stringResource(R.string.import_success_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { clearPendingImport() }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun ImportSourceItem(
    icon: @Composable () -> Unit,
    text: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(32.dp)) { icon() }
        Spacer(modifier = Modifier.width(16.dp))
        Text(text = text, style = MaterialTheme.typography.bodyLarge)
    }
}
