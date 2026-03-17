package com.rapii.snapje.data

import android.content.ContentResolver
import android.content.ContentUris
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.rapii.snapje.util.L
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for accessing photos from MediaStore.
 * Implements PhotoRepositoryInterface for testability and dependency injection.
 */
@Singleton
class PhotoRepository @Inject constructor(
    private val contentResolver: ContentResolver
) : PhotoRepositoryInterface {

    /**
     * Get all photos from the device.
     */
    override suspend fun getAllPhotos(): List<PhotoItem> = withContext(Dispatchers.IO) {
        val photos = mutableListOf<PhotoItem>()

        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.BUCKET_ID,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.MIME_TYPE
        )

        val sortOrder = "${MediaStore.Images.Media.DATE_TAKEN} DESC"

        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        contentResolver.query(
            collection,
            projection,
            null,
            null,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
            val bucketIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID)
            val bucketNameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            val mimeTypeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val name = cursor.getString(nameColumn) ?: "Unknown"
                val dateTaken = cursor.getLong(dateColumn)
                val bucketId = cursor.getLong(bucketIdColumn)
                val bucketName = cursor.getString(bucketNameColumn)
                val size = cursor.getLong(sizeColumn)
                val mimeType = cursor.getString(mimeTypeColumn) ?: "image/jpeg"

                val contentUri = ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    id
                )

                photos.add(
                    PhotoItem(
                        id = id,
                        uri = contentUri,
                        displayName = name,
                        dateTaken = dateTaken,
                        bucketId = bucketId,
                        bucketName = bucketName,
                        size = size,
                        mimeType = mimeType
                    )
                )
            }
        }

        return@withContext photos
    }

    /**
     * Get all categories (folders) with their photos.
     */
    override suspend fun getCategories(): List<Category> = withContext(Dispatchers.IO) {
        val categoriesMap = mutableMapOf<Long, MutableList<PhotoItem>>()
        val categoryNames = mutableMapOf<Long, String>()
        val categoryPaths = mutableMapOf<Long, String>()
        val lastModifiedMap = mutableMapOf<Long, Long>()

        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.DATE_MODIFIED,
            MediaStore.Images.Media.BUCKET_ID,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Images.Media.DATA,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.MIME_TYPE
        )

        val sortOrder = "${MediaStore.Images.Media.DATE_MODIFIED} DESC"

        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        contentResolver.query(
            collection,
            projection,
            null,
            null,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val dateTakenColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
            val dateModifiedColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
            val bucketIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID)
            val bucketNameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
            val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            val mimeTypeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val name = cursor.getString(nameColumn) ?: "Unknown"
                val dateTaken = cursor.getLong(dateTakenColumn)
                val dateModified = cursor.getLong(dateModifiedColumn) * 1000
                val bucketId = cursor.getLong(bucketIdColumn)
                val bucketName = cursor.getString(bucketNameColumn)
                val filePath = cursor.getString(dataColumn)
                val size = cursor.getLong(sizeColumn)
                val mimeType = cursor.getString(mimeTypeColumn) ?: "image/jpeg"

                val contentUri = ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    id
                )

                val photo = PhotoItem(
                    id = id,
                    uri = contentUri,
                    displayName = name,
                    dateTaken = dateTaken,
                    bucketId = bucketId,
                    bucketName = bucketName,
                    size = size,
                    mimeType = mimeType
                )

                categoriesMap.getOrPut(bucketId) { mutableListOf() }.add(photo)

                if (bucketName != null && !categoryNames.containsKey(bucketId)) {
                    categoryNames[bucketId] = Category.getDisplayName(bucketName)
                }

                if (filePath != null && !categoryPaths.containsKey(bucketId)) {
                    categoryPaths[bucketId] = filePath.substringBeforeLast("/")
                }

                val currentLastModified = lastModifiedMap[bucketId] ?: 0L
                if (dateModified > currentLastModified) {
                    lastModifiedMap[bucketId] = dateModified
                }
            }
        }

        return@withContext categoriesMap.mapNotNull { (bucketId, photos) ->
            val categoryName = categoryNames[bucketId] ?: "Folder $bucketId"
            val categoryPath = categoryPaths[bucketId] ?: "Unknown path"
            val lastModified = lastModifiedMap[bucketId] ?: System.currentTimeMillis()

            if (photos.isEmpty()) return@mapNotNull null

            Category(
                id = bucketId,
                name = categoryName,
                path = categoryPath,
                coverUris = photos.take(4).map { it.uri },
                itemCount = photos.size,
                lastModified = lastModified
            )
        }.sortedByDescending { it.lastModified }
    }

    /**
     * Get albums (categories) simplified.
     */
    override suspend fun getAlbums(): List<Album> = withContext(Dispatchers.IO) {
        getCategories().map { category ->
            Album(
                id = category.id,
                name = category.name,
                coverPhotoUri = category.coverUris.firstOrNull(),
                photoCount = category.itemCount,
                photos = emptyList()
            )
        }
    }

    /**
     * Get photos for a specific album/category.
     */
    override suspend fun getPhotosByAlbum(albumId: Long): List<PhotoItem> = withContext(Dispatchers.IO) {
        val photos = mutableListOf<PhotoItem>()

        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.MIME_TYPE
        )

        val selection = "${MediaStore.Images.Media.BUCKET_ID} = ?"
        val selectionArgs = arrayOf(albumId.toString())
        val sortOrder = "${MediaStore.Images.Media.DATE_TAKEN} DESC"

        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        contentResolver.query(
            collection,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            val mimeTypeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val name = cursor.getString(nameColumn) ?: "Unknown"
                val dateTaken = cursor.getLong(dateColumn)
                val size = cursor.getLong(sizeColumn)
                val mimeType = cursor.getString(mimeTypeColumn) ?: "image/jpeg"

                val contentUri = ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    id
                )

                photos.add(
                    PhotoItem(
                        id = id,
                        uri = contentUri,
                        displayName = name,
                        dateTaken = dateTaken,
                        bucketId = albumId,
                        size = size,
                        mimeType = mimeType
                    )
                )
            }
        }

        return@withContext photos
    }

    /**
     * Get photos as a Flow of PagingData for pagination support.
     * This prevents OOM errors on devices with thousands of photos.
     *
     * @param bucketId Optional bucket/folder ID to filter photos (null = all photos)
     * @param pageSize Number of photos to load per page (default 50)
     * @return Flow of PagingData containing paginated photos
     */
    fun getPhotosPaging(bucketId: Long? = null, pageSize: Int = 50): Flow<PagingData<PhotoItem>> {
        return Pager(
            config = PagingConfig(
                pageSize = pageSize,
                enablePlaceholders = false,
                prefetchDistance = pageSize,
                initialLoadSize = pageSize * 2
            ),
            pagingSourceFactory = {
                PhotoPagingSource(contentResolver, bucketId, pageSize)
            }
        ).flow
    }

    /**
     * Search photos by display name.
     */
    override suspend fun searchPhotos(query: String): SearchResult = withContext(Dispatchers.IO) {
        val photos = mutableListOf<PhotoItem>()
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.BUCKET_ID,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.MIME_TYPE
        )

        val selection = "${MediaStore.Images.Media.DISPLAY_NAME} LIKE ?"
        val selectionArgs = arrayOf("%$query%")

        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        contentResolver.query(
            collection,
            projection,
            selection,
            selectionArgs,
            null
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val displayNameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val dateTakenColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
            val bucketIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID)
            val bucketNameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            val mimeTypeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val displayName = cursor.getString(displayNameColumn)
                val dateTaken = cursor.getLong(dateTakenColumn)
                val bucketId = cursor.getLong(bucketIdColumn)
                val bucketName = cursor.getString(bucketNameColumn)
                val size = cursor.getLong(sizeColumn)
                val mimeType = cursor.getString(mimeTypeColumn)

                val uri = ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    id
                )

                photos.add(
                    PhotoItem(
                        id = id,
                        uri = uri,
                        displayName = displayName,
                        dateTaken = dateTaken,
                        bucketId = bucketId,
                        bucketName = bucketName,
                        size = size,
                        mimeType = mimeType
                    )
                )
            }
        }

        SearchResult(query = query, photos = photos)
    }
}
