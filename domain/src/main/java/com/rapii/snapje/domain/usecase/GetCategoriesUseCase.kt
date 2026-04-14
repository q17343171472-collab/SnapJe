package com.rapii.snapje.domain.usecase

import com.rapii.snapje.data.Category
import com.rapii.snapje.data.CachedPhotoRepository
import javax.inject.Inject

/**
 * Use case to get all categories.
 * Part of the domain layer, encapsulating business logic for retrieving categories.
 */
class GetCategoriesUseCase @Inject constructor(
    private val cachedPhotoRepository: CachedPhotoRepository
) {
    /**
     * Execute the use case to get categories with cache-first strategy.
     * Returns cached data immediately if available, otherwise loads from MediaStore.
     */
    suspend operator fun invoke(): List<Category> {
        return cachedPhotoRepository.getCategoriesWithCache()
    }
}
