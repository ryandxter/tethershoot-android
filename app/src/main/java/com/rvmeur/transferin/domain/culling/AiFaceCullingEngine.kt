package com.rvmeur.transferin.domain.culling

/**
 * Pluggable detector contract for ML/CV models (such as NCNN, MediaPipe, OpenCV, ML Kit).
 */
fun interface FaceQualityDetector {
    /**
     * Analyzes image bytes and returns [AiFaceQualityMetrics].
     */
    suspend fun detect(imageBytes: ByteArray): AiFaceQualityMetrics
}

/**
 * High-performance domain engine interface for AI face, blink, and quality culling.
 */
interface AiFaceCullingEngine {
    /**
     * Classifies face and visual quality metrics into a [CullingVerdict].
     */
    fun evaluate(metrics: AiFaceQualityMetrics): CullingVerdict

    /**
     * Combines an exposure verdict and a subject quality verdict into a final prioritized verdict.
     * Exposure critical faults (UNDEREXPOSED_CRITICAL, OVEREXPOSED_BLOWN) take top priority,
     * followed by subject faults (BLURRED_SUBJECT, CLOSED_EYES_DETECTED), and finally optimal/adjusted.
     */
    fun combineVerdicts(exposureVerdict: CullingVerdict, subjectVerdict: CullingVerdict): CullingVerdict

    /**
     * Executes end-to-end detection via the configured [FaceQualityDetector] and classifies the result.
     */
    suspend fun analyzeAndEvaluate(imageBytes: ByteArray): CullingVerdict
}

/**
 * Default rule evaluator for AI face, blink, and sharpness culling.
 *
 * @property detector Optional pluggable [FaceQualityDetector].
 * @property eyeOpenThreshold Threshold below which eyes are classified as closed / blinking (default 0.5f).
 * @property blurSharpnessThreshold Threshold below which the frame/subject is classified as blurred (default 0.5f).
 */
class DefaultAiFaceCullingEngine(
    private val detector: FaceQualityDetector? = null,
    private val eyeOpenThreshold: Float = DEFAULT_EYE_OPEN_THRESHOLD,
    private val blurSharpnessThreshold: Float = DEFAULT_BLUR_SHARPNESS_THRESHOLD
) : AiFaceCullingEngine {

    companion object {
        const val DEFAULT_EYE_OPEN_THRESHOLD: Float = 0.5f
        const val DEFAULT_BLUR_SHARPNESS_THRESHOLD: Float = 0.5f
    }

    override fun evaluate(metrics: AiFaceQualityMetrics): CullingVerdict {
        // 1. Focus / Blur check: applies to both portrait (with faces) and non-portrait frames
        if (metrics.isBlurDetected || metrics.subjectSharpnessScore < blurSharpnessThreshold) {
            return CullingVerdict.BLURRED_SUBJECT
        }

        // 2. Face / Blink check: evaluated when one or more faces are present
        if (metrics.faceCount > 0 && (metrics.isBlinkDetected || metrics.eyeOpenRatio < eyeOpenThreshold)) {
            return CullingVerdict.CLOSED_EYES_DETECTED
        }

        // 3. Fallback when blink was explicitly flagged
        if (metrics.isBlinkDetected) {
            return CullingVerdict.CLOSED_EYES_DETECTED
        }

        return CullingVerdict.OPTIMAL
    }

    override fun combineVerdicts(
        exposureVerdict: CullingVerdict,
        subjectVerdict: CullingVerdict
    ): CullingVerdict {
        return when (exposureVerdict) {
            CullingVerdict.UNDEREXPOSED_CRITICAL,
            CullingVerdict.OVEREXPOSED_BLOWN -> exposureVerdict
            else -> {
                when (subjectVerdict) {
                    CullingVerdict.BLURRED_SUBJECT,
                    CullingVerdict.CLOSED_EYES_DETECTED -> subjectVerdict
                    else -> exposureVerdict
                }
            }
        }
    }

    override suspend fun analyzeAndEvaluate(imageBytes: ByteArray): CullingVerdict {
        if (detector == null || imageBytes.isEmpty()) {
            return CullingVerdict.OPTIMAL
        }
        val metrics = detector.detect(imageBytes)
        return evaluate(metrics)
    }
}
