package com.rapii.snapje.domain.usecase

import com.rapii.snapje.data.Category
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
 * Unit tests for GetCategoriesUseCase.
 * Validates category retrieval business logic.
 */
class GetCategoriesUseCaseTest {

    @Mock
    private lateinit var photoRepository: PhotoRepository

    private lateinit var getCategoriesUseCase: GetCategoriesUseCase

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        getCategoriesUseCase = GetCategoriesUseCase(photoRepository)
    }

    @Test
    fun `invoke returns categories from repository`() {
        // Given
        val expectedCategories = listOf(
            Category(id = 1, displayName = "Photos", path = "/storage/photos", bucketId = 100L),
            Category(id = 2, displayName = "Downloads", path = "/storage/downloads", bucketId = 101L)
        )
        `when`(photoRepository.getCategories()).thenReturn(expectedCategories)

        // When
        val result = getCategoriesUseCase()

        // Then
        assertNotNull(result)
        assertEquals(expectedCategories, result)
        assertEquals(2, result.size)
    }

    @Test
    fun `invoke returns empty list when repository returns empty`() {
        // Given
        `when`(photoRepository.getCategories()).thenReturn(emptyList())

        // When
        val result = getCategoriesUseCase()

        // Then
        assertNotNull(result)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `invoke handles single category`() {
        // Given
        val singleCategory = listOf(
            Category(id = 1, displayName = "Camera", path = "/storage/camera", bucketId = 102L)
        )
        `when`(photoRepository.getCategories()).thenReturn(singleCategory)

        // When
        val result = getCategoriesUseCase()

        // Then
        assertEquals(1, result.size)
        assertEquals("Camera", result[0].displayName)
        assertEquals(1L, result[0].id)
    }

    @Test
    fun `invoke preserves category order from repository`() {
        // Given
        val orderedCategories = listOf(
            Category(id = 3, displayName = "Zebra", path = "/z", bucketId = 103L),
            Category(id = 1, displayName = "Alpha", path = "/a", bucketId = 101L),
            Category(id = 2, displayName = "Beta", path = "/b", bucketId = 102L)
        )
        `when`(photoRepository.getCategories()).thenReturn(orderedCategories)

        // When
        val result = getCategoriesUseCase()

        // Then
        assertEquals(orderedCategories, result)
        assertEquals("Zebra", result[0].displayName)
        assertEquals("Alpha", result[1].displayName)
        assertEquals("Beta", result[2].displayName)
    }
}
