package com.rvmeur.transferin.data.usb.vendor.fuji

import com.rvmeur.transferin.data.usb.model.EmbeddedPreviewResult
import com.rvmeur.transferin.data.usb.model.ExifOrientationHelper
import com.rvmeur.transferin.domain.camera.FujiFilmSimulation
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Thrown when byte buffer does not conform to Fujifilm RAF specification.
 */
class InvalidRafHeaderException(message: String) : Exception(message)

/**
 * Metadata parsed from the first 128 bytes of a Fujifilm .RAF container.
 */
data class RafHeaderInfo(
    val version: String,
    val cameraModel: String,
    val jpegOffset: Long,
    val jpegLength: Long,
    val cfaOffset: Long = 0L,
    val cfaLength: Long = 0L
)

/**
 * Result of extracting embedded preview JPEG and Film Simulation metadata from a .RAF header.
 */
data class RafPreviewResult(
    val previewJpegBytes: ByteArray,
    val filmSimulation: FujiFilmSimulation,
    val cameraModel: String,
    val width: Int = 0,
    val height: Int = 0,
    val orientation: Int = 1,
    val captureTimestamp: Long = System.currentTimeMillis()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as RafPreviewResult
        return previewJpegBytes.contentEquals(other.previewJpegBytes) &&
                filmSimulation == other.filmSimulation &&
                cameraModel == other.cameraModel &&
                width == other.width &&
                height == other.height &&
                orientation == other.orientation &&
                captureTimestamp == other.captureTimestamp
    }

    override fun hashCode(): Int {
        var result = previewJpegBytes.contentHashCode()
        result = 31 * result + filmSimulation.hashCode()
        result = 31 * result + cameraModel.hashCode()
        result = 31 * result + width
        result = 31 * result + height
        result = 31 * result + orientation
        result = 31 * result + captureTimestamp.hashCode()
        return result
    }
}

/**
 * High-performance parser that extracts embedded JPEG previews and Film Simulation
 * metadata directly from Fujifilm .RAF raw file headers without full file downloads.
 */
object RafPreviewExtractor {

    private val RAF_MAGIC = byteArrayOf(
        0x46, 0x55, 0x4A, 0x49, 0x46, 0x49, 0x4C, 0x4D, // "FUJIFILM"
        0x43, 0x43, 0x44, 0x2D, 0x52, 0x41, 0x57, 0x20  // "CCD-RAW "
    )

    private const val MIN_HEADER_LENGTH = 92
    private const val OFFSET_VERSION = 16
    private const val OFFSET_CAMERA_MODEL = 20
    private const val OFFSET_JPEG_OFFSET = 84
    private const val OFFSET_JPEG_LENGTH = 88
    private const val OFFSET_CFA_OFFSET = 100
    private const val OFFSET_CFA_LENGTH = 104

    private const val TAG_FILM_MODE = 0x1401

