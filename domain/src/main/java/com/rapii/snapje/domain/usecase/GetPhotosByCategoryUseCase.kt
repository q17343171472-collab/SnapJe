package com.rapii.snapje.domain.usecase

import com.rapii.snapje.data.PhotoItem
import com.rapii.snapje.data.PhotoRepository
import javax.inject.Inject

/**
 * Use case to get photos by category ID.
 * Part of the domain layer, encapsulating business logic for retrieving photos in a category.
 */
class GetPhotosByCategoryUseCase @Inject constructor(
    private val photoRepository: PhotoRepository
) {
    /**
     * Execute the use case to get photos for a specific category.
     * @param bucketId The bucket/category ID to fetch photos from
     * @return List of photos in the specified category
     */
    suspend operator fun invoke(bucketId: Long): List<PhotoItem> {
        return photoRepository.getPhotosByBucket(bucketId)
    }
}
