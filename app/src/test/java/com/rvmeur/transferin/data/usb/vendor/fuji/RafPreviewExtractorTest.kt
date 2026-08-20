package com.rvmeur.transferin.data.usb.vendor.fuji

import com.rvmeur.transferin.domain.camera.FujiFilmSimulation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class RafPreviewExtractorTest {

    private fun createSyntheticRafHeader(
        magic: ByteArray = byteArrayOf(
            0x46, 0x55, 0x4A, 0x49, 0x46, 0x49, 0x4C, 0x4D, // "FUJIFILM"
            0x43, 0x43, 0x44, 0x2D, 0x52, 0x41, 0x57, 0x20  // "CCD-RAW "
        ),
        version: String = "0201",
        cameraModel: String = "FUJIFILM X-T5",
        jpegOffset: Long = 128L,
        jpegLength: Long = 4096L,
        cfaOffset: Long = 5000L,
        cfaLength: Long = 20000L,
        totalSize: Int = 5120
    ): ByteArray {
        val buffer = ByteArray(totalSize)

        // Magic
        System.arraycopy(magic, 0, buffer, 0, magic.size.coerceAtMost(16))

        // Version (bytes 16..19)
        val versionBytes = version.toByteArray(Charsets.US_ASCII)
        System.arraycopy(versionBytes, 0, buffer, 16, versionBytes.size.coerceAtMost(4))

        // Camera Model (bytes 20..51)
        val modelBytes = cameraModel.toByteArray(Charsets.US_ASCII)
        System.arraycopy(modelBytes, 0, buffer, 20, modelBytes.size.coerceAtMost(32))

        // JPEG Offset & Length (Big-Endian at 84 and 88)
        val bb = ByteBuffer.wrap(buffer).order(ByteOrder.BIG_ENDIAN)
        bb.putInt(84, jpegOffset.toInt())
        bb.putInt(88, jpegLength.toInt())
        bb.putInt(100, cfaOffset.toInt())
        bb.putInt(104, cfaLength.toInt())

        // Insert JPEG SOI marker at jpegOffset
        if (jpegOffset.toInt() + 2 <= totalSize) {
            buffer[jpegOffset.toInt()] = 0xFF.toByte()
            buffer[jpegOffset.toInt() + 1] = 0xD8.toByte()
        }

        return buffer
    }

    @Test
    fun `parseRafHeader successfully parses valid header offsets and camera model`() {
        val headerBytes = createSyntheticRafHeader(
            version = "0201",
            cameraModel = "FUJIFILM X-T5",
            jpegOffset = 140L,
            jpegLength = 8192L,
            cfaOffset = 10000L,
            cfaLength = 50000L
        )

        val result = RafPreviewExtractor.parseRafHeader(headerBytes)
        assertTrue("Header parse should succeed", result.isSuccess)

        val info = result.getOrThrow()
        assertEquals("0201", info.version)
        assertEquals("FUJIFILM X-T5", info.cameraModel)
        assertEquals(140L, info.jpegOffset)
        assertEquals(8192L, info.jpegLength)
        assertEquals(10000L, info.cfaOffset)
        assertEquals(50000L, info.cfaLength)
    }

    @Test
    fun `parseRafHeader fails on invalid magic bytes`() {
        val corrupted = createSyntheticRafHeader(
            magic = "CANON RAW       ".toByteArray(Charsets.US_ASCII)
        )

        val result = RafPreviewExtractor.parseRafHeader(corrupted)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is InvalidRafHeaderException)
    }

    @Test
    fun `parseRafHeader fails on truncated buffer`() {
        val truncated = ByteArray(40) // Under minimum 92 bytes
        val result = RafPreviewExtractor.parseRafHeader(truncated)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is InvalidRafHeaderException)
    }

    @Test
    fun `parseRafHeader fails on invalid jpegOffset or negative length`() {
        val invalidOffset = createSyntheticRafHeader(jpegOffset = 50L) // Under 120
        val result1 = RafPreviewExtractor.parseRafHeader(invalidOffset)
        assertTrue(result1.isFailure)

        val invalidLength = createSyntheticRafHeader(jpegLength = 0L)
        val result2 = RafPreviewExtractor.parseRafHeader(invalidLength)
        assertTrue(result2.isFailure)
    }

    @Test
    fun `extractEmbeddedJpeg extracts full JPEG byte payload starting with SOI`() {
        val rafBytes = createSyntheticRafHeader(
            jpegOffset = 128L,
            jpegLength = 256L,
            totalSize = 1024
        )

        // Fill payload with identifiable test bytes
        for (i in 2 until 256) {
            rafBytes[128 + i] = (i and 0xFF).toByte()
        }

        val result = RafPreviewExtractor.extractEmbeddedJpeg(rafBytes)
        assertTrue(result.isSuccess)

        val jpeg = result.getOrThrow()
        assertEquals(256, jpeg.size)
        assertEquals(0xFF.toByte(), jpeg[0])
        assertEquals(0xD8.toByte(), jpeg[1])
        assertEquals(2.toByte(), jpeg[2])
    }

    @Test
    fun `extractFilmSimulation decodes all Fuji Film Simulation codes accurately`() {
        val simulationsToTest = listOf(
            0x0000 to FujiFilmSimulation.PROVIA,
            0x0100 to FujiFilmSimulation.PROVIA,
            0x0110 to FujiFilmSimulation.ASTIA,
            0x0120 to FujiFilmSimulation.VELVIA,
            0x0200 to FujiFilmSimulation.PRO_NEG_STD,
            0x0201 to FujiFilmSimulation.PRO_NEG_HI,
            0x0300 to FujiFilmSimulation.CLASSIC_CHROME,
            0x0400 to FujiFilmSimulation.ETERNA,
            0x0500 to FujiFilmSimulation.CLASSIC_NEG,
            0x0501 to FujiFilmSimulation.BLEACH_BYPASS,
            0x0502 to FujiFilmSimulation.NOSTALGIC_NEG,
            0x0600 to FujiFilmSimulation.REALA_ACE,
            0x0700 to FujiFilmSimulation.MONOCHROME,
            0x0701 to FujiFilmSimulation.MONOCHROME_YE,
            0x0702 to FujiFilmSimulation.MONOCHROME_R,
            0x0703 to FujiFilmSimulation.MONOCHROME_G,
            0x0710 to FujiFilmSimulation.SEPIA,
            0x0800 to FujiFilmSimulation.ACROS,
            0x0801 to FujiFilmSimulation.ACROS_YE,
            0x0802 to FujiFilmSimulation.ACROS_R,
            0x0803 to FujiFilmSimulation.ACROS_G,
            0x9999 to FujiFilmSimulation.UNKNOWN
        )

        for ((tagValue, expectedSim) in simulationsToTest) {
            val enumResult = FujiFilmSimulation.fromTagValue(tagValue)
            assertEquals("Tag 0x${tagValue.toString(16)} should map to $expectedSim", expectedSim, enumResult)

            // Also test binary pattern scan in JPEG bytes
            val syntheticJpeg = createSyntheticJpegWithMakerNoteTag(tagValue)
            val decoded = RafPreviewExtractor.extractFilmSimulation(syntheticJpeg)
            assertEquals(
                "Binary scan for Tag 0x${tagValue.toString(16)} should decode to $expectedSim",
                expectedSim,
                decoded
            )
        }
    }

    @Test
    fun `film simulation luxury styling tokens and monochrome flags are valid`() {
        assertTrue(FujiFilmSimulation.ACROS.isMonochrome)
        assertTrue(FujiFilmSimulation.MONOCHROME.isMonochrome)
        assertFalse(FujiFilmSimulation.VELVIA.isMonochrome)
        assertFalse(FujiFilmSimulation.CLASSIC_CHROME.isMonochrome)

        assertNotNull(FujiFilmSimulation.CLASSIC_NEG.badgeHexColor)
        assertNotNull(FujiFilmSimulation.CLASSIC_NEG.accentColor)
        assertTrue(FujiFilmSimulation.CLASSIC_NEG.badgeHexColor.startsWith("#"))
    }

    private fun createSyntheticJpegWithMakerNoteTag(tagValue: Int): ByteArray {
        val buffer = ByteArray(256)
        buffer[0] = 0xFF.toByte()
        buffer[1] = 0xD8.toByte() // SOI

        // Embed Tag 0x1401 entry:
        // Tag ID: 0x01, 0x14
        // Type SHORT: 0x03, 0x00
        // Count 1: 0x01, 0x00, 0x00, 0x00
        // Value: tagValue (2 bytes LE)
        val offset = 20
        buffer[offset] = 0x01.toByte()
        buffer[offset + 1] = 0x14.toByte()
        buffer[offset + 2] = 0x03.toByte()
        buffer[offset + 3] = 0x00.toByte()
        buffer[offset + 4] = 0x01.toByte()
        buffer[offset + 5] = 0x00.toByte()
        buffer[offset + 6] = 0x00.toByte()
        buffer[offset + 7] = 0x00.toByte()
        buffer[offset + 8] = (tagValue and 0xFF).toByte()
        buffer[offset + 9] = ((tagValue shr 8) and 0xFF).toByte()
        buffer[offset + 10] = 0x00.toByte()
        buffer[offset + 11] = 0x00.toByte()

        return buffer
    }
}
