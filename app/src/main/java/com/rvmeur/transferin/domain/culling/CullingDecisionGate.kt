package com.rvmeur.transferin.domain.culling

/**
 * Pure rule evaluator for deciding upload action based on exposure/subject quality verdict and session policy.
 */
class CullingDecisionGate {

    /**
     * Evaluates exposure/subject quality verdict, session policy, and manual override status.
     *
     * @param verdict Exposure or AI face/subject quality classification verdict
     * @param policy Session culling policy
     * @param isManualOverride True if the photographer manually pressed "Paksa Unggah"
     * @return Resulting [CullingAction]
     */
    fun evaluate(
        verdict: CullingVerdict,
        policy: CullingPolicy,
        isManualOverride: Boolean = false
    ): CullingAction {
        if (isManualOverride || policy == CullingPolicy.DISABLED) {
            return CullingAction.PROCEED_UPLOAD
        }
        return when (verdict) {
            CullingVerdict.OPTIMAL,
            CullingVerdict.DYNAMIC_PROFILE_ADJUSTED -> CullingAction.PROCEED_UPLOAD
            CullingVerdict.UNDEREXPOSED_CRITICAL,
            CullingVerdict.OVEREXPOSED_BLOWN,
            CullingVerdict.CLOSED_EYES_DETECTED,
            CullingVerdict.BLURRED_SUBJECT -> {
                when (policy) {
                    CullingPolicy.AUTO_SKIP_UPLOAD -> CullingAction.SKIP_UPLOAD_SAVING_QUOTA
                    CullingPolicy.WARN_ONLY -> CullingAction.FLAG_WARNING_ONLY
                    CullingPolicy.DISABLED -> CullingAction.PROCEED_UPLOAD
                }
            }
        }
    }

    /**
     * Overloaded convenience method evaluating both exposure and AI subject metrics against session policy.
     */
    fun evaluate(
        exposureVerdict: CullingVerdict,
        faceMetrics: AiFaceQualityMetrics?,
        policy: CullingPolicy,
        isManualOverride: Boolean = false,
        engine: AiFaceCullingEngine = DefaultAiFaceCullingEngine()
    ): CullingAction {
        val subjectVerdict = faceMetrics?.let { engine.evaluate(it) } ?: CullingVerdict.OPTIMAL
        val finalVerdict = engine.combineVerdicts(exposureVerdict, subjectVerdict)
        return evaluate(finalVerdict, policy, isManualOverride)
    }
}
