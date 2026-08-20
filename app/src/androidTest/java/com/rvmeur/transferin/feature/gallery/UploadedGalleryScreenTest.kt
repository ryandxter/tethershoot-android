package com.rvmeur.transferin.feature.gallery

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rvmeur.transferin.domain.model.UploadStatus
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock

@RunWith(AndroidJUnit4::class)
class UploadedGalleryScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var viewModel: GalleryViewModel

    @Before
    fun setUp() {
        val application = mock(Application::class.java)
        viewModel = GalleryViewModel(application = application)
        viewModel.setGalleryItems(
            listOf(
                GalleryItem(
                    id = "item-1",
                    fileName = "DSCF0001.JPG",
                    filePath = "/dummy/1.jpg",
                    fileSizeBytes = 10_000_000L,
                    uploadedAt = 1000L,
                    status = UploadStatus.SUCCESS,
                    cullingVerdict = "OPTIMAL",
                    isCulled = false
                ),
                GalleryItem(
                    id = "item-2",
                    fileName = "DSCF0002.RAF",
                    filePath = "/dummy/2.raf",
                    fileSizeBytes = 45_000_000L,
                    uploadedAt = 2000L,
                    status = UploadStatus.SKIPPED,
                    cullingVerdict = "UNDEREXPOSED_CRITICAL",
                    isCulled = true
                )
            )
        )
    }

    @Test
    fun galleryScreen_rendersFilterChipsAndBadges() {
        composeTestRule.setContent {
            UploadedGalleryScreen(
                viewModel = viewModel,
                onNavigateBack = {}
            )
        }

        // Verify Filter Chips with counts
        composeTestRule.onNodeWithText("semua (2)").assertIsDisplayed()
        composeTestRule.onNodeWithText("optimal (1)").assertIsDisplayed()
        composeTestRule.onNodeWithText("terpangkas (1)").assertIsDisplayed()
        composeTestRule.onNodeWithText("dilewati (1)").assertIsDisplayed()

        // Verify Under Badge on culled card
        composeTestRule.onNodeWithText("UNDER").assertIsDisplayed()

        // Filter to optimal
        composeTestRule.onNodeWithText("optimal (1)").performClick()
        composeTestRule.onNodeWithText("optimal (1)").assertIsDisplayed()
    }
}
