package com.rapii.snapje.domain.usecase

import com.rapii.snapje.data.PhotoItem
import com.rapii.snapje.data.PhotoRepository
import javax.inject.Inject

/**
 * Use case to search photos by query.
 * Part of the domain layer, encapsulating business logic for searching photos.
 */
class SearchPhotosUseCase @Inject constructor(
    private val photoRepository: PhotoRepository
) {
    /**
     * Execute the use case to search photos matching a query.
     * @param query The search query string
     * @return List of photos matching the search query
     */
    suspend operator fun invoke(query: String): List<PhotoItem> {
        return photoRepository.searchPhotos(query)
    }
}
