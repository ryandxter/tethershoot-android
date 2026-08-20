package com.rvmeur.transferin.domain.culling

import com.rvmeur.transferin.domain.exposure.ExposureClassification

/**
 * Verdict evaluating frame exposure suitability for sync/cloud upload.
 */
enum class CullingVerdict {
    OPTIMAL,
    UNDEREXPOSED_CRITICAL,
    OVEREXPOSED_BLOWN,
    DYNAMIC_PROFILE_ADJUSTED;

    companion object {
        fun fromClassification(classification: ExposureClassification): CullingVerdict {
            return when (classification) {
                ExposureClassification.OPTIMAL -> OPTIMAL
                ExposureClassification.UNDEREXPOSED_CRITICAL -> UNDEREXPOSED_CRITICAL
                ExposureClassification.OVEREXPOSED_BLOWN -> OVEREXPOSED_BLOWN
            }
        }
    }
}
