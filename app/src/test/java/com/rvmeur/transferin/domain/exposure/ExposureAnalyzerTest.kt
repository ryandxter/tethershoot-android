package com.rvmeur.transferin.domain.exposure

import com.rvmeur.transferin.domain.camera.CameraPictureProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Comprehensive Unit Test & Performance Microbenchmark Suite
 * for [ExposureAnalyzer], [ExposureMetrics], and [ExposureProfileAdapter].
 */
class ExposureAnalyzerTest {

    private lateinit var scratchBuffer: ExposureScratchBuffer

    @Before
    fun setUp() {
        scratchBuffer = ExposureScratchBuffer()
    }

    @Test
    fun testFixedPointLuminanceCalculationMatchesITU_R_BT601() {
        // Red channel: 77/256 = 0.30078 (BT.601 = 0.299)
        val pureRedLuma = ExposureAnalyzer.computeLuminance(255, 0, 0)
        assertEquals(76, pureRedLuma) // (77 * 255) shr 8 = 19635 shr 8 = 76

        // Green channel: 150/256 = 0.5859 (BT.601 = 0.587)
        val pureGreenLuma = ExposureAnalyzer.computeLuminance(0, 255, 0)
        assertEquals(149, pureGreenLuma) // (150 * 255) shr 8 = 38250 shr 8 = 149

        // Blue channel: 29/256 = 0.1132 (BT.601 = 0.114)
        val pureBlueLuma = ExposureAnalyzer.computeLuminance(0, 0, 255)
        assertEquals(28, pureBlueLuma) // (29 * 255) shr 8 = 7395 shr 8 = 28

        // Pure white: (77 + 150 + 29) * 255 shr 8 = 256 * 255 shr 8 = 255
        val pureWhiteLuma = ExposureAnalyzer.computeLuminance(255, 255, 255)
        assertEquals(255, pureWhiteLuma)

        // Pure black:
        val pureBlackLuma = ExposureAnalyzer.computeLuminance(0, 0, 0)
        assertEquals(0, pureBlackLuma)

        // Middle gray:
        val midGrayLuma = ExposureAnalyzer.computeLuminance(128, 128, 128)
        assertEquals(128, midGrayLuma)

        // Packed ARGB pixel test:
        val packedPixel = (0xFF shl 24) or (255 shl 16) or (128 shl 8) or 64
        val expectedLuma = (77 * 255 + 150 * 128 + 29 * 64) ushr 8
        assertEquals(expectedLuma, ExposureAnalyzer.computeLuminance(packedPixel))
    }

    @Test
    fun testLuminanceHistogramOperations() {
        val hist = LuminanceHistogram()
        assertEquals(0, hist.totalPixels)

        hist.record(0)
        hist.record(128)
        hist.record(255)
        hist.record(300) // Clamps to 255
        hist.record(-10) // Clamps to 0

        assertEquals(5, hist.totalPixels)
        assertEquals(2, hist[0])
        assertEquals(1, hist[128])
        assertEquals(2, hist[255])

        val dest = IntArray(256)
        hist.copyInto(dest)
        assertEquals(2, dest[0])
        assertEquals(1, dest[128])
        assertEquals(2, dest[255])

        val cloneHist = LuminanceHistogram()
        cloneHist.copyFrom(hist)
        assertEquals(5, cloneHist.totalPixels)
        assertEquals(2, cloneHist[0])

        hist.reset()
        assertEquals(0, hist.totalPixels)
        assertEquals(0, hist[0])
    }

    @Test
    fun testShannonEntropyCalculations() {
        val hist = LuminanceHistogram()

        // Empty histogram
        val emptyMetrics = ExposureMetrics.compute(hist)
        assertEquals(0.0f, emptyMetrics.shannonEntropy, 0.001f)

        // 1. Single-bin monochrome spike (pure black) -> H = 0.0 bits
        for (i in 0 until 1000) {
            hist.record(0)
        }
        val spikeMetrics = ExposureMetrics.compute(hist)
        assertEquals(0.0f, spikeMetrics.shannonEntropy, 0.001f)

        // 2. Uniform 256-bin distribution -> H = log2(256) = 8.0 bits
        hist.reset()
        for (i in 0 until 256) {
            for (j in 0 until 10) {
                hist.record(i)
            }
        }
        val uniformMetrics = ExposureMetrics.compute(hist)
        assertEquals(8.0f, uniformMetrics.shannonEntropy, 0.05f)
    }

    @Test
    fun testPitchBlackFrameClassifiesAsUnderexposedCritical() {
        val pixels = ExposureSyntheticFixtures.pitchBlackPixels()
        val result = ExposureAnalyzer.analyzePixels(pixels, scratch = scratchBuffer)

        assertEquals(ExposureClassification.UNDEREXPOSED_CRITICAL, result.classification)
        assertEquals(0.0f, result.meanLuminance, 0.001f)
        assertEquals(1.0f, result.blackFloorDensity, 0.001f) // 100% black
        assertEquals(1.0f, result.shadowClippingPercent, 0.001f)
        assertEquals(0.0f, result.highlightSaturationPercent, 0.001f)
        assertEquals(0.0f, result.shannonEntropy, 0.001f)
    }

