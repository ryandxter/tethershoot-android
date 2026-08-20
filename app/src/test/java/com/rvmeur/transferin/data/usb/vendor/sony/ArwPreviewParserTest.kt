package com.rvmeur.transferin.data.usb.vendor.sony

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ArwPreviewParserTest {

    private lateinit var parser: ArwPreviewParser

    @Before
    fun setUp() {
        parser = ArwPreviewParser()
    }

    @Test
    fun `parsePreviewInfo resolves Little-Endian SubIFD JPEG tags correctly`() {
        val totalSize = 2048
        val bb = ByteBuffer.allocate(totalSize).order(ByteOrder.LITTLE_ENDIAN)

        // TIFF Header (8 bytes)
        bb.put(0x49.toByte()) // 'I'
        bb.put(0x49.toByte()) // 'I'
        bb.putShort(42.toShort()) // Magic 42
        bb.putInt(8) // IFD0 offset = 8

        // IFD0 at offset 8
        bb.position(8)
        bb.putShort(2.toShort()) // 2 entries
        // Tag 1: ImageWidth (0x0100)
        bb.putShort(0x0100.toShort())
        bb.putShort(4.toShort()) // LONG
        bb.putInt(1)
        bb.putInt(7952)
        // Tag 2: SubIFDs (0x014A) -> points to SubIFD at offset 64
        bb.putShort(0x014A.toShort())
        bb.putShort(4.toShort()) // LONG
        bb.putInt(1)
        bb.putInt(64) // SubIFD offset
        bb.putInt(0) // Next IFD = 0

        // SubIFD at offset 64
        bb.position(64)
        bb.putShort(2.toShort()) // 2 entries
        // Tag 1: JPEGInterchangeFormat (0x0201) -> offset 512
        bb.putShort(0x0201.toShort())
        bb.putShort(4.toShort()) // LONG
        bb.putInt(1)
        bb.putInt(512)
        // Tag 2: JPEGInterchangeFormatLength (0x0202) -> 256 bytes
        bb.putShort(0x0202.toShort())
        bb.putShort(4.toShort()) // LONG
        bb.putInt(1)
        bb.putInt(256)
        bb.putInt(0) // Next IFD = 0

        // Put JPEG SOI at offset 512
        bb.position(512)
        bb.put(0xFF.toByte())
        bb.put(0xD8.toByte())
        bb.put(0xFF.toByte())
        bb.put(0xE1.toByte())

        val rawBytes = bb.array()
        val info = parser.parsePreviewInfo(rawBytes)

        assertNotNull(info)
        assertEquals(512, info!!.previewOffset)
        assertEquals(256, info.previewLength)
        assertTrue(info.isJpegValid)

        val extracted = parser.extractPreview(rawBytes)
        assertNotNull(extracted)
        assertEquals(256, extracted!!.size)
        assertEquals(0xFF.toByte(), extracted[0])
        assertEquals(0xD8.toByte(), extracted[1])
    }

    @Test
    fun `parsePreviewInfo resolves Big-Endian TIFF header and Strip tags`() {
        val totalSize = 1024
        val bb = ByteBuffer.allocate(totalSize).order(ByteOrder.BIG_ENDIAN)

        // TIFF Header (8 bytes)
        bb.put(0x4D.toByte()) // 'M'
        bb.put(0x4D.toByte()) // 'M'
        bb.putShort(42.toShort())
        bb.putInt(8)

        // IFD0 at offset 8
        bb.position(8)
        bb.putShort(2.toShort()) // 2 entries
        // Tag 1: StripOffsets (0x0111) -> offset 200
        bb.putShort(0x0111.toShort())
        bb.putShort(4.toShort())
        bb.putInt(1)
        bb.putInt(200)
        // Tag 2: StripByteCounts (0x0117) -> 128 bytes
        bb.putShort(0x0117.toShort())
        bb.putShort(4.toShort())
        bb.putInt(1)
        bb.putInt(128)
        bb.putInt(0)

        // Put JPEG SOI at offset 200
        bb.position(200)
        bb.put(0xFF.toByte())
        bb.put(0xD8.toByte())

        val rawBytes = bb.array()
        val info = parser.parsePreviewInfo(rawBytes)

        assertNotNull(info)
        assertEquals(200, info!!.previewOffset)
        assertEquals(128, info.previewLength)
        assertTrue(info.isJpegValid)
    }

    @Test
    fun `extractPreview recovers via fallback scan when TIFF structure is non-standard`() {
        val rawBytes = ByteArray(512)
        // Put JPEG SOI at offset 120
        rawBytes[120] = 0xFF.toByte()
        rawBytes[121] = 0xD8.toByte()
        rawBytes[122] = 0xFF.toByte()
        rawBytes[123] = 0xE0.toByte()

        val extracted = parser.extractPreview(rawBytes)

        assertNotNull(extracted)
        assertEquals(0xFF.toByte(), extracted!![0])
        assertEquals(0xD8.toByte(), extracted[1])
    }

    @Test
    fun `parser safely handles empty or truncated byte arrays without crashing`() {
        assertNull(parser.parsePreviewInfo(ByteArray(0)))
        assertNull(parser.parsePreviewInfo(ByteArray(4)))
        assertNull(parser.extractPreview(ByteArray(0)))

        // Corrupted TIFF header
        val corruptTiff = byteArrayOf(0x49, 0x49, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00)
        assertNull(parser.parsePreviewInfo(corruptTiff))
    }
}
