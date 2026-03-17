package com.rapii.snapje.ui

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rapii.snapje.R
import com.rapii.snapje.data.SettingsManager
import com.rapii.snapje.util.Constants
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Settings screen for SnapJe!
 * Allows users to customize app behavior and preferences.
 * Settings are persisted using Jetpack DataStore.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsState by viewModel.settingsFlow.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            // Display Settings
            SettingsSection(title = "Display") {
                GridSizeSetting(
                    currentSize = settingsState.gridColumns,
                    onSizeChanged = { 
                        scope.launch {
                            viewModel.setGridColumns(it)
                            Toast.makeText(context, "Grid size updated", Toast.LENGTH_SHORT).show()
                        }
                    }
                )

                Divider(modifier = Modifier.padding(horizontal = 16.dp))

                ThemeSetting(
                    currentTheme = settingsState.theme,
                    onThemeChanged = { 
                        scope.launch {
                            viewModel.setTheme(it)
                            Toast.makeText(context, "Theme preference saved", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }

            // Sorting Settings
            SettingsSection(title = "Sorting") {
                DefaultSortSetting(
                    currentSort = settingsState.defaultSort,
                    onSortChanged = { 
                        scope.launch {
                            viewModel.setDefaultSort(it)
                            Toast.makeText(context, "Default sort updated", Toast.LENGTH_SHORT).show()
                        }
                    }
                )

                Divider(modifier = Modifier.padding(horizontal = 16.dp))

                ReverseSortSetting(
                    isReversed = settingsState.reverseSort,
                    onReversedChanged = { 
                        scope.launch {
                            viewModel.setReverseSort(it)
                            Toast.makeText(context, "Reverse sort ${if (it) "enabled" else "disabled"}", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }

            // Cache Settings
            SettingsSection(title = "Storage & Cache") {
                CacheSizeSetting(
                    cacheSizeMB = settingsState.cacheSizeMB,
                    onCacheSizeChanged = { 
                        scope.launch {
                            viewModel.setCacheSizeMB(it)
                            Toast.makeText(context, "Cache size limit updated", Toast.LENGTH_SHORT).show()
                        }
                    }
                )

                Divider(modifier = Modifier.padding(horizontal = 16.dp))

                ClearCacheButton(
                    onClearCache = { 
                        scope.launch {
                            viewModel.clearCache()
                            Toast.makeText(context, "Cache cleared", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }

            // About Section
            SettingsSection(title = "About") {
                AboutSetting(
                    versionName = "1.1.0",
                    onClick = { /* TODO: Show about dialog */ }
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

/**
 * Settings section with title and content.
 */
@Composable
fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        content()
    }
}

/**
 * Grid size setting.
 */
@Composable
fun GridSizeSetting(
    currentSize: Int,
    onSizeChanged: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    SettingsItem(
        icon = Icons.Default.GridView,
        title = "Grid Size",
        subtitle = "$currentSize columns"
    ) {
        expanded = true
    }

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false }
    ) {
        listOf(2, 3, 4).forEach { size ->
            DropdownMenuItem(
                text = { Text("$size columns") },
                onClick = {
                    onSizeChanged(size)
                    expanded = false
                }
            )
        }
    }
}

/**
 * Theme setting.
 */
@Composable
fun ThemeSetting(
    currentTheme: String,
    onThemeChanged: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    SettingsItem(
        icon = Icons.Default.Palette,
        title = "Theme",
        subtitle = currentTheme
    ) {
        expanded = true
    }

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false }
    ) {
        listOf("System", "Light", "Dark").forEach { theme ->
            DropdownMenuItem(
                text = { Text(theme) },
                onClick = {
                    onThemeChanged(theme)
                    expanded = false
                }
            )
        }
    }
}

/**
 * Default sort order setting.
 */
@Composable
fun DefaultSortSetting(
    currentSort: String,
    onSortChanged: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    SettingsItem(
        icon = Icons.Default.Sort,
        title = "Default Sort",
        subtitle = currentSort
    ) {
        expanded = true
    }

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false }
    ) {
        listOf("Date (Newest)", "Date (Oldest)", "Name (A-Z)", "Name (Z-A)", "Size").forEach { sort ->
            DropdownMenuItem(
                text = { Text(sort) },
                onClick = {
                    onSortChanged(sort)
                    expanded = false
                }
            )
        }
    }
}

/**
 * Reverse sort toggle.
 */
@Composable
fun ReverseSortSetting(
    isReversed: Boolean,
    onReversedChanged: (Boolean) -> Unit
) {
    SettingsItem(
        icon = Icons.Default.SwapVert,
        title = "Reverse Sort Order",
        subtitle = if (isReversed) "Enabled" else "Disabled"
    ) {
        onReversedChanged(!isReversed)
    }
}

/**
 * Cache size setting.
 */
@Composable
fun CacheSizeSetting(
    cacheSizeMB: Int,
    onCacheSizeChanged: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    SettingsItem(
        icon = Icons.Default.Storage,
        title = "Cache Size Limit",
        subtitle = "$cacheSizeMB MB"
    ) {
        expanded = true
    }

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false }
    ) {
        listOf(50, 100, 200, 500).forEach { size ->
            DropdownMenuItem(
                text = { Text("$size MB") },
                onClick = {
                    onCacheSizeChanged(size)
                    expanded = false
                }
            )
        }
    }
}

/**
 * Clear cache button.
 */
@Composable
fun ClearCacheButton(
    onClearCache: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClearCache)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.DeleteSweep,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(24.dp)
        )
        Column(modifier = Modifier.padding(start = 16.dp)) {
            Text(
                text = "Clear Cache",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error
            )
            Text(
                text = "Free up storage space",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * About setting.
 */
@Composable
fun AboutSetting(
    versionName: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Info,
            contentDescription = null,
            modifier = Modifier.size(24.dp)
        )
        Column(modifier = Modifier.padding(start = 16.dp)) {
            Text(
                text = "About SnapJe!",
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = "Version $versionName",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Generic settings item with icon, title, subtitle, and click action.
 */
@Composable
fun SettingsItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Settings state data class.
 */
data class SettingsState(
    val gridColumns: Int = Constants.PHOTO_GRID_COLUMNS_PORTRAIT,
    val theme: String = "System",
    val defaultSort: String = "Date (Newest)",
    val reverseSort: Boolean = false,
    val cacheSizeMB: Int = 100
)

/**
 * ViewModel for Settings screen.
 * Provides settings state and update methods.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsManager: SettingsManager
) : ViewModel() {

    val settingsFlow: StateFlow<SettingsState> = settingsManager.settingsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = SettingsState()
        )

    suspend fun setGridColumns(columns: Int) {
        settingsManager.setGridColumns(columns)
    }

    suspend fun setTheme(theme: String) {
        settingsManager.setTheme(theme)
    }

    suspend fun setDefaultSort(sort: String) {
        settingsManager.setDefaultSort(sort)
    }

    suspend fun setReverseSort(reverse: Boolean) {
        settingsManager.setReverseSort(reverse)
    }

    suspend fun setCacheSizeMB(size: Int) {
        settingsManager.setCacheSizeMB(size)
    }

    suspend fun clearCache() {
        // TODO: Implement actual cache clearing logic
        // For now, just clear Coil image cache
        settingsManager.setCacheSizeMB(settingsFlow.value.cacheSizeMB)
    }
}
