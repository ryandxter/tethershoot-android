package com.rvmeur.transferin.data.usb.canon

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class Cr3PreviewParserTest {

    private fun createMinimalValidJpeg(width: Int = 1920, height: Int = 1080): ByteArray {
        val stream = mutableListOf<Byte>()
        // SOI
        stream.addAll(listOf(0xFF.toByte(), 0xD8.toByte()))

        // APP0 (JFIF)
        val app0Payload = byteArrayOf(0x4A, 0x46, 0x49, 0x46, 0x00, 0x01, 0x01, 0x00, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00)
        val app0Len = app0Payload.size + 2
        stream.addAll(listOf(0xFF.toByte(), 0xE0.toByte(), (app0Len shr 8).toByte(), (app0Len and 0xFF).toByte()))
        app0Payload.forEach { stream.add(it) }

        // SOF0 (Start of Frame)
        val sofLen = 17
        stream.addAll(listOf(
            0xFF.toByte(), 0xC0.toByte(),
            (sofLen shr 8).toByte(), (sofLen and 0xFF).toByte(),
            0x08.toByte(), // precision 8-bit
            (height shr 8).toByte(), (height and 0xFF).toByte(),
            (width shr 8).toByte(), (width and 0xFF).toByte(),
            0x03.toByte(), // 3 components (Y, Cb, Cr)
            0x01.toByte(), 0x22.toByte(), 0x00.toByte(),
            0x02.toByte(), 0x11.toByte(), 0x01.toByte(),
            0x03.toByte(), 0x11.toByte(), 0x01.toByte()
        ))

        // Some dummy image scan data
        for (i in 0 until 64) {
            stream.add(0xAB.toByte())
        }

        // EOI
        stream.addAll(listOf(0xFF.toByte(), 0xD9.toByte()))

        return stream.toByteArray()
    }

    private fun createCr3Box(type: Int, payload: ByteArray): ByteArray {
        val boxLen = 8 + payload.size
        val bb = ByteBuffer.allocate(boxLen).order(ByteOrder.BIG_ENDIAN)
        bb.putInt(boxLen)
        bb.putInt(type)
        bb.put(payload)
        return bb.array()
    }

    private fun buildSyntheticCr3File(
        includePrvw: Boolean = true,
        includeThmb: Boolean = true,
        prvwWidth: Int = 1920,
        prvwHeight: Int = 1080,
        thmbWidth: Int = 160,
        thmbHeight: Int = 120
    ): ByteArray {
        // 1. ftyp box
        val ftypPayload = ByteBuffer.allocate(12).order(ByteOrder.BIG_ENDIAN)
            .putInt(Cr3PreviewParser.BRAND_CRX) // Major brand 'crx '
            .putInt(0x00000001) // Minor version
            .putInt(Cr3PreviewParser.BRAND_CRX) // Compatible brand
            .array()
        val ftypBox = createCr3Box(Cr3PreviewParser.FOURCC_FTYP, ftypPayload)

        // 2. PRVW Box
        val prvwBox = if (includePrvw) {
            val jpegBytes = createMinimalValidJpeg(prvwWidth, prvwHeight)
            val prvwPayload = ByteBuffer.allocate(12 + jpegBytes.size).order(ByteOrder.BIG_ENDIAN)
                .putInt(0) // version & flags
                .putShort(prvwWidth.toShort())
                .putShort(prvwHeight.toShort())
                .putInt(jpegBytes.size)
                .put(jpegBytes)
                .array()
            createCr3Box(Cr3PreviewParser.FOURCC_PRVW, prvwPayload)
        } else byteArrayOf()

        // 3. THMB Box
        val thmbBox = if (includeThmb) {
            val thmbJpeg = createMinimalValidJpeg(thmbWidth, thmbHeight)
            val thmbPayload = ByteBuffer.allocate(12 + thmbJpeg.size).order(ByteOrder.BIG_ENDIAN)
                .putInt(0)
                .putShort(thmbWidth.toShort())
                .putShort(thmbHeight.toShort())
                .putInt(thmbJpeg.size)
                .put(thmbJpeg)
                .array()
            createCr3Box(Cr3PreviewParser.FOURCC_THMB, thmbPayload)
        } else byteArrayOf()

        // 4. UUID Box (Canon metadata container)
        val canonUuid = byteArrayOf(
            0x85.toByte(), 0xC0.toByte(), 0xB6.toByte(), 0x87.toByte(),
            0x82.toByte(), 0x0F.toByte(), 0x11.toByte(), 0xE0.toByte(),
            0x81.toByte(), 0x11.toByte(), 0xF4.toByte(), 0xCE.toByte(),
            0x46.toByte(), 0x2B.toByte(), 0x02.toByte(), 0x20.toByte()
        )
        val uuidPayload = ByteBuffer.allocate(16 + prvwBox.size + thmbBox.size)
            .put(canonUuid)
            .put(prvwBox)
            .put(thmbBox)
            .array()
        val uuidBox = createCr3Box(Cr3PreviewParser.FOURCC_UUID, uuidPayload)

        // 5. moov Box
        val moovBox = createCr3Box(Cr3PreviewParser.FOURCC_MOOV, uuidBox)

        // Assemble total file (padded to 128KB)
        val totalLen = 128 * 1024
        val totalBuffer = ByteBuffer.allocate(totalLen)
        totalBuffer.put(ftypBox)
        totalBuffer.put(moovBox)

        return totalBuffer.array()
    }

    @Test
    fun `extracts full resolution PRVW preview successfully`() {
        val cr3Data = buildSyntheticCr3File(
            includePrvw = true,
            includeThmb = true,
            prvwWidth = 1920,
            prvwHeight = 1080
        )

        val result = Cr3PreviewParser.parsePreviewFromSlice(cr3Data)

        assertNotNull("Preview extraction must succeed", result)
        val preview = result!!
        assertTrue("Must be full preview", preview.isFullPreview)
        assertEquals(1920, preview.width)
        assertEquals(1080, preview.height)
        assertTrue(preview.jpegBytes.size >= 4)
        // Verify SOI marker (0xFFD8)
        assertEquals(0xFF.toByte(), preview.jpegBytes[0])
        assertEquals(0xD8.toByte(), preview.jpegBytes[1])
        // Verify EOI marker (0xFFD9)
        assertEquals(0xFF.toByte(), preview.jpegBytes[preview.jpegBytes.size - 2])
        assertEquals(0xD9.toByte(), preview.jpegBytes[preview.jpegBytes.size - 1])
    }

    @Test
    fun `falls back to THMB thumbnail when PRVW is absent`() {
        val cr3Data = buildSyntheticCr3File(
            includePrvw = false,
            includeThmb = true,
            thmbWidth = 160,
            thmbHeight = 120
        )

        val result = Cr3PreviewParser.parsePreviewFromSlice(cr3Data)

        assertNotNull("Thumbnail extraction must succeed", result)
        val preview = result!!
        assertFalse("Must be thumbnail fallback", preview.isFullPreview)
        assertEquals(160, preview.width)
        assertEquals(120, preview.height)
        assertEquals(0xFF.toByte(), preview.jpegBytes[0])
        assertEquals(0xD8.toByte(), preview.jpegBytes[1])
    }

    @Test
    fun `benchmark parsing SLA execution time is well below 500ms`() {
        val cr3Data = buildSyntheticCr3File(includePrvw = true)

        val iterations = 50
        val startTime = System.currentTimeMillis()

        for (i in 0 until iterations) {
            val res = Cr3PreviewParser.parsePreviewFromSlice(cr3Data)
            assertNotNull(res)
        }

        val totalElapsedMs = System.currentTimeMillis() - startTime
        val avgElapsedMs = totalElapsedMs.toDouble() / iterations

        assertTrue("Average parse time ($avgElapsedMs ms) must be well under SLA of 500ms", avgElapsedMs < 500.0)
    }

    @Test
    fun `handles malformed and truncated buffers safely without throwing`() {
        // Empty buffer
        assertNull(Cr3PreviewParser.parsePreviewFromSlice(ByteArray(0)))

        // Garbage bytes
        val garbage = ByteArray(1024) { (it % 255).toByte() }
        assertNull(Cr3PreviewParser.parsePreviewFromSlice(garbage))

        // Truncated header
        val truncated = buildSyntheticCr3File().copyOf(32)
        assertNull(Cr3PreviewParser.parsePreviewFromSlice(truncated))

        // Invalid negative/huge slice bounds
        assertNull(Cr3PreviewParser.parsePreviewFromSlice(garbage, -1, 10))
        assertNull(Cr3PreviewParser.parsePreviewFromSlice(garbage, 0, 5000))
    }
}
