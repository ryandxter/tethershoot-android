package com.rvmeur.transferin.data.usb

import android.net.Uri
import android.util.Log
import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs
import kotlin.system.measureTimeMillis

class FileStabilityChecker(
    private val fileInfoProvider: UsbFileInfoProvider
) {

    companion object {
        private const val TAG = "FileStabilityChecker"
        private val requestCounter = AtomicLong(0L)

        // Optimized defaults for photo/video files
        // JPEG: ~2-5 seconds write time
        // RAW (CR2/CR3/NEF/ARW): ~10-30 seconds write time
        // Video (MP4/MOV): varies widely, can be minutes
        const val DEFAULT_MAX_CHECKS = 4           // from 2 to 4 - more samples
        const val DEFAULT_INTERVAL_MS = 2000L       // from 1200 to 2000 - give more time for camera write
        const val DEFAULT_MIN_SIZE_BYTES = 1L
        const val DEFAULT_MODIFIED_TOLERANCE_MS = 5000L
    }

    suspend fun isFileStable(
        uri: Uri,
        maxChecks: Int = DEFAULT_MAX_CHECKS,
        intervalMillis: Long = DEFAULT_INTERVAL_MS,
        minimumSizeBytes: Long = DEFAULT_MIN_SIZE_BYTES,
        modifiedTimeToleranceMillis: Long = DEFAULT_MODIFIED_TOLERANCE_MS
    ): Boolean {
        require(maxChecks >= 2) { "maxChecks minimal 2" }
        require(intervalMillis > 0) { "intervalMillis harus > 0" }
        require(minimumSizeBytes >= 0L) { "minimumSizeBytes harus >= 0" }
        require(modifiedTimeToleranceMillis >= 0L) { "modifiedTimeToleranceMillis harus >= 0" }

        val requestId = nextRequestId()
        val startedAt = System.currentTimeMillis()

        Log.d(
            TAG,
            "[$requestId] isFileStable() start: uri=$uri, maxChecks=$maxChecks, intervalMs=$intervalMillis"
        )

        var previousInfo: UsbFileInfo? = null
        var stableReadCount = 0
        var providerCallCount = 0
        var lastReason = "unknown"
        var lastSize = 0L
        var lastMod = 0L

        val totalDurationMs = measureTimeMillis {
            repeat(maxChecks) { index ->
                val checkIndex = index + 1

                var currentInfo: UsbFileInfo? = null
                val providerDurationMs = measureTimeMillis {
                    currentInfo = try {
                        fileInfoProvider.getFileInfo(uri)
                    } catch (throwable: Throwable) {
                        Log.e(
                            TAG,
                            "[$requestId] getFileInfo() failed: uri=$uri, checkIndex=$checkIndex, error=${throwable.message}"
                        )
                        null
                    }
                }

                providerCallCount += 1

                val resolvedInfo = currentInfo
                if (resolvedInfo == null) {
                    lastReason = "file_info_null"
                    Log.d(
                        TAG,
                        "[$requestId] isFileStable() STOP: getFileInfo returned null, checkIndex=$checkIndex"
                    )
                    return false
                }

                lastSize = resolvedInfo.sizeBytes
                lastMod = resolvedInfo.lastModifiedEpochMs

                if (resolvedInfo.sizeBytes < minimumSizeBytes) {
                    lastReason = "size_below_minimum"
                    Log.d(
                        TAG,
                        "[$requestId] isFileStable() STOP: size below minimum, size=${resolvedInfo.sizeBytes}, min=$minimumSizeBytes"
                    )
                    return false
                }

                val previous = previousInfo
                if (previous != null) {
                    val sizeChanged = resolvedInfo.sizeBytes != previous.sizeBytes
                    val modifiedDiff = abs(resolvedInfo.lastModifiedEpochMs - previous.lastModifiedEpochMs)
                    val modifiedChanged = modifiedDiff > modifiedTimeToleranceMillis

                    if (!sizeChanged && !modifiedChanged) {
                        stableReadCount += 1
                        lastReason = "stable_pair"
                        Log.v(
                            TAG,
                            "[$requestId] isFileStable() check $checkIndex: STABLE (size=${resolvedInfo.sizeBytes}, mod=${resolvedInfo.lastModifiedEpochMs})"
                        )
                    } else {
                        lastReason = "changed(sizeChanged=$sizeChanged, modChanged=$modifiedChanged, modDiff=${modifiedDiff}ms)"
                        Log.v(
                            TAG,
                            "[$requestId] isFileStable() check $checkIndex: CHANGED (size=${resolvedInfo.sizeBytes}, mod=${resolvedInfo.lastModifiedEpochMs}, diff=${modifiedDiff}ms)"
                        )
                    }
                } else {
                    lastReason = "first_read_only"
                    Log.v(
                        TAG,
                        "[$requestId] isFileStable() check $checkIndex: FIRST READ (size=${resolvedInfo.sizeBytes}, mod=${resolvedInfo.lastModifiedEpochMs})"
                    )
                }

                previousInfo = resolvedInfo

                if (index < maxChecks - 1) {
                    delay(intervalMillis)
                }
            }
        }

        // Stable = at least 1 stable read pair OR only 1 read needed and file is valid
        val isStable = stableReadCount >= 1
        val elapsed = System.currentTimeMillis() - startedAt

        if (isStable) {
            Log.i(TAG, "[$requestId] isFileStable() PASS: stable=true, size=$lastSize, mod=$lastMod, elapsed=${elapsed}ms")
        } else {
            Log.w(TAG, "[$requestId] isFileStable() FAIL: stable=false, reason=$lastReason, size=$lastSize, mod=$lastMod, elapsed=${elapsed}ms")
        }

        return isStable
    }

    private fun nextRequestId(): String {
        return "stability-${requestCounter.incrementAndGet()}"
    }
}

data class UsbFileInfo(
    val sizeBytes: Long,
    val lastModifiedEpochMs: Long
)

interface UsbFileInfoProvider {
    suspend fun getFileInfo(uri: Uri): UsbFileInfo?
}