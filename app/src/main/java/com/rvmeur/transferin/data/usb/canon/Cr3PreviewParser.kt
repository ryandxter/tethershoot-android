package com.rvmeur.transferin.data.usb.canon

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Result of extracting an embedded JPEG preview or thumbnail from a Canon .CR3 file.
 *
 * @property jpegBytes Raw binary JPEG payload (starts with 0xFFD8, ends with 0xFFD9).
 * @property width Image width in pixels.
 * @property height Image height in pixels.
 * @property isFullPreview True if high-resolution PRVW atom was extracted, false if 160x120 THMB fallback.
 * @property parseTimeMs Elapsed wall-clock time in milliseconds to locate and extract the preview.
 */
data class Cr3PreviewResult(
    val jpegBytes: ByteArray,
    val width: Int,
    val height: Int,
    val isFullPreview: Boolean,
    val parseTimeMs: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as Cr3PreviewResult
        if (!jpegBytes.contentEquals(other.jpegBytes)) return false
        if (width != other.width) return false
        if (height != other.height) return false
        if (isFullPreview != other.isFullPreview) return false
        return true
    }

    override fun hashCode(): Int {
        var result = jpegBytes.contentHashCode()
        result = 31 * result + width
        result = 31 * result + height
        result = 31 * result + isFullPreview.hashCode()
        return result
    }
}

/**
 * Zero-copy, high-speed ISO-BMFF (ISO Base Media File Format) parser tailored for Canon .CR3 files.
 *
 * Scans the ISO-BMFF atom hierarchy (`ftyp`, `moov`, `uuid`, `PRVW`, `THMB`) within the first 128KB
 * slice of a .CR3 stream to extract embedded high-resolution JPEG previews in <500ms without downloading
 * the entire 30MB-60MB RAW file over USB.
 */
object Cr3PreviewParser {

    // FourCC Atom Identifiers (Big Endian)
    const val FOURCC_FTYP = 0x66747970 // "ftyp"
    const val FOURCC_MOOV = 0x6D6F6F76 // "moov"
    const val FOURCC_TRAK = 0x7472616B // "trak"
    const val FOURCC_MDIA = 0x6D646961 // "mdia"
    const val FOURCC_MINF = 0x6D696E66 // "minf"
    const val FOURCC_DINF = 0x64696E66 // "dinf"
    const val FOURCC_STBL = 0x7374626C // "stbl"
    const val FOURCC_UUID = 0x75756964 // "uuid"
    const val FOURCC_PRVW = 0x50525657 // "PRVW"
    const val FOURCC_THMB = 0x54484D42 // "THMB"

    // Canon Major Brand in ftyp ("crx ")
    const val BRAND_CRX = 0x63727820

    // Canon Custom UUID: 85c0b687-820f-11e0-8111-f4ce462b0220
    private val CANON_METADATA_UUID = byteArrayOf(
        0x85.toByte(), 0xC0.toByte(), 0xB6.toByte(), 0x87.toByte(),
        0x82.toByte(), 0x0F.toByte(), 0x11.toByte(), 0xE0.toByte(),
        0x81.toByte(), 0x11.toByte(), 0xF4.toByte(), 0xCE.toByte(),
        0x46.toByte(), 0x2B.toByte(), 0x02.toByte(), 0x20.toByte()
    )

    private const val DEFAULT_SLICE_SIZE = 128 * 1024 // 128 KB

