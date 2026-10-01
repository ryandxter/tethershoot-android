package com.rvmeur.transferin.domain.culling

import com.rvmeur.transferin.domain.exposure.ExposureClassification

/**
 * Verdict evaluating frame exposure suitability and AI subject quality for sync/cloud upload.
 */
enum class CullingVerdict {
    OPTIMAL,
    UNDEREXPOSED_CRITICAL,
    OVEREXPOSED_BLOWN,
    DYNAMIC_PROFILE_ADJUSTED,
    CLOSED_EYES_DETECTED,
    BLURRED_SUBJECT;

    val isCulled: Boolean
        get() = this == UNDEREXPOSED_CRITICAL ||
                this == OVEREXPOSED_BLOWN ||
                this == CLOSED_EYES_DETECTED ||
                this == BLURRED_SUBJECT

    val isOptimal: Boolean
        get() = this == OPTIMAL || this == DYNAMIC_PROFILE_ADJUSTED

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
