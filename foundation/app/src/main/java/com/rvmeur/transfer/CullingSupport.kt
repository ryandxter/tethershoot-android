package com.rvmeur.transfer

import android.graphics.Bitmap
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile

/* =============================================================================
 * HASIL AUDIT PANEN DARI APLIKASI LAMA:
 *
 * 1. LuminanceHistogram.kt (asal: com.rvmeur.transfer.domain.exposure.LuminanceHistogram):
 *    - Audit: 100% algoritma murni tanpa ketergantungan library/framework.
 *    - Port: Diadopsi utuh dengan representasi 256 bin nol alokasi heap di hot-loop.
 *
 * 2. ExposureMetrics.kt & ExposureAnalyzer.kt (asal: com.rvmeur.transfer.domain.exposure):
 *    - Audit: Logika komputasi luminans ITU-R BT.601 integer bit-shift ((77*R + 150*G + 29*B) >> 8)
 *      dan kalkulasi statistik histogram (meanLuminance, blackFloorDensity, shadowClippingPercent,
 *      highlightSaturationPercent, shannonEntropy) murni dan terbukti stabil.
 *    - Port: Diadopsi dengan melepaskan ketergantungan enum CameraPictureProfile lama agar
 *      mandiri di fondasi, dengan performa tinggi untuk pratinjau bitmap.
 *
 * 3. RafPreviewExtractor.kt (asal: com.rvmeur.transfer.data.usb.vendor.fuji.RafPreviewExtractor):
 *    - Audit: Kode lama memiliki ketergantungan pada model ExifOrientationHelper, EmbeddedPreviewResult,
 *      dan parser MakerNote film simulation. Namun struktur inti kontainer Fujifilm RAF (magic
 *      "FUJIFILMCCD-RAW ", offset JPEG pada bita 84, panjang pada bita 88, marker SOI 0xFFD8)
 *      adalah spesifikasi biner standar deterministik.
 *    - Port: Ditulis ulang minimal dari prinsip biner dasarnya menggunakan RandomAccessFile langsung
 *      ke berkas/aliran, membersihkan semua dependensi internal lama, dan menyediakan fungsi
 *      ekstraksi langsung ke berkas pratinjau <nama>.preview.jpg.
 * ============================================================================= */

/**
 * Kontainer histogram luminans 256-bin berkinerja tinggi.
 *
 * Asal: com.rvmeur.transfer.domain.exposure.LuminanceHistogram
 */
class LuminanceHistogram(
    val bins: IntArray = IntArray(BIN_COUNT)
) {
    companion object {
        const val BIN_COUNT = 256
    }

    var totalPixels: Int = 0
        private set

    init {
        require(bins.size == BIN_COUNT) { "Histogram bins must have size exactly $BIN_COUNT" }
        recalculateTotal()
    }

    fun reset() {
        bins.fill(0)
        totalPixels = 0
    }

    fun record(luma: Int) {
        val bin = if (luma < 0) 0 else if (luma > 255) 255 else luma
        bins[bin]++
        totalPixels++
    }

    operator fun get(binIndex: Int): Int = bins[binIndex]

    operator fun set(binIndex: Int, value: Int) {
        val old = bins[binIndex]
        bins[binIndex] = value
        totalPixels += (value - old)
    }

    fun copyInto(destination: IntArray) {
        require(destination.size >= BIN_COUNT) {
            "Destination array must have size >= $BIN_COUNT, got ${destination.size}"
        }
        System.arraycopy(bins, 0, destination, 0, BIN_COUNT)
    }

    fun recalculateTotal(): Int {
        var count = 0
        for (i in 0 until BIN_COUNT) {
            count += bins[i]
        }
        totalPixels = count
        return count
    }
}

/**
 * Metrik statistik keterpaparan (exposure) yang dihitung dari LuminanceHistogram.
 *
 * Asal: com.rvmeur.transfer.domain.exposure.ExposureMetrics
 */
