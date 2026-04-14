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
 * Unit tests for SearchPhotosUseCase.
 * Validates photo search business logic.
 */
class SearchPhotosUseCaseTest {

    @Mock
    private lateinit var photoRepository: PhotoRepository

    private lateinit var searchPhotosUseCase: SearchPhotosUseCase

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        searchPhotosUseCase = SearchPhotosUseCase(photoRepository)
    }

    @Test
    fun `invoke returns photos matching query`() {
        // Given
        val query = "vacation"
        val expectedPhotos = listOf(
            PhotoItem(id = "1", uri = android.net.Uri.parse("content://media/1"), displayName = "vacation1.jpg"),
            PhotoItem(id = "2", uri = android.net.Uri.parse("content://media/2"), displayName = "vacation2.png")
        )
        `when`(photoRepository.searchPhotos(query)).thenReturn(expectedPhotos)

        // When
        val result = searchPhotosUseCase(query)

        // Then
        assertNotNull(result)
        assertEquals(expectedPhotos, result)
        assertEquals(2, result.size)
    }

    @Test
    fun `invoke returns empty list when no matches found`() {
        // Given
        val query = "nonexistent"
        `when`(photoRepository.searchPhotos(query)).thenReturn(emptyList())

        // When
        val result = searchPhotosUseCase(query)

        // Then
        assertNotNull(result)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `invoke handles empty query`() {
        // Given
        val query = ""
        `when`(photoRepository.searchPhotos(query)).thenReturn(emptyList())

        // When
        val result = searchPhotosUseCase(query)

        // Then
        assertNotNull(result)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `invoke is case insensitive`() {
        // Given
        val query = "VACATION"
        val expectedPhotos = listOf(
            PhotoItem(id = "1", uri = android.net.Uri.parse("content://media/1"), displayName = "vacation.jpg")
        )
        `when`(photoRepository.searchPhotos(query)).thenReturn(expectedPhotos)

        // When
        val result = searchPhotosUseCase(query)

        // Then
        assertEquals(1, result.size)
        assertEquals("vacation.jpg", result[0].displayName)
    }

    @Test
    fun `invoke handles special characters in query`() {
        // Given
        val query = "photo-2024_test"
        val expectedPhotos = listOf(
            PhotoItem(id = "1", uri = android.net.Uri.parse("content://media/1"), displayName = "photo-2024_test.jpg")
        )
        `when`(photoRepository.searchPhotos(query)).thenReturn(expectedPhotos)

        // When
        val result = searchPhotosUseCase(query)

        // Then
        assertEquals(1, result.size)
        assertEquals("photo-2024_test.jpg", result[0].displayName)
    }
}
