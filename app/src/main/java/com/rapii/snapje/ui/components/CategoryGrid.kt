package com.rapii.snapje.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.rapii.snapje.R
import com.rapii.snapje.data.Category

/**
 * 分组网格：支持长按拖拽排序（持久化）。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CategoryGrid(
    categories: List<Category>,
    onCategoryClick: (Category) -> Unit,
    onTogglePin: (Long) -> Unit,
    onHideCategory: (Long) -> Unit,
    modifier: Modifier = Modifier,
    columns: Int = 2,
    onReorder: ((List<Long>) -> Unit)? = null
) {
    if (categories.isEmpty()) {
        EmptyCategoriesState()
    } else {
        val gridState = rememberLazyGridState()
        var draggingId by remember { mutableStateOf<Long?>(null) }
        var dragOffset by remember { mutableStateOf(Offset.Zero) }
        // 拖拽过程中的本地列表：拖动时直接改它，松手一次性提交保存，避免手势被重启打断
        var displayItems by remember { mutableStateOf(categories) }
        val currentCategories by rememberUpdatedState(categories)
        LaunchedEffect(categories) { displayItems = categories }

        // 拖拽手势：长按任意分组卡片后拖动，跨过半个单元格即换位
        val dragModifier = Modifier.pointerInput(columns) {
            detectDragGesturesAfterLongPress(
                onDragStart = { offset ->
                    // 找到长按位置对应的分组
                    val item = gridState.layoutInfo.visibleItemsInfo.minByOrNull {
                        val center = Offset(
                            it.offset.x + it.size.width / 2f,
                            it.offset.y + it.size.height / 2f
                        )
                        (center - offset).getDistance()
                    }
                    draggingId = item?.let { info ->
                        if (info.index in displayItems.indices) displayItems[info.index].id else null
                    }
                    dragOffset = Offset.Zero
                },
                onDrag = { change, dragAmount ->
                    change.consume()
                    dragOffset += dragAmount
                    val currentId = draggingId ?: return@detectDragGesturesAfterLongPress
                    val currentIndex = displayItems.indexOfFirst { it.id == currentId }
                    if (currentIndex < 0) return@detectDragGesturesAfterLongPress

                    // 根据拖动距离计算目标索引（按网格行列移动）
                    val cellHeight = gridState.layoutInfo.visibleItemsInfo.firstOrNull()?.size?.height ?: 0
                    val cellWidth = gridState.layoutInfo.visibleItemsInfo.firstOrNull()?.size?.width ?: 0
                    if (cellHeight <= 0 || cellWidth <= 0) return@detectDragGesturesAfterLongPress

                    val rowOffset = (dragOffset.y / cellHeight).toInt()
                    val colOffset = (dragOffset.x / cellWidth).toInt()
                    val targetIndex = (currentIndex + rowOffset * columns + colOffset)
                        .coerceIn(0, displayItems.size - 1)

                    if (targetIndex != currentIndex) {
                        val newList = displayItems.toMutableList()
                        val item = newList.removeAt(currentIndex)
                        newList.add(targetIndex, item)
                        displayItems = newList
                        // 重置拖拽基准，避免累计漂移
                        dragOffset = Offset.Zero
                    }
                },
                onDragEnd = {
                    // 松手：一次性提交最终顺序并持久化
                    onReorder?.invoke(displayItems.map { it.id })
                    draggingId = null
                    dragOffset = Offset.Zero
                },
                onDragCancel = {
                    draggingId = null
                    dragOffset = Offset.Zero
                    // 取消则恢复外部顺序
                    displayItems = currentCategories
                }
            )
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            modifier = modifier.then(dragModifier),
            state = gridState,
            contentPadding = PaddingValues(0.dp)
        ) {
            items(
                items = displayItems,
                key = { it.id }  // Stable IDs for view recycling
            ) { category ->
                // 拖拽中分组略微放大+提升层级
                val isDragging = draggingId == category.id
                CategoryCard(
                    category = category,
                    onClick = { if (draggingId == null) onCategoryClick(category) },
                    onTogglePin = { onTogglePin(category.id) },
                    onHideCategory = { onHideCategory(category.id) },
                    modifier = Modifier
                        .graphicsLayer {
                            scaleX = if (isDragging) 1.05f else 1f
                            scaleY = if (isDragging) 1.05f else 1f
                            shadowElevation = if (isDragging) 12f else 0f
                        }
                        .zIndex(if (isDragging) 1f else 0f)
                        .animateItem()
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun CategoryCard(
    category: Category,
    onClick: () -> Unit,
    onTogglePin: () -> Unit,
    onHideCategory: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }

    // CRITICAL: Use MutableInteractionSource for proper ripple effect without heavy animations
    val interactionSource = remember { MutableInteractionSource() }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        val context = LocalContext.current

        Box(
            modifier = Modifier
                .fillMaxSize()
                // 长按保留给网格层做拖拽排序；菜单用右上角 ⋮ 按钮打开
                .combinedClickable(
                    onClick = onClick,
                    interactionSource = interactionSource,
                    indication = null // Custom ripple handled by Card
                )
        ) {
            // Category thumbnail collage - provide semantic info
            CategoryThumbnail(
                category = category,
                modifier = Modifier.semantics {
                    contentDescription = context.getString(
                        R.string.cd_category_thumbnail,
                        category.displayName
                    )
                }
            )
            
            // Gradient overlay for text
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.6f)
                            ),
                            startY = 200f
                        )
                    )
            )
            
            // Category info at bottom
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                Text(
                    text = category.displayName,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Medium
                    ),
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Text(
                    text = category.formattedItemCount,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.7f)
                )
            }
            
            // More options button
            Box(
                modifier = Modifier.align(Alignment.TopEnd)
            ) {
                // CRITICAL: Remove heavy haptic feedback - use simple click instead
                IconButton(
                    onClick = { showMenu = true }
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.more_options),
                        tint = Color.White.copy(alpha = 0.8f)
                    )
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                if (category.isPinned) {
                                    stringResource(R.string.unpin)
                                } else {
                                    stringResource(R.string.pin_to_top)
                                }
                            )
                        },
                        onClick = {
                            onTogglePin()
                            showMenu = false
                        }
                    )

                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.hide_category)) },
                        onClick = {
                            onHideCategory()
                            showMenu = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun CategoryThumbnail(
    category: Category,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    // CRITICAL: 保险库封面使用无磁盘缓存的加载器（避免解密明文被 Coil 落盘）
    val thumbnailLoader = remember(context) {
        com.rapii.snapje.util.ImageLoaderFactory.createVaultLoader(context)
    }
    
    val imageRequest = remember(category.coverUris, context) {
        ImageRequest.Builder(context)
            .data(category.coverUris.firstOrNull())
            .crossfade(false)
            .build()
    }
    
    // Just show the first image as cover - simpler and cleaner
    AsyncImage(
        model = imageRequest,
        contentDescription = stringResource(
            R.string.cd_category_thumbnail,
            category.displayName
        ),
        imageLoader = thumbnailLoader,
        modifier = modifier.fillMaxSize(),
        contentScale = ContentScale.Crop
    )
}

@Composable
fun EmptyCategoriesState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "还没有照片",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}
