package com.rapii.snapje.ui

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
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
    var showChangePinDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
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
            SettingsSection(title = "显示") {
                GridSizeSetting(
                    currentSize = settingsState.gridColumns,
                    onSizeChanged = { 
                        scope.launch {
                            viewModel.setGridColumns(it)
                            Toast.makeText(context, "网格大小已更新", Toast.LENGTH_SHORT).show()
                        }
                    }
                )

                Divider(modifier = Modifier.padding(horizontal = 16.dp))

                ThemeSetting(
                    currentTheme = settingsState.theme,
                    onThemeChanged = { 
                        scope.launch {
                            viewModel.setTheme(it)
                            Toast.makeText(context, "主题设置已保存", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }

            // Security Settings
            SettingsSection(title = "安全") {
                PinLockSetting(
                    isEnabled = settingsState.pinEnabled,
                    onEnabledChanged = {
                        scope.launch {
                            viewModel.setPinEnabled(it)
                            Toast.makeText(
                                context,
                                if (it) "启动密码验证已开启（下次启动生效）" else "启动密码验证已关闭（下次启动生效）",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                )

                Divider(modifier = Modifier.padding(horizontal = 16.dp))

                ChangePinSetting(onClick = { showChangePinDialog = true })
            }

            // Import Settings
            SettingsSection(title = "导入") {
                AutoDeleteOriginalSetting(
                    isEnabled = settingsState.autoDeleteOriginal,
                    onEnabledChanged = {
                        scope.launch {
                            viewModel.setAutoDeleteOriginal(it)
                            Toast.makeText(
                                context,
                                if (it) "已开启：导入后自动删除相册原图" else "已关闭：导入后询问是否删除原图",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                )
            }

            if (showChangePinDialog) {
                ChangePinDialog(
                    onDismiss = { showChangePinDialog = false },
                    onConfirm = { oldPin, newPin ->
                        scope.launch {
                            val ok = viewModel.changePin(oldPin, newPin)
                            showChangePinDialog = false
                            Toast.makeText(
                                context,
                                if (ok) "密码修改成功" else "当前密码错误",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                )
            }

            // Sorting Settings
            SettingsSection(title = "排序") {
                DefaultSortSetting(
                    currentSort = settingsState.defaultSort,
                    onSortChanged = { 
                        scope.launch {
                            viewModel.setDefaultSort(it)
                            Toast.makeText(context, "默认排序已更新", Toast.LENGTH_SHORT).show()
                        }
                    }
                )

                Divider(modifier = Modifier.padding(horizontal = 16.dp))

                ReverseSortSetting(
                    isReversed = settingsState.reverseSort,
                    onReversedChanged = { 
                        scope.launch {
                            viewModel.setReverseSort(it)
                            Toast.makeText(context, "反向排序${if (it) "已开启" else "已关闭"}", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }

            // Cache Settings
            SettingsSection(title = "存储与缓存") {
                CacheSizeSetting(
                    cacheSizeMB = settingsState.cacheSizeMB,
                    onCacheSizeChanged = { 
                        scope.launch {
                            viewModel.setCacheSizeMB(it)
                            Toast.makeText(context, "缓存大小已更新", Toast.LENGTH_SHORT).show()
                        }
                    }
                )

                Divider(modifier = Modifier.padding(horizontal = 16.dp))

                ClearCacheButton(
                    onClearCache = { 
                        scope.launch {
                            viewModel.clearCache()
                            Toast.makeText(context, "缓存已清除", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }

            // About Section
            SettingsSection(title = "关于") {
                AboutSetting(
                    versionName = "1.1.0",
                    onClick = { 
                        // Show about dialog with app information
                        // Implemented basic about functionality showing version
                        Toast.makeText(context, "SnapJe! v1.1.0\n一个私密的照片保险库应用", Toast.LENGTH_LONG).show()
                    }
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
 * 启动密码验证开关。
 * 关闭后 App 启动不再要求输入密码（下次启动生效）。
 */
@Composable
fun PinLockSetting(
    isEnabled: Boolean,
    onEnabledChanged: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEnabledChanged(!isEnabled) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Lock,
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
                text = "启动密码验证",
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = if (isEnabled) "已开启：打开 App 需输入密码" else "已关闭：打开 App 无需密码",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = isEnabled,
            onCheckedChange = onEnabledChanged
        )
    }
}

/**
 * 导入后自动删除相册原图开关。
 * 开启后：导入照片到保险库时自动删除手机相册里的原图（不再弹框询问）。
 * 关闭后：导入时弹框询问是否删除原图。
 */
@Composable
fun AutoDeleteOriginalSetting(
    isEnabled: Boolean,
    onEnabledChanged: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEnabledChanged(!isEnabled) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.DeleteSweep,
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
                text = "导入后自动删除相册原图",
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = if (isEnabled) "已开启：导入后相册原图自动删除" else "已关闭：导入后询问是否删除",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = isEnabled,
            onCheckedChange = onEnabledChanged
        )
    }
}

/**
 * 修改密码设置项。
 */
@Composable
fun ChangePinSetting(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Lock,
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
                text = "修改密码",
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = "修改启动时使用的 4-6 位数字密码",
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
 * 修改密码对话框：输入当前密码 + 新密码 + 确认新密码。
 */
@Composable
fun ChangePinDialog(
    onDismiss: () -> Unit,
    onConfirm: (oldPin: String, newPin: String) -> Unit
) {
    var oldPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirmNewPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("修改密码") },
        text = {
            Column {
                OutlinedTextField(
                    value = oldPin,
                    onValueChange = { oldPin = it.filter(Char::isDigit).take(6) },
                    label = { Text("当前密码") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = newPin,
                    onValueChange = { newPin = it.filter(Char::isDigit).take(6) },
                    label = { Text("新密码（4-6 位数字）") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = confirmNewPin,
                    onValueChange = { confirmNewPin = it.filter(Char::isDigit).take(6) },
                    label = { Text("确认新密码") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true
                )
                error?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                when {
                    oldPin.isEmpty() -> error = "请输入当前密码"
                    newPin.length < 4 || newPin.length > 6 -> error = "新密码需为 4-6 位数字"
                    newPin != confirmNewPin -> error = "两次输入的新密码不一致"
                    else -> onConfirm(oldPin, newPin)
                }
            }) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
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
        title = "网格大小",
        subtitle = "$currentSize 列"
    ) {
        expanded = true
    }

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false }
    ) {
        listOf(2, 3, 4).forEach { size ->
            DropdownMenuItem(
                text = { Text("$size 列") },
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
        title = "主题",
        subtitle = currentTheme
    ) {
        expanded = true
    }

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false }
    ) {
        listOf("跟随系统", "浅色", "深色").forEach { theme ->
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
        title = "默认排序",
        subtitle = currentSort
    ) {
        expanded = true
    }

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false }
    ) {
        listOf("最新优先", "最旧优先", "名称 (A-Z)", "名称 (Z-A)", "大小").forEach { sort ->
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
        title = "反向排序",
        subtitle = if (isReversed) "已开启" else "已关闭"
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
        title = "缓存大小上限",
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
                text = "清除缓存",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error
            )
            Text(
                text = "释放存储空间",
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
                text = "关于 SnapJe",
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = "版本 $versionName",
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
    val theme: String = "跟随系统",
    val defaultSort: String = "最新优先",
    val reverseSort: Boolean = false,
    val cacheSizeMB: Int = 100,
    val pinEnabled: Boolean = true,
    val autoDeleteOriginal: Boolean = false
)

/**
 * ViewModel for Settings screen.
 * Provides settings state and update methods.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsManager: SettingsManager,
    private val cachedPhotoRepository: com.rapii.snapje.data.CachedPhotoRepository
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

    suspend fun setPinEnabled(enabled: Boolean) {
        settingsManager.setPinEnabled(enabled)
    }

    suspend fun setAutoDeleteOriginal(enabled: Boolean) {
        settingsManager.setAutoDeleteOriginal(enabled)
    }

    suspend fun changePin(oldPin: String, newPin: String): Boolean {
        return settingsManager.changePin(oldPin, newPin)
    }

    suspend fun clearCache() {
        // Clear Room database cache
        cachedPhotoRepository.clearCache()
        // Note: Coil image cache is managed automatically by Coil's MemoryCache and DiskCache
    }
}
