package com.rapii.snapje.data

/**
 * Interface for photo repository operations.
 * Defines the contract for accessing photos from MediaStore.
 */
interface PhotoRepositoryInterface {

    /**
     * Get all photos from the device.
     * @return List of all photo items
     */
    suspend fun getAllPhotos(): List<PhotoItem>

    /**
     * Get all categories (folders) with their photos.
     * @return List of categories sorted by last modified
     */
    suspend fun getCategories(): List<Category>

    /**
     * Get albums (categories) simplified.
     * @return List of albums with basic info
     */
    suspend fun getAlbums(): List<Album>

    /**
     * Get photos for a specific album/category.
     * @param albumId The bucket ID of the album
     * @return List of photos in the album
     */
    suspend fun getPhotosByAlbum(albumId: Long): List<PhotoItem>

    /**
     * Search photos by display name.
     * @param query Search query string
     * @return SearchResult containing query and matching photos
     */
    suspend fun searchPhotos(query: String): SearchResult
}
