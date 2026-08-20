package com.rvmeur.transferin.domain.exposure

import java.nio.ByteBuffer

/**
 * Deterministic synthetic frame fixtures for validating exposure analysis and classification.
 */
object ExposureSyntheticFixtures {

    const val DEFAULT_WIDTH = 256
    const val DEFAULT_HEIGHT = 170
    const val DEFAULT_TOTAL_PIXELS = DEFAULT_WIDTH * DEFAULT_HEIGHT // 43,520 pixels

    /**
     * Generates a pure pitch-black frame (100% Y=0, RGB=0,0,0).
     */
    fun pitchBlackPixels(size: Int = DEFAULT_TOTAL_PIXELS): IntArray {
        val pixels = IntArray(size)
        // 0xFF000000 (Opaque Black) -> R=0, G=0, B=0
        pixels.fill(0xFF000000.toInt())
        return pixels
    }

    /**
     * Generates a pure blown-white frame (100% Y=255, RGB=255,255,255).
     */
    fun blownWhitePixels(size: Int = DEFAULT_TOTAL_PIXELS): IntArray {
        val pixels = IntArray(size)
        // 0xFFFFFFFF (Opaque White) -> R=255, G=255, B=255
        pixels.fill(0xFFFFFFFF.toInt())
        return pixels
    }

    /**
     * Generates a linear gradient frame with perfectly uniform distribution across all 256 bins.
     */
    fun linearGradientPixels(
        width: Int = DEFAULT_WIDTH,
        height: Int = DEFAULT_HEIGHT
    ): IntArray {
        val total = width * height
        val pixels = IntArray(total)
        for (y in 0 until height) {
            for (x in 0 until width) {
                // Map x to 0..255 uniformly
                val luma = (x * 256) / width
                val rgb = (0xFF shl 24) or (luma shl 16) or (luma shl 8) or luma
                pixels[y * width + x] = rgb
            }
        }
        return pixels
    }

    /**
     * Generates a synthetic low-key portrait scene:
     * - 85% shadow background with luma Y in [0, 3] (e.g. Y=1)
     * - 15% midtone subject with luma Y in [80, 160] (multi-bin distribution, high entropy H >= 3.0)
     */
    fun lowKeyPortraitPixels(size: Int = DEFAULT_TOTAL_PIXELS): IntArray {
        val pixels = IntArray(size)
        val shadowCount = (size * 0.85).toInt()

        for (i in 0 until shadowCount) {
            // Shadow background with luma = 1
            pixels[i] = (0xFF shl 24) or (1 shl 16) or (1 shl 8) or 1
        }

        val midtoneCount = size - shadowCount
        for (i in 0 until midtoneCount) {
            // Distribute across bins 80..160
            val luma = 80 + (i % 81)
            pixels[shadowCount + i] = (0xFF shl 24) or (luma shl 16) or (luma shl 8) or luma
        }

        return pixels
    }

    /**
     * Generates a synthetic high-key cyclorama scene:
     * - 75% high-key bright backdrop (Y in [200, 240])
     * - 10% blown specular highlights (Y = 255)
     * - 15% subject midtones (Y in [100, 180])
     */
    fun highKeyCycloramaPixels(size: Int = DEFAULT_TOTAL_PIXELS): IntArray {
        val pixels = IntArray(size)
        val highKeyCount = (size * 0.75).toInt()
        val blownCount = (size * 0.10).toInt()
        val subjectCount = size - highKeyCount - blownCount

        var idx = 0
        for (i in 0 until highKeyCount) {
            val luma = 200 + (i % 41) // 200..240
            pixels[idx++] = (0xFF shl 24) or (luma shl 16) or (luma shl 8) or luma
        }

        for (i in 0 until blownCount) {
            val luma = 255
            pixels[idx++] = (0xFF shl 24) or (luma shl 16) or (luma shl 8) or luma
        }

        for (i in 0 until subjectCount) {
            val luma = 100 + (i % 81) // 100..180
            pixels[idx++] = (0xFF shl 24) or (luma shl 16) or (luma shl 8) or luma
        }

        return pixels
    }

    /**
     * Generates a scene with 97% midtones and 3% saturated specular catchlights.
     */
    fun specularCatchlightPixels(size: Int = DEFAULT_TOTAL_PIXELS): IntArray {
        val pixels = IntArray(size)
        val specularCount = (size * 0.03).toInt()
        val midtoneCount = size - specularCount

        for (i in 0 until midtoneCount) {
            val luma = 100 + (i % 51) // 100..150
            pixels[i] = (0xFF shl 24) or (luma shl 16) or (luma shl 8) or luma
        }

        for (i in 0 until specularCount) {
            val luma = 255
            pixels[midtoneCount + i] = (0xFF shl 24) or (luma shl 16) or (luma shl 8) or luma
        }

        return pixels
    }

    /**
     * Converts packed ARGB pixel array into RGB byte array (3 bytes per pixel).
     */
    fun toRgbBytes(pixels: IntArray): ByteArray {
        val bytes = ByteArray(pixels.size * 3)
        var byteIdx = 0
        for (pixel in pixels) {
            bytes[byteIdx++] = ((pixel ushr 16) and 0xFF).toByte()
            bytes[byteIdx++] = ((pixel ushr 8) and 0xFF).toByte()
            bytes[byteIdx++] = (pixel and 0xFF).toByte()
        }
        return bytes
    }

    /**
     * Converts packed ARGB pixel array into Direct off-heap ByteBuffer (RGB 3 bytes per pixel).
     */
    fun toDirectRgbByteBuffer(pixels: IntArray): ByteBuffer {
        val rgbBytes = toRgbBytes(pixels)
        val buffer = ByteBuffer.allocateDirect(rgbBytes.size)
        buffer.put(rgbBytes)
        buffer.flip()
        return buffer
    }
}
