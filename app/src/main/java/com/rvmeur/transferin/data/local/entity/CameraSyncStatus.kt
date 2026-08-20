package com.rvmeur.transferin.data.local.entity

/**
 * Deterministic 5-stage Camera-to-Cloud State Machine status.
 *
 * Sequence:
 * DETECTED -> BUFFERED_LOCAL -> STREAMING_CLOUD -> CONFIRMED_CLOUD -> PURGED_LOCAL
 * or FAILED if an unrecoverable error occurs.
 */
enum class CameraSyncStatus {
    /**
     * Object identified via PTP event or handle scan; metadata recorded in database.
     */
    DETECTED,

    /**
     * File staged to local transit disk cache (/cache/transit/).
     */
    BUFFERED_LOCAL,

    /**
     * Active zero-copy USB or buffered OkHttp upload in progress.
     */
    STREAMING_CLOUD,

    /**
     * Cloud upload verified by server (200 OK + File ID + Checksum).
     */
    CONFIRMED_CLOUD,

    /**
     * Local transit cache safely removed following cloud confirmation.
     */
    PURGED_LOCAL,

    /**
     * Frame flagged by culling engine; Tier 2 RAW transfer skipped to save cloud quota.
     */
    CULLED_SKIPPED,

    /**
     * Manual "Paksa Unggah" override triggered; enqueued for forced cloud transmission.
     */
    FORCE_UPLOAD_PENDING,

    /**
     * Terminal or recoverable failure (increments retry count).
     */
    FAILED;

    val isTerminal: Boolean
        get() = this == CONFIRMED_CLOUD || this == PURGED_LOCAL || this == CULLED_SKIPPED

    val isPendingUpload: Boolean
        get() = this == DETECTED || this == BUFFERED_LOCAL || this == FORCE_UPLOAD_PENDING

    val isTransferring: Boolean
        get() = this == STREAMING_CLOUD

    val isPurgeable: Boolean
        get() = this == CONFIRMED_CLOUD

    val isCulled: Boolean
        get() = this == CULLED_SKIPPED
}
