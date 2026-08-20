package com.rvmeur.transferin.data.usb.core

import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.os.Build
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import java.io.IOException
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Exception thrown when a transfer is attempted on a closed or draining USB transport.
 */
class UsbTransportClosedException(
    message: String = "USB transport is closed or terminated"
) : IOException(message)

/**
 * Thread-safe transport manager that serializes and guards native [UsbDeviceConnection]
 * operations against native kernel page faults (SIGSEGV in libusbhost.so) during cable disconnects.
 */
class SafeUsbTransportManager(
    val connection: UsbDeviceConnection
) {

    private val _activeIoCount = AtomicInteger(0)
    private val _isTerminated = AtomicBoolean(false)
    val ioMutex = Mutex()
    val priorityMutex = UsbPriorityMutex()

    val activeIoCount: Int get() = _activeIoCount.get()
    val isTerminated: Boolean get() = _isTerminated.get()

    /**
     * Executes a guarded bulk transfer on a [ByteArray] buffer.
     */
    @Throws(UsbTransportClosedException::class)
    fun safeBulkTransfer(
        endpoint: UsbEndpoint,
        buffer: ByteArray,
        offset: Int,
        length: Int,
        timeoutMs: Int
    ): Int {
        ensureActive()
        _activeIoCount.incrementAndGet()
        try {
            ensureActive()
            return connection.bulkTransfer(endpoint, buffer, offset, length, timeoutMs)
        } finally {
            _activeIoCount.decrementAndGet()
        }
    }

    /**
     * Executes a guarded bulk transfer on a [ByteArray] buffer (zero offset).
     */
    @Throws(UsbTransportClosedException::class)
    fun safeBulkTransfer(
        endpoint: UsbEndpoint,
        buffer: ByteArray,
        length: Int,
        timeoutMs: Int
    ): Int {
        return safeBulkTransfer(endpoint, buffer, 0, length, timeoutMs)
    }

    /**
     * Executes a guarded bulk transfer using a direct [ByteBuffer].
     * Compatible with API 28+ direct buffer transfer, with fallback for earlier APIs.
     */
    @Throws(UsbTransportClosedException::class)
    fun safeBulkTransfer(
        endpoint: UsbEndpoint,
        byteBuffer: ByteBuffer,
        length: Int,
        timeoutMs: Int
    ): Int {
        ensureActive()
        _activeIoCount.incrementAndGet()
        try {
            ensureActive()
            val temp = ByteArray(length)
            val isOut = endpoint.direction == android.hardware.usb.UsbConstants.USB_DIR_OUT
            if (isOut && byteBuffer.remaining() >= length) {
                val initialPos = byteBuffer.position()
                byteBuffer.get(temp, 0, length)
                byteBuffer.position(initialPos)
            }
            val transferred = connection.bulkTransfer(endpoint, temp, 0, length, timeoutMs)
            if (transferred > 0 && !isOut) {
                byteBuffer.put(temp, 0, transferred)
            }
            return transferred
        } finally {
            _activeIoCount.decrementAndGet()
        }
    }

    /**
     * Executes a guarded bulk transfer using a [PooledByteBuffer].
     */
    @Throws(UsbTransportClosedException::class)
    fun safeBulkTransfer(
        endpoint: UsbEndpoint,
        pooledBuffer: PooledByteBuffer,
        length: Int,
        timeoutMs: Int
    ): Int {
        return safeBulkTransfer(endpoint, pooledBuffer.byteBuffer, length, timeoutMs)
    }

    /**
     * Executes a guarded control transfer.
     */
    @Throws(UsbTransportClosedException::class)
    fun safeControlTransfer(
        requestType: Int,
        request: Int,
        value: Int,
        index: Int,
        buffer: ByteArray?,
        offset: Int,
        length: Int,
        timeoutMs: Int
    ): Int {
        ensureActive()
        _activeIoCount.incrementAndGet()
        try {
            ensureActive()
            return connection.controlTransfer(requestType, request, value, index, buffer, offset, length, timeoutMs)
        } finally {
            _activeIoCount.decrementAndGet()
        }
    }

    /**
     * Executes a guarded control transfer (zero offset).
     */
    @Throws(UsbTransportClosedException::class)
    fun safeControlTransfer(
        requestType: Int,
        request: Int,
        value: Int,
        index: Int,
        buffer: ByteArray?,
        length: Int,
        timeoutMs: Int
    ): Int {
        return safeControlTransfer(requestType, request, value, index, buffer, 0, length, timeoutMs)
    }

    /**
     * Executes [action] protected by the two-tier priority mutex.
     */
    suspend fun <T> withPriorityLock(
        priority: UsbTransferPriority = UsbTransferPriority.TIER_2_STREAMING,
        action: suspend () -> T
    ): T {
        return priorityMutex.withLock(priority) {
            action()
        }
    }

    /**
     * Executes [action] with Tier 1 (High) priority for embedded preview extraction.
     */
    suspend fun <T> withTier1PreviewLock(action: suspend () -> T): T {
        return withPriorityLock(UsbTransferPriority.TIER_1_PREVIEW, action)
    }

    /**
     * Executes [action] with Tier 2 (Standard) priority for streaming/transit data transfers.
     */
    suspend fun <T> withTier2StreamingLock(action: suspend () -> T): T {
        return withPriorityLock(UsbTransferPriority.TIER_2_STREAMING, action)
    }

    /**
     * Initiates non-cancellable drainage of all active USB IO transfers.
     * Subsequent transfers will immediately fail with [UsbTransportClosedException].
     *
     * @param timeoutMs Maximum duration to await in-flight transfers to complete.
     * @return true if all in-flight IO drained to 0, false if timed out.
     */
    suspend fun beginDrainAndClose(timeoutMs: Long = 1000L): Boolean {
        _isTerminated.set(true)
        priorityMutex.close()
        val startTime = System.currentTimeMillis()
        while (_activeIoCount.get() > 0) {
            if (System.currentTimeMillis() - startTime >= timeoutMs) {
                return _activeIoCount.get() == 0
            }
            delay(5)
        }
        return true
    }

    private fun ensureActive() {
        if (_isTerminated.get()) {
            throw UsbTransportClosedException("Transport has been terminated")
        }
        if (connection.fileDescriptor == -1) {
            _isTerminated.set(true)
            throw UsbTransportClosedException("Underlying UsbDeviceConnection file descriptor is invalid (fd=-1)")
        }
    }
}
