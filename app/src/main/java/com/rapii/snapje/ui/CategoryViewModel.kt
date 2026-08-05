package com.rapii.snapje.ui

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rapii.snapje.data.Category
import com.rapii.snapje.data.Result
import com.rapii.snapje.data.SortBy
import com.rapii.snapje.data.VaultPhoto
import com.rapii.snapje.data.VaultRepository
import com.rapii.snapje.util.L
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for managing categories (保险库相册) in SnapJe!.
 *
 * 数据源已从 MediaStore 改为 [VaultRepository]（加密保险库）：
 * - 首页只展示保险库内的加密照片分组，系统相册不可见。
 * - 分组依据导入时选择的相册名（bucketName）。
 */
@HiltViewModel
class CategoryViewModel @Inject constructor(
    private val vaultRepository: VaultRepository
) : ViewModel() {

    // UI state
    private val _uiState = MutableStateFlow(CategoryUiState())
    val uiState: StateFlow<CategoryUiState> = _uiState.asStateFlow()

    // Search query
    var searchQuery by mutableStateOf("")
        private set

    // Sort order
    var sortBy by mutableStateOf(SortBy.RECENT)
        private set

    // Cached categories - shared across app for faster navigation
    private var cachedCategories: List<Category> = emptyList()

    init {
        observeVault()
    }

    /**
     * 订阅保险库照片 Flow：照片增删时自动重建分组列表。
     */
    private fun observeVault() {
        viewModelScope.launch {
            vaultRepository.getVaultPhotos().collect { photos ->
                L.d("CategoryVM", "Vault photos updated: ${photos.size}")
                buildCategories(photos)
            }
        }
    }

    /**
     * 从保险库照片构建分组（按 bucketId 聚合，封面取前 4 张解密缩略图）。
     */
    private suspend fun buildCategories(photos: List<VaultPhoto>) {
        val grouped = photos.groupBy { it.bucketId }
        val categories = grouped.map { (bucketId, list) ->
            val bucketName = list.first().bucketName.ifBlank { VaultRepository.DEFAULT_ALBUM }
            val covers = mutableListOf<Uri>()
            for (photo in list.take(4)) {
                runCatching { vaultRepository.thumbnailUri(photo) }.getOrNull()?.let { covers.add(it) }
            }
            Category(
                id = bucketId,
                name = bucketName,
                path = "",
                coverUris = covers,
                itemCount = list.size,
                lastModified = list.maxOf { it.dateTaken }
            )
        }
        cachedCategories = categories
        applyFilters()
    }

    /**
     * Get cached categories if available, avoiding redundant queries.
     */
    fun getCachedCategories(): List<Category> = cachedCategories

    /**
     * 保险库数据由 Flow 自动推送，无需手动刷新（保留接口以兼容旧调用）。
     */
    fun loadCategories() {
        applyFilters()
    }

    fun refreshCategories() {
        applyFilters()
    }

    /**
     * 导入照片到保险库（加密存储）。
     */
    suspend fun addPhotoToVault(uri: Uri, albumName: String): Result<VaultPhoto> {
        return vaultRepository.addPhotoToVault(uri, albumName)
    }

    /**
     * 获取已有相册名列表（导入时选择用）。
     */
    suspend fun getAlbumNames(): List<String> {
        return vaultRepository.getAlbumNames()
    }

    fun updateSearchQuery(query: String) {
        searchQuery = query
        applyFilters()
    }

    fun updateSortBy(newSortBy: SortBy) {
        sortBy = newSortBy
        applyFilters()
    }

    fun toggleCategoryPin(categoryId: Long) {
        _uiState.update { currentState ->
            val updatedCategories = currentState.categories.map { category ->
                if (category.id == categoryId) {
                    category.copy(isPinned = !category.isPinned)
                } else {
                    category
                }
            }
            cachedCategories = updatedCategories  // Update cache
            currentState.copy(categories = Category.sortCategories(updatedCategories, SortBy.PINNED))
        }
    }

    fun hideCategory(categoryId: Long) {
        _uiState.update { currentState ->
            val updatedCategories = currentState.categories.map { category ->
                if (category.id == categoryId) {
                    category.copy(isHidden = true)
                } else {
                    category
                }
            }.filter { !it.isHidden }
            cachedCategories = updatedCategories  // Update cache
            currentState.copy(categories = updatedCategories)
        }
    }

    private fun applyFilters() {
        val currentCategories = cachedCategories
        val filtered = if (searchQuery.isBlank()) {
            currentCategories
        } else {
            currentCategories.filter { category ->
                category.name.contains(searchQuery, ignoreCase = true) ||
                    category.path.contains(searchQuery, ignoreCase = true)
            }
        }

        val visibleCategories = filtered.filter { !it.isHidden }

        val sortedCategories = Category.sortCategories(visibleCategories, sortBy)

        _uiState.update {
            it.copy(
                categories = sortedCategories,
                isLoading = false,
                isEmpty = sortedCategories.isEmpty()
            )
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}

/**
 * UI state for categories screen.
 */
data class CategoryUiState(
    val categories: List<Category> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isEmpty: Boolean = false,
    val error: String? = null
)
