package com.rvmeur.transferin.data.usb.core

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

class SafeUsbPriorityMutexTest {

    @Test
    fun `tier 1 priority requests preempt waiting tier 2 requests upon lock release`() = runBlocking {
        val mutex = UsbPriorityMutex()
        val executionOrder = ConcurrentLinkedQueue<String>()

        val holdLatch = CompletableDeferred<Unit>()
        val tier2QueuedLatch = CompletableDeferred<Unit>()

        // 1. Initial lock held by Tier 2
        val initialJob = launch(Dispatchers.Default) {
            mutex.withLock(UsbTransferPriority.TIER_2_STREAMING) {
                executionOrder.add("INITIAL_LOCK_ACQUIRED")
                holdLatch.await()
            }
        }

        // Wait for initial lock to be acquired
        while (!mutex.holdsLock) delay(5)

        // 2. Queue multiple Tier 2 requests
        val tier2Job1 = launch(Dispatchers.Default) {
            mutex.withLock(UsbTransferPriority.TIER_2_STREAMING) {
                executionOrder.add("TIER_2_REQUEST_1")
            }
        }
        val tier2Job2 = launch(Dispatchers.Default) {
            mutex.withLock(UsbTransferPriority.TIER_2_STREAMING) {
                executionOrder.add("TIER_2_REQUEST_2")
            }
        }

        // Wait until Tier 2 waiters are in queue
        while (mutex.tier2WaitersCount < 2) delay(5)

        // 3. Queue high priority Tier 1 request
        val tier1Job = launch(Dispatchers.Default) {
            mutex.withLock(UsbTransferPriority.TIER_1_PREVIEW) {
                executionOrder.add("TIER_1_PREVIEW_HIGH_PRIORITY")
            }
        }

        while (mutex.tier1WaitersCount < 1) delay(5)

        // 4. Release initial lock
        holdLatch.complete(Unit)

        // Wait for all to complete
        listOf(initialJob, tier1Job, tier2Job1, tier2Job2).joinAll()

        val results = executionOrder.toList()
        assertEquals("INITIAL_LOCK_ACQUIRED", results[0])
        assertEquals("TIER_1_PREVIEW_HIGH_PRIORITY", results[1])
        assertTrue("Tier 2 requests must run after Tier 1", results[2].startsWith("TIER_2"))
        assertTrue("Tier 2 requests must run after Tier 1", results[3].startsWith("TIER_2"))
    }

    @Test
    fun `FIFO ordering is preserved within each priority tier`() = runBlocking {
        val mutex = UsbPriorityMutex()
        val results = ConcurrentLinkedQueue<String>()
        val holdLatch = CompletableDeferred<Unit>()

        launch(Dispatchers.Default) {
            mutex.withLock(UsbTransferPriority.TIER_1_PREVIEW) {
                holdLatch.await()
            }
        }
        while (!mutex.holdsLock) delay(5)

        val t1A = launch(Dispatchers.Default) {
            mutex.withLock(UsbTransferPriority.TIER_1_PREVIEW) { results.add("T1_A") }
        }
        delay(10)
        val t1B = launch(Dispatchers.Default) {
            mutex.withLock(UsbTransferPriority.TIER_1_PREVIEW) { results.add("T1_B") }
        }
        delay(10)
        val t1C = launch(Dispatchers.Default) {
            mutex.withLock(UsbTransferPriority.TIER_1_PREVIEW) { results.add("T1_C") }
        }

        while (mutex.tier1WaitersCount < 3) delay(5)
        holdLatch.complete(Unit)

        listOf(t1A, t1B, t1C).joinAll()
        assertEquals(listOf("T1_A", "T1_B", "T1_C"), results.toList())
    }

    @Test
    fun `cancelled waiting coroutine cleans up from queue without leaking lock or deadlocking next waiters`() = runBlocking {
        val mutex = UsbPriorityMutex()
        val holdLatch = CompletableDeferred<Unit>()
        val executedNext = CompletableDeferred<Boolean>()

        launch(Dispatchers.Default) {
            mutex.withLock(UsbTransferPriority.TIER_2_STREAMING) {
                holdLatch.await()
            }
        }
        while (!mutex.holdsLock) delay(5)

        val waitingJob = launch(Dispatchers.Default) {
            mutex.withLock(UsbTransferPriority.TIER_1_PREVIEW) {
                fail("Should not execute because cancelled")
            }
        }
        while (mutex.tier1WaitersCount < 1) delay(5)

        // Cancel the waiting job
        waitingJob.cancelAndJoin()

        // Launch a new waiter
        val nextJob = launch(Dispatchers.Default) {
            mutex.withLock(UsbTransferPriority.TIER_1_PREVIEW) {
                executedNext.complete(true)
            }
        }

        // Release the initial lock
        holdLatch.complete(Unit)

        assertTrue("Next waiter must acquire lock successfully after cancellation", executedNext.await())
        nextJob.join()
        assertFalse("Mutex must not remain locked", mutex.holdsLock)
    }

    @Test
    fun `close aborts all waiting coroutines with UsbTransportClosedException`() = runBlocking {
        val mutex = UsbPriorityMutex()
        val holdLatch = CompletableDeferred<Unit>()
        val exceptionCount = AtomicInteger(0)

        launch(Dispatchers.Default) {
            mutex.withLock(UsbTransferPriority.TIER_1_PREVIEW) {
                holdLatch.await()
            }
        }
        while (!mutex.holdsLock) delay(5)

        val waiters = (1..5).map { i ->
            launch(Dispatchers.Default) {
                try {
                    val prio = if (i % 2 == 0) UsbTransferPriority.TIER_1_PREVIEW else UsbTransferPriority.TIER_2_STREAMING
                    mutex.withLock(prio) {
                        fail("Should have thrown closed exception")
                    }
                } catch (e: UsbTransportClosedException) {
                    exceptionCount.incrementAndGet()
                }
            }
        }

        while (mutex.tier1WaitersCount + mutex.tier2WaitersCount < 5) delay(5)

        mutex.close()
        holdLatch.complete(Unit)

        waiters.joinAll()
        assertEquals(5, exceptionCount.get())
    }

    @Test
    fun `stress test with 100 concurrent coroutines confirms zero deadlocks`() = runBlocking {
        val mutex = UsbPriorityMutex()
        val counter = AtomicInteger(0)
        val jobs = mutableListOf<Job>()

        repeat(100) { i ->
            val prio = if (i % 3 == 0) UsbTransferPriority.TIER_1_PREVIEW else UsbTransferPriority.TIER_2_STREAMING
            jobs.add(
                launch(Dispatchers.Default) {
                    mutex.withLock(prio) {
                        val current = counter.incrementAndGet()
                        delay(1)
                    }
                }
            )
        }

        jobs.joinAll()
        assertEquals(100, counter.get())
        assertFalse(mutex.holdsLock)
    }
}