data class ExposureMetrics(
    val meanLuminance: Float,
    val blackFloorDensity: Float,
    val shadowClippingPercent: Float,
    val highlightSaturationPercent: Float,
    val shannonEntropy: Float,
    val histogramBins: IntArray,
    val totalPixels: Int
) {
    companion object {
        private const val INV_LN_2 = 1.4426950408889634

        fun compute(
            histogram: LuminanceHistogram,
            destinationBins: IntArray? = null
        ): ExposureMetrics {
            val total = histogram.totalPixels
            if (total == 0) {
                val bins = destinationBins ?: IntArray(LuminanceHistogram.BIN_COUNT)
                histogram.copyInto(bins)
                return ExposureMetrics(
                    meanLuminance = 0.0f,
                    blackFloorDensity = 0.0f,
                    shadowClippingPercent = 0.0f,
                    highlightSaturationPercent = 0.0f,
                    shannonEntropy = 0.0f,
                    histogramBins = bins,
                    totalPixels = 0
                )
            }

            val bins = destinationBins ?: IntArray(LuminanceHistogram.BIN_COUNT)
            histogram.copyInto(bins)

            var lumaSum = 0L
            var blackFloorCount = 0
            var shadowClipCount = 0
            var highlightSatCount = 0
            var sumCountLogCount = 0.0

            for (i in 0 until LuminanceHistogram.BIN_COUNT) {
                val count = bins[i]
                if (count > 0) {
                    lumaSum += (i.toLong() * count)
                    val countD = count.toDouble()
                    sumCountLogCount += countD * (Math.log(countD) * INV_LN_2)
                }
                // Lantai hitam pekat: bin 0..3 (misfire blitz / tutup lensa)
                if (i <= 3) {
                    blackFloorCount += count
                }
                // Bayangan gelap terpotong: bin 0..15
                if (i <= 15) {
                    shadowClipCount += count
                }
                // Sorotan jenuh / blowout: bin 248..255
                if (i >= 248) {
                    highlightSatCount += count
                }
            }

            val totalD = total.toDouble()
            val totalF = total.toFloat()
            val log2Total = Math.log(totalD) * INV_LN_2
            val entropy = (log2Total - (sumCountLogCount / totalD)).coerceAtLeast(0.0).toFloat()

            return ExposureMetrics(
                meanLuminance = (lumaSum.toFloat() / totalF),
                blackFloorDensity = (blackFloorCount.toFloat() / totalF),
                shadowClippingPercent = (shadowClipCount.toFloat() / totalF),
                highlightSaturationPercent = (highlightSatCount.toFloat() / totalF),
                shannonEntropy = entropy,
                histogramBins = bins,
                totalPixels = total
            )
        }
    }
}

/**
 * Mesin analisis pencahayaan berbasis ITU-R BT.601 integer math.
 *
 * Asal: com.rvmeur.transfer.domain.exposure.ExposureAnalyzer
 */
object ExposureAnalyzer {

    /**
     * Menghitung nilai luminans 8-bit dari kanal RGB dengan formula ITU-R BT.601:
     *   Y = (77 * R + 150 * G + 29 * B) ushr 8
     */
    inline fun computeLuminance(r: Int, g: Int, b: Int): Int {
        return (77 * (r and 0xFF) + 150 * (g and 0xFF) + 29 * (b and 0xFF)) ushr 8
    }

    inline fun computeLuminance(argbPixel: Int): Int {
        val r = (argbPixel ushr 16) and 0xFF
        val g = (argbPixel ushr 8) and 0xFF
        val b = argbPixel and 0xFF
        return (77 * r + 150 * g + 29 * b) ushr 8
    }

    /**
     * Menganalisis Bitmap Android dengan pemindaian per baris untuk meminimalkan beban memori.
     */
    fun analyzeBitmap(
        bitmap: Bitmap,
        sampleStep: Int = 1
    ): ExposureMetrics {
        require(sampleStep >= 1) { "sampleStep must be >= 1" }
        val width = bitmap.width
        val height = bitmap.height
        val rowBuffer = IntArray(width)
        val histogram = LuminanceHistogram()

        var y = 0
        while (y < height) {
            bitmap.getPixels(rowBuffer, 0, width, 0, y, width, 1)
            var x = 0
            while (x < width) {
                val pixel = rowBuffer[x]
                val r = (pixel ushr 16) and 0xFF
                val g = (pixel ushr 8) and 0xFF
                val b = pixel and 0xFF
                val luma = (77 * r + 150 * g + 29 * b) ushr 8
                histogram.record(luma)
                x += sampleStep
            }
            y += sampleStep
        }
        histogram.recalculateTotal()
        return ExposureMetrics.compute(histogram)
    }
}

/**
 * Pengekstrak pratinjau JPEG tertanam dari kontainer Fujifilm .RAF.
 *
 * Asal: com.rvmeur.transfer.data.usb.vendor.fuji.RafPreviewExtractor
 * Ditulis ulang minimal dari prinsip struktur biner RAF tanpa dependensi eksternal.
 */
