package com.rvmeur.transferin.data.usb.vendor.nikon

import android.util.Log
import com.rvmeur.transferin.data.usb.model.EmbeddedPreviewResult
import com.rvmeur.transferin.data.usb.model.ExifOrientationHelper
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * High-speed binary parser for Nikon .NEF (TIFF-based) RAW files.
 *
 * Traverses TIFF IFD0, SubIFD, and ExifIFD to locate embedded JPEG preview byte streams
 * and EXIF Tag 0x0112 orientation metadata in sub-50ms execution time.
 */
object NikonPreviewParser {

    private const val TAG = "NikonPreviewParser"

    private const val TIFF_LITTLE_ENDIAN = 0x4949 // 'II'
    private const val TIFF_BIG_ENDIAN = 0x4D4D    // 'MM'
    private const val TIFF_MAGIC = 42

    private const val TAG_IMAGE_WIDTH = 0x0100
    private const val TAG_IMAGE_HEIGHT = 0x0101
    private const val TAG_ORIENTATION = 0x0112
    private const val TAG_STRIP_OFFSETS = 0x0111
    private const val TAG_STRIP_BYTE_COUNTS = 0x0117
    private const val TAG_SUB_IFDS = 0x014A
    private const val TAG_JPEG_INTERCHANGE_FORMAT = 0x0201
    private const val TAG_JPEG_INTERCHANGE_FORMAT_LENGTH = 0x0202
    private const val TAG_EXIF_IFD = 0x8769

    private const val JPEG_SOI_0 = 0xFF.toByte()
    private const val JPEG_SOI_1 = 0xD8.toByte()

    data class NefPreviewInfo(
        val previewOffset: Int,
        val previewLength: Int,
        val width: Int = 0,
        val height: Int = 0,
        val orientation: Int = 1,
        val isJpegValid: Boolean = true
    )

    /**
     * Parses the embedded preview metadata from a .NEF binary slice.
     */
    fun parsePreviewInfo(bytes: ByteArray): NefPreviewInfo? {
        if (bytes.size < 8) return null

        val byteOrder = getByteOrder(bytes) ?: return fallbackScanJpeg(bytes)
        val magic = readUInt16(bytes, 2, byteOrder)
        if (magic != TIFF_MAGIC) return fallbackScanJpeg(bytes)

        val ifd0Offset = readUInt32(bytes, 4, byteOrder)
        if (ifd0Offset <= 0 || ifd0Offset >= bytes.size) return fallbackScanJpeg(bytes)

        val candidates = mutableListOf<NefPreviewInfo>()

        // 1. Scan IFD0
        val ifd0Result = scanIfd(bytes, ifdOffset = ifd0Offset, byteOrder = byteOrder)
        if (ifd0Result.previewInfo != null) candidates.add(ifd0Result.previewInfo)

        // 2. Scan SubIFDs
        for (subOffset in ifd0Result.subIfdOffsets) {
            if (subOffset in 1 until bytes.size) {
                val subResult = scanIfd(bytes, ifdOffset = subOffset, byteOrder = byteOrder)
                if (subResult.previewInfo != null) candidates.add(subResult.previewInfo)
            }
        }

        // 3. Scan ExifIFD
        if (ifd0Result.exifIfdOffset in 1 until bytes.size) {
            val exifResult = scanIfd(bytes, ifdOffset = ifd0Result.exifIfdOffset, byteOrder = byteOrder)
            if (exifResult.previewInfo != null) candidates.add(exifResult.previewInfo)
        }

        if (candidates.isNotEmpty()) {
            val best = candidates.maxByOrNull { it.previewLength }
            if (best != null) {
                val isValid = isJpegHeaderAt(bytes, best.previewOffset)
                return best.copy(isJpegValid = isValid)
            }
        }

        return fallbackScanJpeg(bytes)
    }

    /**
     * Extracts embedded preview JPEG bytes from a .NEF slice.
     */
    fun extractPreview(bytes: ByteArray): ByteArray? {
        val info = parsePreviewInfo(bytes) ?: return null
        val offset = info.previewOffset
        val length = info.previewLength

        if (offset < 0 || offset >= bytes.size) return null
        val availableLength = minOf(length, bytes.size - offset)
        if (availableLength <= 0) return null

        val extracted = bytes.copyOfRange(offset, offset + availableLength)
        if (extracted.size >= 2 && extracted[0] == JPEG_SOI_0 && extracted[1] == JPEG_SOI_1) {
            return extracted
        }

        val fallback = fallbackScanJpeg(bytes)
        if (fallback != null && fallback.previewOffset < bytes.size) {
            val fbAvail = minOf(fallback.previewLength, bytes.size - fallback.previewOffset)
            return bytes.copyOfRange(fallback.previewOffset, fallback.previewOffset + fbAvail)
        }

        return extracted
    }

    /**
     * Extracts unified [EmbeddedPreviewResult] representation.
     */
    fun extractUnifiedPreview(bytes: ByteArray): EmbeddedPreviewResult? {
        val startNs = System.nanoTime()
        val info = parsePreviewInfo(bytes) ?: return null
        val jpegBytes = extractPreview(bytes) ?: return null
        val elapsedMs = (System.nanoTime() - startNs) / 1_000_000L
        val orientation = if (info.orientation in 1..8) info.orientation else ExifOrientationHelper.extractOrientation(jpegBytes)

        return EmbeddedPreviewResult(
            jpegBytes = jpegBytes,
            width = info.width,
            height = info.height,
            orientation = orientation,
            format = "NEF",
            parseTimeMs = elapsedMs
        )
    }

    private data class IfdScanResult(
        val previewInfo: NefPreviewInfo?,
        val subIfdOffsets: List<Int>,
        val exifIfdOffset: Int
    )