    /**
     * Parses the RAF header structure from a byte array (typically the first 128 KB or 128 bytes).
     */
    fun parseRafHeader(headerBytes: ByteArray): Result<RafHeaderInfo> = runCatching {
        if (headerBytes.size < MIN_HEADER_LENGTH) {
            throw InvalidRafHeaderException("Header too short: ${headerBytes.size} bytes (minimum $MIN_HEADER_LENGTH)")
        }

        // Validate 16-byte magic
        for (i in RAF_MAGIC.indices) {
            if (headerBytes[i] != RAF_MAGIC[i]) {
                throw InvalidRafHeaderException("Invalid RAF magic header: expected 'FUJIFILMCCD-RAW '")
            }
        }

        val version = String(headerBytes, OFFSET_VERSION, 4, Charsets.US_ASCII).trim()
        val cameraModel = String(headerBytes, OFFSET_CAMERA_MODEL, 32, Charsets.US_ASCII)
            .replace("\u0000", "")
            .trim()

        val jpegOffset = readUInt32BigEndian(headerBytes, OFFSET_JPEG_OFFSET)
        val jpegLength = readUInt32BigEndian(headerBytes, OFFSET_JPEG_LENGTH)

        if (jpegOffset < 120L) {
            throw InvalidRafHeaderException("Invalid embedded JPEG offset: $jpegOffset (minimum 120)")
        }
        if (jpegLength <= 0L || jpegLength > 50_000_000L) {
            throw InvalidRafHeaderException("Invalid embedded JPEG length: $jpegLength bytes")
        }

        var cfaOffset = 0L
        var cfaLength = 0L
        if (headerBytes.size >= OFFSET_CFA_LENGTH + 4) {
            cfaOffset = readUInt32BigEndian(headerBytes, OFFSET_CFA_OFFSET)
            cfaLength = readUInt32BigEndian(headerBytes, OFFSET_CFA_LENGTH)
        }

        RafHeaderInfo(
            version = version,
            cameraModel = cameraModel,
            jpegOffset = jpegOffset,
            jpegLength = jpegLength,
            cfaOffset = cfaOffset,
            cfaLength = cfaLength
        )
    }

    /**
     * Extracts the embedded JPEG preview from the provided RAF byte array.
     */
    fun extractEmbeddedJpeg(rafBytes: ByteArray): Result<ByteArray> = runCatching {
        val header = parseRafHeader(rafBytes).getOrThrow()
        val start = header.jpegOffset.toInt()
        val end = (header.jpegOffset + header.jpegLength).toInt()

        if (rafBytes.size < end) {
            throw InvalidRafHeaderException(
                "Incomplete RAF buffer: needed $end bytes for JPEG preview, got ${rafBytes.size}"
            )
        }

        // Check JPEG SOI (0xFF, 0xD8)
        if (rafBytes[start] != 0xFF.toByte() || rafBytes[start + 1] != 0xD8.toByte()) {
            throw InvalidRafHeaderException("Embedded JPEG SOI marker (0xFFD8) not found at offset $start")
        }

        rafBytes.copyOfRange(start, end)
    }

    /**
     * Extracts embedded JPEG preview directly from a RAF file on disk using fast seek.
     */
    fun extractEmbeddedJpeg(rafFile: java.io.File): Result<ByteArray> = runCatching {
        if (!rafFile.exists() || rafFile.length() < MIN_HEADER_LENGTH) {
            throw InvalidRafHeaderException("File not found or too small: ${rafFile.absolutePath}")
        }
        java.io.RandomAccessFile(rafFile, "r").use { raf ->
            val headerBytes = ByteArray(128.coerceAtLeast(MIN_HEADER_LENGTH))
            raf.readFully(headerBytes)
            val header = parseRafHeader(headerBytes).getOrThrow()

            val start = header.jpegOffset
            val len = header.jpegLength
            if (start + len > rafFile.length()) {
                throw InvalidRafHeaderException("File truncated: needed ${start + len} bytes, file is ${rafFile.length()}")
            }

            raf.seek(start)
            val jpegBytes = ByteArray(len.toInt())
            raf.readFully(jpegBytes)

            if (jpegBytes.size < 2 || jpegBytes[0] != 0xFF.toByte() || jpegBytes[1] != 0xD8.toByte()) {
                throw InvalidRafHeaderException("Embedded JPEG SOI marker (0xFFD8) not found at offset $start")
            }
            jpegBytes
        }
    }

    /**
     * Extracts Fuji Film Simulation from embedded JPEG or Exif MakerNotes.
     */
    fun extractFilmSimulation(jpegBytes: ByteArray): FujiFilmSimulation {
        return runCatching {
            // First attempt structured Exif MakerNote parser
            val structuredSim = parseFilmSimulationFromExif(jpegBytes)
            if (structuredSim != FujiFilmSimulation.UNKNOWN) {
                return structuredSim
            }

            // Fallback pattern scan for Tag 0x1401 in MakerNote
            scanForFilmSimulationTag(jpegBytes)
        }.getOrDefault(FujiFilmSimulation.UNKNOWN)
    }

