package com.rvmeur.transferin.domain.culling

/**
 * Transactional action dispatched by [CullingDecisionGate].
 */
enum class CullingAction {
    /**
     * Proceed with normal Tier 2 RAW transfer and cloud streaming.
     */
    PROCEED_UPLOAD,

    /**
     * Skip Tier 2 transfer and cloud streaming to save bandwidth and quota.
     */
    SKIP_UPLOAD_SAVING_QUOTA,

    /**
     * Flag warning badge in UI, but proceed with upload.
     */
    FLAG_WARNING_ONLY
}
