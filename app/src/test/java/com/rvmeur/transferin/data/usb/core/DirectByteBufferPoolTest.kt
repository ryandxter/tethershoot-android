package com.rvmeur.transferin.data.usb.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class DirectByteBufferPoolTest {

    private lateinit var pool: DirectByteBufferPool

    @Before
    fun setUp() {
        pool = DirectByteBufferPool(maxSmallBuffers = 8, maxLargeBuffers = 4)
    }

    @Test
    fun `acquire small buffer returns 128KB direct buffer`() {
        val pooled = pool.acquire(64 * 1024)
        assertNotNull(pooled)
        assertTrue(pooled.byteBuffer.isDirect)
        assertEquals(DirectByteBufferPool.SMALL_BUFFER_SIZE, pooled.capacity)
        assertEquals(1, pool.activeLeaseCount)
        assertEquals(1L, pool.acquiredCount)
        assertEquals(0L, pool.releasedCount)

        pooled.close()
        assertEquals(0, pool.activeLeaseCount)
        assertEquals(1L, pool.releasedCount)
        assertTrue(pooled.isClosed)
    }

    @Test
    fun `acquire large buffer returns 1MB direct buffer`() {
        val pooled = pool.acquire(512 * 1024)
        assertNotNull(pooled)
        assertTrue(pooled.byteBuffer.isDirect)
        assertEquals(DirectByteBufferPool.LARGE_BUFFER_SIZE, pooled.capacity)
        assertEquals(1, pool.activeLeaseCount)

        pooled.close()
        assertEquals(0, pool.activeLeaseCount)
        assertEquals(1L, pool.releasedCount)
    }

    @Test
    fun `acquire unpooled oversized buffer`() {
        val oversized = 2 * 1024 * 1024
        val pooled = pool.acquire(oversized)
        assertNotNull(pooled)
        assertTrue(pooled.byteBuffer.isDirect)
        assertEquals(oversized, pooled.capacity)
        assertEquals(1, pool.activeLeaseCount)

        pooled.close()
        assertEquals(0, pool.activeLeaseCount)
    }

    @Test
    fun `use block automatically closes pooled buffer`() {
        var capturedCapacity = 0
        pool.acquire(DirectByteBufferPool.SMALL_BUFFER_SIZE).use { pooled ->
            capturedCapacity = pooled.capacity
            assertEquals(1, pool.activeLeaseCount)
            pooled.byteBuffer.put(0x42.toByte())
        }

        assertEquals(DirectByteBufferPool.SMALL_BUFFER_SIZE, capturedCapacity)
        assertEquals(0, pool.activeLeaseCount)
        assertEquals(1L, pool.releasedCount)
    }

    @Test
    fun `double close is idempotent and safe`() {
        val pooled = pool.acquire(DirectByteBufferPool.SMALL_BUFFER_SIZE)
        assertEquals(1, pool.activeLeaseCount)

        pooled.close()
        assertEquals(0, pool.activeLeaseCount)
        assertEquals(1L, pool.releasedCount)

        // Second close
        pooled.close()
        assertEquals(0, pool.activeLeaseCount)
        assertEquals(1L, pool.releasedCount)
    }

    @Test
    fun `accessing byteBuffer after close throws IllegalStateException`() {
        val pooled = pool.acquire(DirectByteBufferPool.SMALL_BUFFER_SIZE)
        pooled.close()

        try {
            pooled.byteBuffer
            fail("Expected IllegalStateException when accessing closed buffer")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message?.contains("closed") == true)
        }
    }

    @Test
    fun `buffer position and limit are reset on reuse`() {
        val pooled1 = pool.acquire(DirectByteBufferPool.SMALL_BUFFER_SIZE)
        pooled1.byteBuffer.put(byteArrayOf(1, 2, 3, 4, 5))
        pooled1.byteBuffer.flip()
        assertEquals(5, pooled1.byteBuffer.limit())
        pooled1.close()

        // Acquire again - should reuse the cleared buffer
        val pooled2 = pool.acquire(DirectByteBufferPool.SMALL_BUFFER_SIZE)
        assertEquals(0, pooled2.byteBuffer.position())
        assertEquals(DirectByteBufferPool.SMALL_BUFFER_SIZE, pooled2.byteBuffer.limit())
        pooled2.close()
    }

    @Test
    fun `concurrent multi-threaded acquisition and release stress test`() {
        val threadCount = 20
        val iterationsPerThread = 100
        val executor = Executors.newFixedThreadPool(threadCount)
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(threadCount)
        val errorCount = AtomicInteger(0)

        for (i in 0 until threadCount) {
            executor.submit {
                try {
                    startLatch.await()
                    for (j in 0 until iterationsPerThread) {
                        val size = if (j % 2 == 0) DirectByteBufferPool.SMALL_BUFFER_SIZE else DirectByteBufferPool.LARGE_BUFFER_SIZE
                        pool.acquire(size).use { pooled ->
                            val buf = pooled.byteBuffer
                            buf.put(0, (j % 128).toByte())
                        }
                    }
                } catch (t: Throwable) {
                    errorCount.incrementAndGet()
                } finally {
                    doneLatch.countDown()
                }
            }
        }

        startLatch.countDown()
        val finished = doneLatch.await(10, TimeUnit.SECONDS)
        executor.shutdown()

        assertTrue("Stress test should finish within timeout", finished)
        assertEquals("Zero errors during concurrent pooling", 0, errorCount.get())
        assertEquals("Active leases must be 0 after all releases", 0, pool.activeLeaseCount)
        assertEquals((threadCount * iterationsPerThread).toLong(), pool.acquiredCount)
        assertEquals((threadCount * iterationsPerThread).toLong(), pool.releasedCount)
    }

    @Test
    fun `clear resets pool queues`() {
        val p1 = pool.acquire(DirectByteBufferPool.SMALL_BUFFER_SIZE)
        val p2 = pool.acquire(DirectByteBufferPool.LARGE_BUFFER_SIZE)
        p1.close()
        p2.close()

        pool.clear()
        // Subsequent acquire allocates fresh buffer cleanly
        val p3 = pool.acquire(DirectByteBufferPool.SMALL_BUFFER_SIZE)
        assertEquals(1, pool.activeLeaseCount)
        p3.close()
    }
}
