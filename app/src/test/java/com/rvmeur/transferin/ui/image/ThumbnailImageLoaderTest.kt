package com.rvmeur.transferin.ui.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ThumbnailImageLoaderTest {

    @Test
    fun `ThumbnailCacheKey produces deterministic compound keys`() {
        val key1 = ThumbnailCacheKey.build(
            fingerprint = "DSCF0001",
            deviceId = "fuji_x_t5",
            objectHandle = 42
        )
        val key2 = ThumbnailCacheKey.build(
            fingerprint = "DSCF0001",
            deviceId = "fuji_x_t5",
            objectHandle = 42
        )
        assertEquals(key1, key2)
        assertEquals("thumb_fuji_x_t5_42_DSCF0001", key1)
    }

    @Test
    fun `ThumbnailCacheKey differentiates distinct fingerprints and handles`() {
        val keyA = ThumbnailCacheKey.build(
            fingerprint = "DSCF0001",
            deviceId = "fuji_x_t5",
            objectHandle = 42
        )
        val keyB = ThumbnailCacheKey.build(
            fingerprint = "DSCF0002",
            deviceId = "fuji_x_t5",
            objectHandle = 42
        )
        val keyC = ThumbnailCacheKey.build(
            fingerprint = "DSCF0001",
            deviceId = "fuji_x_t5",
            objectHandle = 43
        )
        val keyD = ThumbnailCacheKey.build(
            fingerprint = "DSCF0001",
            deviceId = null,
            objectHandle = null
        )

        assertNotEquals(keyA, keyB)
        assertNotEquals(keyA, keyC)
        assertEquals("thumb_local_0_DSCF0001", keyD)
    }
}
