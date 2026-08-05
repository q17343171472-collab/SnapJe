package com.rapii.snapje.ui

import com.rapii.snapje.data.Category
import com.rapii.snapje.data.SortBy
import com.rapii.snapje.data.VaultRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations

/**
 * Unit tests for CategoryViewModel (保险库数据源)。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CategoryViewModelTest {

    @Mock
    private lateinit var vaultRepository: VaultRepository

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var viewModel: CategoryViewModel

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): CategoryViewModel {
        `when`(vaultRepository.getVaultPhotos()).thenReturn(flowOf(emptyList()))
        return CategoryViewModel(vaultRepository)
    }

    @Test
    fun `ViewModel should be created successfully`() = runTest(testDispatcher) {
        viewModel = createViewModel()
        assertNotNull(viewModel)
    }

    @Test
    fun `getCachedCategories should return empty list when vault is empty`() = runTest(testDispatcher) {
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(viewModel.getCachedCategories())
        assertTrue(viewModel.getCachedCategories().isEmpty())
    }

    @Test
    fun `getCachedCategories should build categories from vault photos`() = runTest(testDispatcher) {
        // Given - 保险库内有两张照片，同属一个相册
        val photos = listOf(
            com.rapii.snapje.data.VaultPhoto(
                id = "uuid-1",
                originalName = "a.jpg",
                bucketId = 100L,
                bucketName = "我的保险库",
                dateTaken = 1000L,
                size = 10,
                mimeType = "image/jpeg",
                encryptedPath = "/x/a.enc"
            ),
            com.rapii.snapje.data.VaultPhoto(
                id = "uuid-2",
                originalName = "b.jpg",
                bucketId = 100L,
                bucketName = "我的保险库",
                dateTaken = 2000L,
                size = 20,
                mimeType = "image/jpeg",
                encryptedPath = "/x/b.enc"
            )
        )
        `when`(vaultRepository.getVaultPhotos()).thenReturn(flowOf(photos))

        viewModel = CategoryViewModel(vaultRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        // When
        val categories = viewModel.getCachedCategories()

        // Then - 两照片聚合为一个相册
        assertEquals(1, categories.size)
        assertEquals(100L, categories[0].id)
        assertEquals(2, categories[0].itemCount)
    }

    @Test
    fun `uiState should be empty when vault has no photos`() = runTest(testDispatcher) {
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(viewModel.uiState.value)
        assertEquals(false, viewModel.uiState.value.isLoading)
        assertTrue(viewModel.uiState.value.isEmpty)
    }

    @Test
    fun `searchQuery should be empty by default`() = runTest(testDispatcher) {
        viewModel = createViewModel()
        assertEquals("", viewModel.searchQuery)
    }

    @Test
    fun `sortBy should default to RECENT`() = runTest(testDispatcher) {
        viewModel = createViewModel()
        assertEquals(SortBy.RECENT, viewModel.sortBy)
    }

    @Test
    fun `toggleCategoryPin should update pinned state`() = runTest(testDispatcher) {
        val photos = listOf(
            com.rapii.snapje.data.VaultPhoto(
                id = "uuid-1",
                originalName = "a.jpg",
                bucketId = 100L,
                bucketName = "我的保险库",
                dateTaken = 1000L,
                size = 10,
                mimeType = "image/jpeg",
                encryptedPath = "/x/a.enc"
            )
        )
        `when`(vaultRepository.getVaultPhotos()).thenReturn(flowOf(photos))
        viewModel = CategoryViewModel(vaultRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        val category = viewModel.getCachedCategories().first()
        assertTrue(!category.isPinned)

        viewModel.toggleCategoryPin(category.id)
        val updated = viewModel.getCachedCategories().first()
        assertTrue(updated.isPinned)
    }
}
