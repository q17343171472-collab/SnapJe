package com.rapii.snapje.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.rapii.snapje.R
import com.rapii.snapje.data.Album

@Composable
fun AlbumGrid(
    albums: List<Album>,
    modifier: Modifier = Modifier,
    onAlbumClick: (Album) -> Unit = {}
) {
    if (albums.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "没有找到相册",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(albums, key = { it.id }) { album ->
                AlbumCard(
                    album = album,
                    onClick = { onAlbumClick(album) }
                )
            }
        }
    }
}

@Composable
fun AlbumCard(
    album: Album,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            // Album cover grid (shows first 4 photos)
            if (album.photos.isNotEmpty()) {
                AlbumCoverGrid(photos = album.photos)
            } else {
                // Fallback: folder icon
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }
            
            // Album info overlay
            AlbumInfoOverlay(album = album)
        }
    }
}

@Composable
private fun AlbumCoverGrid(photos: List<com.rapii.snapje.data.PhotoItem>) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp)
    ) {
        when (photos.size) {
            1 -> {
                // Single photo - full size
                AlbumPhotoItem(
                    photo = photos[0],
                    modifier = Modifier.fillMaxSize()
                )
            }
            2 -> {
                // Two photos - side by side
                Row(
                    modifier = Modifier.fillMaxSize()
                ) {
                    AlbumPhotoItem(
                        photo = photos[0],
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 2.dp)
                    )
                    AlbumPhotoItem(
                        photo = photos[1],
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 2.dp)
                    )
                }
            }
            3 -> {
                // Three photos - one on top, two below
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    AlbumPhotoItem(
                        photo = photos[0],
                        modifier = Modifier
                            .weight(1f)
                            .padding(bottom = 2.dp)
                    )
                    Row(
                        modifier = Modifier.weight(1f)
                    ) {
                        AlbumPhotoItem(
                            photo = photos[1],
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 2.dp)
                        )
                        AlbumPhotoItem(
                            photo = photos[2],
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 2.dp)
                        )
                    }
                }
            }
            else -> {
                // Four photos - 2x2 grid
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    Row(
                        modifier = Modifier.weight(1f)
                    ) {
                        AlbumPhotoItem(
                            photo = photos[0],
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 2.dp, bottom = 2.dp)
                        )
                        AlbumPhotoItem(
                            photo = photos[1],
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 2.dp, bottom = 2.dp)
                        )
                    }
                    Row(
                        modifier = Modifier.weight(1f)
                    ) {
                        AlbumPhotoItem(
                            photo = photos[2],
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 2.dp, top = 2.dp)
                        )
                        AlbumPhotoItem(
                            photo = photos[3],
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 2.dp, top = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AlbumPhotoItem(
    photo: com.rapii.snapje.data.PhotoItem,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
    ) {
        val painter = rememberAsyncImagePainter(
            model = ImageRequest.Builder(LocalContext.current)
                .data(photo.uri)
                .crossfade(false)
                .build()
        )
        
        Image(
            painter = painter,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
    }
}

@Composable
private fun AlbumInfoOverlay(album: Album) {
    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.7f))
                .padding(8.dp)
        ) {
            Text(
                text = album.displayName,
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            
            Spacer(modifier = Modifier.height(2.dp))
            
            Text(
                text = "${album.photoCount} 张照片",
                color = Color.White.copy(alpha = 0.8f),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}