    /**
     * Extracts full preview result with dimensions, film simulation, and EXIF orientation.
     */
    fun extractPreviewResult(headerBytes: ByteArray, jpegPayload: ByteArray? = null): Result<RafPreviewResult> = runCatching {
        val header = parseRafHeader(headerBytes).getOrThrow()
        val jpegBytes = jpegPayload ?: extractEmbeddedJpeg(headerBytes).getOrThrow()
        val filmSim = extractFilmSimulation(jpegBytes)
        val (width, height) = parseJpegDimensions(jpegBytes)
        val orientation = extractOrientation(jpegBytes)

        RafPreviewResult(
            previewJpegBytes = jpegBytes,
            filmSimulation = filmSim,
            cameraModel = header.cameraModel,
            width = width,
            height = height,
            orientation = orientation
        )
    }

    /**
     * Extracts EXIF Tag 0x0112 orientation from embedded JPEG payload.
     */
    fun extractOrientation(jpegBytes: ByteArray): Int {
        return ExifOrientationHelper.extractOrientation(jpegBytes)
    }

    /**
     * Extracts unified [EmbeddedPreviewResult] representation.
     */
    fun extractUnifiedPreview(headerBytes: ByteArray, jpegPayload: ByteArray? = null): Result<EmbeddedPreviewResult> = runCatching {
        val startNs = System.nanoTime()
        val preview = extractPreviewResult(headerBytes, jpegPayload).getOrThrow()
        val elapsedMs = (System.nanoTime() - startNs) / 1_000_000L

        EmbeddedPreviewResult(
            jpegBytes = preview.previewJpegBytes,
            width = preview.width,
            height = preview.height,
            orientation = preview.orientation,
            format = "RAF",
            parseTimeMs = elapsedMs
        )
    }

    private fun parseFilmSimulationFromExif(jpegBytes: ByteArray): FujiFilmSimulation {
        if (jpegBytes.size < 4 || jpegBytes[0] != 0xFF.toByte() || jpegBytes[1] != 0xD8.toByte()) {
            return FujiFilmSimulation.UNKNOWN
        }

        var offset = 2
        while (offset + 4 <= jpegBytes.size) {
            if (jpegBytes[offset] != 0xFF.toByte()) break
            val marker = jpegBytes[offset + 1].toInt() and 0xFF
            offset += 2

            // Standalone markers
            if (marker == 0xD9 || marker == 0xDA) break // EOI or SOS

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
                    jpegBytes[exifDataOffset + 3] == 'f'.code.toByte()
                ) {
                    val tiffOffset = exifDataOffset + 6
                    val tiffLength = exifDataLength - 6
                    val sim = parseTiffMakerNoteForFilmSim(jpegBytes, tiffOffset, tiffLength)
                    if (sim != FujiFilmSimulation.UNKNOWN) return sim
                }
            }