    /**
     * Parses the preview image from a [ByteBuffer].
     */
    fun parsePreview(buffer: ByteBuffer): Cr3PreviewResult? {
        val startNs = System.nanoTime()
        val originalOrder = buffer.order()
        val originalPos = buffer.position()
        val originalLimit = buffer.limit()

        try {
            buffer.order(ByteOrder.BIG_ENDIAN)
            val totalBytes = buffer.remaining()
            if (totalBytes < 16) return null

            // Verify ftyp box
            val firstBoxSize = buffer.getInt(buffer.position())
            val firstBoxType = buffer.getInt(buffer.position() + 4)
            if (firstBoxType != FOURCC_FTYP && firstBoxType != FOURCC_MOOV) {
                // If not standard ftyp/moov header, check if buffer contains PRVW/THMB directly
                return scanDirectlyForAtoms(buffer, startNs)
            }

            var prvwResult: Cr3PreviewResult? = null
            var thmbResult: Cr3PreviewResult? = null

            // Traverse container boxes
            fun scanBoxes(startOffset: Int, endOffset: Int) {
                var cursor = startOffset
                while (cursor + 8 <= endOffset && prvwResult == null) {
                    val boxSizeRaw = buffer.getInt(cursor).toLong() and 0xFFFFFFFFL
                    val boxType = buffer.getInt(cursor + 4)

                    val (headerSize, boxSize) = when (boxSizeRaw) {
                        1L -> {
                            if (cursor + 16 > endOffset) return
                            val extendedSize = buffer.getLong(cursor + 8)
                            16 to extendedSize
                        }
                        0L -> 8 to (endOffset - cursor).toLong()
                        else -> 8 to boxSizeRaw
                    }

                    if (boxSize < headerSize) break

                    val payloadStart = cursor + headerSize
                    val boxEnd = (cursor + boxSize).toInt().coerceAtMost(endOffset)

                    when (boxType) {
                        FOURCC_PRVW -> {
                            val res = extractJpegFromAtom(buffer, payloadStart, boxEnd, isFullPreview = true, startNs)
                            if (res != null) {
                                prvwResult = res
                                return
                            }
                        }
                        FOURCC_THMB -> {
                            if (thmbResult == null) {
                                thmbResult = extractJpegFromAtom(buffer, payloadStart, boxEnd, isFullPreview = false, startNs)
                            }
                        }
                        FOURCC_MOOV, FOURCC_TRAK, FOURCC_MDIA, FOURCC_MINF, FOURCC_DINF, FOURCC_STBL -> {
                            scanBoxes(payloadStart, boxEnd)
                        }
                        FOURCC_UUID -> {
                            if (payloadStart + 16 <= boxEnd) {
                                // Canon UUID payload contains nested PRVW / THMB boxes
                                scanBoxes(payloadStart + 16, boxEnd)
                            }
                        }
                    }

                    if (boxSize <= 0) break
                    val nextCursor = cursor + boxSize
                    if (nextCursor <= cursor || nextCursor > endOffset) break
                    cursor = nextCursor.toInt()
                }
            }

            scanBoxes(buffer.position(), buffer.limit())

            return prvwResult ?: thmbResult ?: scanDirectlyForAtoms(buffer, startNs)
        } catch (e: Exception) {
            return null
        } finally {
            buffer.position(originalPos)
            buffer.limit(originalLimit)
            buffer.order(originalOrder)
        }
    }

    /**
     * Parses preview from a [ByteArray] slice.
     */
    fun parsePreviewFromSlice(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size): Cr3PreviewResult? {
        if (offset < 0 || length <= 0 || offset + length > bytes.size) return null
        val byteBuffer = ByteBuffer.wrap(bytes, offset, length)
        return parsePreview(byteBuffer)
    }

    /**
     * Parses preview using a progressive partial slice provider (e.g. via USB PTP GetPartialObject).
     * Fetches up to [initialSliceSize] (defaults to 128KB).
     */
    suspend fun parsePreviewFromPartialProvider(
        initialSliceSize: Int = DEFAULT_SLICE_SIZE,
        fetchSlice: suspend (offset: Long, length: Int) -> ByteArray?
    ): Cr3PreviewResult? {
        val slice = fetchSlice(0L, initialSliceSize) ?: return null
        return parsePreviewFromSlice(slice, 0, slice.size)
    }

    /**
     * Extracts JPEG bytes and dimensions from a discovered PRVW or THMB atom payload.
     */
    private fun extractJpegFromAtom(
        buffer: ByteBuffer,
        payloadStart: Int,
        payloadEnd: Int,
        isFullPreview: Boolean,
        startNs: Long
    ): Cr3PreviewResult? {
        if (payloadStart >= payloadEnd) return null

        // Locate JPEG Start of Image (0xFF, 0xD8)
        var soiPos = -1
        for (i in payloadStart until payloadEnd - 1) {
            if ((buffer.get(i).toInt() and 0xFF) == 0xFF &&
                (buffer.get(i + 1).toInt() and 0xFF) == 0xD8
            ) {
                soiPos = i
                break
            }
        }

        if (soiPos == -1) return null

        // Locate JPEG End of Image (0xFF, 0xD9) from the end backwards
        var eoiPos = -1
        for (i in payloadEnd - 1 downTo soiPos + 2) {
            if ((buffer.get(i - 1).toInt() and 0xFF) == 0xFF &&
                (buffer.get(i).toInt() and 0xFF) == 0xD9
            ) {
                eoiPos = i
                break
            }
        }

        val jpegLength = if (eoiPos != -1) {
            eoiPos - soiPos + 1
        } else {
            payloadEnd - soiPos
        }

        if (jpegLength < 4) return null

        val jpegBytes = ByteArray(jpegLength)
        val savedPos = buffer.position()
        buffer.position(soiPos)
        buffer.get(jpegBytes, 0, jpegLength)
        buffer.position(savedPos)

        // Extract dimensions from atom header or JPEG SOF marker
        var width = 0
        var height = 0

        // If PRVW atom header precedes SOI, check atom header metadata
        if (soiPos >= payloadStart + 12) {
            // PRVW layout: version/flags (4 bytes), width (2 or 4 bytes), height (2 or 4 bytes)
            val w = buffer.getShort(payloadStart + 4).toInt() and 0xFFFF
            val h = buffer.getShort(payloadStart + 6).toInt() and 0xFFFF
            if (w > 0 && h > 0) {
                width = w
                height = h
            }
        }

        // If dimensions not in atom header, parse from JPEG SOF markers
        if (width == 0 || height == 0) {
            val (parsedW, parsedH) = parseDimensionsFromJpeg(jpegBytes)
            width = parsedW
            height = parsedH
        }

        if (width == 0 && height == 0) {
            width = if (isFullPreview) 1920 else 160
            height = if (isFullPreview) 1080 else 120
        }

        val elapsedMs = (System.nanoTime() - startNs) / 1_000_000L

        return Cr3PreviewResult(
            jpegBytes = jpegBytes,
            width = width,
            height = height,
            isFullPreview = isFullPreview,
            parseTimeMs = elapsedMs
        )
    }

