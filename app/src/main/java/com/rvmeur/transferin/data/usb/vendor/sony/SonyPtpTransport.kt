package com.rvmeur.transferin.data.usb.vendor.sony

import android.hardware.usb.UsbEndpoint
import android.util.Log
import com.rvmeur.transferin.data.usb.core.DirectByteBufferPool
import com.rvmeur.transferin.data.usb.core.SafeUsbTransportManager
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class SonyPtpResponse(
    val responseCode: Int,
    val transactionId: Int,
    val params: List<Int> = emptyList(),
    val dataPayload: ByteArray? = null
)

/**
 * Sony PTP Transport.
 *
 * Provides thread-safe, mutex-guarded USB PTP command execution, multi-packet data reassembly,
 * and response verification.
 */
class SonyPtpTransport(
    val transportManager: SafeUsbTransportManager,
    val outEndpoint: UsbEndpoint,
    val inEndpoint: UsbEndpoint,
    val bufferPool: DirectByteBufferPool = DirectByteBufferPool.Default
) {
    companion object {
        private const val TAG = "SonyPtpTransport"
        private const val HEADER_SIZE = 12
    }

    val defragmenter = SonyPtpDefragmenter(
        transportManager = transportManager,
        inEndpoint = inEndpoint,
        bufferPool = bufferPool
    )

    private val mutex = Mutex()

    /**
     * Executes a synchronous PTP command transaction (Command -> [Data] -> Response).
     */
    @Throws(IOException::class)
    fun transact(
        operationCode: Int,
        transactionId: Int,
        params: IntArray = intArrayOf(),
        timeoutMs: Int = SonyPtpConstants.DEFAULT_TIMEOUT_MS
    ): SonyPtpResponse {
        sendCommand(
            operationCode = operationCode,
            transactionId = transactionId,
            params = params,
            timeoutMs = timeoutMs
        )

        var dataPayload: ByteArray? = null

        while (true) {
            when (val container = defragmenter.readContainer(expectedTxId = transactionId, timeoutMs = timeoutMs)) {
                is SonyPtpContainer.Data -> {
                    dataPayload = container.payload
                }

                is SonyPtpContainer.Response -> {
                    return SonyPtpResponse(
                        responseCode = container.responseCode,
                        transactionId = container.transactionId,
                        params = container.params,
                        dataPayload = dataPayload
                    )
                }
            }
        }
    }

    /**
     * Executes a synchronous PTP data-out transaction (Command -> Data Out -> Response).
     */
    @Throws(IOException::class)
    fun transactWithData(
        operationCode: Int,
        transactionId: Int,
        params: IntArray = intArrayOf(),
        data: ByteArray,
        timeoutMs: Int = SonyPtpConstants.DEFAULT_TIMEOUT_MS
    ): SonyPtpResponse {
        sendCommand(
            operationCode = operationCode,
            transactionId = transactionId,
            params = params,
            timeoutMs = timeoutMs
        )

        sendData(
            operationCode = operationCode,
            transactionId = transactionId,
            data = data,
            timeoutMs = timeoutMs
        )

        while (true) {
            when (val container = defragmenter.readContainer(expectedTxId = transactionId, timeoutMs = timeoutMs)) {
                is SonyPtpContainer.Response -> {
                    return SonyPtpResponse(
                        responseCode = container.responseCode,
                        transactionId = container.transactionId,
                        params = container.params,
                        dataPayload = null
                    )
                }

                is SonyPtpContainer.Data -> {
                    Log.w(TAG, "Unexpected data container received during transactWithData")
                }
            }
        }
    }

    /**
     * Thread-safe coroutine wrapper for transact().
     */
    suspend fun transactAsync(
        operationCode: Int,
        transactionId: Int,
        params: IntArray = intArrayOf(),
        timeoutMs: Int = SonyPtpConstants.DEFAULT_TIMEOUT_MS
    ): SonyPtpResponse = mutex.withLock {
        transact(operationCode, transactionId, params, timeoutMs)
    }

    /**
     * Thread-safe coroutine wrapper for transactWithData().
     */
    suspend fun transactWithDataAsync(
        operationCode: Int,
        transactionId: Int,
        params: IntArray = intArrayOf(),
        data: ByteArray,
        timeoutMs: Int = SonyPtpConstants.DEFAULT_TIMEOUT_MS
    ): SonyPtpResponse = mutex.withLock {
        transactWithData(operationCode, transactionId, params, data, timeoutMs)
    }

    private fun sendCommand(
        operationCode: Int,
        transactionId: Int,
        params: IntArray,
        timeoutMs: Int
    ) {
        val packetLength = HEADER_SIZE + params.size * 4
        val packet = ByteBuffer.allocate(packetLength)
            .order(ByteOrder.LITTLE_ENDIAN)
            .apply {
                putInt(packetLength)
                putShort(SonyPtpConstants.CONTAINER_COMMAND.toShort())
                putShort(operationCode.toShort())
                putInt(transactionId)
                params.forEach { putInt(it) }
            }
            .array()

        val sent = transportManager.safeBulkTransfer(outEndpoint, packet, 0, packet.size, timeoutMs)
        if (sent != packet.size) {
            throw IOException("Failed to send PTP command (sent $sent / ${packet.size} bytes, op=0x${operationCode.toString(16)})")
        }
    }

    private fun sendData(
        operationCode: Int,
        transactionId: Int,
        data: ByteArray,
        timeoutMs: Int
    ) {
        val totalLength = HEADER_SIZE + data.size
        val headerPacket = ByteBuffer.allocate(HEADER_SIZE)
            .order(ByteOrder.LITTLE_ENDIAN)
            .apply {
                putInt(totalLength)
                putShort(SonyPtpConstants.CONTAINER_DATA.toShort())
                putShort(operationCode.toShort())
                putInt(transactionId)
            }
            .array()

        // Combine header + data or send sequentially
        val fullPacket = ByteArray(totalLength)
        System.arraycopy(headerPacket, 0, fullPacket, 0, HEADER_SIZE)
        System.arraycopy(data, 0, fullPacket, HEADER_SIZE, data.size)

        var sentTotal = 0
        while (sentTotal < totalLength) {
            val toSend = minOf(32 * 1024, totalLength - sentTotal)
            val sent = transportManager.safeBulkTransfer(
                outEndpoint,
                fullPacket,
                sentTotal,
                toSend,
                timeoutMs
            )
            if (sent <= 0) {
                throw IOException("Failed to send PTP data payload: sent $sentTotal / $totalLength bytes")
            }
            sentTotal += sent
        }
    }
}
