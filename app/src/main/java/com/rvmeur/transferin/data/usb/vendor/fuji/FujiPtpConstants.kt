package com.rvmeur.transferin.data.usb.vendor.fuji

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * PTP (ISO 15740) constants, Fujifilm vendor extensions, and packet framing helpers.
 */
object FujiPtpConstants {

    const val FUJI_VENDOR_ID = 0x04CB

    // Container types
    const val CONTAINER_UNDEFINED = 0
    const val CONTAINER_COMMAND = 1
    const val CONTAINER_DATA = 2
    const val CONTAINER_RESPONSE = 3
    const val CONTAINER_EVENT = 4

    // Standard PTP OpCodes (ISO 15740)
    const val OC_OPEN_SESSION = 0x1002
    const val OC_CLOSE_SESSION = 0x1003
    const val OC_GET_STORAGE_IDS = 0x1004
    const val OC_GET_STORAGE_INFO = 0x1005
    const val OC_GET_OBJECT_HANDLES = 0x1007
    const val OC_GET_OBJECT_INFO = 0x1008
    const val OC_GET_OBJECT = 0x1009
    const val OC_GET_THUMB = 0x100A
    const val OC_GET_DEVICE_PROP_DESC = 0x1014
    const val OC_GET_DEVICE_PROP_VALUE = 0x1015
    const val OC_SET_DEVICE_PROP_VALUE = 0x1016
    const val OC_GET_PARTIAL_OBJECT = 0x101B

    // Fujifilm Vendor Specific OpCodes & Properties
    const val OC_FUJI_SET_DEVICE_PROP = 0x902B
    const val OC_FUJI_GET_DEVICE_PROP = 0x902C
    const val PROP_FUJI_APP_VERSION = 0xD212
    const val PROP_FUJI_HOST_CONTROL = 0xD214
    const val PROP_FUJI_SAVE_DESTINATION = 0xD216
    const val PROP_FUJI_RECIPE_NAME = 0xD218

    // Property Values
    const val HOST_CONTROL_RELEASE = 0x0000
    const val HOST_CONTROL_ACTIVE = 0x0001
    const val SAVE_DEST_SD_ONLY = 0x0000
    const val SAVE_DEST_PC_ONLY = 0x0001
    const val SAVE_DEST_PC_SD = 0x0002

    // PTP Event Codes
    const val EVT_OBJECT_ADDED = 0x4002
    const val EVT_DEVICE_PROP_CHANGED = 0x4006
    const val EVT_FUJI_OBJECT_ADDED = 0xC001

    // PTP Response Codes
    const val RC_OK = 0x2001
    const val RC_GENERAL_ERROR = 0x2002
    const val RC_SESSION_NOT_OPEN = 0x2003
    const val RC_INVALID_TRANSACTION_ID = 0x2004
    const val RC_OPERATION_NOT_SUPPORTED = 0x2005
    const val RC_PARAMETER_NOT_SUPPORTED = 0x2006
    const val RC_DEVICE_BUSY = 0x2019

    // Format Codes
    const val FMT_UNDEFINED = 0x3000
    const val FMT_ASSOCIATION = 0x3001
    const val FMT_EXIF_JPEG = 0x3801
    const val FMT_FUJI_RAF = 0xB101

    /**
     * Builds a standard little-endian PTP command container packet.
     */
    fun buildCommandPacket(opCode: Int, txId: Int, params: IntArray = intArrayOf()): ByteArray {
        val packetLength = 12 + params.size * 4
        return ByteBuffer.allocate(packetLength)
            .order(ByteOrder.LITTLE_ENDIAN)
            .apply {
                putInt(packetLength)
                putShort(CONTAINER_COMMAND.toShort())
                putShort(opCode.toShort())
                putInt(txId)
                params.forEach { putInt(it) }
            }
            .array()
    }

    /**
     * Builds a standard little-endian PTP data container packet.
     */
    fun buildDataPacket(opCode: Int, txId: Int, payload: ByteArray): ByteArray {
        val packetLength = 12 + payload.size
        return ByteBuffer.allocate(packetLength)
            .order(ByteOrder.LITTLE_ENDIAN)
            .apply {
                putInt(packetLength)
                putShort(CONTAINER_DATA.toShort())
                putShort(opCode.toShort())
                putInt(txId)
                put(payload)
            }
            .array()
    }

    /**
     * Parses a standard PTP response container from a received byte buffer.
     */
    fun parseResponsePacket(buffer: ByteArray, length: Int): PtpResponse {
        require(length >= 12) { "PTP response packet too short: $length bytes (minimum 12)" }
        val bb = ByteBuffer.wrap(buffer, 0, length).order(ByteOrder.LITTLE_ENDIAN)
        val totalLength = bb.int
        val containerType = bb.short.toInt() and 0xFFFF
        val responseCode = bb.short.toInt() and 0xFFFF
        val txId = bb.int

        val availableParamBytes = (length.coerceAtMost(if (totalLength > 0) totalLength else length) - 12)
        val paramCount = (availableParamBytes / 4).coerceAtLeast(0)
        val params = IntArray(paramCount)
        for (i in 0 until paramCount) {
            params[i] = bb.int
        }

        return PtpResponse(
            responseCode = responseCode,
            transactionId = txId,
            params = params
        )
    }

    /**
     * Parses a standard PTP event container from an interrupt IN buffer.
     */
    fun parseEventPacket(buffer: ByteArray, length: Int): PtpEvent? {
        if (length < 12) return null
        val bb = ByteBuffer.wrap(buffer, 0, length).order(ByteOrder.LITTLE_ENDIAN)
        val totalLength = bb.int
        val containerType = bb.short.toInt() and 0xFFFF
        val eventCode = bb.short.toInt() and 0xFFFF
        val txId = bb.int

        val availableParamBytes = (length.coerceAtMost(if (totalLength > 0) totalLength else length) - 12)
        val paramCount = (availableParamBytes / 4).coerceAtLeast(0)
        val params = IntArray(paramCount)
        for (i in 0 until paramCount) {
            params[i] = bb.int
        }

        return PtpEvent(
            eventCode = eventCode,
            transactionId = txId,
            params = params
        )
    }
}

/**
 * PTP response container packet payload.
 */
data class PtpResponse(
    val responseCode: Int,
    val transactionId: Int,
    val params: IntArray = intArrayOf()
) {
    val isOk: Boolean get() = responseCode == FujiPtpConstants.RC_OK

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as PtpResponse
        return responseCode == other.responseCode &&
                transactionId == other.transactionId &&
                params.contentEquals(other.params)
    }

    override fun hashCode(): Int {
        var result = responseCode
        result = 31 * result + transactionId
        result = 31 * result + params.contentHashCode()
        return result
    }
}

/**
 * PTP event container packet payload.
 */
data class PtpEvent(
    val eventCode: Int,
    val transactionId: Int,
    val params: IntArray = intArrayOf()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as PtpEvent
        return eventCode == other.eventCode &&
                transactionId == other.transactionId &&
                params.contentEquals(other.params)
    }

    override fun hashCode(): Int {
        var result = eventCode
        result = 31 * result + transactionId
        result = 31 * result + params.contentHashCode()
        return result
    }
}