    /**
     * Fallback scan to search for raw PRVW / THMB FourCC patterns directly in the buffer slice.
     */
    private fun scanDirectlyForAtoms(buffer: ByteBuffer, startNs: Long): Cr3PreviewResult? {
        val limit = buffer.limit()
        var prvwResult: Cr3PreviewResult? = null
        var thmbResult: Cr3PreviewResult? = null

        for (i in buffer.position() until limit - 8) {
            val fourCC = buffer.getInt(i)
            if (fourCC == FOURCC_PRVW) {
                val boxStart = (i - 4).coerceAtLeast(0)
                val boxSize = buffer.getInt(boxStart).toLong() and 0xFFFFFFFFL
                val payloadStart = i + 4
                val boxEnd = if (boxSize in 8..0x10000000) {
                    (boxStart + boxSize).toInt().coerceAtMost(limit)
                } else {
                    limit
                }
                val res = extractJpegFromAtom(buffer, payloadStart, boxEnd, isFullPreview = true, startNs)
                if (res != null) return res
            } else if (fourCC == FOURCC_THMB && thmbResult == null) {
                val boxStart = (i - 4).coerceAtLeast(0)
                val boxSize = buffer.getInt(boxStart).toLong() and 0xFFFFFFFFL
                val payloadStart = i + 4
                val boxEnd = if (boxSize in 8..0x10000000) {
                    (boxStart + boxSize).toInt().coerceAtMost(limit)
                } else {
                    limit
                }
                thmbResult = extractJpegFromAtom(buffer, payloadStart, boxEnd, isFullPreview = false, startNs)
            }
        }

        return prvwResult ?: thmbResult
    }

    /**
     * Parses image width and height directly from JPEG SOF (Start of Frame) markers.
     */
    private fun parseDimensionsFromJpeg(jpegBytes: ByteArray): Pair<Int, Int> {
        var offset = 2
        while (offset < jpegBytes.size - 8) {
            if ((jpegBytes[offset].toInt() and 0xFF) != 0xFF) {
                offset++
                continue
            }

            val marker = jpegBytes[offset + 1].toInt() and 0xFF

            // SOF0 (0xC0), SOF1 (0xC1), SOF2 (0xC2), SOF3 (0xC3), SOF5..SOF7, SOF9..SOF11, SOF13..SOF15
            if (marker in 0xC0..0xC3 || marker in 0xC5..0xC7 || marker in 0xC9..0xCB || marker in 0xCD..0xCF) {
                if (offset + 8 < jpegBytes.size) {
                    val h = ((jpegBytes[offset + 5].toInt() and 0xFF) shl 8) or (jpegBytes[offset + 6].toInt() and 0xFF)
                    val w = ((jpegBytes[offset + 7].toInt() and 0xFF) shl 8) or (jpegBytes[offset + 8].toInt() and 0xFF)
                    return Pair(w, h)
                }
            }

            if (marker == 0xD9 || marker == 0xDA) {
                // End of image or start of scan - stop parsing headers
                break
            }

            if (offset + 3 < jpegBytes.size) {
                val segmentLen = ((jpegBytes[offset + 2].toInt() and 0xFF) shl 8) or (jpegBytes[offset + 3].toInt() and 0xFF)
                if (segmentLen < 2) break
                offset += 2 + segmentLen
            } else {
                break
            }
        }
        return Pair(0, 0)
    }
}
