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
     * Terminal or recoverable failure (increments retry count).
     */
    FAILED;

    val isTerminal: Boolean
        get() = this == CONFIRMED_CLOUD || this == PURGED_LOCAL

    val isPendingUpload: Boolean
        get() = this == DETECTED || this == BUFFERED_LOCAL

    val isTransferring: Boolean
        get() = this == STREAMING_CLOUD

    val isPurgeable: Boolean
        get() = this == CONFIRMED_CLOUD
}
