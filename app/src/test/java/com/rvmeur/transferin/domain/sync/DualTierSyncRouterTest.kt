package com.rvmeur.transferin.domain.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DualTierSyncRouterTest {

    private val router = DualTierSyncRouter()

    @Test
    fun `raw image produces dual-tier plan with instant preview extraction`() {
        val plan = router.route("DSCF1234.RAF", 52_000_000L)

        assertEquals(SyncTier.TIER_1_PREVIEW, plan.primaryTier)
        assertTrue(plan.requiresDualTier)
        assertEquals("DSCF1234_preview.jpg", plan.previewFilename)
        assertEquals("DSCF1234.RAF", plan.archiveFilename)
    }

    @Test
    fun `video file produces dual-tier plan with thumbnail extraction`() {
        val plan = router.route("CLIP0001.MP4", 150_000_000L)

        assertEquals(SyncTier.TIER_1_PREVIEW, plan.primaryTier)
        assertTrue(plan.requiresDualTier)
        assertEquals("CLIP0001_thumbnail.jpg", plan.previewFilename)
        assertEquals("CLIP0001.MP4", plan.archiveFilename)
    }

    @Test
    fun `small jpeg image produces single-tier direct sync plan`() {
        val plan = router.route("IMG_0001.JPG", 1_500_000L) // 1.5 MB

        assertEquals(SyncTier.SINGLE_TIER_DIRECT, plan.primaryTier)
        assertFalse(plan.requiresDualTier)
        assertEquals(null, plan.previewFilename)
        assertEquals("IMG_0001.JPG", plan.archiveFilename)
    }

    @Test
    fun `large jpeg image produces dual-tier plan for instant low-latency preview`() {
        val plan = router.route("LARGE_PANORAMA.JPG", 25_000_000L) // 25 MB

        assertEquals(SyncTier.TIER_1_PREVIEW, plan.primaryTier)
        assertTrue(plan.requiresDualTier)
        assertEquals("LARGE_PANORAMA_preview.jpg", plan.previewFilename)
        assertEquals("LARGE_PANORAMA.JPG", plan.archiveFilename)
    }
}
