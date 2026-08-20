package com.rvmeur.transferin.feature.gallery

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rvmeur.transferin.feature.gallery.components.ExposureInspectionModal
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExposureInspectionModalTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun exposureModal_rendersMetadataAndHandlesForceUpload() {
        var forceUploadClicked = false
        var dismissClicked = false

        composeTestRule.setContent {
            ExposureInspectionModal(
                fileName = "DSCF0042.RAF",
                filePath = "/dummy/path/DSCF0042.RAF",
                statusText = "SKIPPED",
                fileSizeText = "48 MB",
                cullingVerdict = "UNDEREXPOSED_CRITICAL",
                meanLuminance = 12.4,
                entropyScore = 2.1,
                isCulled = true,
                isManualUploadOverride = false,
                onDismiss = { dismissClicked = true },
                onForceUpload = { forceUploadClicked = true },
                iso = "640",
                shutterSpeed = "1/500s",
                aperture = "f/1.4"
            )
        }

        // Verify EXIF metadata tags
        composeTestRule.onNodeWithText("ISO").assertIsDisplayed()
        composeTestRule.onNodeWithText("640").assertIsDisplayed()
        composeTestRule.onNodeWithText("1/500s").assertIsDisplayed()
        composeTestRule.onNodeWithText("f/1.4").assertIsDisplayed()

        // Verify Histogram label
        composeTestRule.onNodeWithText("HISTOGRAM LUMINANSI 256-BIN").assertIsDisplayed()

        // Perform 1-tap Paksa Unggah
        composeTestRule.onNodeWithText("paksa unggah ke cloud").performClick()
        assertTrue("Force upload callback should be triggered", forceUploadClicked)

        // Perform Close
        composeTestRule.onNodeWithContentDescription("Tutup pratinjau foto").performClick()
        assertTrue("Dismiss callback should be triggered", dismissClicked)
    }
}
