package com.rvmeur.transferin.domain.model

data class MediaJob(
    val id: String,
    val projectId: String,
    val localTempPath: String,
    val originalFileName: String,
    val sourceUri: String,
    val sourceFingerprint: String,
    val fileSizeBytes: Long,
    val lastModifiedEpochMs: Long,
    val createdAtEpochMs: Long,
    val uploadStatus: UploadStatus
)

enum class UploadStatus {
    QUEUED,
    UPLOADING,
    SUCCESS,
    FAILED,
    SKIPPED
}