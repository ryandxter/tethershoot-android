package com.rvmeur.transferin.data.thumbnail

import com.rvmeur.transferin.data.usb.canon.Cr3PreviewParser
import com.rvmeur.transferin.data.usb.core.SafeUsbTransportManager
import com.rvmeur.transferin.data.usb.core.UsbPriorityMutex
import com.rvmeur.transferin.data.usb.core.UsbTransferPriority
import com.rvmeur.transferin.data.usb.model.EmbeddedPreviewResult
import com.rvmeur.transferin.data.usb.vendor.fuji.RafPreviewExtractor
import com.rvmeur.transferin.data.usb.vendor.nikon.NikonPreviewParser
import com.rvmeur.transferin.data.usb.vendor.sony.ArwPreviewParser
import com.rvmeur.transferin.ui.image.ThumbnailCacheKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicInteger

class ThumbnailPipelineIntegrationTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var thumbnailsDir: File
    private lateinit var storageManager: ThumbnailStorageManager
    private lateinit var usbMutex: UsbPriorityMutex

    @Before
    fun setup() {
        thumbnailsDir = tempFolder.newFolder("integration_thumbnails")
        storageManager = ThumbnailStorageManager(
            thumbnailsDir = thumbnailsDir,
            config = ThumbnailStorageConfig(
                maxCacheBytes = 50 * 1024 * 1024L, // 50MB
                maxTtlMs = 3 * 24 * 60 * 60 * 1000L,
                targetCacheBytes = 40 * 1024 * 1024L
            )
        )
        usbMutex = UsbPriorityMutex()
    }

    private fun createSyntheticJpeg(orientation: Int = 1, width: Int = 1920, height: Int = 1080, size: Int = 2048): ByteArray {
        val stream = mutableListOf<Byte>()
        // 1. SOI
        stream.addAll(listOf(0xFF.toByte(), 0xD8.toByte()))

        // 2. APP1 with Exif Tag 0x0112 (Orientation)
        val tiffBlock = ByteBuffer.allocate(32).order(ByteOrder.LITTLE_ENDIAN)
            .put('I'.code.toByte()).put('I'.code.toByte())
            .putShort(42.toShort())
            .putInt(8)
            .putShort(1.toShort())
            .putShort(0x0112.toShort()) // Orientation tag
            .putShort(3.toShort()) // SHORT
            .putInt(1)
            .putShort(orientation.toShort())
            .putShort(0.toShort())
            .putInt(0)
            .array()

        val exifHeader = byteArrayOf(0x45, 0x78, 0x69, 0x66, 0x00, 0x00)
        val app1Len = 2 + exifHeader.size + tiffBlock.size
        stream.addAll(listOf(
            0xFF.toByte(), 0xE1.toByte(),
            ((app1Len shr 8) and 0xFF).toByte(),
            (app1Len and 0xFF).toByte()
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

        // 4. Safe non-marker dummy scan bytes (0xEE)
        val remaining = size - stream.size - 2
        for (i in 0 until remaining.coerceAtLeast(32)) {
            stream.add(0xEE.toByte())
        }

        // 5. EOI
        stream.addAll(listOf(0xFF.toByte(), 0xD9.toByte()))
        return stream.toByteArray()
    }

    private fun createSyntheticRaf(jpeg: ByteArray): ByteArray {
        val buffer = ByteArray(128 + jpeg.size)
        val magic = "FUJIFILMCCD-RAW ".toByteArray(Charsets.US_ASCII)
        System.arraycopy(magic, 0, buffer, 0, 16)
        val version = "0201".toByteArray(Charsets.US_ASCII)
        System.arraycopy(version, 0, buffer, 16, 4)
        val model = "FUJIFILM X-T5".toByteArray(Charsets.US_ASCII)
        System.arraycopy(model, 0, buffer, 20, model.size)
        val bb = ByteBuffer.wrap(buffer).order(ByteOrder.BIG_ENDIAN)
        bb.putInt(84, 128)
        bb.putInt(88, jpeg.size)
        System.arraycopy(jpeg, 0, buffer, 128, jpeg.size)
        return buffer
    }

    @Test
    fun `end-to-end 100 consecutive shot pipeline cycle achieves 100 percent success with 0 corrupted files`() = runBlocking {
        val totalShots = 100
        val successCount = AtomicInteger(0)
        val orientations = listOf(1, 3, 6, 8)
        val deviceId = "camera_fuji_xt5_001"

        for (i in 0 until totalShots) {
            val orientation = orientations[i % orientations.size]
            val handle = 1000 + i
            val fingerprint = "DSCF_${String.format("%04d", i)}"

            val rawBytes = createSyntheticRaf(createSyntheticJpeg(orientation = orientation, size = 4096))

            // Step 1: Priority-arbitrated preview extraction
            val previewResult: EmbeddedPreviewResult? = usbMutex.withLock(UsbTransferPriority.TIER_1_PREVIEW) {
                RafPreviewExtractor.extractUnifiedPreview(rawBytes).getOrNull()
            }

            assertNotNull("Preview must be successfully extracted for shot $i", previewResult)
            assertEquals("Orientation must match input", orientation, previewResult!!.orientation)

            // Step 2: Atomic disk storage
            val savedFile = storageManager.saveThumbnail(fingerprint, previewResult.jpegBytes)
            assertNotNull("Saved thumbnail file must not be null for shot $i", savedFile)
            assertTrue("File must exist and have length > 0", savedFile!!.exists() && savedFile.length() > 0)

            // Step 3: Compound cache key generation
            val cacheKey = ThumbnailCacheKey.build(fingerprint, deviceId, handle)
            assertEquals("thumb_${deviceId}_${handle}_${fingerprint}", cacheKey)

            // Step 4: Verification of persistent retrieval
            val retrievedFile = storageManager.getThumbnailFile(fingerprint)
            assertNotNull("Retrieved file must exist", retrievedFile)
            assertEquals(savedFile.absolutePath, retrievedFile!!.absolutePath)

            successCount.incrementAndGet()
        }

        assertEquals(totalShots, successCount.get())

        // Verify zero .tmp files remaining
        val tempFiles = thumbnailsDir.listFiles { _, name -> name.endsWith(".tmp") } ?: emptyArray()
        assertEquals("Zero temporary staging files must remain", 0, tempFiles.size)
        assertTrue("Total used bytes must be bounded", storageManager.getUsedBytes() <= 50 * 1024 * 1024L)
    }
}
