package com.rvmeur.transferin.data.usb.vendor.sony

import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * High-performance binary parser for Sony .ARW (TIFF-based) RAW files.
 *
 * Traverses TIFF IFD0 and SubIFD structures to locate and extract embedded JPEG previews
 * from the first 128KB–256KB of the file without downloading the full multi-megabyte RAW file.
 */
class ArwPreviewParser {

    companion object {
        private const val TAG = "ArwPreviewParser"

        // TIFF Constants
        private const val TIFF_LITTLE_ENDIAN = 0x4949 // 'II'
        private const val TIFF_BIG_ENDIAN = 0x4D4D    // 'MM'
        private const val TIFF_MAGIC = 42

        // Tag IDs
        private const val TAG_IMAGE_WIDTH = 0x0100
        private const val TAG_IMAGE_HEIGHT = 0x0101
        private const val TAG_STRIP_OFFSETS = 0x0111
        private const val TAG_STRIP_BYTE_COUNTS = 0x0117
        private const val TAG_SUB_IFDS = 0x014A
        private const val TAG_JPEG_INTERCHANGE_FORMAT = 0x0201
        private const val TAG_JPEG_INTERCHANGE_FORMAT_LENGTH = 0x0202
        private const val TAG_EXIF_IFD = 0x8769

        // JPEG Markers
        private const val JPEG_SOI_BYTE_0 = 0xFF.toByte()
        private const val JPEG_SOI_BYTE_1 = 0xD8.toByte()
    }

    /**
     * Parses the TIFF IFD and SubIFD structures in [bytes] to locate the embedded preview metadata.
     *
     * @param bytes Initial binary slice of the .ARW file (typically 128KB - 256KB).
     * @return [ArwPreviewInfo] containing preview offset, length, and dimensions, or null if not found.
     */
    fun parsePreviewInfo(bytes: ByteArray): ArwPreviewInfo? {
        if (bytes.size < 8) {
            Log.w(TAG, "ARW data too short for TIFF header: ${bytes.size} bytes")
            return null
        }

        val byteOrder = getByteOrder(bytes) ?: run {
            Log.w(TAG, "Invalid TIFF byte order marker")
            return fallbackScanJpeg(bytes)
        }

        val magic = readUInt16(bytes, 2, byteOrder)
        if (magic != TIFF_MAGIC) {
            Log.w(TAG, "Invalid TIFF magic number: $magic")
            return fallbackScanJpeg(bytes)
        }

        val ifd0Offset = readUInt32(bytes, 4, byteOrder)
        if (ifd0Offset <= 0 || ifd0Offset >= bytes.size) {
            Log.w(TAG, "Invalid IFD0 offset: $ifd0Offset")
            return fallbackScanJpeg(bytes)
        }

        val candidates = mutableListOf<ArwPreviewInfo>()

        // 1. Scan IFD0
        val ifd0Result = scanIfd(bytes, ifd0Offset, byteOrder)
        if (ifd0Result.previewInfo != null) {
            candidates.add(ifd0Result.previewInfo)
        }

        // 2. Scan SubIFDs if present
        for (subIfdOffset in ifd0Result.subIfdOffsets) {
            if (subIfdOffset > 0 && subIfdOffset < bytes.size) {
                val subResult = scanIfd(bytes, subIfdOffset, byteOrder)
                if (subResult.previewInfo != null) {
                    candidates.add(subResult.previewInfo)
                }
            }
        }

        // 3. Scan ExifIFD if present
        if (ifd0Result.exifIfdOffset > 0 && ifd0Result.exifIfdOffset < bytes.size) {
            val exifResult = scanIfd(bytes, ifd0Result.exifIfdOffset, byteOrder)
            if (exifResult.previewInfo != null) {
                candidates.add(exifResult.previewInfo)
            }
        }

        if (candidates.isNotEmpty()) {
            // Select the candidate with the largest preview length or valid JPEG
            val best = candidates.maxByOrNull { it.previewLength }
            if (best != null) {
                val isValid = isJpegHeaderAt(bytes, best.previewOffset)
                return best.copy(isJpegValid = isValid)
            }
        }

        return fallbackScanJpeg(bytes)
    }

    /**
     * Extracts the raw JPEG byte array of the embedded preview from [bytes].
     */
    fun extractPreview(bytes: ByteArray): ByteArray? {
        val info = parsePreviewInfo(bytes) ?: return null
        val offset = info.previewOffset
        val length = info.previewLength

        if (offset < 0 || offset >= bytes.size) return null

        val availableLength = minOf(length, bytes.size - offset)
        if (availableLength <= 0) return null

        val extracted = bytes.copyOfRange(offset, offset + availableLength)
        if (extracted.size >= 2 && extracted[0] == JPEG_SOI_BYTE_0 && extracted[1] == JPEG_SOI_BYTE_1) {
            return extracted
        }

        // If offset did not match JPEG SOI, try fallback scan
        val fallback = fallbackScanJpeg(bytes)
        if (fallback != null && fallback.previewOffset < bytes.size) {
            val fbAvail = minOf(fallback.previewLength, bytes.size - fallback.previewOffset)
            return bytes.copyOfRange(fallback.previewOffset, fallback.previewOffset + fbAvail)
        }

        return extracted
    }

    private data class IfdScanResult(
        val previewInfo: ArwPreviewInfo?,
        val subIfdOffsets: List<Int>,
        val exifIfdOffset: Int
    )

    private fun scanIfd(bytes: ByteArray, ifdOffset: Int, byteOrder: ByteOrder): IfdScanResult {
        if (ifdOffset + 2 > bytes.size) {
            return IfdScanResult(null, emptyList(), 0)
        }

        val entryCount = readUInt16(bytes, ifdOffset, byteOrder)
        val entriesEnd = ifdOffset + 2 + entryCount * 12

        if (entriesEnd > bytes.size) {
            // Truncated IFD, read up to available entries
        }

        var jpegOffset = 0
        var jpegLength = 0
        var stripOffset = 0
        var stripByteCount = 0
        var width = 0
        var height = 0
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
                TAG_JPEG_INTERCHANGE_FORMAT -> jpegOffset = valOrOffset
                TAG_JPEG_INTERCHANGE_FORMAT_LENGTH -> jpegLength = valOrOffset
                TAG_STRIP_OFFSETS -> stripOffset = valOrOffset
                TAG_STRIP_BYTE_COUNTS -> stripByteCount = valOrOffset
                TAG_EXIF_IFD -> exifIfdOffset = valOrOffset
                TAG_SUB_IFDS -> {
                    if (count == 1) {
                        subIfdOffsets.add(valOrOffset)
                    } else if (count > 1 && valOrOffset < bytes.size) {
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

        val previewInfo = if (chosenOffset > 0 && chosenLength > 0) {
            ArwPreviewInfo(
                previewOffset = chosenOffset,
                previewLength = chosenLength,
                width = width,
                height = height,
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
        return bytes[offset] == JPEG_SOI_BYTE_0 && bytes[offset + 1] == JPEG_SOI_BYTE_1
    }

    private fun fallbackScanJpeg(bytes: ByteArray): ArwPreviewInfo? {
        val limit = minOf(bytes.size - 2, 64 * 1024)
        for (i in 0 until limit) {
            if (bytes[i] == JPEG_SOI_BYTE_0 && bytes[i + 1] == JPEG_SOI_BYTE_1) {
                // Found JPEG SOI marker
                return ArwPreviewInfo(
                    previewOffset = i,
                    previewLength = bytes.size - i,
                    isJpegValid = true
                )
            }
        }
        return null
    }
}
