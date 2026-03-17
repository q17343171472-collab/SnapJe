package com.rapii.snapje.ui

import android.content.ContentResolver
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rapii.snapje.data.Category
import com.rapii.snapje.data.FileOperationResult
import com.rapii.snapje.data.FileOperations
import com.rapii.snapje.data.PhotoInfo
import com.rapii.snapje.data.PhotoItem
import com.rapii.snapje.data.PhotoRepositoryInterface
import com.rapii.snapje.data.PhotoSortOption
import com.rapii.snapje.data.TrashRepository
import com.rapii.snapje.util.L
import com.rapii.snapje.util.sortedByOption
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for CategoryDetailScreen.
 * Uses StateFlow for consistent state management across the app.
 * Optimized for fast loading by caching categories and loading photos directly.
 *
 * Uses Hilt for dependency injection with SavedStateHandle for categoryId.
 */
@HiltViewModel
class CategoryDetailViewModel @Inject constructor(
    private val contentResolver: ContentResolver,
    private val savedStateHandle: SavedStateHandle,
    private val photoRepository: PhotoRepositoryInterface,
    private val trashRepository: TrashRepository,
    private val fileOperations: FileOperations
) : ViewModel() {

    // Get categoryId from SavedStateHandle (passed via navigation)
    // The categoryId should be available from the navigation route arguments
    // Public access allows UI and navigation to check the current ID
    val categoryId: Long by lazy {
        savedStateHandle.get<Long>("categoryId")?.also { id ->
            L.d("CategoryDetailVM", "Received categoryId from SavedStateHandle: $id")
        } ?: run {
            L.e("CategoryDetailVM", "categoryId not found in SavedStateHandle! Check navigation arguments.")
            -1L // Return invalid ID, will show error state
        }
    }

    // Expose trashRepository for use in UI
    val trashRepositoryInstance: TrashRepository get() = trashRepository

    // FileOperations is now injected via Hilt - no manual initialization needed

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
     * This avoids redundant MediaStore queries.
     */
    fun setCachedCategory(category: Category?) {
        cachedCategory = category
    }

    /**
     * Expose fileOperations for use in UI.
     */
    val fileOperationsInstance: FileOperations get() = fileOperations

    /**
     * Load photos for this category.
     * Optimized: Loads photos directly without loading all categories first.
     * Shows cached data immediately while refreshing in background.
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

        L.d("CategoryDetailVM", "Loading photos for categoryId: $categoryId")
        
        // Cancel any existing load job before starting a new one (prevents memory leak)
        loadJob?.cancel()

        loadJob = viewModelScope.launch {
            try {
                // Don't show loading if we already have cached data (faster perceived load)
                val showLoading = _uiState.value.photos.isEmpty()

                if (showLoading) {
                    _uiState.update { it.copy(isLoading = true, error = null) }
                }

                L.d("CategoryDetailVM", "Calling getPhotosByAlbum for categoryId: $categoryId")
                
                // Load photos directly - no need to load all categories first
                rawPhotos = photoRepository.getPhotosByAlbum(categoryId)
                L.d("CategoryDetailVM", "Loaded ${rawPhotos.size} photos for categoryId: $categoryId")
                
                val sortedPhotos = rawPhotos.sortedByOption(_currentSort.value)

                // Load category info in background (non-blocking)
                val category = cachedCategory ?: run {
                    val categories = photoRepository.getCategories()
                    categories.find { it.id == categoryId }?.also {
                        cachedCategory = it
                        L.d("CategoryDetailVM", "Found category: ${it.displayName}")
                    }
                }

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        photos = sortedPhotos,
                        category = category,
                        error = null
                    )
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // CRITICAL: Log cancellation but don't propagate to UI
                // This is expected when navigating away during a load
                L.d("CategoryDetailVM", "Load cancelled - likely due to navigation")
                return@launch
            } catch (e: Exception) {
                L.e("CategoryDetailVM", "Failed to load photos: ${e.message}", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Failed to load photos: ${e.message}"
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

    /**
     * Rename a photo.
     */
    suspend fun renamePhoto(photo: PhotoItem, newName: String): FileOperationResult? {
        return fileOperations?.renamePhoto(photo, newName)
    }

    /**
     * Get photo info.
     */
    suspend fun getPhotoInfo(photo: PhotoItem): PhotoInfo? {
        return fileOperations?.getPhotoInfo(photo)
    }

    /**
     * Share a photo.
     */
    fun sharePhoto(photo: PhotoItem) {
        fileOperations.sharePhoto(photo)
    }

    /**
     * Remove a photo from the in-memory list after deletion.
     * This ensures UI updates immediately without waiting for reload.
     * IMPORTANT: Uses StateFlow.update{} to guarantee state emission.
     */
    fun removePhotoFromList(photo: PhotoItem) {
        // Remove from raw photos first
        rawPhotos = rawPhotos.filter { it.id != photo.id }

        // CRITICAL: Use update{} to ensure StateFlow emits the new state
        _uiState.update { currentState ->
            val newPhotos = currentState.photos.filter { it.id != photo.id }
            currentState.copy(
                photos = newPhotos
            )
        }

        // Log for debugging
        L.d("CategoryDetailVM", "Removed photo ${photo.id}, remaining: ${rawPhotos.size}")
    }

    /**
     * Update a photo's name in the in-memory list after rename.
     * This ensures UI updates immediately without waiting for reload.
     */
    fun updatePhotoName(photo: PhotoItem, newName: String) {
        // Update in raw photos
        rawPhotos = rawPhotos.map { 
            if (it.id == photo.id) it.copy(displayName = newName) else it 
        }

        // CRITICAL: Use update{} to ensure StateFlow emits the new state
        _uiState.update { currentState ->
            val updatedPhotos = currentState.photos.map {
                if (it.id == photo.id) it.copy(displayName = newName) else it
            }
            currentState.copy(
                photos = updatedPhotos
            )
        }

        L.d("CategoryDetailVM", "Updated photo name to $newName")
    }

    /**
     * Add a photo back to the in-memory list after restoration from trash.
     * This ensures UI updates immediately without waiting for reload.
     * IMPORTANT: Uses StateFlow.update{} to guarantee state emission.
     */
    fun addPhotoBack(photo: PhotoItem) {
        // Check if photo already exists (avoid duplicates)
        if (rawPhotos.any { it.id == photo.id }) {
            return
        }
        
        // Add to raw photos
        rawPhotos = rawPhotos + photo

        // CRITICAL: Use update{} to ensure StateFlow emits the new state
        _uiState.update { currentState ->
            val newPhotos = currentState.photos + photo
            currentState.copy(
                photos = newPhotos
            )
        }

        // Log for debugging
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
