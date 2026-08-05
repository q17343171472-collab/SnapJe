package com.rapii.snapje.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.rapii.snapje.R
import com.rapii.snapje.data.Category

/**
 * Simplified version of CategoryGrid without experimental APIs.
 * Optimized for smooth transitions with stable IDs and lightweight animations.
 */
@Composable
fun CategoryGrid(
    categories: List<Category>,
    onCategoryClick: (Category) -> Unit,
    onTogglePin: (Long) -> Unit,
    onHideCategory: (Long) -> Unit,
    modifier: Modifier = Modifier,
    columns: Int = 2
) {
    if (categories.isEmpty()) {
        EmptyCategoriesState()
    } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            modifier = modifier,
            // No padding for seamless look
            contentPadding = PaddingValues(0.dp)
        ) {
            items(
                items = categories,
                key = { it.id }  // Stable IDs for view recycling
            ) { category ->
                // CRITICAL: Removed AnimatedVisibility to eliminate scroll lag
                // Items are now rendered directly without fade-in animations
                CategoryCard(
                    category = category,
                    onClick = { onCategoryClick(category) },
                    onTogglePin = { onTogglePin(category.id) },
                    onHideCategory = { onHideCategory(category.id) }
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
                // CRITICAL: Use combinedClickable for better performance than separate click/longClick
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = {
                        // Show menu on long press - lighter than haptic feedback
                        showMenu = true
                    },
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
            text = "No photos yet",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}