object RafPreviewExtractor {

    private val RAF_MAGIC = byteArrayOf(
        0x46, 0x55, 0x4A, 0x49, 0x46, 0x49, 0x4C, 0x4D, // "FUJIFILM"
        0x43, 0x43, 0x44, 0x2D, 0x52, 0x41, 0x57, 0x20  // "CCD-RAW "
    )

    private const val MIN_HEADER_LENGTH = 92
    private const val OFFSET_JPEG_OFFSET = 84
    private const val OFFSET_JPEG_LENGTH = 88

    /**
     * Membaca unsigned 32-bit big endian integer dari buffer byte.
     */
    private fun readUInt32BigEndian(bytes: ByteArray, offset: Int): Long {
        return ((bytes[offset].toLong() and 0xFFL) shl 24) or
            ((bytes[offset + 1].toLong() and 0xFFL) shl 16) or
            ((bytes[offset + 2].toLong() and 0xFFL) shl 8) or
            (bytes[offset + 3].toLong() and 0xFFL)
    }

    /**
     * Mengekstrak bita JPEG pratinjau tertanam langsung dari berkas RAF di disk.
     */
    fun extractEmbeddedJpeg(rafFile: File): ByteArray? {
        if (!rafFile.exists() || rafFile.length() < MIN_HEADER_LENGTH) {
            return null
        }
        return try {
            RandomAccessFile(rafFile, "r").use { raf ->
                val headerBytes = ByteArray(128.coerceAtLeast(MIN_HEADER_LENGTH))
                raf.readFully(headerBytes)

                // Validasi magic Fujifilm RAF
                for (i in RAF_MAGIC.indices) {
                    if (headerBytes[i] != RAF_MAGIC[i]) {
                        return null
                    }
                }

                val jpegOffset = readUInt32BigEndian(headerBytes, OFFSET_JPEG_OFFSET)
                val jpegLength = readUInt32BigEndian(headerBytes, OFFSET_JPEG_LENGTH)

                if (jpegOffset < 120L || jpegLength <= 0L || jpegOffset + jpegLength > rafFile.length()) {
                    return null
                }

                raf.seek(jpegOffset)
                val jpegBytes = ByteArray(jpegLength.toInt())
                raf.readFully(jpegBytes)

                // Validasi SOI JPEG (0xFF 0xD8)
                if (jpegBytes.size < 2 || jpegBytes[0] != 0xFF.toByte() || jpegBytes[1] != 0xD8.toByte()) {
                    return null
                }
                jpegBytes
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Mengekstrak pratinjau JPEG tertanam dan menyimpannya langsung ke berkas output.
     */
    fun extractAndSavePreview(rafFile: File, outputFile: File): Boolean {
        if (!rafFile.exists() || rafFile.length() < MIN_HEADER_LENGTH) {
            return false
        }
        return try {
            RandomAccessFile(rafFile, "r").use { raf ->
                val headerBytes = ByteArray(128.coerceAtLeast(MIN_HEADER_LENGTH))
                raf.readFully(headerBytes)

                for (i in RAF_MAGIC.indices) {
                    if (headerBytes[i] != RAF_MAGIC[i]) {
                        return false
                    }
                }

                val jpegOffset = readUInt32BigEndian(headerBytes, OFFSET_JPEG_OFFSET)
                val jpegLength = readUInt32BigEndian(headerBytes, OFFSET_JPEG_LENGTH)

                if (jpegOffset < 120L || jpegLength <= 0L || jpegOffset + jpegLength > rafFile.length()) {
                    return false
                }

                raf.seek(jpegOffset)
                // Pastikan dua bita awal adalah SOI JPEG
                val soi = ByteArray(2)
                raf.readFully(soi)
                if (soi[0] != 0xFF.toByte() || soi[1] != 0xD8.toByte()) {
                    return false
                }
                raf.seek(jpegOffset)

                // Tulis streaming langsung ke berkas output dengan buffer 16KB
                val buffer = ByteArray(16384)
                var remaining = jpegLength
                FileOutputStream(outputFile).use { fos ->
                    while (remaining > 0) {
                        val toRead = Math.min(buffer.size.toLong(), remaining).toInt()
                        raf.readFully(buffer, 0, toRead)
                        fos.write(buffer, 0, toRead)
                        remaining -= toRead
                    }
                    fos.flush()
                }
                outputFile.exists() && outputFile.length() == jpegLength
            }
        } catch (_: Exception) {
            outputFile.delete()
            false
        }
    }
}
