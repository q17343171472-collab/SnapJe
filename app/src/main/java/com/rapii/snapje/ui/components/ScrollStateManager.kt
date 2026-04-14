package com.rapii.snapje.ui.components

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.*
import com.rapii.snapje.data.PhotoItem
import com.rapii.snapje.util.L

/**
 * Manages scroll state for photo grids to preserve position when returning from fullscreen.
 * Extracted from CategoryDetailScreen to reduce complexity.
 */
class ScrollStateManager(
    private val gridState: LazyGridState
) {
    data class ScrollStateData(
        val tappedPhotoIndex: Int,
        val tappedPhotoOffset: Int,
        val viewportHeight: Int,
        val totalItems: Int
    )
    
    var savedScrollState by mutableStateOf<ScrollStateData?>(null)
        private set
    
    var shouldRestoreScroll by mutableStateOf(false)
        private set
    
    /**
     * Save scroll state synchronously when a photo is tapped
     */
    fun saveScrollState(tappedIndex: Int) {
        val visibleItems = gridState.layoutInfo.visibleItemsInfo
        val totalItems = gridState.layoutInfo.totalItemsCount
        val viewportHeight = gridState.layoutInfo.viewportSize.height
        
        val tappedItem = visibleItems.find { it.index == tappedIndex }
        val tappedPhotoOffset = tappedItem?.offset?.y?.toInt() ?: 0
        
        savedScrollState = ScrollStateData(
            tappedPhotoIndex = tappedIndex,
            tappedPhotoOffset = tappedPhotoOffset,
            viewportHeight = viewportHeight,
            totalItems = totalItems
        )
        
        L.d("ScrollStateManager", "Saving scroll (sync): tappedIndex=$tappedIndex, tappedOffset=$tappedPhotoOffset, viewportHeight=$viewportHeight, totalItems=$totalItems")
    }
    
    /**
     * Trigger scroll restoration when returning from fullscreen
     */
    fun triggerRestoration() {
        if (savedScrollState != null) {
            shouldRestoreScroll = true
        }
    }
    
    /**
     * Perform the actual scroll restoration
     */
    suspend fun restoreScroll(onComplete: () -> Unit) {
        val scrollData = savedScrollState ?: return
        val totalItems = scrollData.totalItems
        val tappedIndex = scrollData.tappedPhotoIndex.coerceIn(0, totalItems - 1)
        val tappedOffset = scrollData.tappedPhotoOffset
        val viewportHeight = scrollData.viewportHeight
        
        L.d("ScrollStateManager", "Restoring scroll: tappedIndex=$tappedIndex, tappedOffset=$tappedOffset, viewportHeight=$viewportHeight, totalItems=$totalItems")
        
        try {
            val restoreOffset = -tappedOffset
            gridState.scrollToItem(tappedIndex, restoreOffset)
            L.d("ScrollStateManager", "Scroll restored: index=$tappedIndex, offset=$restoreOffset (tapped at $tappedOffset from top)")
        } catch (e: Exception) {
            L.e("ScrollStateManager", "Scroll restoration failed: ${e.message}")
            gridState.scrollToItem(tappedIndex, 0)
            L.d("ScrollStateManager", "Fallback scroll to index=$tappedIndex, offset=0")
        }
        
        shouldRestoreScroll = false
        savedScrollState = null
        onComplete()
    }
    
    /**
     * Reset scroll state manager
     */
    fun reset() {
        savedScrollState = null
        shouldRestoreScroll = false
    }
}

/**
 * Creates and remembers a ScrollStateManager instance
 */
@Composable
fun rememberScrollStateManager(gridState: LazyGridState): ScrollStateManager {
    return remember(gridState) { ScrollStateManager(gridState) }
}
