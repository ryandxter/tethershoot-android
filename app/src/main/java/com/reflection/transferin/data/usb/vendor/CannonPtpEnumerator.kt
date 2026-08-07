package com.reflection.transferin.data.usb.vendor

import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class CanonPtpObjectInfo(
    val storageId: Int,
    val objectFormat: Int,
    val compressedSize: Long,
    val thumbFormat: Int,
    val thumbCompressedSize: Long,
    val thumbPixWidth: Int,
    val thumbPixHeight: Int,
    val imagePixWidth: Int,
    val imagePixHeight: Int,
    val imageBitDepth: Int,
    val parentObject: Int,
    val associationType: Int,
    val associationDesc: Int,
    val sequenceNumber: Int,
    val fileName: String,
    val captureDateEpochMillis: Long?,
    val modificationDateEpochMillis: Long?,
    val keywords: String
)

class CanonPtpEnumerator(
    private val session: CanonPtpSession
) {

    companion object {
        private const val TAG = "CanonPtpEnumerator"
        private const val FORMAT_ALL = 0
        private const val ROOT_PARENT = -1

        private val PTP_DATE_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
    }

    private val transport = CanonPtpTransport(
        connection = session.connection,
        outEndpoint = session.outEndpoint,
        inEndpoint = session.inEndpoint
    )

    private var nextTxId: Int = session.nextTransactionId

    fun getStorageIds(): List<Int> {
        val response = transport.transact(
            operationCode = CanonPtpTransport.OC_GET_STORAGE_IDS,
            transactionId = nextTransactionId()
        )
        require(response.responseCode == CanonPtpTransport.RC_OK) {
            "GetStorageIds failed: code=0x${response.responseCode.toString(16)}"
        }
        return decodeUInt32Array(response.dataPayload)
    }

    fun getObjectHandles(storageId: Int): List<Int> {
        val response = transport.transact(
            operationCode = CanonPtpTransport.OC_GET_OBJECT_HANDLES,
            transactionId = nextTransactionId(),
            params = intArrayOf(storageId, FORMAT_ALL, ROOT_PARENT)
        )
        require(response.responseCode == CanonPtpTransport.RC_OK) {
            "GetObjectHandles failed: code=0x${response.responseCode.toString(16)}"
        }

        val handles = decodeUInt32Array(response.dataPayload)
        Log.d(TAG, "Native Canon getObjectHandles(storageId=$storageId): count=${handles.size}")
        return handles
    }

    fun getObjectInfoRaw(handle: Int): ByteArray? {
        val response = transport.transact(
            operationCode = CanonPtpTransport.OC_GET_OBJECT_INFO,
            transactionId = nextTransactionId(),
            params = intArrayOf(handle)
        )
        require(response.responseCode == CanonPtpTransport.RC_OK) {
            "GetObjectInfo failed: code=0x${response.responseCode.toString(16)}"
        }
        return response.dataPayload
    }

    fun getObjectInfo(handle: Int): CanonPtpObjectInfo? {
        val payload = getObjectInfoRaw(handle) ?: return null
        return runCatching {
            parseObjectInfo(payload)
        }.onFailure {
            Log.e(TAG, "Failed parsing Canon object info for handle=$handle", it)
        }.getOrNull()
    }

    private fun parseObjectInfo(payload: ByteArray): CanonPtpObjectInfo? {
        if (payload.size < 52) return null

        val bb = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)

        val storageId = bb.int
        val objectFormat = bb.short.toInt() and 0xFFFF
        bb.short
        val compressedSize = bb.int.toLong() and 0xFFFFFFFFL
        val thumbFormat = bb.short.toInt() and 0xFFFF
        val thumbCompressedSize = bb.int.toLong() and 0xFFFFFFFFL
        val thumbPixWidth = bb.int
        val thumbPixHeight = bb.int
        val imagePixWidth = bb.int
        val imagePixHeight = bb.int
        val imageBitDepth = bb.int
        val parentObject = bb.int
        val associationType = bb.short.toInt() and 0xFFFF
        val associationDesc = bb.int
        val sequenceNumber = bb.int

        val fileName = readPtpString(bb)
        val captureDate = readPtpString(bb)
        val modificationDate = readPtpString(bb)
        val keywords = readPtpString(bb)

        return CanonPtpObjectInfo(
            storageId = storageId,
            objectFormat = objectFormat,
            compressedSize = compressedSize,
            thumbFormat = thumbFormat,
            thumbCompressedSize = thumbCompressedSize,
            thumbPixWidth = thumbPixWidth,
            thumbPixHeight = thumbPixHeight,
            imagePixWidth = imagePixWidth,
            imagePixHeight = imagePixHeight,
            imageBitDepth = imageBitDepth,
            parentObject = parentObject,
            associationType = associationType,
            associationDesc = associationDesc,
            sequenceNumber = sequenceNumber,
            fileName = fileName,
            captureDateEpochMillis = parsePtpDate(captureDate),
            modificationDateEpochMillis = parsePtpDate(modificationDate),
            keywords = keywords
        )
    }

    private fun readPtpString(bb: ByteBuffer): String {
        if (!bb.hasRemaining()) return ""
        val charCount = bb.get().toInt() and 0xFF
        if (charCount == 0) return ""

        val actualChars = (charCount - 1).coerceAtLeast(0)
        val chars = CharArray(actualChars)

        for (i in 0 until actualChars) {
            if (bb.remaining() < 2) return chars.concatToString(0, i)
            chars[i] = bb.short.toInt().toChar()
        }

        if (bb.remaining() >= 2) {
            bb.short
        }

        return chars.concatToString().trimEnd('\u0000')
    }

    private fun parsePtpDate(value: String): Long? {
        if (value.isBlank()) return null
        return runCatching {
            val cleaned = value.substringBefore('.').trim()
            val localDateTime = LocalDateTime.parse(cleaned, PTP_DATE_FORMATTER)
            localDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }.getOrNull()
    }

    private fun nextTransactionId(): Int = nextTxId++

    private fun decodeUInt32Array(payload: ByteArray?): List<Int> {
        if (payload == null || payload.size < 4) return emptyList()

        val bb = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)
        val count = bb.int
        if (count <= 0) return emptyList()

        val result = ArrayList<Int>(count)
        repeat(count) {
            if (bb.remaining() >= 4) {
                result += bb.int
            }
        }
        return result
    }
}