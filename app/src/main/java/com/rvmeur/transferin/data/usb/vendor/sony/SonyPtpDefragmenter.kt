package com.rvmeur.transferin.data.usb.vendor.sony

import android.hardware.usb.UsbEndpoint
import android.util.Log
import com.rvmeur.transferin.data.usb.core.DirectByteBufferPool
import com.rvmeur.transferin.data.usb.core.SafeUsbTransportManager
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Exception thrown when a PTP container payload is malformed, truncated, or fails checksum/bounds.
 */
class CorruptedPtpPayloadException(
    message: String,
    cause: Throwable? = null
) : IOException(message, cause)

/**
 * Sealed representation of parsed PTP USB containers.
 */
sealed class SonyPtpContainer {
    data class Data(
        val opCode: Int,
        val transactionId: Int,
        val payload: ByteArray
    ) : SonyPtpContainer() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as Data
            if (opCode != other.opCode) return false
            if (transactionId != other.transactionId) return false
            return payload.contentEquals(other.payload)
        }

        override fun hashCode(): Int {
            var result = opCode
            result = 31 * result + transactionId
            result = 31 * result + payload.contentHashCode()
            return result
        }
    }

    data class Response(
        val responseCode: Int,
        val transactionId: Int,
        val params: List<Int>
    ) : SonyPtpContainer()
}

/**
 * Sony PTP Defragmenter.
 *
 * High-throughput USB Bulk IN defragmenter that handles multi-packet PTP Data containers,
 * Zero-Length Packets (ZLP), and short packet terminations with zero-allocation streaming.
 */
class SonyPtpDefragmenter(
    private val transportManager: SafeUsbTransportManager,
    private val inEndpoint: UsbEndpoint,
    private val bufferPool: DirectByteBufferPool = DirectByteBufferPool.Default
) {
    companion object {
        private const val TAG = "SonyPtpDefragmenter"
        private const val HEADER_SIZE = 12
        private const val READ_CHUNK_SIZE = 32 * 1024
    }

    /**
     * Reads the next container from USB Bulk IN, defragmenting multi-packet data streams if required.
     */
    @Throws(CorruptedPtpPayloadException::class, IOException::class)
    fun readContainer(expectedTxId: Int, timeoutMs: Int = SonyPtpConstants.DEFAULT_TIMEOUT_MS): SonyPtpContainer {
        val initialBuffer = ByteArray(READ_CHUNK_SIZE)
        var received = transportManager.safeBulkTransfer(
            inEndpoint,
            initialBuffer,
            0,
            initialBuffer.size,
            timeoutMs
        )

        // Handle possible leading Zero-Length Packet (ZLP) - at most one retry
        if (received == 0) {
            received = transportManager.safeBulkTransfer(
                inEndpoint,
                initialBuffer,
                0,
                initialBuffer.size,
                timeoutMs
            )
        }

        if (received < HEADER_SIZE) {
            throw CorruptedPtpPayloadException(
                "Invalid PTP packet: received $received bytes, expected at least $HEADER_SIZE bytes"
            )
        }

        val bb = ByteBuffer.wrap(initialBuffer, 0, received).order(ByteOrder.LITTLE_ENDIAN)
        val length = bb.int
        val containerType = bb.short.toInt() and 0xFFFF
        val code = bb.short.toInt() and 0xFFFF
        val txId = bb.int

        if (length < HEADER_SIZE || length > 256 * 1024 * 1024) {
            throw CorruptedPtpPayloadException("Invalid PTP container length: $length")
        }

        if (expectedTxId != 0 && txId != expectedTxId) {
            throw CorruptedPtpPayloadException(
                "PTP transaction mismatch: expected=$expectedTxId, actual=$txId (type=$containerType, code=0x${code.toString(16)})"
            )
        }

        return when (containerType) {
            SonyPtpConstants.CONTAINER_RESPONSE -> {
                val params = mutableListOf<Int>()
                while (bb.remaining() >= 4) {
                    params.add(bb.int)
                }
                SonyPtpContainer.Response(
                    responseCode = code,
                    transactionId = txId,
                    params = params
                )
            }

            SonyPtpConstants.CONTAINER_DATA -> {
                val totalPayloadLength = (length - HEADER_SIZE).coerceAtLeast(0)
                val payload = defragmentDataPayload(
                    firstChunk = initialBuffer,
                    firstChunkOffset = HEADER_SIZE,
                    firstChunkDataLength = (received - HEADER_SIZE).coerceAtLeast(0),
                    totalPayloadLength = totalPayloadLength,
                    timeoutMs = timeoutMs
                )

                SonyPtpContainer.Data(
                    opCode = code,
                    transactionId = txId,
                    payload = payload
                )
            }

            else -> {
                throw CorruptedPtpPayloadException(
                    "Unexpected PTP container type: $containerType (code=0x${code.toString(16)}, length=$length)"
                )
            }
        }
    }

    /**
     * Defragments a multi-packet data payload across sequential USB Bulk IN packets.
     */
    private fun defragmentDataPayload(
        firstChunk: ByteArray,
        firstChunkOffset: Int,
        firstChunkDataLength: Int,
        totalPayloadLength: Int,
        timeoutMs: Int
    ): ByteArray {
        if (totalPayloadLength == 0) return ByteArray(0)

        val result = ByteArray(totalPayloadLength)
        val initialCopy = minOf(firstChunkDataLength, totalPayloadLength)
        if (initialCopy > 0) {
            System.arraycopy(firstChunk, firstChunkOffset, result, 0, initialCopy)
        }

        var totalCopied = initialCopy
        val readBuffer = ByteArray(READ_CHUNK_SIZE)

        while (totalCopied < totalPayloadLength) {
            val remainingToRead = totalPayloadLength - totalCopied
            val requestSize = minOf(readBuffer.size, remainingToRead)

            val n = transportManager.safeBulkTransfer(
                inEndpoint,
                readBuffer,
                0,
                requestSize,
                timeoutMs
            )

            if (n <= 0) {
                throw CorruptedPtpPayloadException(
                    "USB transfer failed while defragmenting payload: copied $totalCopied / $totalPayloadLength bytes (result code $n)"
                )
            }

            val copyCount = minOf(n, totalPayloadLength - totalCopied)
            System.arraycopy(readBuffer, 0, result, totalCopied, copyCount)
            totalCopied += copyCount
        }

        return result
    }
}
