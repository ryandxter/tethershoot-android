package com.rvmeur.transferin.domain.model

data class TransferQueueItem(
    val id: String,
    val sessionId: String,
    val sourceUri: String,
    val sourceFileName: String,
    val sourceRelativePath: String?,
    val sourceMimeType: String?,
    val fileType: FileType,
    val priority: Int,
    val fileSizeBytes: Long,
    val lastModifiedEpochMs: Long?,
    val targetNasDirPath: String,
    val targetFileName: String,
    val status: TransferItemStatus,
    val progressPercent: Int,
    val bytesUploaded: Long,
    val retryCount: Int,
    val errorMessage: String?,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val dispatchedAtEpochMs: Long?,
    val uploadStartedAtEpochMs: Long?,
    val completedAtEpochMs: Long?
) {
    init {
        require(id.isNotBlank()) { "id must not be blank" }
        require(sessionId.isNotBlank()) { "sessionId must not be blank" }
        require(sourceUri.isNotBlank()) { "sourceUri must not be blank" }
        require(sourceFileName.isNotBlank()) { "sourceFileName must not be blank" }
        require(targetNasDirPath.isNotBlank()) { "targetNasDirPath must not be blank" }
        require(targetFileName.isNotBlank()) { "targetFileName must not be blank" }
        require(priority >= 0) { "priority must be >= 0" }
        require(fileSizeBytes >= 0L) { "fileSizeBytes must be >= 0" }
        require(progressPercent in 0..100) { "progressPercent must be between 0 and 100" }
        require(bytesUploaded >= 0L) { "bytesUploaded must be >= 0" }
        require(retryCount >= 0) { "retryCount must be >= 0" }
    }

    val displayName: String
        get() = targetFileName.ifBlank { sourceFileName }

    val isUploadable: Boolean
        get() = fileType.isUploadable

    val isFinished: Boolean
        get() = status.isFinished

    val canRetry: Boolean
        get() = status == TransferItemStatus.FAILED

    val isInFlight: Boolean
        get() = status.isInFlight

    companion object {
        fun newDetected(
            id: String,
            sessionId: String,
            sourceUri: String,
            sourceFileName: String,
            sourceRelativePath: String?,
            sourceMimeType: String?,
            fileSizeBytes: Long,
            lastModifiedEpochMs: Long?,
            targetNasDirPath: String,
            targetFileName: String,
            createdAtEpochMs: Long
        ): TransferQueueItem {
            val fileType = FileType.fromFileName(sourceFileName)

            return TransferQueueItem(
                id = id,
                sessionId = sessionId,
                sourceUri = sourceUri,
                sourceFileName = sourceFileName,
                sourceRelativePath = sourceRelativePath,
                sourceMimeType = sourceMimeType,
                fileType = fileType,
                priority = fileType.priority,
                fileSizeBytes = fileSizeBytes,
                lastModifiedEpochMs = lastModifiedEpochMs,
                targetNasDirPath = targetNasDirPath,
                targetFileName = targetFileName,
                status = TransferItemStatus.DETECTED,
                progressPercent = 0,
                bytesUploaded = 0L,
                retryCount = 0,
                errorMessage = null,
                createdAtEpochMs = createdAtEpochMs,
                updatedAtEpochMs = createdAtEpochMs,
                dispatchedAtEpochMs = null,
                uploadStartedAtEpochMs = null,
                completedAtEpochMs = null
            )
        }
    }
}