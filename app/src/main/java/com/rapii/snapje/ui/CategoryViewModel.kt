package com.rapii.snapje.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rapii.snapje.data.Category
import com.rapii.snapje.data.CachedPhotoRepository
import com.rapii.snapje.data.SortBy
import com.rapii.snapje.util.L
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for managing categories (folders) in PhotoX.
 * Implements caching to improve navigation performance.
 *
 * Uses Hilt for dependency injection.
 */
@HiltViewModel
class CategoryViewModel @Inject constructor(
    private val cachedPhotoRepository: CachedPhotoRepository
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

    // Refresh job to prevent multiple refreshes
    private var refreshJob: Job? = null

    // Cached categories - shared across app for faster navigation
    private var cachedCategories: List<Category> = emptyList()

    init {
        // Only load if not already cached (prevents redundant loads on back navigation)
        if (cachedCategories.isEmpty()) {
            loadCategories()
        }
    }

    /**
     * Get cached categories if available, avoiding redundant queries.
     */
    fun getCachedCategories(): List<Category> = cachedCategories

    fun loadCategories() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true, error = null) }

                // First, load from cache for instant UI
                val cachedCats = cachedPhotoRepository.getCachedCategories().firstOrNull()
                if (!cachedCats.isNullOrEmpty()) {
                    cachedCategories = cachedCats
                    val filteredCategories = filterAndSortCategories(cachedCats)
                    _uiState.update {
                        it.copy(
                            categories = filteredCategories,
                            isLoading = false,
                            isEmpty = filteredCategories.isEmpty()
                        )
                    }
                }

                // Then refresh from MediaStore in background
                val freshCategories = cachedPhotoRepository.refreshCategories().getOrNull()
                if (freshCategories != null) {
                    cachedCategories = freshCategories
                    val filteredCategories = filterAndSortCategories(freshCategories)
                    _uiState.update {
                        it.copy(
                            categories = filteredCategories,
                            isLoading = false,
                            isEmpty = filteredCategories.isEmpty()
                        )
                    }
                } else if (cachedCats.isNullOrEmpty()) {
                    // No cache and no MediaStore - show error
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = "No photos found",
                            isEmpty = true
                        )
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // CRITICAL: Log cancellation but don't propagate to UI
                L.d("CategoryVM", "Load cancelled - likely due to navigation")
                return@launch
            } catch (e: Exception) {
                L.e("CategoryVM", "Failed to load categories: ${e.message}", e)
                _uiState.update {
                    it.copy(
                        error = e.message ?: "Failed to load categories",
                        isLoading = false
                    )
                }
            }
        }
    }

    fun refreshCategories() {
        loadCategories()
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
        val currentCategories = _uiState.value.categories
        val filteredCategories = filterAndSortCategories(currentCategories)

        _uiState.update {
            it.copy(
                categories = filteredCategories,
                isEmpty = filteredCategories.isEmpty()
            )
        }
    }

    private fun filterAndSortCategories(categories: List<Category>): List<Category> {
        // Filter by search query
        val filtered = if (searchQuery.isBlank()) {
            categories
        } else {
            categories.filter { category ->
                category.name.contains(searchQuery, ignoreCase = true) ||
                category.path.contains(searchQuery, ignoreCase = true)
            }
        }

        // Filter out hidden categories
        val visibleCategories = filtered.filter { !it.isHidden }

        // Sort by selected criteria
        return Category.sortCategories(visibleCategories, sortBy)
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
