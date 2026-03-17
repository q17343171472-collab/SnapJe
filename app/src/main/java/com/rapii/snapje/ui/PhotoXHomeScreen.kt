package com.rapii.snapje.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.rapii.snapje.R
import com.rapii.snapje.data.Category
import com.rapii.snapje.data.SortBy
import com.rapii.snapje.ui.components.CategoryGrid
import com.rapii.snapje.util.CameraLauncher
import com.rapii.snapje.util.L

/**
 * Simplified main home screen for PhotoX showing categories organized by folders.
 * No pull-to-refresh to avoid experimental API issues.
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
    val activity = (context as? androidx.activity.ComponentActivity)
    
    // Camera launcher
    val cameraLauncher = remember { CameraLauncher() }
    var cameraPermissionGranted by remember { mutableStateOf(false) }
    
    // Camera permission launcher
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        cameraPermissionGranted = isGranted
        if (isGranted) {
            // Launch camera after permission granted
            activity?.let {
                val intent = cameraLauncher.createCaptureIntent(context)
                cameraLauncher.createLauncher(it) { photoUri ->
                    L.d("PhotoXHomeScreen", "Photo captured: $photoUri")
                    Toast.makeText(context, "Photo captured successfully", Toast.LENGTH_SHORT).show()
                }.launch(intent)
            }
        }
    }
    
    // Permission state
    var hasPermission by remember { mutableStateOf(false) }
    var permissionRequested by remember { mutableStateOf(false) }

    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            hasPermission = isGranted
            if (isGranted) {
                viewModel.loadCategories()
            }
        }
    )

    // Request permission on first launch
    LaunchedEffect(Unit) {
        if (!permissionRequested) {
            permissionRequested = true
            // Only request if we don't have cached categories (i.e., first launch)
            if (viewModel.getCachedCategories().isEmpty()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    permissionLauncher.launch(Manifest.permission.READ_MEDIA_IMAGES)
                } else {
                    permissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                }
            } else {
                // Already have permission and cached data
                hasPermission = true
            }
        }
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
            LargeTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.Light,
                            letterSpacing = (-0.5).sp
                        )
                    )
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
                ),
                actions = {
                    // Search button
                    IconButton(onClick = onNavigateToSearch) {
                        Icon(
                            imageVector = Icons.Outlined.Search,
                            contentDescription = stringResource(R.string.search),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    // Trash button
                    IconButton(onClick = onNavigateToTrash) {
                        Icon(
                            imageVector = Icons.Outlined.DeleteOutline,
                            contentDescription = "Recently Deleted",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    // More menu
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
                onClick = {
                    // Check and request camera permission
                    when {
                        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED -> {
                            // Permission already granted, launch camera
                            activity?.let {
                                val intent = cameraLauncher.createCaptureIntent(context)
                                cameraLauncher.createLauncher(it) { photoUri ->
                                    L.d("PhotoXHomeScreen", "Photo captured: $photoUri")
                                    Toast.makeText(context, "Photo captured successfully", Toast.LENGTH_SHORT).show()
                                }.launch(intent)
                            }
                        }
                        else -> {
                            // Request camera permission
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    }
                },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                elevation = FloatingActionButtonDefaults.elevation(
                    defaultElevation = 2.dp,
                    pressedElevation = 4.dp
                )
            ) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = stringResource(R.string.camera)
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
                // Permission not granted
                !hasPermission -> {
                    PermissionRequiredScreen(
                        onRequestPermission = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                permissionLauncher.launch(Manifest.permission.READ_MEDIA_IMAGES)
                            } else {
                                permissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                            }
                        }
                    )
                }
                
                // Loading
                uiState.isLoading -> {
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
                                text = "No photos yet",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
                
                // Success - show categories
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
}

@Composable
fun PermissionRequiredScreen(
    onRequestPermission: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) {
            Text(
                text = "Photo Access Required",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            Text(
                text = "PhotoX needs permission to access your photos to organize them by folders.",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(bottom = 24.dp),
                textAlign = TextAlign.Center
            )
            
            Text(
                text = "You'll be able to browse photos organized by:",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                textAlign = TextAlign.Start
            )
            
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                listOf(
                    "• Camera photos",
                    "• Screenshots",
                    "• WhatsApp images",
                    "• Downloads",
                    "• Instagram photos",
                    "• Custom folders"
                ).forEach { item ->
                    Text(
                        text = item,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }
            
            androidx.compose.material3.Button(
                onClick = onRequestPermission,
                modifier = Modifier.padding(top = 24.dp)
            ) {
                Text("Grant Permission")
            }
        }
    }
}
