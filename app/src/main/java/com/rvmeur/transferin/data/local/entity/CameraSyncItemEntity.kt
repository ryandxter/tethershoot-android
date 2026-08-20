package com.rvmeur.transferin.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Room entity representing a single camera media item tracked across the 5-stage
 * Camera-to-Cloud synchronization pipeline.
 */
@Entity(
    tableName = "camera_sync_items",
    indices = [
        Index(value = ["sessionId"]),
        Index(value = ["objectHandle"]),
        Index(value = ["compoundFingerprint"], unique = true),
        Index(value = ["status"]),
        Index(value = ["confirmedAtEpochMs"]),
        Index(value = ["sessionId", "status"])
    ]
)
data class CameraSyncItemEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),

    val sessionId: String,
    val vendorId: Int,
    val cameraSerialNumber: String,
    val storageId: Int,
    val objectHandle: Int,
    val filename: String,
    val fileSizeBytes: Long,
    val captureTimestampMs: Long,
    val compoundFingerprint: String,

    val status: CameraSyncStatus = CameraSyncStatus.DETECTED,

    val thumbnailPath: String? = null,
    val cullingVerdict: String = "OPTIMAL",
    val meanLuminance: Double = 128.0,
    val entropyScore: Double = 5.0,
    val isCulled: Boolean = false,
    val isManualUploadOverride: Boolean = false,

    val localFilePath: String? = null,
    val remoteFileId: String? = null,
    val bytesUploaded: Long = 0L,
    val retryCount: Int = 0,
    val lastErrorMessage: String? = null,

    val createdAtEpochMs: Long = System.currentTimeMillis(),
    val updatedAtEpochMs: Long = System.currentTimeMillis(),
    val confirmedAtEpochMs: Long? = null,
    val purgedAtEpochMs: Long? = null
) {
    val isConfirmed: Boolean
        get() = status == CameraSyncStatus.CONFIRMED_CLOUD || status == CameraSyncStatus.PURGED_LOCAL

    val isPurgeable: Boolean
        get() = status == CameraSyncStatus.CONFIRMED_CLOUD && !localFilePath.isNullOrBlank()

    val progressPercent: Float
        get() = if (fileSizeBytes > 0L) (bytesUploaded.toFloat() / fileSizeBytes.toFloat()).coerceIn(0f, 1f) * 100f else 0f
}
