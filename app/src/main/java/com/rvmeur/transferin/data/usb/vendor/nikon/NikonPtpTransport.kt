package com.rvmeur.transferin.data.usb.vendor.nikon

import android.hardware.usb.UsbEndpoint
import android.util.Log
import com.rvmeur.transferin.data.usb.core.DirectByteBufferPool
import com.rvmeur.transferin.data.usb.core.SafeUsbTransportManager
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class NikonPtpResponse(
    val responseCode: Int,
    val transactionId: Int,
    val params: List<Int> = emptyList(),
    val dataPayload: ByteArray? = null
)

data class NikonPtpEvent(
    val eventCode: Int,
    val transactionId: Int,
    val param1: Int = 0,
    val param2: Int = 0,
    val param3: Int = 0
)

/**
 * Nikon PTP Transport.
 *
 * Implements low-level USB command dispatch, data container streaming, and event polling for Nikon cameras.
 */
class NikonPtpTransport(
    val transportManager: SafeUsbTransportManager,
    val outEndpoint: UsbEndpoint,
    val inEndpoint: UsbEndpoint,
    val bufferPool: DirectByteBufferPool = DirectByteBufferPool.Default
) {
    companion object {
        private const val TAG = "NikonPtpTransport"
        private const val HEADER_SIZE = 12
        private const val READ_CHUNK_SIZE = 32 * 1024
    }

    private val mutex = Mutex()

    /**
     * Sends a command and waits for response/data.
     * Throws [NikonDeviceBusyException] if camera returns 0x2019 DeviceBusy.
     */
    @Throws(IOException::class, NikonDeviceBusyException::class)
    fun transact(
        operationCode: Int,
        transactionId: Int,
        params: IntArray = intArrayOf(),
        timeoutMs: Int = NikonPtpConstants.DEFAULT_TIMEOUT_MS
    ): NikonPtpResponse {
        sendCommand(operationCode, transactionId, params, timeoutMs)

        var dataPayload: ByteArray? = null

        while (true) {
            val packet = readRawPacket(timeoutMs)
            if (packet.size < HEADER_SIZE) {
                throw IOException("Invalid Nikon PTP packet: length=${packet.size}")
            }

            val bb = ByteBuffer.wrap(packet).order(ByteOrder.LITTLE_ENDIAN)
            val length = bb.int
            val type = bb.short.toInt() and 0xFFFF
            val code = bb.short.toInt() and 0xFFFF
            val txId = bb.int

            when (type) {
                NikonPtpConstants.CONTAINER_DATA -> {
                    val totalPayloadLength = (length - HEADER_SIZE).coerceAtLeast(0)
                    dataPayload = readFullPayload(
                        initialPacket = packet,
                        initialPayloadLength = (packet.size - HEADER_SIZE).coerceAtLeast(0),
                        totalPayloadLength = totalPayloadLength,
                        timeoutMs = timeoutMs
                    )
                }

                NikonPtpConstants.CONTAINER_RESPONSE -> {
                    val respParams = mutableListOf<Int>()
                    while (bb.remaining() >= 4) {
                        respParams.add(bb.int)
                    }

                    if (code == NikonPtpConstants.RC_DEVICE_BUSY) {
                        throw NikonDeviceBusyException(
                            "Nikon returned 0x2019 DeviceBusy for op=0x${operationCode.toString(16)}"
                        )
                    }

                    return NikonPtpResponse(
                        responseCode = code,
                        transactionId = txId,
                        params = respParams,
                        dataPayload = dataPayload
                    )
                }

                else -> {
                    Log.w(TAG, "Unexpected container type=$type code=0x${code.toString(16)}")
                }
            }
        }
    }

    /**
     * Polls for Nikon events using 0x90C0 CheckEvent.
     */
    @Throws(IOException::class)
    fun checkEvents(
        transactionId: Int,
        timeoutMs: Int = NikonPtpConstants.DEFAULT_TIMEOUT_MS
    ): List<NikonPtpEvent> {
        val response = transact(
            operationCode = NikonPtpConstants.OC_NIKON_CHECK_EVENT,
            transactionId = transactionId,
            params = intArrayOf(),
            timeoutMs = timeoutMs
        )

        val payload = response.dataPayload ?: return emptyList()
        return parseEventRecords(payload, transactionId)
    }

    suspend fun transactAsync(
        operationCode: Int,
        transactionId: Int,
        params: IntArray = intArrayOf(),
        timeoutMs: Int = NikonPtpConstants.DEFAULT_TIMEOUT_MS
    ): NikonPtpResponse = mutex.withLock {
        transact(operationCode, transactionId, params, timeoutMs)
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
                putShort(NikonPtpConstants.CONTAINER_COMMAND.toShort())
                putShort(operationCode.toShort())
                putInt(transactionId)
                params.forEach { putInt(it) }
            }
            .array()

        val sent = transportManager.safeBulkTransfer(outEndpoint, packet, 0, packet.size, timeoutMs)
        if (sent != packet.size) {
            throw IOException("Nikon Bulk OUT failed: sent=$sent expected=${packet.size}")
        }
    }

    private fun readRawPacket(timeoutMs: Int): ByteArray {
        val buffer = ByteArray(READ_CHUNK_SIZE)
        val received = transportManager.safeBulkTransfer(inEndpoint, buffer, 0, buffer.size, timeoutMs)
        if (received <= 0) {
            throw IOException("Nikon Bulk IN read failed: received=$received")
        }
        return buffer.copyOf(received)
    }

    private fun readFullPayload(
        initialPacket: ByteArray,
        initialPayloadLength: Int,
        totalPayloadLength: Int,
        timeoutMs: Int
    ): ByteArray {
        if (totalPayloadLength == 0) return ByteArray(0)

        val result = ByteArray(totalPayloadLength)
        val copyFirst = minOf(initialPayloadLength, totalPayloadLength)
        if (copyFirst > 0) {
            System.arraycopy(initialPacket, HEADER_SIZE, result, 0, copyFirst)
        }

        var copied = copyFirst
        val temp = ByteArray(READ_CHUNK_SIZE)

        while (copied < totalPayloadLength) {
            val toRead = minOf(temp.size, totalPayloadLength - copied)
            val n = transportManager.safeBulkTransfer(inEndpoint, temp, 0, toRead, timeoutMs)
            if (n <= 0) {
                throw IOException("Nikon payload stream truncated: copied $copied / $totalPayloadLength")
            }
            val copyCount = minOf(n, totalPayloadLength - copied)
            System.arraycopy(temp, 0, result, copied, copyCount)
            copied += copyCount
        }

        return result
    }

    private fun parseEventRecords(payload: ByteArray, defaultTxId: Int): List<NikonPtpEvent> {
        if (payload.size < 2) return emptyList()

        val bb = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)
        val events = mutableListOf<NikonPtpEvent>()

        // Nikon event payload format: Count of records (UInt16), followed by records (EventCode 2B, Param1 4B)
        val count = bb.short.toInt() and 0xFFFF

        repeat(count) {
            if (bb.remaining() >= 6) {
                val eventCode = bb.short.toInt() and 0xFFFF
                val param1 = bb.int
                events.add(
                    NikonPtpEvent(
                        eventCode = eventCode,
                        transactionId = defaultTxId,
                        param1 = param1
                    )
                )
            }
        }

        // Fallback: If count was 0 or records are raw (EventCode 2B, Param1 4B)
        if (events.isEmpty() && payload.size >= 6) {
            bb.position(0)
            while (bb.remaining() >= 6) {
                val eventCode = bb.short.toInt() and 0xFFFF
                val param1 = bb.int
                if (eventCode != 0) {
                    events.add(
                        NikonPtpEvent(
                            eventCode = eventCode,
                            transactionId = defaultTxId,
                            param1 = param1
                        )
                    )
                }
            }
        }

        return events
    }
}
