package com.rvmeur.transferin.domain.sync

import com.rvmeur.transferin.data.local.dao.CameraSyncDao
import com.rvmeur.transferin.data.local.dao.UploadedFingerprintDao
import com.rvmeur.transferin.data.local.entity.CameraSyncStatus

/**
 * Evaluates duplicate status of camera media across sessions and active uploads.
 */
class CameraSyncDuplicateDetector(
    private val cameraSyncDao: CameraSyncDao,
    private val uploadedFingerprintDao: UploadedFingerprintDao? = null
) {

    /**
     * Checks if a media item represented by [fingerprint] is already tracked or uploaded.
     */
    suspend fun checkDuplicate(fingerprint: String): DuplicateVerdict {
        // 1. Check current CameraSyncDao state
        val existingItem = cameraSyncDao.getByFingerprint(fingerprint)
        if (existingItem != null) {
            return when (existingItem.status) {
                CameraSyncStatus.CONFIRMED_CLOUD,
                CameraSyncStatus.PURGED_LOCAL -> {
                    DuplicateVerdict.AlreadyConfirmed(remoteFileId = existingItem.remoteFileId)
                }
                CameraSyncStatus.DETECTED,
                CameraSyncStatus.BUFFERED_LOCAL,
                CameraSyncStatus.STREAMING_CLOUD -> {
                    DuplicateVerdict.InProgress(status = existingItem.status)
                }
                CameraSyncStatus.FAILED -> {
                    DuplicateVerdict.FailedRetryable(
                        retryCount = existingItem.retryCount,
                        lastErrorMessage = existingItem.lastErrorMessage
                    )
                }
            }
        }

        // 2. Check legacy UploadedFingerprintDao for cross-session backwards compatibility
        if (uploadedFingerprintDao != null && uploadedFingerprintDao.hasUploaded(fingerprint)) {
            return DuplicateVerdict.AlreadyConfirmed(remoteFileId = null)
        }

        return DuplicateVerdict.NewMedia
    }

    /**
     * Convenience method computing fingerprint directly from item attributes and evaluating duplicate status.
     */
    suspend fun checkDuplicate(
        vendorId: Int,
        serialNumber: String?,
        objectHandle: Int,
        fileSizeBytes: Long,
        captureTimestampMs: Long
    ): DuplicateVerdict {
        val fingerprint = CompoundFingerprintGenerator.generate(
            vendorId = vendorId,
            serialNumber = serialNumber,
            objectHandle = objectHandle,
            fileSizeBytes = fileSizeBytes,
            captureTimestampMs = captureTimestampMs
        )
        return checkDuplicate(fingerprint)
    }
}
