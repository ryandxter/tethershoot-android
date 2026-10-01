package com.rvmeur.transferin.domain.culling

/**
 * Encapsulates AI-detected face, blink, focus, and visual quality metrics for a single image/preview frame.
 *
 * @property faceCount Number of human faces detected in the frame.
 * @property isBlinkDetected True if one or more subjects are captured with closed eyes.
 * @property isBlurDetected True if the primary subject or overall frame exhibits unacceptable blur / poor focus.
 * @property subjectSharpnessScore Focus/sharpness score (0.0 to 1.0+, where >= 0.5 is sharp).
 * @property eyeOpenRatio Ratio of eye openness (1.0 = fully open, 0.0 = fully closed).
 */
data class AiFaceQualityMetrics(
    val faceCount: Int = 0,
    val isBlinkDetected: Boolean = false,
    val isBlurDetected: Boolean = false,
    val subjectSharpnessScore: Float = 1.0f,
    val eyeOpenRatio: Float = 1.0f
)
