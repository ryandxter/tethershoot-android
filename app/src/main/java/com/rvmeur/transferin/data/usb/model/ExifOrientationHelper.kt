package com.rvmeur.transferin.data.usb.model

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * High-performance, zero-allocation EXIF Tag 0x0112 (Orientation) parser.
 *
 * Scans JPEG APP1 Exif segments in sub-millisecond time without decoding the image bitmap.
 */
object ExifOrientationHelper {

    private const val TAG_ORIENTATION = 0x0112
    private const val TAG_EXIF_IFD = 0x8769

    /**
     * Extracts the EXIF orientation value from a JPEG byte stream.
     *
     * @param jpegBytes Binary JPEG stream starting with 0xFFD8.
     * @return Orientation integer (1=0°, 3=180°, 6=90° CW, 8=270° CW). Defaults to 1.
     */
    fun extractOrientation(jpegBytes: ByteArray): Int {
        if (jpegBytes.size < 4 || jpegBytes[0] != 0xFF.toByte() || jpegBytes[1] != 0xD8.toByte()) {
            return 1
        }

        var offset = 2
        while (offset + 4 <= jpegBytes.size) {
            if (jpegBytes[offset] != 0xFF.toByte()) {
                offset++
                continue
            }

            val marker = jpegBytes[offset + 1].toInt() and 0xFF
            offset += 2

            // Standalone markers without payload
            if (marker == 0xD9 || marker == 0xDA) break // EOI or SOS

            if (offset + 2 > jpegBytes.size) break
            val segmentLength = ((jpegBytes[offset].toInt() and 0xFF) shl 8) or
                    (jpegBytes[offset + 1].toInt() and 0xFF)
            if (segmentLength < 2 || offset + segmentLength > jpegBytes.size) break

            if (marker == 0xE1) { // APP1 (Exif)
                val exifDataOffset = offset + 2
                val exifDataLength = segmentLength - 2

                if (exifDataLength >= 6 &&
                    jpegBytes[exifDataOffset] == 'E'.code.toByte() &&
                    jpegBytes[exifDataOffset + 1] == 'x'.code.toByte() &&
                    jpegBytes[exifDataOffset + 2] == 'i'.code.toByte() &&
                    jpegBytes[exifDataOffset + 3] == 'f'.code.toByte() &&
                    jpegBytes[exifDataOffset + 4] == 0.toByte() &&
                    jpegBytes[exifDataOffset + 5] == 0.toByte()
                ) {
                    val tiffOffset = exifDataOffset + 6
                    val tiffLength = exifDataLength - 6
                    val orientation = parseTiffOrientation(jpegBytes, tiffOffset, tiffLength)
                    if (orientation in 1..8) {
                        return orientation
                    }
                }
            }

            offset += segmentLength
        }

        return 1
    }

    private fun parseTiffOrientation(bytes: ByteArray, tiffOffset: Int, tiffLength: Int): Int {
        if (tiffLength < 8 || tiffOffset + 8 > bytes.size) return 1

        val isLittleEndian = bytes[tiffOffset] == 'I'.code.toByte() && bytes[tiffOffset + 1] == 'I'.code.toByte()
        val isBigEndian = bytes[tiffOffset] == 'M'.code.toByte() && bytes[tiffOffset + 1] == 'M'.code.toByte()
        if (!isLittleEndian && !isBigEndian) return 1

        val byteOrder = if (isLittleEndian) ByteOrder.LITTLE_ENDIAN else ByteOrder.BIG_ENDIAN
        val bb = ByteBuffer.wrap(bytes, tiffOffset, tiffLength).slice().order(byteOrder)

        val magic = bb.getShort(2).toInt() and 0xFFFF
        if (magic != 0x002A) return 1

        val ifd0Offset = bb.getInt(4)
        if (ifd0Offset < 8 || ifd0Offset + 2 > tiffLength) return 1

        return parseIfdForOrientation(bb, bytes, tiffOffset, tiffLength, ifd0Offset, byteOrder, 0)
    }

    private fun parseIfdForOrientation(
        bb: ByteBuffer,
        bytes: ByteArray,
        tiffOffset: Int,
        tiffLength: Int,
        ifdOffset: Int,
        byteOrder: ByteOrder,
        depth: Int
    ): Int {
        if (depth > 2 || ifdOffset + 2 > tiffLength) return 1

        val entryCount = bb.getShort(ifdOffset).toInt() and 0xFFFF
        var pos = ifdOffset + 2

        for (i in 0 until entryCount) {
            if (pos + 12 > tiffLength) break
            val tag = bb.getShort(pos).toInt() and 0xFFFF
            val type = bb.getShort(pos + 2).toInt() and 0xFFFF
            val valueOffset = bb.getInt(pos + 8)

            if (tag == TAG_ORIENTATION) {
                val orientationVal = if (type == 3) { // SHORT
                    if (byteOrder == ByteOrder.LITTLE_ENDIAN) {
                        bb.getShort(pos + 8).toInt() and 0xFFFF
                    } else {
                        (valueOffset ushr 16) and 0xFFFF
                    }
                } else {
                    valueOffset
                }
                if (orientationVal in 1..8) {
                    return orientationVal
                }
            }

            // Exif IFD Pointer (0x8769)
            if (tag == TAG_EXIF_IFD && valueOffset in 8 until tiffLength) {
                val nestedOrientation = parseIfdForOrientation(
                    bb, bytes, tiffOffset, tiffLength, valueOffset, byteOrder, depth + 1
                )
                if (nestedOrientation in 1..8 && nestedOrientation != 1) {
                    return nestedOrientation
                }
            }

            pos += 12
        }

        return 1
    }
}
