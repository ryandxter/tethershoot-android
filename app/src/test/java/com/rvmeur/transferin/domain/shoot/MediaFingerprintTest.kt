package com.rvmeur.transferin.domain.shoot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class MediaFingerprintTest {

    @Test
    fun `originalFileName preserves exact casing from camera`() {
        val originalName = "DSCF0123_Raw_Preview.RAF"
        val fingerprint = MediaFingerprint(
            handle = 42,
            filename = originalName,
            sizeBytes = 32_000_000L
        )

        assertEquals("DSCF0123_Raw_Preview.RAF", fingerprint.filename)
        assertEquals("DSCF0123_Raw_Preview.RAF", fingerprint.originalFileName)
    }

    @Test
    fun `toStorageKey generates lowercase normalized key for Room deduplication`() {
        val fingerprint = MediaFingerprint(
            handle = 101,
            filename = "  IMG_9999.CR3  ",
            sizeBytes = 25_000_000L
        )

        val storageKey = fingerprint.toStorageKey()
        assertEquals("101|img_9999.cr3|25000000", storageKey)
    }

    @Test
    fun `fromStorageKey preserves key casing without forced mutation`() {
        val parsed = MediaFingerprint.fromStorageKey("101|IMG_9999.CR3|25000000")
        assertNotNull(parsed)
        assertEquals(101, parsed!!.handle)
        assertEquals("IMG_9999.CR3", parsed.filename)
        assertEquals("IMG_9999.CR3", parsed.originalFileName)
        assertEquals(25_000_000L, parsed.sizeBytes)
    }

    @Test
    fun `fromStorageKey handles invalid format gracefully`() {
        assertNull(MediaFingerprint.fromStorageKey("invalid_key"))
        assertNull(MediaFingerprint.fromStorageKey("101|only_two_parts"))
        assertNull(MediaFingerprint.fromStorageKey("not_a_number|file.jpg|12345"))
        assertNull(MediaFingerprint.fromStorageKey("101|file.jpg|not_a_number"))
    }
}
