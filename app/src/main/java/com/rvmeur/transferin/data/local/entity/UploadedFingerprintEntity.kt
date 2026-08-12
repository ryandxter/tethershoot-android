package com.rvmeur.transferin.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entity for tracking successfully uploaded files across sessions.
 *
 * This entity persists fingerprints of uploaded files to enable cross-session
 * deduplication. The fingerprint serves as the primary key, formatted as:
 * "{handle}|{normalizedFilename}|{sizeBytes}"
 *
 * Indices on sessionId and uploadedAt enable efficient queries for session
 * management and cleanup operations.
 */
@Entity(
    tableName = "uploaded_fingerprints",
    indices = [
        Index(value = ["session_id"]),
        Index(value = ["uploaded_at"]),
        Index(value = ["session_id", "uploaded_at"])
    ]
)
data class UploadedFingerprintEntity(
    @PrimaryKey
    @ColumnInfo(name = "fingerprint")
    val fingerprint: String,

    @ColumnInfo(name = "session_id")
    val sessionId: String,

    @ColumnInfo(name = "source_filename")
    val sourceFilename: String,

    @ColumnInfo(name = "file_size_bytes")
    val fileSizeBytes: Long,

    @ColumnInfo(name = "nas_target_path")
    val nasTargetPath: String,

    @ColumnInfo(name = "nas_filename")
    val nasFilename: String,

    @ColumnInfo(name = "uploaded_at")
    val uploadedAt: Long,

    @ColumnInfo(name = "ctg_handle")
    val ctgHandle: Int,

    @ColumnInfo(name = "camera_device_id")
    val cameraDeviceId: String? = null
)