            offset += segmentLength
        }
        return FujiFilmSimulation.UNKNOWN
    }

    private fun parseTiffMakerNoteForFilmSim(bytes: ByteArray, tiffOffset: Int, tiffLength: Int): FujiFilmSimulation {
        if (tiffLength < 8 || tiffOffset + 8 > bytes.size) return FujiFilmSimulation.UNKNOWN

        val isLittleEndian = bytes[tiffOffset] == 'I'.code.toByte() && bytes[tiffOffset + 1] == 'I'.code.toByte()
        val isBigEndian = bytes[tiffOffset] == 'M'.code.toByte() && bytes[tiffOffset + 1] == 'M'.code.toByte()
        if (!isLittleEndian && !isBigEndian) return FujiFilmSimulation.UNKNOWN

        val byteOrder = if (isLittleEndian) ByteOrder.LITTLE_ENDIAN else ByteOrder.BIG_ENDIAN
        val bb = ByteBuffer.wrap(bytes, tiffOffset, tiffLength).slice().order(byteOrder)

        val magic = bb.getShort(2).toInt() and 0xFFFF
        if (magic != 0x002A) return FujiFilmSimulation.UNKNOWN

        val ifd0Offset = bb.getInt(4)
        if (ifd0Offset < 8 || ifd0Offset + 2 > tiffLength) return FujiFilmSimulation.UNKNOWN

        return parseIfdEntries(bytes, tiffOffset, tiffLength, ifd0Offset, byteOrder, 0)
    }

    private fun parseIfdEntries(
        bytes: ByteArray,
        tiffOffset: Int,
        tiffLength: Int,
        ifdOffset: Int,
        byteOrder: ByteOrder,
        depth: Int
    ): FujiFilmSimulation {
        if (depth > 3 || ifdOffset + 2 > tiffLength) return FujiFilmSimulation.UNKNOWN

        val bb = ByteBuffer.wrap(bytes, tiffOffset, tiffLength).slice().order(byteOrder)
        val entryCount = bb.getShort(ifdOffset).toInt() and 0xFFFF
        var pos = ifdOffset + 2

        for (i in 0 until entryCount) {
            if (pos + 12 > tiffLength) break
            val tag = bb.getShort(pos).toInt() and 0xFFFF
            val type = bb.getShort(pos + 2).toInt() and 0xFFFF
            val count = bb.getInt(pos + 4)
            val valueOffset = bb.getInt(pos + 8)

            if (tag == TAG_FILM_MODE) {
                val tagValue = if (type == 3) { // SHORT
                    if (byteOrder == ByteOrder.LITTLE_ENDIAN) {
                        bb.getShort(pos + 8).toInt() and 0xFFFF
                    } else {
                        (valueOffset ushr 16) and 0xFFFF
                    }
                } else {
                    valueOffset
                }
                return FujiFilmSimulation.fromTagValue(tagValue)
            }

            // Exif IFD Pointer (0x8769)
            if (tag == 0x8769 && valueOffset in 8 until tiffLength) {
                val res = parseIfdEntries(bytes, tiffOffset, tiffLength, valueOffset, byteOrder, depth + 1)
                if (res != FujiFilmSimulation.UNKNOWN) return res
            }

            // MakerNote Pointer (0x927C)
            if (tag == 0x927C && valueOffset in 0 until tiffLength) {
                val makerNoteAbsOffset = tiffOffset + valueOffset
                val sim = parseFujiMakerNoteBlock(bytes, makerNoteAbsOffset, bytes.size - makerNoteAbsOffset)
                if (sim != FujiFilmSimulation.UNKNOWN) return sim
            }

            pos += 12
        }
        return FujiFilmSimulation.UNKNOWN
    }

    private fun parseFujiMakerNoteBlock(bytes: ByteArray, offset: Int, length: Int): FujiFilmSimulation {
        if (length < 14 || offset + 14 > bytes.size) return FujiFilmSimulation.UNKNOWN

        // Check for "FUJIFILM" signature
        if (bytes[offset] == 'F'.code.toByte() &&
            bytes[offset + 1] == 'U'.code.toByte() &&
            bytes[offset + 2] == 'J'.code.toByte() &&
            bytes[offset + 3] == 'I'.code.toByte() &&
            bytes[offset + 4] == 'F'.code.toByte() &&
            bytes[offset + 5] == 'I'.code.toByte() &&
            bytes[offset + 6] == 'L'.code.toByte() &&
            bytes[offset + 7] == 'M'.code.toByte()
        ) {
            // Fuji MakerNotes use Little-Endian IFD relative to the start of the MakerNote header
            val headerOffset = ByteBuffer.wrap(bytes, offset + 8, 4).order(ByteOrder.LITTLE_ENDIAN).int
            val ifdStart = offset + if (headerOffset in 8..1024) headerOffset else 12

            if (ifdStart + 2 > offset + length || ifdStart + 2 > bytes.size) return FujiFilmSimulation.UNKNOWN

            val bb = ByteBuffer.wrap(bytes, ifdStart, (offset + length) - ifdStart).order(ByteOrder.LITTLE_ENDIAN)
            val count = bb.short.toInt() and 0xFFFF

            var entryPos = 2
            for (i in 0 until count) {
                if (entryPos + 12 > bb.capacity()) break
                val tag = bb.getShort(entryPos).toInt() and 0xFFFF
                val type = bb.getShort(entryPos + 2).toInt() and 0xFFFF
                val tagVal = bb.getShort(entryPos + 8).toInt() and 0xFFFF

                if (tag == TAG_FILM_MODE) {
                    return FujiFilmSimulation.fromTagValue(tagVal)
                }
                entryPos += 12
            }
        }
        return FujiFilmSimulation.UNKNOWN
    }

    private fun scanForFilmSimulationTag(bytes: ByteArray): FujiFilmSimulation {
        // Direct pattern scan for Tag 0x1401 (0x01, 0x14) followed by type 0x0003 (0x03, 0x00)
        for (i in 0 until bytes.size - 12) {
            if (bytes[i] == 0x01.toByte() && bytes[i + 1] == 0x14.toByte() &&
                bytes[i + 2] == 0x03.toByte() && bytes[i + 3] == 0x00.toByte()
            ) {
                val tagVal = (bytes[i + 8].toInt() and 0xFF) or ((bytes[i + 9].toInt() and 0xFF) shl 8)
                val sim = FujiFilmSimulation.fromTagValue(tagVal)
                if (sim != FujiFilmSimulation.UNKNOWN) {
                    return sim
                }
            }
        }
        return FujiFilmSimulation.UNKNOWN
    }

    private fun parseJpegDimensions(jpegBytes: ByteArray): Pair<Int, Int> {
        if (jpegBytes.size < 4 || jpegBytes[0] != 0xFF.toByte() || jpegBytes[1] != 0xD8.toByte()) {
            return Pair(0, 0)
        }

        var offset = 2
        while (offset + 8 <= jpegBytes.size) {
            if (jpegBytes[offset] != 0xFF.toByte()) break
            val marker = jpegBytes[offset + 1].toInt() and 0xFF
            offset += 2

            if (marker == 0xD9 || marker == 0xDA) break // EOI or SOS

            val segmentLength = ((jpegBytes[offset].toInt() and 0xFF) shl 8) or
                    (jpegBytes[offset + 1].toInt() and 0xFF)
            if (segmentLength < 2 || offset + segmentLength > jpegBytes.size) break

            // SOF0 (0xC0), SOF1 (0xC1), SOF2 (0xC2)
            if (marker == 0xC0 || marker == 0xC1 || marker == 0xC2) {
                if (offset + 7 <= jpegBytes.size) {
                    val height = ((jpegBytes[offset + 3].toInt() and 0xFF) shl 8) or (jpegBytes[offset + 4].toInt() and 0xFF)
                    val width = ((jpegBytes[offset + 5].toInt() and 0xFF) shl 8) or (jpegBytes[offset + 6].toInt() and 0xFF)
                    return Pair(width, height)
                }
            }

            offset += segmentLength
        }
        return Pair(0, 0)
    }

    private fun readUInt32BigEndian(bytes: ByteArray, offset: Int): Long {
        return ((bytes[offset].toLong() and 0xFF) shl 24) or
                ((bytes[offset + 1].toLong() and 0xFF) shl 16) or
                ((bytes[offset + 2].toLong() and 0xFF) shl 8) or
                (bytes[offset + 3].toLong() and 0xFF)
    }
}
