package com.rapii.snapje.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rapii.snapje.data.FileOperationResult
import com.rapii.snapje.data.TrashRepository
import com.rapii.snapje.data.TrashedPhoto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UI state for Recently Deleted screen.
 */
data class TrashUiState(
    val isLoading: Boolean = true,
    val photos: List<TrashedPhoto> = emptyList(),
    val error: String? = null,
    val selectedPhoto: TrashedPhoto? = null
)

/**
 * ViewModel for Recently Deleted (Trash) screen.
 * Scoped to Activity so it can be shared across CategoryDetailScreen and RecentlyDeletedScreen.
 */
@HiltViewModel
class TrashViewModel @Inject constructor(
    private val trashRepository: TrashRepository
) : ViewModel() {

    // Track cleanup job for cancellation
    private var cleanupJob: Job? = null

    private val _uiState = MutableStateFlow(TrashUiState())
    val uiState: StateFlow<TrashUiState> = _uiState.asStateFlow()

    init {
        loadTrashedPhotos()
    }

    /**
     * Load all trashed photos.
     */
    fun loadTrashedPhotos() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            try {
                val photos = trashRepository.loadTrashedPhotos()
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    photos = photos,
                    error = null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Failed to load: ${e.message}"
                )
            }
        }
    }

    /**
     * Select a photo for actions.
     */
    fun selectPhoto(photo: TrashedPhoto?) {
        _uiState.value = _uiState.value.copy(selectedPhoto = photo)
    }

    /**
     * Start restore from trash - returns NeedsPermission for API 30+.
     */
    fun startRestore(photo: TrashedPhoto, onResult: (FileOperationResult) -> Unit) {
        viewModelScope.launch {
            val result = trashRepository.restoreFromTrash(photo)
            when (result) {
                is FileOperationResult.Success -> {
                    // Remove from trash list immediately
                    val updatedPhotos = _uiState.value.photos.filter { it.id != photo.id }
                    _uiState.value = _uiState.value.copy(photos = updatedPhotos)
                }
                is FileOperationResult.NeedsPermission -> {
                    // Caller will launch permission dialog
                }
                is FileOperationResult.Error -> {
                    // Error will be shown by caller
                }
            }
            onResult(result)
        }
    }

    /**
     * Confirm restore after permission granted.
     */
    fun confirmRestore(photo: TrashedPhoto) {
        viewModelScope.launch {
            trashRepository.confirmRestore(photo)
            // Remove from trash list
            val updatedPhotos = _uiState.value.photos.filter { it.id != photo.id }
            _uiState.value = _uiState.value.copy(photos = updatedPhotos)
        }
    }

    /**
     * Cancel restore (user denied permission).
     */
    fun cancelRestore(photo: TrashedPhoto) {
        viewModelScope.launch {
            trashRepository.cancelRestore(photo)
        }
    }

    /**
     * Permanently delete a photo.
     */
    fun permanentDelete(photo: TrashedPhoto, onResult: (FileOperationResult) -> Unit) {
        viewModelScope.launch {
            val result = trashRepository.permanentDelete(photo)
            if (result is FileOperationResult.Success) {
                val updatedPhotos = _uiState.value.photos.filter { it.id != photo.id }
                _uiState.value = _uiState.value.copy(photos = updatedPhotos)
            }
            onResult(result)
        }
    }

    /**
     * Empty the entire trash.
     */
    fun emptyTrash(onResult: (FileOperationResult) -> Unit) {
        viewModelScope.launch {
            val result = trashRepository.emptyTrash()
            if (result is FileOperationResult.Success) {
                _uiState.value = _uiState.value.copy(photos = emptyList())
            }
            onResult(result)
        }
    }

    /**
     * Clean up expired items.
     */
    fun cleanupExpired() {
        viewModelScope.launch {
            val deletedCount = trashRepository.cleanupExpiredItems()
            if (deletedCount > 0) {
                loadTrashedPhotos()
            }
        }
    }

    /**
     * Get total storage used by trashed items.
     */
    fun getTrashSize(): Long {
        return trashRepository.getTrashSize()
    }

    /**
     * Get count of items in trash.
     */
    fun getTrashCount(): Int {
        return trashRepository.getTrashCount()
    }

    /**
     * Clean up resources when ViewModel is destroyed.
     */
    override fun onCleared() {
        super.onCleared()
        // Cancel any ongoing cleanup jobs
        cleanupJob?.cancel()
        cleanupJob = null
    }
}
