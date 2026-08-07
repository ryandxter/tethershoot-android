package com.reflection.transferin.session.model

data class SessionRuntimeState(
    val sessionId: String = "",
    val sessionLabel: String? = null,
    val customerName: String? = null,

    val sourceTreeUri: String = "",
    val targetNasDirPath: String = "",

    val sessionState: String = "",
    val watcherStatus: String = "",

    val acceptedFileCount: Int = 0,
    val queuedFileCount: Int = 0,
    val uploadingFileCount: Int = 0,
    val successFileCount: Int = 0,
    val failedFileCount: Int = 0,
    val skippedFileCount: Int = 0,

    val totalBytesDiscovered: Long = 0L,
    val totalBytesUploaded: Long = 0L,

    val activeItems: Int = 0,
    val lastUpdatedAtEpochMs: Long = 0L
) {
    val isIdle: Boolean
        get() = sessionId.isBlank()

    val isRunning: Boolean
        get() = sessionState == "RUNNING"

    val isStopping: Boolean
        get() = sessionState == "STOPPING" || sessionState == "UPLOAD_DRAINING"

    val isCompleted: Boolean
        get() = sessionState == "COMPLETED"

    val isFailed: Boolean
        get() = sessionState == "FAILED"

    val hasActiveTransfer: Boolean
        get() = queuedFileCount > 0 || uploadingFileCount > 0

    val totalHandledFileCount: Int
        get() = successFileCount + failedFileCount + skippedFileCount

    val totalKnownFileCount: Int
        get() = queuedFileCount + uploadingFileCount + totalHandledFileCount

    val progressPercent: Int
        get() = when {
            totalBytesDiscovered <= 0L -> 0
            totalBytesUploaded <= 0L -> 0
            totalBytesUploaded >= totalBytesDiscovered -> 100
            else -> ((totalBytesUploaded * 100L) / totalBytesDiscovered).toInt()
        }
}