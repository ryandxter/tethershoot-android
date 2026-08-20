package com.rvmeur.transferin.domain.exposure

import com.rvmeur.transferin.domain.camera.CameraPictureProfile

/**
 * Threshold configuration used to calibrate exposure evaluation rules
 * based on camera picture profile and film simulation dynamics.
 */
data class ExposureProfileThresholds(
    val blackFloorDensityThreshold: Float = 0.98f,
    val entropyCeiling: Float = 1.0f,
    val meanLumaLowThreshold: Float = 15.0f,
    val highlightSaturationThreshold: Float = 0.20f,
    val meanLumaHighThreshold: Float = 230.0f
)

/**
 * Adapts exposure classification thresholds according to camera picture profiles
 * (such as high-contrast B&W film simulations or vivid color profiles).
 */
object ExposureProfileAdapter {

    private val STANDARD_THRESHOLDS = ExposureProfileThresholds(
        blackFloorDensityThreshold = 0.98f,
        entropyCeiling = 1.0f,
        meanLumaLowThreshold = 15.0f,
        highlightSaturationThreshold = 0.20f,
        meanLumaHighThreshold = 230.0f
    )

    private val MONOCHROME_HIGH_CONTRAST_THRESHOLDS = ExposureProfileThresholds(
        blackFloorDensityThreshold = 0.995f,
        entropyCeiling = 0.6f,
        meanLumaLowThreshold = 10.0f,
        highlightSaturationThreshold = 0.20f,
        meanLumaHighThreshold = 230.0f
    )

    private val VIVID_THRESHOLDS = ExposureProfileThresholds(
        blackFloorDensityThreshold = 0.98f,
        entropyCeiling = 1.0f,
        meanLumaLowThreshold = 15.0f,
        highlightSaturationThreshold = 0.25f,
        meanLumaHighThreshold = 235.0f
    )

    /**
     * Resolves the appropriate [ExposureProfileThresholds] for the given [CameraPictureProfile].
     */
    fun getThresholds(profile: CameraPictureProfile = CameraPictureProfile.STANDARD_SRGB): ExposureProfileThresholds {
        return when (profile) {
            CameraPictureProfile.FUJI_ACROS,
            CameraPictureProfile.FUJI_MONOCHROME,
            CameraPictureProfile.CANON_MONOCHROME,
            CameraPictureProfile.SONY_BW,
            CameraPictureProfile.NIKON_MONOCHROME -> MONOCHROME_HIGH_CONTRAST_THRESHOLDS

            CameraPictureProfile.FUJI_VELVIA,
            CameraPictureProfile.SONY_VIVID -> VIVID_THRESHOLDS

            CameraPictureProfile.STANDARD_SRGB,
            CameraPictureProfile.FUJI_CLASSIC_CHROME,
            CameraPictureProfile.CANON_PORTRAIT,
            CameraPictureProfile.CUSTOM -> STANDARD_THRESHOLDS
        }
    }

    /**
     * Classifies the exposure metrics using the profile-adapted thresholds.
     */
    fun classify(
        metrics: ExposureMetrics,
        profile: CameraPictureProfile = CameraPictureProfile.STANDARD_SRGB
    ): ExposureClassification {
        val thresholds = getThresholds(profile)

        // Flash misfire / Total underexposure check:
        // Must satisfy all 3 conditions: black-floor density >= threshold, mean luma < threshold, and entropy < threshold
        if (metrics.blackFloorDensity >= thresholds.blackFloorDensityThreshold &&
            metrics.meanLuminance < thresholds.meanLumaLowThreshold &&
            metrics.shannonEntropy < thresholds.entropyCeiling
        ) {
            return ExposureClassification.UNDEREXPOSED_CRITICAL
        }

        // Highlight blowout / Catastrophic overexposure check:
        // Must satisfy dual conditions: highlight saturation >= threshold and mean luma > threshold
        if (metrics.highlightSaturationPercent >= thresholds.highlightSaturationThreshold &&
            metrics.meanLuminance > thresholds.meanLumaHighThreshold
        ) {
            return ExposureClassification.OVEREXPOSED_BLOWN
        }

        return ExposureClassification.OPTIMAL
    }
}
