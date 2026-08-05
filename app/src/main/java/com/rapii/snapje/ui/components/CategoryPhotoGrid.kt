package com.rapii.snapje.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.rapii.snapje.R
import com.rapii.snapje.data.PhotoItem

/**
 * Grid item for displaying photos in a category.
 * Supports click, long press, and selection state.
 */
@Composable
fun CategoryPhotoGridItemWithLongPress(
    photo: PhotoItem,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = { onLongPress() }
                )
            }
    ) {
        val context = LocalContext.current
        // 保险库照片使用无磁盘缓存的加载器（避免解密明文被 Coil 落盘）
        val vaultLoader = remember(context) {
            com.rapii.snapje.util.ImageLoaderFactory.createVaultLoader(context)
        }
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(photo.uri)
                .crossfade(false)
                .build(),
            imageLoader = vaultLoader,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        if (isSelectionMode) {
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    contentAlignment = Alignment.TopEnd
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CheckCircle,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

/**
 * Photo grid with drag-to-select support.
 * 
 * DRAG-TO-SELECT IMPLEMENTATION:
 * 1. On long press — enter selection mode, select initial item
 * 2. On ACTION_MOVE while pressed — find item under finger and select it
 * 3. On ACTION_UP — stop drag selection, keep selection mode active
 */
@Composable
fun PhotoGridWithOperations(
    photos: List<PhotoItem>,
    isSelectionMode: Boolean,
    selectedPhotos: Set<PhotoItem>,
    onPhotoClick: (PhotoItem, Int) -> Unit,
    onPhotoLongPress: (PhotoItem) -> Unit,
    onDragSelect: ((PhotoItem) -> Unit)? = null,
    modifier: Modifier = Modifier,
    columns: Int = 3,
    state: LazyGridState = rememberLazyGridState()
) {
    var isDragging by remember { mutableStateOf(false) }
    var lastDraggedIndex by remember { mutableStateOf(-1) }

    // CRITICAL: Improved drag-to-select using pointerInput with proper gesture detection
    val dragModifier = if (isSelectionMode && onDragSelect != null && photos.isNotEmpty()) {
        Modifier.pointerInput(photos.size, selectedPhotos.size, isSelectionMode) {
            awaitEachGesture {
                // Wait for initial down event
                val downEvent = awaitFirstDown()
                isDragging = false
                lastDraggedIndex = -1
                
                // Track movement
                do {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.pressed } ?: break
                    
                    if (change.pressed) {
                        // Check if we've moved enough to consider it a drag
                        if (!isDragging) {
                            val positionChange = change.positionChange()
                            if (positionChange.getDistance() > 8.dp.toPx()) {
                                isDragging = true
                            }
                        }
                        
                        if (isDragging) {
                            // Find item under finger using grid state
                            val touchPosition = change.position
                            val visibleItems = state.layoutInfo.visibleItemsInfo
                            
                            // Find which item is under the touch position
                            val itemUnderTouch = visibleItems.find { itemInfo ->
                                val itemTop = itemInfo.offset.y
                                val itemBottom = itemInfo.offset.y + itemInfo.size.height
                                val itemLeft = itemInfo.offset.x
                                val itemRight = itemInfo.offset.x + itemInfo.size.width

                                touchPosition.x >= itemLeft && 
                                touchPosition.x <= itemRight &&
                                touchPosition.y >= itemTop && 
                                touchPosition.y <= itemBottom
                            }
                            
                            itemUnderTouch?.let { itemInfo ->
                                val index = itemInfo.index
                                if (index in photos.indices && index != lastDraggedIndex) {
                                    lastDraggedIndex = index
                                    val photoUnderTouch = photos[index]
                                    // Select the photo if not already selected
                                    onDragSelect(photoUnderTouch)
                                }
                            }
                        }
                    }
                    
                    change.consume()
                } while (event.changes.any { it.pressed })
                
                // Reset on release
                isDragging = false
                lastDraggedIndex = -1
            }
        }
    } else {
        Modifier
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = modifier.then(dragModifier),
        state = state,
        contentPadding = PaddingValues(0.dp)
    ) {
        items(
            count = photos.size,
            key = { index -> photos[index].id }
        ) { index ->
            val photo = photos[index]
            val isSelected = photo in selectedPhotos
            CategoryPhotoGridItemWithLongPress(
                photo = photo,
                isSelectionMode = isSelectionMode,
                isSelected = isSelected,
                onClick = { onPhotoClick(photo, index) },
                onLongPress = { onPhotoLongPress(photo) }
            )
        }
    }
}