    private fun scanIfd(bytes: ByteArray, ifdOffset: Int, byteOrder: ByteOrder): IfdScanResult {
        if (ifdOffset + 2 > bytes.size) {
            return IfdScanResult(null, emptyList(), 0)
        }

        val entryCount = readUInt16(bytes, ifdOffset, byteOrder)
        var jpegOffset = 0
        var jpegLength = 0
        var stripOffset = 0
        var stripByteCount = 0
        var width = 0
        var height = 0
        var orientation = 1
        var exifIfdOffset = 0
        val subIfdOffsets = mutableListOf<Int>()

        val maxSafeEntries = minOf(entryCount, (bytes.size - (ifdOffset + 2)) / 12)

        for (i in 0 until maxSafeEntries) {
            val entryPos = ifdOffset + 2 + i * 12
            val tag = readUInt16(bytes, entryPos, byteOrder)
            val type = readUInt16(bytes, entryPos + 2, byteOrder)
            val count = readUInt32(bytes, entryPos + 4, byteOrder)
            val valOrOffset = readUInt32(bytes, entryPos + 8, byteOrder)

            when (tag) {
                TAG_IMAGE_WIDTH -> width = valOrOffset
                TAG_IMAGE_HEIGHT -> height = valOrOffset
                TAG_ORIENTATION -> {
                    val parsedOrientation = if (type == 3) {
                        if (byteOrder == ByteOrder.LITTLE_ENDIAN) {
                            valOrOffset and 0xFFFF
                        } else {
                            (valOrOffset ushr 16) and 0xFFFF
                        }
                    } else {
                        valOrOffset
                    }
                    if (parsedOrientation in 1..8) {
                        orientation = parsedOrientation
                    }
                }
                TAG_JPEG_INTERCHANGE_FORMAT -> jpegOffset = valOrOffset
                TAG_JPEG_INTERCHANGE_FORMAT_LENGTH -> jpegLength = valOrOffset
                TAG_STRIP_OFFSETS -> stripOffset = valOrOffset
                TAG_STRIP_BYTE_COUNTS -> stripByteCount = valOrOffset
                TAG_EXIF_IFD -> exifIfdOffset = valOrOffset
                TAG_SUB_IFDS -> {
                    if (count == 1) {
                        subIfdOffsets.add(valOrOffset)
                    } else if (count > 1 && valOrOffset in 1 until bytes.size) {
                        for (k in 0 until minOf(count, 8)) {
                            val off = valOrOffset + k * 4
                            if (off + 4 <= bytes.size) {
                                subIfdOffsets.add(readUInt32(bytes, off, byteOrder))
                            }
                        }
                    }
                }
            }
        }

        val chosenOffset = if (jpegOffset > 0) jpegOffset else stripOffset
        val chosenLength = if (jpegLength > 0) jpegLength else stripByteCount

        if (orientation == 1 && chosenOffset > 0 && chosenOffset + 4 < bytes.size && isJpegHeaderAt(bytes, chosenOffset)) {
            val maxCheck = minOf(chosenLength, bytes.size - chosenOffset)
            if (maxCheck > 4) {
                val exifOrientation = ExifOrientationHelper.extractOrientation(
                    bytes.copyOfRange(chosenOffset, chosenOffset + minOf(maxCheck, 65536))
                )
                if (exifOrientation in 1..8 && exifOrientation != 1) {
                    orientation = exifOrientation
                }
            }
        }

        val previewInfo = if (chosenOffset > 0 && chosenLength > 0) {
            NefPreviewInfo(
                previewOffset = chosenOffset,
                previewLength = chosenLength,
                width = width,
                height = height,
                orientation = orientation,
                isJpegValid = isJpegHeaderAt(bytes, chosenOffset)
            )
        } else null

        return IfdScanResult(previewInfo, subIfdOffsets, exifIfdOffset)
    }

    private fun getByteOrder(bytes: ByteArray): ByteOrder? {
        val b0 = bytes[0].toInt() and 0xFF
        val b1 = bytes[1].toInt() and 0xFF
        val marker = (b0 shl 8) or b1
        return when (marker) {
            TIFF_LITTLE_ENDIAN -> ByteOrder.LITTLE_ENDIAN
            TIFF_BIG_ENDIAN -> ByteOrder.BIG_ENDIAN
            else -> null
        }
    }

    private fun readUInt16(bytes: ByteArray, offset: Int, byteOrder: ByteOrder): Int {
        if (offset + 2 > bytes.size) return 0
        val bb = ByteBuffer.wrap(bytes, offset, 2).order(byteOrder)
        return bb.short.toInt() and 0xFFFF
    }

    private fun readUInt32(bytes: ByteArray, offset: Int, byteOrder: ByteOrder): Int {
        if (offset + 4 > bytes.size) return 0
        val bb = ByteBuffer.wrap(bytes, offset, 4).order(byteOrder)
        return bb.int
    }

    private fun isJpegHeaderAt(bytes: ByteArray, offset: Int): Boolean {
        if (offset < 0 || offset + 2 > bytes.size) return false
        return bytes[offset] == JPEG_SOI_0 && bytes[offset + 1] == JPEG_SOI_1
    }

    private fun fallbackScanJpeg(bytes: ByteArray): NefPreviewInfo? {
        val limit = minOf(bytes.size - 2, 64 * 1024)
        for (i in 0 until limit) {
            if (bytes[i] == JPEG_SOI_0 && bytes[i + 1] == JPEG_SOI_1) {
                return NefPreviewInfo(
                    previewOffset = i,
                    previewLength = bytes.size - i,
                    isJpegValid = true
                )
            }
        }
        return null
    }
}
