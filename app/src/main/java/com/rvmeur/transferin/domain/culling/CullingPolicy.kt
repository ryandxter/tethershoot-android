package com.rvmeur.transferin.domain.culling

/**
 * Culling policy configured per photoshoot session.
 */
enum class CullingPolicy {
    /**
     * Default Quota-First Protection.
     * Pitch-black misfires and blown highlights are flagged as CULLED_SKIPPED,
     * halting Tier 2 RAW transfer over USB and cloud upload sockets to save bandwidth.
     */
    AUTO_SKIP_UPLOAD,

    /**
     * Warning only.
     * Frames are flagged with warning badges in gallery, but uploaded unconditionally.
     */
    WARN_ONLY,

    /**
     * Disables culling evaluation completely; all frames proceed to upload.
     */
    DISABLED
}
