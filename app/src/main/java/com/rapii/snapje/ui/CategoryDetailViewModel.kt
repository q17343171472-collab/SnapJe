package com.rapii.snapje.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rapii.snapje.data.Category
import com.rapii.snapje.data.FileOperationResult
import com.rapii.snapje.data.FileOperations
import com.rapii.snapje.data.PhotoInfo
import com.rapii.snapje.data.PhotoItem
import com.rapii.snapje.data.PhotoSortOption
import com.rapii.snapje.data.Result
import com.rapii.snapje.data.TrashRepository
import com.rapii.snapje.data.VaultRepository
import com.rapii.snapje.util.L
import com.rapii.snapje.util.sortedByOption
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/**
 * ViewModel for CategoryDetailScreen.
 *
 * 数据源已从 MediaStore（PhotoRepository）改为 [VaultRepository]（加密保险库）：
 * - 网格缩略图使用解密后的临时文件 URI（加密缩略图）。
 * - 全屏预览通过 [fullImageUri] 解密原图到临时文件，用后即删。
 * - 删除/重命名直接操作保险库（密文文件 + DB 记录）。
 */
@HiltViewModel
class CategoryDetailViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val savedStateHandle: SavedStateHandle,
    private val vaultRepository: VaultRepository,
    private val trashRepository: TrashRepository,
    private val fileOperations: FileOperations
) : ViewModel() {

    // Get categoryId (bucketId) from SavedStateHandle (passed via navigation)
    val categoryId: Long by lazy {
        savedStateHandle.get<Long>("categoryId")?.also { id ->
            L.d("CategoryDetailVM", "Received categoryId from SavedStateHandle: $id")
        } ?: run {
            L.e("CategoryDetailVM", "categoryId not found in SavedStateHandle! Check navigation arguments.")
            -1L // Return invalid ID, will show error state
        }
    }

    /** 供 UI 使用（系统相册照片的回收站流程仍保留） */
    val trashRepositoryInstance: TrashRepository get() = trashRepository

    /** 供 UI 使用（文件信息 / 系统相册照片的复制移动等） */
    val fileOperationsInstance: FileOperations get() = fileOperations

    // UI state - Using StateFlow for consistency with CategoryViewModel
    private val _uiState = MutableStateFlow(CategoryDetailUiState())
    val uiState: StateFlow<CategoryDetailUiState> = _uiState.asStateFlow()

    // Raw photos (unsorted) - kept for sorting without re-loading
    private var rawPhotos: List<PhotoItem> = emptyList()

    // Load job to prevent multiple concurrent loads
    private var loadJob: Job? = null

    // Current sort option
    private val _currentSort = MutableStateFlow(PhotoSortOption.DEFAULT)
    val currentSort: StateFlow<PhotoSortOption> = _currentSort.asStateFlow()

    // Cached category info - loaded once and reused
    private var cachedCategory: Category? = null

    /**
     * Set cached category from external source (e.g., CategoryViewModel).
     */
    fun setCachedCategory(category: Category?) {
        cachedCategory = category
    }

    /**
     * Load vault photos for this bucket. 数据由 Room Flow 自动推送，增删即时刷新。
     * 缩略图在加载时解密到临时文件并缓存。
     */
    fun loadPhotos() {
        // Check for invalid categoryId
        if (categoryId == -1L) {
            L.e("CategoryDetailVM", "Cannot load photos: invalid categoryId (-1)")
            _uiState.update {
                it.copy(
                    isLoading = false,
                    error = "Invalid category ID. Please go back and try again.",
                    photos = emptyList()
                )
            }
            return
        }

        L.d("CategoryDetailVM", "Loading vault photos for bucketId: $categoryId")

        // Cancel any existing load job before starting a new one
        loadJob?.cancel()

        loadJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            vaultRepository.getPhotosByBucket(categoryId).collect { vaultPhotos ->
                L.d("CategoryDetailVM", "Vault photos updated for bucket $categoryId: ${vaultPhotos.size}")

                val photoItems = mutableListOf<PhotoItem>()
                for (vp in vaultPhotos) {
                    val thumbUri = runCatching { vaultRepository.thumbnailUri(vp) }.getOrNull()
                    if (thumbUri != null) {
                        photoItems.add(vp.toPhotoItem(thumbUri))
                    } else {
                        L.e("CategoryDetailVM", "Failed to decrypt thumbnail for ${vp.id}")
                    }
                }

                rawPhotos = photoItems

                // 相册信息：优先用外部缓存，否则从照片元数据推导
                val category = cachedCategory ?: vaultPhotos.firstOrNull()?.let {
                    Category(
                        id = it.bucketId,
                        name = it.bucketName,
                        path = "",
                        coverUris = emptyList(),
                        itemCount = vaultPhotos.size,
                        lastModified = vaultPhotos.maxOf { p -> p.dateTaken }
                    )
                }
                if (category != null) cachedCategory = category

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        photos = rawPhotos.sortedByOption(_currentSort.value),
                        category = category ?: it.category,
                        error = null
                    )
                }
            }
        }
    }

    /**
     * Clear any error state.
     */
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    /**
     * Set sort option and re-sort photos.
     */
    fun setSortOption(option: PhotoSortOption) {
        _currentSort.value = option
        _uiState.update { currentState ->
            currentState.copy(
                photos = rawPhotos.sortedByOption(option)
            )
        }
    }

    // ---------------------------------------------------------------------
    // 保险库操作
    // ---------------------------------------------------------------------

    /**
     * 删除保险库照片（密文文件 + DB 记录 + 临时文件）。
     */
    suspend fun deleteVaultPhoto(photo: PhotoItem): Result<Unit> {
        val vaultId = photo.vaultId ?: return Result.failure(IllegalStateException("Not a vault photo"))
        return vaultRepository.deletePhoto(vaultId)
    }

    /**
     * 重命名保险库照片（仅显示名）。
     */
    suspend fun renameVaultPhoto(photo: PhotoItem, newName: String): Result<Unit> {
        val vaultId = photo.vaultId ?: return Result.failure(IllegalStateException("Not a vault photo"))
        return vaultRepository.renamePhoto(vaultId, newName)
    }

    /**
     * 获取解密原图 URI（全屏预览用）。
     */
    suspend fun fullImageUri(photo: PhotoItem): Uri? {
        val vaultId = photo.vaultId ?: return null
        return vaultRepository.fullImageUri(vaultId)
    }

    /**
     * 退出全屏预览后清理解密原图临时文件（保留缩略图缓存）。
     */
    fun clearFullImageCache() {
        vaultRepository.clearFullImageCache()
    }

    /**
     * 分享照片：保险库照片先解密到临时文件，再通过 FileProvider 分享。
     */
    fun sharePhoto(photo: PhotoItem) {
        if (photo.isVaultPhoto) {
            viewModelScope.launch {
                val uri = runCatching { fullImageUri(photo) }.getOrNull()
                if (uri == null) {
                    L.e("CategoryDetailVM", "Share failed: cannot decrypt ${photo.displayName}")
                    return@launch
                }
                val tempFile = File(uri.path ?: return@launch)
                val providerUri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    tempFile
                )
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = photo.mimeType.ifBlank { "image/*" }
                    putExtra(Intent.EXTRA_STREAM, providerUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                runCatching {
                    context.startActivity(
                        Intent.createChooser(shareIntent, null)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            }
        } else {
            fileOperations.sharePhoto(photo)
        }
    }

    // ---------------------------------------------------------------------
    // 兼容保留（系统相册照片路径）
    // ---------------------------------------------------------------------

    /**
     * Rename a system photo (kept for non-vault photos).
     */
    suspend fun renamePhoto(photo: PhotoItem, newName: String): FileOperationResult? {
        return fileOperations.renamePhoto(photo, newName)
    }

    /**
     * Get photo info.
     */
    suspend fun getPhotoInfo(photo: PhotoItem): PhotoInfo? {
        return fileOperations.getPhotoInfo(photo)
    }

    /**
     * Remove a photo from the in-memory list after deletion.
     */
    fun removePhotoFromList(photo: PhotoItem) {
        rawPhotos = rawPhotos.filter { it.id != photo.id }
        _uiState.update { currentState ->
            val newPhotos = currentState.photos.filter { it.id != photo.id }
            currentState.copy(photos = newPhotos)
        }
        L.d("CategoryDetailVM", "Removed photo ${photo.id}, remaining: ${rawPhotos.size}")
    }

    /**
     * Update a photo's name in the in-memory list after rename.
     */
    fun updatePhotoName(photo: PhotoItem, newName: String) {
        rawPhotos = rawPhotos.map {
            if (it.id == photo.id) it.copy(displayName = newName) else it
        }
        _uiState.update { currentState ->
            val updatedPhotos = currentState.photos.map {
                if (it.id == photo.id) it.copy(displayName = newName) else it
            }
            currentState.copy(photos = updatedPhotos)
        }
        L.d("CategoryDetailVM", "Updated photo name to $newName")
    }

    /**
     * Add a photo back to the in-memory list after restoration from trash.
     */
    fun addPhotoBack(photo: PhotoItem) {
        if (rawPhotos.any { it.id == photo.id }) {
            return
        }
        rawPhotos = rawPhotos + photo
        _uiState.update { currentState ->
            currentState.copy(photos = currentState.photos + photo)
        }
        L.d("CategoryDetailVM", "Added restored photo ${photo.id}, total: ${rawPhotos.size}")
    }
}

/**
 * UI state for CategoryDetailScreen.
 */
data class CategoryDetailUiState(
    val isLoading: Boolean = true,
    val photos: List<PhotoItem> = emptyList(),
    val category: Category? = null,
    val error: String? = null
)
