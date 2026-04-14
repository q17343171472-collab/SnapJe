package com.rapii.snapje.domain.usecase

import com.rapii.snapje.data.PhotoItem
import com.rapii.snapje.data.PhotoRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations

/**
 * Unit tests for GetPhotosByCategoryUseCase.
 * Validates photo retrieval business logic by category.
 */
class GetPhotosByCategoryUseCaseTest {

    @Mock
    private lateinit var photoRepository: PhotoRepository

    private lateinit var getPhotosByCategoryUseCase: GetPhotosByCategoryUseCase

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        getPhotosByCategoryUseCase = GetPhotosByCategoryUseCase(photoRepository)
    }

    @Test
    fun `invoke returns photos for given category id`() {
        // Given
        val categoryId = 1L
        val expectedPhotos = listOf(
            PhotoItem(id = "1", uri = android.net.Uri.parse("content://media/1"), displayName = "photo1.jpg"),
            PhotoItem(id = "2", uri = android.net.Uri.parse("content://media/2"), displayName = "photo2.jpg")
        )
        `when`(photoRepository.getPhotosByBucket(categoryId)).thenReturn(expectedPhotos)

        // When
        val result = getPhotosByCategoryUseCase(categoryId)

        // Then
        assertNotNull(result)
        assertEquals(expectedPhotos, result)
        assertEquals(2, result.size)
    }

    @Test
    fun `invoke returns empty list when category has no photos`() {
        // Given
        val categoryId = 999L
        `when`(photoRepository.getPhotosByBucket(categoryId)).thenReturn(emptyList())

        // When
        val result = getPhotosByCategoryUseCase(categoryId)

        // Then
        assertNotNull(result)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `invoke handles single photo in category`() {
        // Given
        val categoryId = 5L
        val singlePhoto = listOf(
            PhotoItem(id = "42", uri = android.net.Uri.parse("content://media/42"), displayName = "single.png")
        )
        `when`(photoRepository.getPhotosByBucket(categoryId)).thenReturn(singlePhoto)

        // When
        val result = getPhotosByCategoryUseCase(categoryId)

        // Then
        assertEquals(1, result.size)
        assertEquals("42", result[0].id)
        assertEquals("single.png", result[0].displayName)
    }

    @Test
    fun `invoke with zero category id returns all photos`() {
        // Given - assuming 0 means "all photos"
        val allPhotos = listOf(
            PhotoItem(id = "1", uri = android.net.Uri.parse("content://media/1"), displayName = "all1.jpg"),
            PhotoItem(id = "2", uri = android.net.Uri.parse("content://media/2"), displayName = "all2.jpg"),
            PhotoItem(id = "3", uri = android.net.Uri.parse("content://media/3"), displayName = "all3.jpg")
        )
        `when`(photoRepository.getPhotosByBucket(0L)).thenReturn(allPhotos)

        // When
        val result = getPhotosByCategoryUseCase(0L)

        // Then
        assertEquals(3, result.size)
        assertEquals("all1.jpg", result[0].displayName)
    }

    @Test
    fun `invoke preserves photo order from repository`() {
        // Given
        val categoryId = 10L
        val orderedPhotos = listOf(
            PhotoItem(id = "c", uri = android.net.Uri.parse("content://media/c"), displayName = "zebra.jpg"),
            PhotoItem(id = "a", uri = android.net.Uri.parse("content://media/a"), displayName = "alpha.jpg"),
            PhotoItem(id = "b", uri = android.net.Uri.parse("content://media/b"), displayName = "beta.jpg")
        )
        `when`(photoRepository.getPhotosByBucket(categoryId)).thenReturn(orderedPhotos)

        // When
        val result = getPhotosByCategoryUseCase(categoryId)

        // Then
        assertEquals(orderedPhotos, result)
        assertEquals("zebra.jpg", result[0].displayName)
        assertEquals("alpha.jpg", result[1].displayName)
        assertEquals("beta.jpg", result[2].displayName)
    }
}
