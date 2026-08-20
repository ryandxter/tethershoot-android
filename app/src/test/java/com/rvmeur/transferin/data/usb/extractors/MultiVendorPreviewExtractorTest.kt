package com.rvmeur.transferin.data.usb.extractors

import com.rvmeur.transferin.data.usb.canon.Cr3PreviewParser
import com.rvmeur.transferin.data.usb.model.ExifOrientationHelper
import com.rvmeur.transferin.data.usb.vendor.fuji.RafPreviewExtractor
import com.rvmeur.transferin.data.usb.vendor.nikon.NikonPreviewParser
import com.rvmeur.transferin.data.usb.vendor.sony.ArwPreviewParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class MultiVendorPreviewExtractorTest {

    private fun createSyntheticJpegWithExifOrientation(orientation: Int, width: Int = 1920, height: Int = 1080): ByteArray {
        val stream = mutableListOf<Byte>()
        // 1. SOI
        stream.addAll(listOf(0xFF.toByte(), 0xD8.toByte()))

        // 2. APP1 Exif segment with Tag 0x0112 (Orientation)
        val tiffBlock = ByteBuffer.allocate(32).order(ByteOrder.LITTLE_ENDIAN)
            .put('I'.code.toByte()).put('I'.code.toByte()) // "II" Little Endian
            .putShort(42.toShort()) // Magic 42
            .putInt(8) // IFD0 offset
            .putShort(1.toShort()) // 1 entry in IFD0
            .putShort(0x0112.toShort()) // Tag 0x0112 (Orientation)
            .putShort(3.toShort()) // Type SHORT
            .putInt(1) // Count 1
            .putShort(orientation.toShort()) // Value
            .putShort(0.toShort()) // Padding
            .putInt(0) // Next IFD offset
            .array()

        val exifHeader = byteArrayOf(0x45, 0x78, 0x69, 0x66, 0x00, 0x00) // "Exif\0\0"
        val app1PayloadLen = 2 + exifHeader.size + tiffBlock.size
        stream.addAll(listOf(
            0xFF.toByte(), 0xE1.toByte(),
            ((app1PayloadLen shr 8) and 0xFF).toByte(),
            (app1PayloadLen and 0xFF).toByte()
        ))
        exifHeader.forEach { stream.add(it) }
        tiffBlock.forEach { stream.add(it) }

        // 3. SOF0 (Start of Frame)
        val sofLen = 17
        stream.addAll(listOf(
            0xFF.toByte(), 0xC0.toByte(),
            (sofLen shr 8).toByte(), (sofLen and 0xFF).toByte(),
            0x08.toByte(),
            (height shr 8).toByte(), (height and 0xFF).toByte(),
            (width shr 8).toByte(), (width and 0xFF).toByte(),
            0x03.toByte(),
            0x01.toByte(), 0x22.toByte(), 0x00.toByte(),
            0x02.toByte(), 0x11.toByte(), 0x01.toByte(),
            0x03.toByte(), 0x11.toByte(), 0x01.toByte()
        ))

        // 4. Dummy scan bytes
        for (i in 0 until 64) {
            stream.add(0xEE.toByte())
        }

        // 5. EOI
        stream.addAll(listOf(0xFF.toByte(), 0xD9.toByte()))

        return stream.toByteArray()
    }

    private fun createSyntheticRaf(jpegBytes: ByteArray): ByteArray {
        val headerLen = 128
        val totalSize = headerLen + jpegBytes.size
        val buffer = ByteArray(totalSize)

        // Magic: "FUJIFILMCCD-RAW "
        val magic = "FUJIFILMCCD-RAW ".toByteArray(Charsets.US_ASCII)
        System.arraycopy(magic, 0, buffer, 0, 16)

        // Version: "0201"
        val version = "0201".toByteArray(Charsets.US_ASCII)
        System.arraycopy(version, 0, buffer, 16, 4)

        // Camera model: "FUJIFILM X-T5"
        val model = "FUJIFILM X-T5".toByteArray(Charsets.US_ASCII)
        System.arraycopy(model, 0, buffer, 20, model.size)

        val bb = ByteBuffer.wrap(buffer).order(ByteOrder.BIG_ENDIAN)
        bb.putInt(84, headerLen) // jpegOffset
        bb.putInt(88, jpegBytes.size) // jpegLength

        System.arraycopy(jpegBytes, 0, buffer, headerLen, jpegBytes.size)
        return buffer
    }

    private fun createSyntheticCr3(jpegBytes: ByteArray, width: Int = 1920, height: Int = 1080): ByteArray {
        fun createBox(type: Int, payload: ByteArray): ByteArray {
            val len = 8 + payload.size
            val bb = ByteBuffer.allocate(len).order(ByteOrder.BIG_ENDIAN)
            bb.putInt(len)
            bb.putInt(type)
            bb.put(payload)
            return bb.array()
        }

        val ftypPayload = ByteBuffer.allocate(12).order(ByteOrder.BIG_ENDIAN)
            .putInt(Cr3PreviewParser.BRAND_CRX)
            .putInt(1)
            .putInt(Cr3PreviewParser.BRAND_CRX)
            .array()
        val ftypBox = createBox(Cr3PreviewParser.FOURCC_FTYP, ftypPayload)

        val prvwPayload = ByteBuffer.allocate(12 + jpegBytes.size).order(ByteOrder.BIG_ENDIAN)
            .putInt(0)
            .putShort(width.toShort())
            .putShort(height.toShort())
            .putInt(jpegBytes.size)
            .put(jpegBytes)
            .array()
        val prvwBox = createBox(Cr3PreviewParser.FOURCC_PRVW, prvwPayload)

        val canonUuid = byteArrayOf(
            0x85.toByte(), 0xC0.toByte(), 0xB6.toByte(), 0x87.toByte(),
            0x82.toByte(), 0x0F.toByte(), 0x11.toByte(), 0xE0.toByte(),
            0x81.toByte(), 0x11.toByte(), 0xF4.toByte(), 0xCE.toByte(),
            0x46.toByte(), 0x2B.toByte(), 0x02.toByte(), 0x20.toByte()
        )
        val uuidPayload = ByteBuffer.allocate(16 + prvwBox.size)
            .put(canonUuid)
            .put(prvwBox)
            .array()
        val uuidBox = createBox(Cr3PreviewParser.FOURCC_UUID, uuidPayload)
        val moovBox = createBox(Cr3PreviewParser.FOURCC_MOOV, uuidBox)

        val totalBuffer = ByteBuffer.allocate(ftypBox.size + moovBox.size)
        totalBuffer.put(ftypBox)
        totalBuffer.put(moovBox)
        return totalBuffer.array()
    }

    private fun createSyntheticTiff(jpegBytes: ByteArray, orientation: Int, width: Int = 1920, height: Int = 1080): ByteArray {
        val totalSize = 256 + jpegBytes.size
        val bb = ByteBuffer.allocate(totalSize).order(ByteOrder.LITTLE_ENDIAN)

        // TIFF Header
        bb.put('I'.code.toByte()).put('I'.code.toByte())
        bb.putShort(42.toShort())
        bb.putInt(8) // IFD0 offset

        // IFD0: 5 tags
        bb.putShort(5.toShort()) // 5 entries

        val jpegOffset = 256

        // Tag 1: ImageWidth (0x0100)
        bb.putShort(0x0100.toShort()).putShort(4.toShort()).putInt(1).putInt(width)
        // Tag 2: ImageHeight (0x0101)
        bb.putShort(0x0101.toShort()).putShort(4.toShort()).putInt(1).putInt(height)
        // Tag 3: Orientation (0x0112)
        bb.putShort(0x0112.toShort()).putShort(3.toShort()).putInt(1).putShort(orientation.toShort()).putShort(0.toShort())
        // Tag 4: JpegOffset (0x0201)
        bb.putShort(0x0201.toShort()).putShort(4.toShort()).putInt(1).putInt(jpegOffset)
        // Tag 5: JpegLength (0x0202)
        bb.putShort(0x0202.toShort()).putShort(4.toShort()).putInt(1).putInt(jpegBytes.size)

        bb.putInt(0) // Next IFD

        bb.position(jpegOffset)
        bb.put(jpegBytes)

        return bb.array()
    }

    @Test
    fun `ExifOrientationHelper accurately parses orientations 1, 3, 6, and 8`() {
        listOf(1, 3, 6, 8).forEach { ori ->
            val jpeg = createSyntheticJpegWithExifOrientation(ori)
            val parsed = ExifOrientationHelper.extractOrientation(jpeg)
            assertEquals("Orientation $ori must be extracted accurately", ori, parsed)
        }
    }

    @Test
    fun `Fujifilm RAF parser extracts embedded JPEG and preserves orientation in sub-50ms`() {
        val orientations = listOf(1, 6, 8)
        for (ori in orientations) {
            val jpeg = createSyntheticJpegWithExifOrientation(ori, width = 1920, height = 1080)
            val rafBytes = createSyntheticRaf(jpeg)

            val start = System.currentTimeMillis()
            val result = RafPreviewExtractor.extractPreviewResult(rafBytes)
            val elapsed = System.currentTimeMillis() - start

            assertTrue("RAF extraction must succeed", result.isSuccess)
            val preview = result.getOrThrow()
            assertEquals("Orientation must match", ori, preview.orientation)
            assertEquals(1920, preview.width)
            assertEquals(1080, preview.height)
            assertTrue("RAF parsing must be sub-50ms (took ${elapsed}ms)", elapsed < 50)
        }
    }

    @Test
    fun `Canon CR3 parser extracts PRVW preview and preserves orientation in sub-50ms`() {
        val orientations = listOf(1, 6, 8)
        for (ori in orientations) {
            val jpeg = createSyntheticJpegWithExifOrientation(ori, width = 1920, height = 1080)
            val cr3Bytes = createSyntheticCr3(jpeg, width = 1920, height = 1080)

            val start = System.currentTimeMillis()
            val result = Cr3PreviewParser.parsePreviewFromSlice(cr3Bytes)
            val elapsed = System.currentTimeMillis() - start

            assertNotNull("CR3 extraction must succeed", result)
            assertEquals("Orientation must match", ori, result!!.orientation)
            assertEquals(1920, result.width)
            assertEquals(1080, result.height)
            assertTrue("CR3 parsing must be sub-50ms (took ${elapsed}ms)", elapsed < 50)
        }
    }

    @Test
    fun `Sony ARW parser extracts SubIFD JPEG and reads TIFF orientation in sub-10ms`() {
        val orientations = listOf(1, 3, 6, 8)
        val arwParser = ArwPreviewParser()

        for (ori in orientations) {
            val jpeg = createSyntheticJpegWithExifOrientation(ori, width = 1616, height = 1080)
            val arwBytes = createSyntheticTiff(jpeg, orientation = ori, width = 1616, height = 1080)

            val start = System.currentTimeMillis()
            val info = arwParser.parsePreviewInfo(arwBytes)
            val elapsed = System.currentTimeMillis() - start

            assertNotNull("ARW parseInfo must succeed", info)
            assertEquals("ARW orientation must match", ori, info!!.orientation)
            assertEquals(1616, info.width)
            assertEquals(1080, info.height)

            val extractedJpeg = arwParser.extractPreview(arwBytes)
            assertNotNull(extractedJpeg)
            assertTrue("ARW parsing must be sub-10ms (took ${elapsed}ms)", elapsed < 10)
        }
    }

    @Test
    fun `Nikon NEF parser extracts preview and orientation in sub-50ms`() {
        val orientations = listOf(1, 6, 8)
        for (ori in orientations) {
            val jpeg = createSyntheticJpegWithExifOrientation(ori, width = 1920, height = 1080)
            val nefBytes = createSyntheticTiff(jpeg, orientation = ori, width = 1920, height = 1080)

            val start = System.currentTimeMillis()
            val info = NikonPreviewParser.parsePreviewInfo(nefBytes)
            val elapsed = System.currentTimeMillis() - start

            assertNotNull("NEF parseInfo must succeed", info)
            assertEquals("NEF orientation must match", ori, info!!.orientation)
            assertEquals(1920, info.width)
            assertEquals(1080, info.height)

            val extractedJpeg = NikonPreviewParser.extractPreview(nefBytes)
            assertNotNull(extractedJpeg)
            assertTrue("NEF parsing must be sub-50ms (took ${elapsed}ms)", elapsed < 50)
        }
    }
}
