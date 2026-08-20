package com.rvmeur.transferin.data.usb.core

import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.ArrayDeque
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Priority levels for arbitrating half-duplex USB transport access.
 */
enum class UsbTransferPriority {
    /**
     * High priority: embedded preview extraction (<50ms), shutter event polling, and control transfers.
     */
    TIER_1_PREVIEW,

    /**
     * Standard background priority: chunked 64KB/128KB RAW or transit file uploads.
     */
    TIER_2_STREAMING
}

/**
 * Coroutine-safe priority mutex providing two-tier priority arbitration over the USB endpoint.
 *
 * Guarantees that pending [UsbTransferPriority.TIER_1_PREVIEW] requests immediately preempt
 * waiting [UsbTransferPriority.TIER_2_STREAMING] requests upon lock release, while maintaining
 * strict FIFO fairness within each priority tier and preventing lock leakage on cancellation.
 */
class UsbPriorityMutex {
    private val mutexLock = ReentrantLock()
    private var isLocked = false
    private var isClosed = false

    private val tier1Queue = ArrayDeque<CancellableContinuation<Unit>>()
    private val tier2Queue = ArrayDeque<CancellableContinuation<Unit>>()

    val holdsLock: Boolean
        get() = mutexLock.withLock { isLocked }

    val isQueueEmpty: Boolean
        get() = mutexLock.withLock { tier1Queue.isEmpty() && tier2Queue.isEmpty() }

    val tier1WaitersCount: Int
        get() = mutexLock.withLock { tier1Queue.size }

    val tier2WaitersCount: Int
        get() = mutexLock.withLock { tier2Queue.size }

    /**
     * Attempts to acquire the lock immediately without suspending.
     */
    fun tryLock(priority: UsbTransferPriority = UsbTransferPriority.TIER_2_STREAMING): Boolean {
        mutexLock.withLock {
            if (isClosed) return false
            if (!isLocked && (priority == UsbTransferPriority.TIER_1_PREVIEW || tier1Queue.isEmpty())) {
                isLocked = true
                return true
            }
            return false
        }
    }

    /**
     * Acquires the priority lock, suspending if necessary.
     */
    suspend fun lock(priority: UsbTransferPriority = UsbTransferPriority.TIER_2_STREAMING) {
        suspendCancellableCoroutine<Unit> { cont ->
            mutexLock.withLock {
                if (isClosed) {
                    cont.resumeWithException(UsbTransportClosedException("USB Priority Mutex is closed"))
                    return@withLock
                }

                if (!isLocked && (priority == UsbTransferPriority.TIER_1_PREVIEW || tier1Queue.isEmpty())) {
                    isLocked = true
                    cont.resume(Unit)
                } else {
                    val targetQueue = if (priority == UsbTransferPriority.TIER_1_PREVIEW) tier1Queue else tier2Queue
                    targetQueue.addLast(cont)

                    cont.invokeOnCancellation {
                        mutexLock.withLock {
                            val removed = targetQueue.remove(cont)
                            if (!removed && isLocked && !isClosed) {
                                unlockInternal()
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Releases the priority lock and resumes the next highest priority waiter.
     */
    fun unlock() {
        mutexLock.withLock {
            if (isClosed) {
                isLocked = false
                return
            }
            if (!isLocked) {
                return
            }
            unlockInternal()
        }
    }

    private fun unlockInternal() {
        if (isClosed) {
            isLocked = false
            return
        }

        // Resume next Tier 1 waiter if available
        while (tier1Queue.isNotEmpty()) {
            val cont = tier1Queue.removeFirst()
            if (cont.isActive) {
                isLocked = true
                cont.resume(Unit)
                return
            }
        }

        // Otherwise resume next Tier 2 waiter
        while (tier2Queue.isNotEmpty()) {
            val cont = tier2Queue.removeFirst()
            if (cont.isActive) {
                isLocked = true
                cont.resume(Unit)
                return
            }
        }

        isLocked = false
    }

    /**
     * Closes the mutex and cancels all waiting coroutines with [UsbTransportClosedException].
     */
    fun close() {
        val waitersToCancel = mutableListOf<CancellableContinuation<Unit>>()
        mutexLock.withLock {
            if (isClosed) return
            isClosed = true
            isLocked = false
            waitersToCancel.addAll(tier1Queue)
            waitersToCancel.addAll(tier2Queue)
            tier1Queue.clear()
            tier2Queue.clear()
        }
        for (w in waitersToCancel) {
            if (w.isActive) {
                w.resumeWithException(UsbTransportClosedException("USB Priority Mutex has been closed"))
            }
        }
    }

    /**
     * Executes the given [action] under the priority lock.
     */
    suspend inline fun <T> withLock(
        priority: UsbTransferPriority = UsbTransferPriority.TIER_2_STREAMING,
        crossinline action: suspend () -> T
    ): T {
        lock(priority)
        try {
            return action()
        } finally {
            withContext(NonCancellable) {
                unlock()
            }
        }
    }
}
