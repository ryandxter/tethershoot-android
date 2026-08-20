package com.rvmeur.transferin.domain.sync

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CameraSyncTrackerTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @Test
    fun `sliding-window speed estimation over 3-second window`() {
        val tracker = CameraSyncTracker(externalScope = testScope)

        val baseTime = 10_000L
        // Pump 15 MB (15,728,640 bytes)
        val transferredBytes = 15 * 1024 * 1024L
        tracker.recordBytesTransferred(transferredBytes, timestampMs = baseTime)

        val metrics = tracker.syncMetrics.value
        assertEquals(transferredBytes, metrics.totalBytesUploaded)

        // 15 MB / 3 seconds = 5 MB/s = 5,242,880 Bps
        val expectedSpeedBps = transferredBytes / 3L
        assertEquals(expectedSpeedBps, metrics.uploadSpeedBps)
        assertEquals(5.0, metrics.uploadSpeedMBps, 0.01)

        // Advance time past 3s window (e.g. at 14_000L, which is 4s after 10_000L)
        tracker.recordBytesTransferred(0L, timestampMs = baseTime + 4_000L)
        // Previous 15MB dropped out of the 3s window
        assertEquals(0L, tracker.syncMetrics.value.uploadSpeedBps)

        tracker.close()
    }

    @Test
    fun `metrics progress percent and active flight calculation`() {
        val tracker = CameraSyncTracker(externalScope = testScope)

        tracker.updateCounts(
            detected = 2,
            buffered = 3,
            streaming = 1,
            confirmed = 6,
            purged = 4,
            failed = 0,
            total = 16
        )

        val metrics = tracker.syncMetrics.value
        assertEquals(10, metrics.completedCount) // 6 confirmed + 4 purged
        assertEquals(4, metrics.activeInFlightCount) // 3 buffered + 1 streaming
        assertEquals(62.5f, metrics.percentComplete, 0.01f) // 10 / 16 * 100 = 62.5%
        assertFalse(metrics.isIdle)

        tracker.resetMetrics()
        assertTrue(tracker.syncMetrics.value.isIdle)
        assertEquals(0, tracker.syncMetrics.value.totalFiles)

        tracker.close()
    }

    @Test
    fun `direct instantaneous speed recording`() {
        val tracker = CameraSyncTracker(externalScope = testScope)

        tracker.recordInstantSpeed(10 * 1024 * 1024L) // 10 MB/s
        assertEquals(10 * 1024 * 1024L, tracker.syncMetrics.value.uploadSpeedBps)
        assertEquals(10.0, tracker.syncMetrics.value.uploadSpeedMBps, 0.01)

        tracker.close()
    }
}
