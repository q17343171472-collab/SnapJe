package com.rapii.snapje.data

import android.content.ContentResolver
import android.net.Uri
import android.provider.MediaStore
import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.rapii.snapje.util.L

/**
 * PagingSource for loading photos in pages from MediaStore.
 * Prevents OOM errors on devices with thousands of photos.
 *
 * @param contentResolver ContentResolver for MediaStore access
 * @param bucketId Optional bucket/folder ID to filter photos
 * @param pageSize Number of photos to load per page
 */
class PhotoPagingSource(
    private val contentResolver: ContentResolver,
    private val bucketId: Long? = null,
    private val pageSize: Int = 50
) : PagingSource<Int, PhotoItem>() {

    override fun getRefreshKey(state: PagingState<Int, PhotoItem>): Int? {
        return state.anchorPosition?.let { anchorPosition ->
            state.closestPageToPosition(anchorPosition)?.prevKey?.plus(1)
                ?: state.closestPageToPosition(anchorPosition)?.nextKey?.minus(1)
        }
    }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, PhotoItem> {
        val position = params.key ?: 0
        
        return try {
            val photos = loadPhotosFromMediaStore(position, params.loadSize)
            
            LoadResult.Page(
                data = photos,
                prevKey = if (position == 0) null else position - pageSize,
                nextKey = if (photos.isEmpty()) null else position + pageSize
            )
        } catch (e: Exception) {
            L.e("PhotoPagingSource", "Failed to load photos at position $position: ${e.message}", e)
            LoadResult.Error(e)
        }
    }

    /**
     * Load a page of photos from MediaStore.
     */
    private fun loadPhotosFromMediaStore(offset: Int, loadSize: Int): List<PhotoItem> {
        val photos = mutableListOf<PhotoItem>()
        
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.MIME_TYPE,
            MediaStore.Images.Media.BUCKET_ID,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT
        )

        val selection = if (bucketId != null) {
            "${MediaStore.Images.Media.BUCKET_ID} = ?"
        } else null

        val selectionArgs = if (bucketId != null) {
            arrayOf(bucketId.toString())
        } else null

        val sortOrder = "${MediaStore.Images.Media.DATE_TAKEN} DESC"

        try {
            contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                // Skip to offset
                if (offset > 0) {
                    if (!cursor.moveToPosition(offset - 1)) {
                        return emptyList()
                    }
                }

                // Load up to loadSize items
                var loaded = 0
                while (cursor.moveToNext() && loaded < loadSize) {
                    val id = cursor.getLongOrNull(MediaStore.Images.Media._ID) ?: continue
                    
                    val photo = PhotoItem(
                        id = id,
                        uri = Uri.withAppendedPath(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            id.toString()
                        ),
                        displayName = cursor.getStringOrNull(MediaStore.Images.Media.DISPLAY_NAME) ?: "Unknown",
                        dateTaken = cursor.getLongOrNull(MediaStore.Images.Media.DATE_TAKEN) ?: 0L,
                        size = cursor.getLongOrNull(MediaStore.Images.Media.SIZE) ?: 0L,
                        mimeType = cursor.getStringOrNull(MediaStore.Images.Media.MIME_TYPE) ?: "image/jpeg",
                        bucketId = cursor.getLongOrNull(MediaStore.Images.Media.BUCKET_ID),
                        bucketName = cursor.getStringOrNull(MediaStore.Images.Media.BUCKET_DISPLAY_NAME),
                        width = cursor.getIntOrNull(MediaStore.Images.Media.WIDTH) ?: 0,
                        height = cursor.getIntOrNull(MediaStore.Images.Media.HEIGHT) ?: 0
                    )
                    photos.add(photo)
                    loaded++
                }
            }
        } catch (e: Exception) {
            L.e("PhotoPagingSource", "Error querying MediaStore: ${e.message}", e)
            throw e
        }

        return photos
    }

    /**
     * Safe cursor get methods that handle null values.
     */
    private fun android.database.Cursor.getStringOrNull(columnName: String): String? {
        val index = getColumnIndex(columnName)
        return if (index >= 0 && !isNull(index)) getString(index) else null
    }

    private fun android.database.Cursor.getLongOrNull(columnName: String): Long? {
        val index = getColumnIndex(columnName)
        return if (index >= 0 && !isNull(index)) getLong(index) else null
    }

    private fun android.database.Cursor.getIntOrNull(columnName: String): Int? {
        val index = getColumnIndex(columnName)
        return if (index >= 0 && !isNull(index)) getInt(index) else null
    }
}
