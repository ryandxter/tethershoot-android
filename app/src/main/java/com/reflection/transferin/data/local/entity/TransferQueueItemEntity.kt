package com.reflection.transferin.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "transfer_queue_items",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.NO_ACTION
        )
    ],
    indices = [
        Index(value = ["sessionId"]),
        Index(value = ["status"]),
        Index(value = ["priority", "createdAtEpochMs"]),
        Index(value = ["sourceUri"]),
        Index(value = ["sourceFingerprint"]),
        Index(value = ["sessionId", "sourceFingerprint"], unique = true)
    ]
)
data class TransferQueueItemEntity(
    @PrimaryKey
    val id: String,

    val sessionId: String,

    val sourceUri: String,
    val sourceFingerprint: String,
    val sourceFileName: String,
    val sourceRelativePath: String?,
    val sourceMimeType: String?,

    val fileType: String,
    val priority: Int,

    val fileSizeBytes: Long,
    val lastModifiedEpochMs: Long?,

    val targetNasDirPath: String,
    val targetFileName: String,

    val status: String,
    val progressPercent: Int,
    val bytesUploaded: Long,

    val retryCount: Int,
    val errorMessage: String?,

    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val dispatchedAtEpochMs: Long?,
    val uploadStartedAtEpochMs: Long?,
    val completedAtEpochMs: Long?
)