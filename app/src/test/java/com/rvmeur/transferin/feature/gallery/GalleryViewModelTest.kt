package com.rvmeur.transferin.feature.gallery

import android.app.Application
import com.rvmeur.transferin.domain.model.UploadStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GalleryViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var application: Application
    private lateinit var viewModel: GalleryViewModel

    private val sampleItems = listOf(
        GalleryItem(
            id = "item-1",
            fileName = "DSCF0001.JPG",
            filePath = "/path/1.jpg",
            fileSizeBytes = 10_000_000L,
            uploadedAt = 1000L,
            status = UploadStatus.SUCCESS,
            cullingVerdict = "OPTIMAL",
            isCulled = false
        ),
        GalleryItem(
            id = "item-2",
            fileName = "DSCF0002.RAF",
            filePath = "/path/2.raf",
            fileSizeBytes = 50_000_000L,
            uploadedAt = 2000L,
            status = UploadStatus.SKIPPED,
            cullingVerdict = "UNDEREXPOSED_CRITICAL",
            isCulled = true
        ),
        GalleryItem(
            id = "item-3",
            fileName = "DSCF0003.RAF",
            filePath = "/path/3.raf",
            fileSizeBytes = 48_000_000L,
            uploadedAt = 3000L,
            status = UploadStatus.SKIPPED,
            cullingVerdict = "OVEREXPOSED_BLOWN",
            isCulled = true
        ),
        GalleryItem(
            id = "item-4",
            fileName = "DSCF0004.JPG",
            filePath = "/path/4.jpg",
            fileSizeBytes = 12_000_000L,
            uploadedAt = 4000L,
            status = UploadStatus.SUCCESS,
            cullingVerdict = "DYNAMIC_PROFILE_ADJUSTED",
            isCulled = false
        )
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        application = Application()
        viewModel = GalleryViewModel(application = application)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `setGalleryItems computes live culling metrics correctly`() = runTest {
        viewModel.setGalleryItems(sampleItems)
        val state = viewModel.uiState.value

        assertEquals(4, state.cullingMetrics.totalCount)
        assertEquals(2, state.cullingMetrics.optimalCount)
        assertEquals(2, state.cullingMetrics.culledCount)
        assertEquals(2, state.cullingMetrics.skippedCount)
        assertEquals(98_000_000L, state.cullingMetrics.quotaSavedBytes)
    }

    @Test
    fun `setFilterMode filters items accurately`() = runTest {
        viewModel.setGalleryItems(sampleItems)

        // 1. ALL
        viewModel.setFilterMode(FilterMode.ALL)
        assertEquals(4, viewModel.uiState.value.uploadedItems.size)

        // 2. OPTIMAL
        viewModel.setFilterMode(FilterMode.OPTIMAL)
        assertEquals(2, viewModel.uiState.value.uploadedItems.size)
        assertTrue(viewModel.uiState.value.uploadedItems.all { !it.isCulled })

        // 3. CULLED
        viewModel.setFilterMode(FilterMode.CULLED)
        assertEquals(2, viewModel.uiState.value.uploadedItems.size)
        assertTrue(viewModel.uiState.value.uploadedItems.all { it.isCulled })

        // 4. SKIPPED
        viewModel.setFilterMode(FilterMode.SKIPPED)
        assertEquals(2, viewModel.uiState.value.uploadedItems.size)
        assertTrue(viewModel.uiState.value.uploadedItems.all { it.status == UploadStatus.SKIPPED })
    }

    @Test
    fun `forceUpload optimistically transitions item state`() = runTest {
        viewModel.setGalleryItems(sampleItems)

        viewModel.forceUpload("item-2")
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        val item2 = state.allItems.find { it.id == "item-2" }

        assertEquals(UploadStatus.UPLOADING, item2?.status)
        assertFalse(item2?.isCulled ?: true)
        assertTrue(item2?.isManualUploadOverride ?: false)

        // Metrics should update: 1 skipped remaining
        assertEquals(1, state.cullingMetrics.skippedCount)
        assertEquals(48_000_000L, state.cullingMetrics.quotaSavedBytes)
    }
}
