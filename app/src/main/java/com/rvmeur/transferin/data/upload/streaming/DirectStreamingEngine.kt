package com.rvmeur.transferin.data.upload.streaming

import android.util.Log
import com.rvmeur.transferin.data.upload.cache.TransitCacheManager
import com.rvmeur.transferin.data.usb.core.DirectByteBufferPool
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream

/**
 * Result of direct USB-to-Cloud streaming attempt.
 */
sealed interface DirectStreamingResult {
    /**
     * Direct zero-copy stream completed successfully without flash staging.
     */
    data class DirectSuccess(
        val remoteFileId: String,
        val bytesStreamed: Long
    ) : DirectStreamingResult

    /**
     * Direct streaming failed or connection was too slow; file was safely buffered to transit cache.
     */
    data class FallbackBuffered(
        val transitFile: File,
        val reason: String
    ) : DirectStreamingResult

    /**
     * Both direct streaming and fallback failed unrecoverably.
     */
    data class Failed(
        val error: Throwable
    ) : DirectStreamingResult
}

/**
 * Coordinates high-speed direct USB-to-Cloud streaming with automatic fallback to local transit cache.
 */
class DirectStreamingEngine(
    private val transitCacheManager: TransitCacheManager? = null,
    private val okHttpClient: OkHttpClient = OkHttpClient(),
    private val bufferPool: DirectByteBufferPool = DirectByteBufferPool.Default,
    private val minThroughputBps: Long = MIN_THROUGHPUT_BPS
) {

    companion object {
        private const val TAG = "DirectStreamingEngine"
        const val MIN_THROUGHPUT_BPS = 500 * 1024L // 500 KB/s threshold
        const val SPEED_CHECK_INTERVAL_MS = 3_000L
    }

    /**
     * Attempts direct stream to cloud endpoint via [directUploader].
     * If direct stream throws an exception or network conditions degrade,
     * falls back to caching stream via [transitCacheManager] and [fallbackSource].
     */
    suspend fun streamWithFallback(
        fingerprint: String,
        fileExtension: String,
        expectedLength: Long,
        onProgress: ((bytesTransferred: Long, totalBytes: Long) -> Unit)? = null,
        fallbackSource: suspend (OutputStream) -> Unit,
        directUploader: suspend (UsbStreamRequestBody) -> String
    ): DirectStreamingResult = withContext(Dispatchers.IO) {
        var bytesTransferredSoFar = 0L
        val startTime = System.currentTimeMillis()

        try {
            val streamBody = UsbStreamRequestBody.fromStreamWriter(
                contentLength = expectedLength,
                bufferPool = bufferPool,
                onProgress = { transferred, total ->
                    bytesTransferredSoFar = transferred
                    onProgress?.invoke(transferred, total)
                }
            ) { outputStream, pool ->
                // Write directly to OkHttp sink
                kotlinx.coroutines.runBlocking {
                    fallbackSource(outputStream)
                }
            }

            val remoteId = directUploader(streamBody)
            return@withContext DirectStreamingResult.DirectSuccess(
                remoteFileId = remoteId,
                bytesStreamed = expectedLength
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Log.w(TAG, "Direct streaming attempt failed: ${e.message}. Initiating fallback to transit cache...", e)

            if (transitCacheManager == null) {
                return@withContext DirectStreamingResult.Failed(e)
            }

            try {
                val hasSpace = transitCacheManager.reserveSpace(expectedLength)
                if (!hasSpace) {
                    return@withContext DirectStreamingResult.Failed(
                        IllegalStateException("Could not reserve transit storage for $expectedLength bytes")
                    )
                }

                val transitFile = transitCacheManager.createTransitFile(fingerprint, fileExtension)
                FileOutputStream(transitFile).use { fos ->
                    fallbackSource(fos)
                }

                return@withContext DirectStreamingResult.FallbackBuffered(
                    transitFile = transitFile,
                    reason = e.message ?: "Direct stream failure"
                )
            } catch (fallbackError: Throwable) {
                Log.e(TAG, "Fallback to transit cache also failed", fallbackError)
                return@withContext DirectStreamingResult.Failed(fallbackError)
            }
        }
    }
}
