package com.rvmeur.transferin.domain.sync

import com.rvmeur.transferin.data.local.entity.CameraSyncStatus

/**
 * Result of checking whether a camera media item is a duplicate.
 */
sealed interface DuplicateVerdict {
    /**
     * Item has never been encountered before; eligible for sync.
     */
    data object NewMedia : DuplicateVerdict

    /**
     * Item was previously confirmed uploaded to cloud; skip upload completely.
     */
    data class AlreadyConfirmed(val remoteFileId: String?) : DuplicateVerdict

    /**
     * Item is currently actively buffering or streaming; do not spawn duplicate worker.
     */
    data class InProgress(val status: CameraSyncStatus) : DuplicateVerdict

    /**
     * Previous attempt failed; eligible for retry attempt.
     */
    data class FailedRetryable(val retryCount: Int, val lastErrorMessage: String?) : DuplicateVerdict
}
