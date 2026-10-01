package com.rvmeur.transferin.domain.culling

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class AiFaceCullingEngineTest {

    private lateinit var engine: DefaultAiFaceCullingEngine

    @Before
    fun setUp() {
        engine = DefaultAiFaceCullingEngine()
    }

    @Test
    fun `evaluate returns OPTIMAL when face is sharp and eyes are open`() {
        val metrics = AiFaceQualityMetrics(
            faceCount = 1,
            isBlinkDetected = false,
            isBlurDetected = false,
            subjectSharpnessScore = 0.95f,
            eyeOpenRatio = 0.85f
        )
        val verdict = engine.evaluate(metrics)
        assertEquals(CullingVerdict.OPTIMAL, verdict)
    }

    @Test
    fun `evaluate returns CLOSED_EYES_DETECTED when isBlinkDetected is true`() {
        val metrics = AiFaceQualityMetrics(
            faceCount = 1,
            isBlinkDetected = true,
            isBlurDetected = false,
            subjectSharpnessScore = 0.9f,
            eyeOpenRatio = 0.8f
        )
        val verdict = engine.evaluate(metrics)
        assertEquals(CullingVerdict.CLOSED_EYES_DETECTED, verdict)
    }

    @Test
    fun `evaluate returns CLOSED_EYES_DETECTED when eyeOpenRatio is below threshold`() {
        val metrics = AiFaceQualityMetrics(
            faceCount = 2,
            isBlinkDetected = false,
            isBlurDetected = false,
            subjectSharpnessScore = 0.85f,
            eyeOpenRatio = 0.35f
        )
        val verdict = engine.evaluate(metrics)
        assertEquals(CullingVerdict.CLOSED_EYES_DETECTED, verdict)
    }

    @Test
    fun `evaluate returns BLURRED_SUBJECT when isBlurDetected is true`() {
        val metrics = AiFaceQualityMetrics(
            faceCount = 1,
            isBlinkDetected = false,
            isBlurDetected = true,
            subjectSharpnessScore = 0.8f,
            eyeOpenRatio = 0.9f
        )
        val verdict = engine.evaluate(metrics)
        assertEquals(CullingVerdict.BLURRED_SUBJECT, verdict)
    }

    @Test
    fun `evaluate returns BLURRED_SUBJECT when subjectSharpnessScore is below threshold`() {
        val metrics = AiFaceQualityMetrics(
            faceCount = 1,
            isBlinkDetected = false,
            isBlurDetected = false,
            subjectSharpnessScore = 0.3f,
            eyeOpenRatio = 0.9f
        )
        val verdict = engine.evaluate(metrics)
        assertEquals(CullingVerdict.BLURRED_SUBJECT, verdict)
    }

    @Test
    fun `evaluate returns BLURRED_SUBJECT when both blur and blink are detected`() {
        val metrics = AiFaceQualityMetrics(
            faceCount = 1,
            isBlinkDetected = true,
            isBlurDetected = true,
            subjectSharpnessScore = 0.2f,
            eyeOpenRatio = 0.1f
        )
        val verdict = engine.evaluate(metrics)
        assertEquals(CullingVerdict.BLURRED_SUBJECT, verdict)
    }

    @Test
    fun `evaluate returns OPTIMAL when no face is present and scene is sharp`() {
        val metrics = AiFaceQualityMetrics(
            faceCount = 0,
            isBlinkDetected = false,
            isBlurDetected = false,
            subjectSharpnessScore = 0.8f,
            eyeOpenRatio = 0.0f
        )
        val verdict = engine.evaluate(metrics)
        assertEquals(CullingVerdict.OPTIMAL, verdict)
    }

    @Test
    fun `evaluate returns BLURRED_SUBJECT when no face is present but scene is blurry`() {
        val metrics = AiFaceQualityMetrics(
            faceCount = 0,
            isBlinkDetected = false,
            isBlurDetected = true,
            subjectSharpnessScore = 0.2f,
            eyeOpenRatio = 1.0f
        )
        val verdict = engine.evaluate(metrics)
        assertEquals(CullingVerdict.BLURRED_SUBJECT, verdict)
    }

    @Test
    fun `custom thresholds for eyeOpenRatio and sharpness are respected`() {
        val customEngine = DefaultAiFaceCullingEngine(
            eyeOpenThreshold = 0.7f,
            blurSharpnessThreshold = 0.6f
        )

        // Eye openness 0.65 is below 0.7 threshold
        val blinkMetrics = AiFaceQualityMetrics(
            faceCount = 1,
            eyeOpenRatio = 0.65f,
            subjectSharpnessScore = 0.8f
        )
        assertEquals(CullingVerdict.CLOSED_EYES_DETECTED, customEngine.evaluate(blinkMetrics))

        // Sharpness 0.55 is below 0.6 threshold
        val blurMetrics = AiFaceQualityMetrics(
            faceCount = 1,
            eyeOpenRatio = 0.9f,
            subjectSharpnessScore = 0.55f
        )
        assertEquals(CullingVerdict.BLURRED_SUBJECT, customEngine.evaluate(blurMetrics))
    }

    @Test
    fun `combineVerdicts prioritizes critical exposure over subject faults`() {
        assertEquals(
            CullingVerdict.UNDEREXPOSED_CRITICAL,
            engine.combineVerdicts(CullingVerdict.UNDEREXPOSED_CRITICAL, CullingVerdict.CLOSED_EYES_DETECTED)
        )
        assertEquals(
            CullingVerdict.OVEREXPOSED_BLOWN,
            engine.combineVerdicts(CullingVerdict.OVEREXPOSED_BLOWN, CullingVerdict.BLURRED_SUBJECT)
        )
    }

    @Test
    fun `combineVerdicts applies subject quality verdict when exposure is optimal`() {
        assertEquals(
            CullingVerdict.CLOSED_EYES_DETECTED,
            engine.combineVerdicts(CullingVerdict.OPTIMAL, CullingVerdict.CLOSED_EYES_DETECTED)
        )
        assertEquals(
            CullingVerdict.BLURRED_SUBJECT,
            engine.combineVerdicts(CullingVerdict.OPTIMAL, CullingVerdict.BLURRED_SUBJECT)
        )
    }

    @Test
    fun `combineVerdicts applies subject quality verdict when exposure is dynamic profile adjusted`() {
        assertEquals(
            CullingVerdict.CLOSED_EYES_DETECTED,
            engine.combineVerdicts(CullingVerdict.DYNAMIC_PROFILE_ADJUSTED, CullingVerdict.CLOSED_EYES_DETECTED)
        )
        assertEquals(
            CullingVerdict.BLURRED_SUBJECT,
            engine.combineVerdicts(CullingVerdict.DYNAMIC_PROFILE_ADJUSTED, CullingVerdict.BLURRED_SUBJECT)
        )
    }

    @Test
    fun `combineVerdicts returns optimal when both exposure and subject quality are optimal`() {
        assertEquals(
            CullingVerdict.OPTIMAL,
            engine.combineVerdicts(CullingVerdict.OPTIMAL, CullingVerdict.OPTIMAL)
        )
    }

    @Test
    fun `analyzeAndEvaluate delegates to pluggable detector`() = runTest {
        val mockDetector = FaceQualityDetector {
            AiFaceQualityMetrics(
                faceCount = 1,
                isBlinkDetected = true,
                subjectSharpnessScore = 0.9f
            )
        }
        val customEngine = DefaultAiFaceCullingEngine(detector = mockDetector)
        val dummyBytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())

        val verdict = customEngine.analyzeAndEvaluate(dummyBytes)
        assertEquals(CullingVerdict.CLOSED_EYES_DETECTED, verdict)
    }

    @Test
    fun `analyzeAndEvaluate returns OPTIMAL when detector is null or image is empty`() = runTest {
        val noDetectorEngine = DefaultAiFaceCullingEngine(detector = null)
        val result1 = noDetectorEngine.analyzeAndEvaluate(byteArrayOf(1, 2, 3))
        assertEquals(CullingVerdict.OPTIMAL, result1)

        val result2 = noDetectorEngine.analyzeAndEvaluate(byteArrayOf())
        assertEquals(CullingVerdict.OPTIMAL, result2)
    }
}
