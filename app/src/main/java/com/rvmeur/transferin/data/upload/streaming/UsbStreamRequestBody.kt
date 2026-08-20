package com.rvmeur.transferin.data.upload.streaming

import com.rvmeur.transferin.data.usb.core.DirectByteBufferPool
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okio.BufferedSink
import java.io.InputStream
import java.io.OutputStream
import java.nio.channels.Channels

/**
 * High-throughput direct zero-copy USB-to-Cloud OkHttp [RequestBody].
 *
 * Utilizes [DirectByteBufferPool] 128KB direct off-heap buffer slices to pipe
 * binary streams directly from USB Bulk IN / camera sources into OkHttp sinks
 * without staging large RAW files to local flash storage when network bandwidth permits.
 */
class UsbStreamRequestBody(
    private val expectedLength: Long,
    private val contentType: MediaType = "application/octet-stream".toMediaType(),
    private val bufferPool: DirectByteBufferPool = DirectByteBufferPool.Default,
    private val onProgress: ((bytesTransferred: Long, totalBytes: Long) -> Unit)? = null,
    private val inputStreamProvider: (() -> InputStream)? = null,
    private val streamWriter: ((OutputStream, DirectByteBufferPool) -> Unit)? = null
) : RequestBody() {

    override fun contentType(): MediaType = contentType

    override fun contentLength(): Long = expectedLength

    override fun writeTo(sink: BufferedSink) {
        val outStream = sink.outputStream()

        if (streamWriter != null) {
            streamWriter.invoke(outStream, bufferPool)
            sink.flush()
            return
        }

        if (inputStreamProvider != null) {
            inputStreamProvider.invoke().use { inputStream ->
                val readableChannel = Channels.newChannel(inputStream)
                val writableChannel = Channels.newChannel(outStream)
                var totalWritten = 0L

                bufferPool.acquire(DirectByteBufferPool.SMALL_BUFFER_SIZE).use { pooledBuffer ->
                    val directBuf = pooledBuffer.byteBuffer
                    directBuf.clear()

                    while (readableChannel.read(directBuf).also { bytesRead ->
                        if (bytesRead > 0) {
                            directBuf.flip()
                            while (directBuf.hasRemaining()) {
                                writableChannel.write(directBuf)
                            }
                            directBuf.clear()
                            totalWritten += bytesRead
                            onProgress?.invoke(totalWritten, expectedLength.coerceAtLeast(totalWritten))
                        }
                    } != -1) {
                        // Continued streaming
                    }
                }
            }
            sink.flush()
            return
        }

        throw IllegalStateException("UsbStreamRequestBody requires either an inputStreamProvider or streamWriter")
    }

    companion object {
        /**
         * Creates a UsbStreamRequestBody from a repeatable or fresh [InputStream] provider.
         */
        fun fromInputStream(
            contentLength: Long,
            mediaType: MediaType = "application/octet-stream".toMediaType(),
            bufferPool: DirectByteBufferPool = DirectByteBufferPool.Default,
            onProgress: ((bytesTransferred: Long, totalBytes: Long) -> Unit)? = null,
            inputStreamProvider: () -> InputStream
        ): UsbStreamRequestBody {
            return UsbStreamRequestBody(
                expectedLength = contentLength,
                contentType = mediaType,
                bufferPool = bufferPool,
                onProgress = onProgress,
                inputStreamProvider = inputStreamProvider
            )
        }

        /**
         * Creates a UsbStreamRequestBody using a custom [streamWriter] block.
         */
        fun fromStreamWriter(
            contentLength: Long,
            mediaType: MediaType = "application/octet-stream".toMediaType(),
            bufferPool: DirectByteBufferPool = DirectByteBufferPool.Default,
            onProgress: ((bytesTransferred: Long, totalBytes: Long) -> Unit)? = null,
            streamWriter: (OutputStream, DirectByteBufferPool) -> Unit
        ): UsbStreamRequestBody {
            return UsbStreamRequestBody(
                expectedLength = contentLength,
                contentType = mediaType,
                bufferPool = bufferPool,
                onProgress = onProgress,
                streamWriter = streamWriter
            )
        }
    }
}