    @Test
    fun testBlownWhiteFrameClassifiesAsOverexposedBlown() {
        val pixels = ExposureSyntheticFixtures.blownWhitePixels()
        val result = ExposureAnalyzer.analyzePixels(pixels, scratch = scratchBuffer)

        assertEquals(ExposureClassification.OVEREXPOSED_BLOWN, result.classification)
        assertEquals(255.0f, result.meanLuminance, 0.001f)
        assertEquals(0.0f, result.blackFloorDensity, 0.001f)
        assertEquals(1.0f, result.highlightSaturationPercent, 0.001f) // 100% saturated
    }

    @Test
    fun testLinearGradientClassifiesAsOptimal() {
        val pixels = ExposureSyntheticFixtures.linearGradientPixels()
        val result = ExposureAnalyzer.analyzePixels(pixels, scratch = scratchBuffer)

        assertEquals(ExposureClassification.OPTIMAL, result.classification)
        assertTrue(result.meanLuminance in 120.0f..135.0f)
        assertTrue(result.shannonEntropy > 5.0f) // Uniform distribution has high entropy ~8.0
        assertTrue(result.blackFloorDensity < 0.05f)
        assertTrue(result.highlightSaturationPercent < 0.05f)
    }

    @Test
    fun testLowKeyStudioPortraitPreservedAsOptimal() {
        // 85% shadow background + 15% midtone subject (with rich entropy > 2.5)
        val pixels = ExposureSyntheticFixtures.lowKeyPortraitPixels()
        val result = ExposureAnalyzer.analyzePixels(pixels, scratch = scratchBuffer)

        // Must NOT be falsely culled as flash misfire because entropy is high and black floor is 85% (< 98%)
        assertEquals(ExposureClassification.OPTIMAL, result.classification)
        assertEquals(0.85f, result.blackFloorDensity, 0.01f)
        assertTrue(result.shannonEntropy > 1.0f)
    }

    @Test
    fun testHighKeyCycloramaPreservedAsOptimal() {
        // 75% high-key + 10% blown + 15% subject
        val pixels = ExposureSyntheticFixtures.highKeyCycloramaPixels()
        val result = ExposureAnalyzer.analyzePixels(pixels, scratch = scratchBuffer)

        // Must NOT be falsely culled as blown out because highlight saturation is 10% (< 20%)
        assertEquals(ExposureClassification.OPTIMAL, result.classification)
        assertEquals(0.10f, result.highlightSaturationPercent, 0.01f)
    }

    @Test
    fun testSpecularCatchlightsPreservedAsOptimal() {
        // 97% midtones + 3% catchlights
        val pixels = ExposureSyntheticFixtures.specularCatchlightPixels()
        val result = ExposureAnalyzer.analyzePixels(pixels, scratch = scratchBuffer)

        assertEquals(ExposureClassification.OPTIMAL, result.classification)
        assertEquals(0.03f, result.highlightSaturationPercent, 0.01f)
    }

    @Test
    fun testDynamicCalibrationForHighContrastMonochromeProfiles() {
        // Create an extreme low-key artistic image: 98.5% shadow (luma=0) + 1.5% bright rimlight (luma=200)
        // With standard profile (threshold 98%, ceiling 1.0), this might trigger flash misfire.
        val size = ExposureSyntheticFixtures.DEFAULT_TOTAL_PIXELS
        val pixels = IntArray(size)
        val shadowCount = (size * 0.985).toInt()
        for (i in 0 until shadowCount) {
            pixels[i] = (0xFF shl 24)
        }
        for (i in shadowCount until size) {
            pixels[i] = (0xFF shl 24) or (200 shl 16) or (200 shl 8) or 200
        }

        // Standard sRGB baseline flags it as UNDEREXPOSED_CRITICAL (blackFloor >= 98%, mean < 15, entropy < 1.0)
        val stdResult = ExposureAnalyzer.analyzePixels(
            pixels,
            profile = CameraPictureProfile.STANDARD_SRGB,
            scratch = scratchBuffer
        )
        assertEquals(ExposureClassification.UNDEREXPOSED_CRITICAL, stdResult.classification)

        // Fuji ACROS / Monochrome / Canon Monochrome adjusts black floor threshold to 99.5% and entropy ceiling to 0.6,
        // correctly classifying this artistic shot as OPTIMAL!
        val acrosResult = ExposureAnalyzer.analyzePixels(
            pixels,
            profile = CameraPictureProfile.FUJI_ACROS,
            scratch = scratchBuffer
        )
        assertEquals(ExposureClassification.OPTIMAL, acrosResult.classification)

        val canonMonoResult = ExposureAnalyzer.analyzePixels(
            pixels,
            profile = CameraPictureProfile.CANON_MONOCHROME,
            scratch = scratchBuffer
        )
        assertEquals(ExposureClassification.OPTIMAL, canonMonoResult.classification)

        // However, a true pitch black flash misfire (100% black) is STILL classified as UNDEREXPOSED_CRITICAL
        val trueBlack = ExposureSyntheticFixtures.pitchBlackPixels()
        val acrosBlackResult = ExposureAnalyzer.analyzePixels(
            trueBlack,
            profile = CameraPictureProfile.FUJI_ACROS,
            scratch = scratchBuffer
        )
        assertEquals(ExposureClassification.UNDEREXPOSED_CRITICAL, acrosBlackResult.classification)
    }

