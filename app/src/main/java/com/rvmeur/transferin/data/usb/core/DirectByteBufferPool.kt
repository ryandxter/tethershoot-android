package com.rvmeur.transferin.data.usb.core

import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Off-heap Direct ByteBuffer Pool designed to eliminate JVM garbage collection churn
 * during high-throughput camera image streaming over USB.
 */
class DirectByteBufferPool(
    private val maxSmallBuffers: Int = DEFAULT_MAX_SMALL_BUFFERS,
    private val maxLargeBuffers: Int = DEFAULT_MAX_LARGE_BUFFERS
) {

    companion object {
        const val SMALL_BUFFER_SIZE = 128 * 1024 // 128 KB
        const val LARGE_BUFFER_SIZE = 1024 * 1024 // 1 MB

        const val DEFAULT_MAX_SMALL_BUFFERS = 16 // 2 MB total off-heap max
        const val DEFAULT_MAX_LARGE_BUFFERS = 8 // 8 MB total off-heap max

        val Default: DirectByteBufferPool by lazy { DirectByteBufferPool() }
    }

    private val smallQueue = ConcurrentLinkedQueue<ByteBuffer>()
    private val smallQueueSize = AtomicInteger(0)

    private val largeQueue = ConcurrentLinkedQueue<ByteBuffer>()
    private val largeQueueSize = AtomicInteger(0)

    private val _acquiredCount = AtomicLong(0)
    private val _releasedCount = AtomicLong(0)
    private val _totalAllocatedDirectBytes = AtomicLong(0)

    val acquiredCount: Long get() = _acquiredCount.get()
    val releasedCount: Long get() = _releasedCount.get()
    val totalAllocatedDirectBytes: Long get() = _totalAllocatedDirectBytes.get()
    val activeLeaseCount: Int get() = (_acquiredCount.get() - _releasedCount.get()).toInt().coerceAtLeast(0)
    val inUseDirectBytes: Long
        get() {
            val leases = activeLeaseCount
            return if (leases <= 0) 0L else leases.toLong() * SMALL_BUFFER_SIZE
        }

    /**
     * Acquires a pooled or newly allocated direct [ByteBuffer] wrapped in a [PooledByteBuffer].
     * If [requiredCapacity] <= 128KB, returns a 128KB buffer.
     * If [requiredCapacity] <= 1MB, returns a 1MB buffer.
     * Otherwise, allocates an exact-sized direct buffer.
     */
    fun acquire(requiredCapacity: Int = SMALL_BUFFER_SIZE): PooledByteBuffer {
        require(requiredCapacity > 0) { "Capacity must be positive: $requiredCapacity" }

        val bucketType: BufferBucketType
        val buffer: ByteBuffer

        when {
            requiredCapacity <= SMALL_BUFFER_SIZE -> {
                bucketType = BufferBucketType.SMALL
                val polled = smallQueue.poll()
                if (polled != null) {
                    smallQueueSize.decrementAndGet()
                    buffer = polled
                } else {
                    buffer = ByteBuffer.allocateDirect(SMALL_BUFFER_SIZE)
                    _totalAllocatedDirectBytes.addAndGet(SMALL_BUFFER_SIZE.toLong())
                }
            }
            requiredCapacity <= LARGE_BUFFER_SIZE -> {
                bucketType = BufferBucketType.LARGE
                val polled = largeQueue.poll()
                if (polled != null) {
                    largeQueueSize.decrementAndGet()
                    buffer = polled
                } else {
                    buffer = ByteBuffer.allocateDirect(LARGE_BUFFER_SIZE)
                    _totalAllocatedDirectBytes.addAndGet(LARGE_BUFFER_SIZE.toLong())
                }
            }
            else -> {
                bucketType = BufferBucketType.UNPOOLED
                buffer = ByteBuffer.allocateDirect(requiredCapacity)
                _totalAllocatedDirectBytes.addAndGet(requiredCapacity.toLong())
            }
        }

        buffer.clear()
        _acquiredCount.incrementAndGet()

        return PooledByteBuffer(
            buffer = buffer,
            bucketType = bucketType,
            pool = this
        )
    }

    /**
     * Internal return mechanism called by [PooledByteBuffer.close].
     */
    internal fun releaseBuffer(buffer: ByteBuffer, bucketType: BufferBucketType) {
        buffer.clear()
        _releasedCount.incrementAndGet()

        when (bucketType) {
            BufferBucketType.SMALL -> {
                if (smallQueueSize.get() < maxSmallBuffers) {
                    smallQueue.offer(buffer)
                    smallQueueSize.incrementAndGet()
                }
            }
            BufferBucketType.LARGE -> {
                if (largeQueueSize.get() < maxLargeBuffers) {
                    largeQueue.offer(buffer)
                    largeQueueSize.incrementAndGet()
                }
            }
            BufferBucketType.UNPOOLED -> {
                // Let JVM direct memory cleaner / GC reclaim unpooled direct buffer
            }
        }
    }

    /**
     * Clears all pooled buffers and resets queue capacities.
     */
    fun clear() {
        smallQueue.clear()
        smallQueueSize.set(0)
        largeQueue.clear()
        largeQueueSize.set(0)
    }

    enum class BufferBucketType {
        SMALL,
        LARGE,
        UNPOOLED
    }
}

/**
 * AutoCloseable holder for a pooled direct [ByteBuffer].
 */
class PooledByteBuffer internal constructor(
    private val buffer: ByteBuffer,
    internal val bucketType: DirectByteBufferPool.BufferBucketType,
    private val pool: DirectByteBufferPool
) : AutoCloseable {

    private val isClosedFlag = AtomicBoolean(false)

    val byteBuffer: ByteBuffer
        get() {
            check(!isClosedFlag.get()) { "Attempted to access closed PooledByteBuffer" }
            return buffer
        }

    val capacity: Int get() = buffer.capacity()

    val isClosed: Boolean get() = isClosedFlag.get()

    override fun close() {
        if (isClosedFlag.compareAndSet(false, true)) {
            pool.releaseBuffer(buffer, bucketType)
        }
    }
}
