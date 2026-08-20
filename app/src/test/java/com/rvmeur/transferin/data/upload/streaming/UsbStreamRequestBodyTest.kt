package com.rvmeur.transferin.data.upload.streaming

import com.rvmeur.transferin.data.usb.core.DirectByteBufferPool
import okhttp3.MediaType.Companion.toMediaType
import okio.Buffer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.util.Random

class UsbStreamRequestBodyTest {

    @Test
    fun `streaming from InputStream pipes exact bytes without corruption`() {
        val random = Random(42)
        val testData = ByteArray(300 * 1024) // 300 KB (> 2 x 128KB chunks)
        random.nextBytes(testData)

        val progressSnapshots = mutableListOf<Pair<Long, Long>>()
        val pool = DirectByteBufferPool(maxSmallBuffers = 4)

        val requestBody = UsbStreamRequestBody.fromInputStream(
            contentLength = testData.size.toLong(),
            mediaType = "image/x-fuji-raw".toMediaType(),
            bufferPool = pool,
            onProgress = { transferred, total ->
                progressSnapshots.add(transferred to total)
            },
            inputStreamProvider = { ByteArrayInputStream(testData) }
        )

        assertEquals("image/x-fuji-raw", requestBody.contentType().toString())
        assertEquals(testData.size.toLong(), requestBody.contentLength())

        val sinkBuffer = Buffer()
        requestBody.writeTo(sinkBuffer)

        val outputBytes = sinkBuffer.readByteArray()
        assertArrayEquals(testData, outputBytes)

        assertTrue("Progress should have been reported", progressSnapshots.isNotEmpty())
        assertEquals(testData.size.toLong(), progressSnapshots.last().first)
        assertEquals(testData.size.toLong(), progressSnapshots.last().second)

        // Buffer pool sanity check: all buffers returned
        assertEquals(0, pool.activeLeaseCount)
    }

    @Test
    fun `streaming from custom streamWriter pipes data to sink`() {
        val testString = "TransferIn Direct USB Streaming Binary Chunk Test Data 12345"
        val testBytes = testString.toByteArray(Charsets.UTF_8)
        val pool = DirectByteBufferPool(maxSmallBuffers = 2)

        val requestBody = UsbStreamRequestBody.fromStreamWriter(
            contentLength = testBytes.size.toLong(),
            bufferPool = pool
        ) { outStream, _ ->
            outStream.write(testBytes)
        }

        val sinkBuffer = Buffer()
        requestBody.writeTo(sinkBuffer)

        val outputString = sinkBuffer.readUtf8()
        assertEquals(testString, outputString)
    }
}