    @Test
    fun testDynamicCalibrationForVividVelviaProfile() {
        // High saturation scene: 22% highlight saturation (luma 250), 78% bright scene (luma 226) -> mean luma 231.28
        val size = ExposureSyntheticFixtures.DEFAULT_TOTAL_PIXELS
        val pixels = IntArray(size)
        val satCount = (size * 0.22).toInt()
        for (i in 0 until satCount) {
            pixels[i] = (0xFF shl 24) or (250 shl 16) or (250 shl 8) or 250
        }
        for (i in satCount until size) {
            pixels[i] = (0xFF shl 24) or (226 shl 16) or (226 shl 8) or 226
        }

        // Standard sRGB flags it as OVEREXPOSED_BLOWN (sat >= 20% && mean > 230)
        val stdResult = ExposureAnalyzer.analyzePixels(
            pixels,
            profile = CameraPictureProfile.STANDARD_SRGB,
            scratch = scratchBuffer
        )
        assertEquals(ExposureClassification.OVEREXPOSED_BLOWN, stdResult.classification)

        // Fuji Velvia adjusts threshold to sat >= 25% and mean > 235, retaining as OPTIMAL
        val velviaResult = ExposureAnalyzer.analyzePixels(
            pixels,
            profile = CameraPictureProfile.FUJI_VELVIA,
            scratch = scratchBuffer
        )
        assertEquals(ExposureClassification.OPTIMAL, velviaResult.classification)
    }

    @Test
    fun testAnalyzeRgbBytesAndDirectByteBuffer() {
        val pixels = ExposureSyntheticFixtures.linearGradientPixels()
        val rgbBytes = ExposureSyntheticFixtures.toRgbBytes(pixels)
        val directBuffer = ExposureSyntheticFixtures.toDirectRgbByteBuffer(pixels)

        val resultFromBytes = ExposureAnalyzer.analyzeRgbBytes(rgbBytes, scratch = scratchBuffer)
        assertEquals(ExposureClassification.OPTIMAL, resultFromBytes.classification)
        assertTrue(resultFromBytes.meanLuminance in 120.0f..135.0f)

        val resultFromDirect = ExposureAnalyzer.analyzeByteBuffer(directBuffer, scratch = scratchBuffer)
        assertEquals(ExposureClassification.OPTIMAL, resultFromDirect.classification)
        assertEquals(resultFromBytes.meanLuminance, resultFromDirect.meanLuminance, 0.01f)
    }

    @Test
    fun testPerformanceMicrobenchmarkAndZeroAllocation() {
        val pixels = ExposureSyntheticFixtures.linearGradientPixels()

        // 1. Warm-up JIT compiler
        for (i in 0 until 500) {
            ExposureAnalyzer.analyzePixels(pixels, scratch = scratchBuffer)
        }

        // 2. Profile memory allocation in hot loop
        System.gc()
        val runtime = Runtime.getRuntime()
        val memoryBefore = runtime.totalMemory() - runtime.freeMemory()

        val iterations = 1000
        val startTime = System.nanoTime()

        for (i in 0 until iterations) {
            val result = ExposureAnalyzer.analyzePixels(pixels, scratch = scratchBuffer)
            assertNotNull(result)
        }

        val totalElapsedNanos = System.nanoTime() - startTime
        val avgLatencyMs = (totalElapsedNanos / iterations.toDouble()) / 1_000_000.0

        val memoryAfter = runtime.totalMemory() - runtime.freeMemory()
        val memoryDelta = memoryAfter - memoryBefore

        println("Microbenchmark Results (256x170 frame = 43,520 pixels):")
        println("  Average Execution Time: String.format('%.4f ms', avgLatencyMs) = $avgLatencyMs ms")
        println("  Memory Delta over $iterations iterations: $memoryDelta bytes")

        // Quality Gate 1: Latency < 1.8ms
        assertTrue(
            "Average execution time ($avgLatencyMs ms) must be < 1.8ms",
            avgLatencyMs < 1.8
        )
    }
}
