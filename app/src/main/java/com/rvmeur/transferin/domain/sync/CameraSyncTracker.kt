package com.rvmeur.transferin.domain.sync

import com.rvmeur.transferin.data.local.dao.CameraSyncDao
import com.rvmeur.transferin.data.local.entity.CameraSyncStatus
import com.rvmeur.transferin.data.upload.cache.TransitCacheManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicLong

/**
 * Reactive real-time telemetry tracker aggregating sync queue state,
 * sliding-window upload throughput, and local transit cache usage.
 */
class CameraSyncTracker(
    private val cameraSyncDao: CameraSyncDao? = null,
    private val transitCacheManager: TransitCacheManager? = null,
    private val externalScope: CoroutineScope? = null
) : AutoCloseable {

    companion object {
        const val SPEED_CALCULATION_WINDOW_MS = 3_000L // 3-second sliding window
    }

    private val internalScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val activeScope = externalScope ?: internalScope

    private val _syncMetrics = MutableStateFlow(CameraSyncMetrics())
    val syncMetrics: StateFlow<CameraSyncMetrics> = _syncMetrics.asStateFlow()

    private val transferRecords = ArrayDeque<Pair<Long, Long>>() // (timestampMs, bytes)
    private val lock = Any()
    private val totalAccumulatedBytes = AtomicLong(0L)
    private var tickerJob: Job? = null

    init {
        startDaoObservers()
        startSpeedTicker()
    }

    private fun startDaoObservers() {
        if (cameraSyncDao == null) return

        val detectedFlow = cameraSyncDao.observeCountByStatus(CameraSyncStatus.DETECTED)
        val bufferedFlow = cameraSyncDao.observeCountByStatus(CameraSyncStatus.BUFFERED_LOCAL)
        val streamingFlow = cameraSyncDao.observeCountByStatus(CameraSyncStatus.STREAMING_CLOUD)
        val confirmedFlow = cameraSyncDao.observeCountByStatus(CameraSyncStatus.CONFIRMED_CLOUD)
        val purgedFlow = cameraSyncDao.observeCountByStatus(CameraSyncStatus.PURGED_LOCAL)
        val failedFlow = cameraSyncDao.observeCountByStatus(CameraSyncStatus.FAILED)
        val totalCountFlow = cameraSyncDao.observeTotalCount()
        val totalBytesFlow = cameraSyncDao.observeTotalBytesUploaded()

        combine(
            listOf(
                detectedFlow,
                bufferedFlow,
                streamingFlow,
                confirmedFlow,
                purgedFlow,
                failedFlow
            )
        ) { countsArray ->
            StatusCounts(
                detected = countsArray[0],
                buffered = countsArray[1],
                streaming = countsArray[2],
                confirmed = countsArray[3],
                purged = countsArray[4],
                failed = countsArray[5]
            )
        }.combine(totalCountFlow) { counts, total ->
            counts to total
        }.combine(totalBytesFlow) { (counts, total), bytes ->
            val cacheBytes = transitCacheManager?.getUsedBytes() ?: 0L
            _syncMetrics.value = _syncMetrics.value.copy(
                detectedCount = counts.detected,
                bufferedCount = counts.buffered,
                streamingCount = counts.streaming,
                confirmedCount = counts.confirmed,
                purgedCount = counts.purged,
                failedCount = counts.failed,
                totalFiles = total,
                totalBytesUploaded = (bytes ?: 0L).coerceAtLeast(totalAccumulatedBytes.get()),
                cacheUsedBytes = cacheBytes
            )
        }.launchIn(activeScope)
    }

    private fun startSpeedTicker() {
        tickerJob = activeScope.launch {
            while (isActive) {
                delay(500L)
                updateSpeedCalculation()
            }
        }
    }

    /**
     * Records bytes transferred for live sliding-window speed calculation.
     */
    fun recordBytesTransferred(bytes: Long, timestampMs: Long = System.currentTimeMillis()) {
        if (bytes > 0L) {
            totalAccumulatedBytes.addAndGet(bytes)
            synchronized(lock) {
                transferRecords.addLast(timestampMs to bytes)
            }
        }
        updateSpeedCalculation(timestampMs)
    }

    /**
     * Manually updates the speed calculation based on sliding window entries.
     */
    fun updateSpeedCalculation(now: Long = System.currentTimeMillis()) {
        val windowStart = now - SPEED_CALCULATION_WINDOW_MS
        var totalBytesInWindow = 0L

        synchronized(lock) {
            while (transferRecords.isNotEmpty() && transferRecords.first().first < windowStart) {
                transferRecords.removeFirst()
            }
            for (record in transferRecords) {
                totalBytesInWindow += record.second
            }
        }

        val calculatedSpeedBps = if (totalBytesInWindow > 0L) {
            val windowDurationSec = SPEED_CALCULATION_WINDOW_MS / 1000.0
            (totalBytesInWindow / windowDurationSec).toLong()
        } else {
            0L
        }

        val cacheBytes = transitCacheManager?.getUsedBytes() ?: _syncMetrics.value.cacheUsedBytes
        _syncMetrics.value = _syncMetrics.value.copy(
            uploadSpeedBps = calculatedSpeedBps,
            totalBytesUploaded = totalAccumulatedBytes.get().coerceAtLeast(_syncMetrics.value.totalBytesUploaded),
            cacheUsedBytes = cacheBytes
        )
    }

    /**
     * Directly injects an instantaneous speed metric.
     */
    fun recordInstantSpeed(speedBps: Long) {
        _syncMetrics.value = _syncMetrics.value.copy(
            uploadSpeedBps = speedBps.coerceAtLeast(0L)
        )
    }

    /**
     * Manually updates metrics with given counts.
     */
    fun updateCounts(
        detected: Int = _syncMetrics.value.detectedCount,
        buffered: Int = _syncMetrics.value.bufferedCount,
        streaming: Int = _syncMetrics.value.streamingCount,
        confirmed: Int = _syncMetrics.value.confirmedCount,
        purged: Int = _syncMetrics.value.purgedCount,
        failed: Int = _syncMetrics.value.failedCount,
        total: Int = _syncMetrics.value.totalFiles
    ) {
        _syncMetrics.value = _syncMetrics.value.copy(
            detectedCount = detected,
            bufferedCount = buffered,
            streamingCount = streaming,
            confirmedCount = confirmed,
            purgedCount = purged,
            failedCount = failed,
            totalFiles = total
        )
    }

    /**
     * Resets metrics to default initial state.
     */
    fun resetMetrics() {
        synchronized(lock) {
            transferRecords.clear()
        }
        totalAccumulatedBytes.set(0L)
        _syncMetrics.value = CameraSyncMetrics()
    }

    override fun close() {
        tickerJob?.cancel()
        if (externalScope == null) {
            internalScope.cancel()
        }
    }

    private data class StatusCounts(
        val detected: Int,
        val buffered: Int,
        val streaming: Int,
        val confirmed: Int,
        val purged: Int,
        val failed: Int
    )
}
