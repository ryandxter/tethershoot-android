package com.rvmeur.transferin.data.usb.vendor

import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import java.nio.ByteBuffer
import java.nio.ByteOrder

class CanonPtpTransport(
    private val connection: UsbDeviceConnection,
    private val outEndpoint: UsbEndpoint,
    private val inEndpoint: UsbEndpoint,
    private val timeoutMs: Int = 5000
) {

    companion object {
        const val CONTAINER_COMMAND = 1
        const val CONTAINER_DATA = 2
        const val CONTAINER_RESPONSE = 3

        const val OC_GET_STORAGE_IDS = 0x1004
        const val OC_GET_OBJECT_HANDLES = 0x1007
        const val OC_GET_OBJECT_INFO = 0x1008

        const val RC_OK = 0x2001
    }

    data class PtpResponse(
        val responseCode: Int,
        val transactionId: Int,
        val params: List<Int>,
        val dataPayload: ByteArray?
    )

    fun transact(
        operationCode: Int,
        transactionId: Int,
        params: IntArray = intArrayOf()
    ): PtpResponse {
        sendCommand(
            operationCode = operationCode,
            transactionId = transactionId,
            params = params
        )

        var dataPayload: ByteArray? = null

        while (true) {
            val packet = readContainer()
            val bb = ByteBuffer.wrap(packet).order(ByteOrder.LITTLE_ENDIAN)

            val length = bb.int
            val type = bb.short.toInt() and 0xFFFF
            val code = bb.short.toInt() and 0xFFFF
            val tx = bb.int

            require(tx == transactionId) {
                "PTP transaction mismatch: expected=$transactionId actual=$tx"
            }

            when (type) {
                CONTAINER_DATA -> {
                    val payloadLength = (length - 12).coerceAtLeast(0)
                    val payload = ByteArray(payloadLength)
                    if (payloadLength > 0) {
                        bb.get(payload, 0, minOf(payloadLength, bb.remaining()))
                    }
                    dataPayload = payload
                }

                CONTAINER_RESPONSE -> {
                    val paramsOut = mutableListOf<Int>()
                    while (bb.remaining() >= 4) {
                        paramsOut += bb.int
                    }
                    return PtpResponse(
                        responseCode = code,
                        transactionId = tx,
                        params = paramsOut,
                        dataPayload = dataPayload
                    )
                }

                else -> error("Unexpected PTP container type=$type code=0x${code.toString(16)}")
            }
        }
    }

    private fun sendCommand(
        operationCode: Int,
        transactionId: Int,
        params: IntArray
    ) {
        val packetLength = 12 + params.size * 4
        val packet = ByteBuffer.allocate(packetLength)
            .order(ByteOrder.LITTLE_ENDIAN)
            .apply {
                putInt(packetLength)
                putShort(CONTAINER_COMMAND.toShort())
                putShort(operationCode.toShort())
                putInt(transactionId)
                params.forEach { putInt(it) }
            }
            .array()

        val sent = connection.bulkTransfer(outEndpoint, packet, packet.size, timeoutMs)
        require(sent == packet.size) {
            "PTP bulk OUT failed: sent=$sent expected=${packet.size}"
        }
    }

    private fun readContainer(): ByteArray {
        val buffer = ByteArray(32 * 1024)
        val received = connection.bulkTransfer(inEndpoint, buffer, buffer.size, timeoutMs)
        require(received >= 12) {
            "PTP bulk IN invalid: received=$received"
        }
        return buffer.copyOf(received)
    }
}