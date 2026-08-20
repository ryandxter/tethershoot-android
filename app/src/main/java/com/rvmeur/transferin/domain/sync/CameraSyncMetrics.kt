package com.rvmeur.transferin.domain.sync

/**
 * Real-time telemetry metrics representing the current state of camera-to-cloud synchronization.
 */
data class CameraSyncMetrics(
    val detectedCount: Int = 0,
    val bufferedCount: Int = 0,
    val streamingCount: Int = 0,
    val confirmedCount: Int = 0,
    val purgedCount: Int = 0,
    val failedCount: Int = 0,
    val totalFiles: Int = 0,
    val totalBytesUploaded: Long = 0L,
    val uploadSpeedBps: Long = 0L,
    val cacheUsedBytes: Long = 0L,
    val cacheMaxBytes: Long = DEFAULT_CACHE_MAX_BYTES
) {
    companion object {
        const val DEFAULT_CACHE_MAX_BYTES: Long = 5L * 1024 * 1024 * 1024 // 5 GB
    }

    val completedCount: Int
        get() = confirmedCount + purgedCount

    val activeInFlightCount: Int
        get() = bufferedCount + streamingCount

    val percentComplete: Float
        get() = if (totalFiles > 0) {
            (completedCount.toFloat() / totalFiles.toFloat()).coerceIn(0f, 1f) * 100f
        } else {
            0f
        }

    val isIdle: Boolean
        get() = detectedCount == 0 && bufferedCount == 0 && streamingCount == 0

    val uploadSpeedMBps: Double
        get() = uploadSpeedBps / (1024.0 * 1024.0)

    val cacheUsagePercent: Float
        get() = if (cacheMaxBytes > 0L) {
            (cacheUsedBytes.toFloat() / cacheMaxBytes.toFloat()).coerceIn(0f, 1f) * 100f
        } else {
            0f
        }
}
