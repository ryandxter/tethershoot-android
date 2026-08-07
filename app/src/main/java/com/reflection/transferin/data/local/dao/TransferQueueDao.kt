package com.reflection.transferin.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.reflection.transferin.data.local.entity.TransferQueueItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TransferQueueDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(items: List<TransferQueueItemEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertOne(item: TransferQueueItemEntity): Long

    @Update
    suspend fun update(item: TransferQueueItemEntity)

    @Query("SELECT * FROM transfer_queue_items WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): TransferQueueItemEntity?

    @Query("SELECT * FROM transfer_queue_items WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<TransferQueueItemEntity?>

    @Query("SELECT * FROM transfer_queue_items ORDER BY createdAtEpochMs ASC")
    suspend fun getAll(): List<TransferQueueItemEntity>

    @Query("SELECT * FROM transfer_queue_items ORDER BY createdAtEpochMs ASC")
    fun observeAll(): Flow<List<TransferQueueItemEntity>>

    @Query(
        """
        SELECT * FROM transfer_queue_items
        WHERE sessionId = :sessionId
        ORDER BY createdAtEpochMs ASC
        """
    )
    suspend fun getBySession(sessionId: String): List<TransferQueueItemEntity>

    @Query(
        """
        SELECT * FROM transfer_queue_items
        WHERE sessionId = :sessionId
        ORDER BY createdAtEpochMs ASC
        """
    )
    fun observeBySession(sessionId: String): Flow<List<TransferQueueItemEntity>>

    @Query(
        """
        SELECT * FROM transfer_queue_items
        WHERE status = :status
        ORDER BY priority DESC, createdAtEpochMs ASC
        """
    )
    suspend fun getByStatus(status: String): List<TransferQueueItemEntity>

    @Query(
        """
        SELECT * FROM transfer_queue_items
        WHERE sessionId = :sessionId
          AND status = :status
        ORDER BY priority DESC, createdAtEpochMs ASC
        """
    )
    suspend fun getBySessionAndStatus(
        sessionId: String,
        status: String
    ): List<TransferQueueItemEntity>

    @Query(
        """
        SELECT * FROM transfer_queue_items
        WHERE status IN ('QUEUED', 'RETRY_SCHEDULED')
        ORDER BY priority DESC, createdAtEpochMs ASC
        LIMIT 1
        """
    )
    suspend fun getNextDispatchCandidate(): TransferQueueItemEntity?

    @Query(
        """
        SELECT * FROM transfer_queue_items
        WHERE sessionId = :sessionId
          AND status IN ('QUEUED', 'RETRY_SCHEDULED')
        ORDER BY priority DESC, createdAtEpochMs ASC
        LIMIT 1
        """
    )
    suspend fun getNextDispatchCandidateBySession(sessionId: String): TransferQueueItemEntity?

    @Query(
        """
        SELECT * FROM transfer_queue_items
        WHERE sourceFingerprint = :sourceFingerprint
        LIMIT 1
        """
    )
    suspend fun getBySourceFingerprint(sourceFingerprint: String): TransferQueueItemEntity?

    @Query(
        """
        SELECT * FROM transfer_queue_items
        WHERE sourceFingerprint = :sourceFingerprint
          AND status IN ('QUEUED', 'DISPATCHED', 'UPLOADING', 'RETRY_SCHEDULED', 'SUCCESS')
        LIMIT 1
        """
    )
    suspend fun getActiveOrCompletedByFingerprint(
        sourceFingerprint: String
    ): TransferQueueItemEntity?

    @Query(
        """
        SELECT * FROM transfer_queue_items
        WHERE sessionId = :sessionId
          AND sourceFingerprint = :sourceFingerprint
        LIMIT 1
        """
    )
    suspend fun getBySessionAndFingerprint(
        sessionId: String,
        sourceFingerprint: String
    ): TransferQueueItemEntity?

    @Query(
        """
        SELECT COUNT(*) FROM transfer_queue_items
        WHERE status = :status
        """
    )
    suspend fun countByStatus(status: String): Int

    @Query(
        """
        SELECT COUNT(*) FROM transfer_queue_items
        WHERE sessionId = :sessionId
          AND status = :status
        """
    )
    suspend fun countBySessionAndStatus(
        sessionId: String,
        status: String
    ): Int

    @Query(
        """
        SELECT COUNT(*) FROM transfer_queue_items
        WHERE sessionId = :sessionId
        """
    )
    suspend fun countAllBySession(sessionId: String): Int

    @Query(
        """
        SELECT COALESCE(SUM(fileSizeBytes), 0) FROM transfer_queue_items
        WHERE sessionId = :sessionId
        """
    )
    suspend fun sumDiscoveredBytesBySession(sessionId: String): Long

    @Query(
        """
        SELECT COALESCE(SUM(bytesUploaded), 0) FROM transfer_queue_items
        WHERE sessionId = :sessionId
        """
    )
    suspend fun sumUploadedBytesBySession(sessionId: String): Long

    @Query(
        """
        UPDATE transfer_queue_items
        SET status = :status,
            updatedAtEpochMs = :updatedAtEpochMs
        WHERE id = :id
        """
    )
    suspend fun updateStatus(
        id: String,
        status: String,
        updatedAtEpochMs: Long
    )

    @Query(
        """
        UPDATE transfer_queue_items
        SET status = :status,
            progressPercent = :progressPercent,
            bytesUploaded = :bytesUploaded,
            updatedAtEpochMs = :updatedAtEpochMs
        WHERE id = :id
        """
    )
    suspend fun updateProgress(
        id: String,
        status: String,
        progressPercent: Int,
        bytesUploaded: Long,
        updatedAtEpochMs: Long
    )

    @Query(
        """
        UPDATE transfer_queue_items
        SET status = :status,
            dispatchedAtEpochMs = :dispatchedAtEpochMs,
            updatedAtEpochMs = :updatedAtEpochMs
        WHERE id = :id
        """
    )
    suspend fun markDispatched(
        id: String,
        dispatchedAtEpochMs: Long,
        updatedAtEpochMs: Long,
        status: String = "DISPATCHED"
    )

    @Query(
        """
        UPDATE transfer_queue_items
        SET status = :status,
            uploadStartedAtEpochMs = :uploadStartedAtEpochMs,
            updatedAtEpochMs = :updatedAtEpochMs
        WHERE id = :id
        """
    )
    suspend fun markUploading(
        id: String,
        uploadStartedAtEpochMs: Long,
        updatedAtEpochMs: Long,
        status: String = "UPLOADING"
    )

    @Query(
        """
        UPDATE transfer_queue_items
        SET status = :status,
            progressPercent = 100,
            bytesUploaded = fileSizeBytes,
            errorMessage = NULL,
            completedAtEpochMs = :completedAtEpochMs,
            updatedAtEpochMs = :updatedAtEpochMs
        WHERE id = :id
        """
    )
    suspend fun markSuccess(
        id: String,
        completedAtEpochMs: Long,
        updatedAtEpochMs: Long,
        status: String = "SUCCESS"
    )

    @Query(
        """
        UPDATE transfer_queue_items
        SET status = :status,
            retryCount = retryCount + 1,
            errorMessage = :errorMessage,
            updatedAtEpochMs = :updatedAtEpochMs
        WHERE id = :id
        """
    )
    suspend fun markRetryScheduled(
        id: String,
        errorMessage: String?,
        updatedAtEpochMs: Long,
        status: String = "RETRY_SCHEDULED"
    )

    @Query(
        """
        UPDATE transfer_queue_items
        SET status = :status,
            errorMessage = :errorMessage,
            completedAtEpochMs = :completedAtEpochMs,
            updatedAtEpochMs = :updatedAtEpochMs
        WHERE id = :id
        """
    )
    suspend fun markFailed(
        id: String,
        errorMessage: String?,
        completedAtEpochMs: Long,
        updatedAtEpochMs: Long,
        status: String = "FAILED"
    )

    @Query(
        """
        UPDATE transfer_queue_items
        SET status = :status,
            completedAtEpochMs = :completedAtEpochMs,
            updatedAtEpochMs = :updatedAtEpochMs
        WHERE id = :id
        """
    )
    suspend fun markCancelled(
        id: String,
        completedAtEpochMs: Long,
        updatedAtEpochMs: Long,
        status: String = "CANCELLED"
    )

    @Query(
        """
        UPDATE transfer_queue_items
        SET status = :status,
            errorMessage = NULL,
            progressPercent = 0,
            bytesUploaded = 0,
            dispatchedAtEpochMs = NULL,
            uploadStartedAtEpochMs = NULL,
            completedAtEpochMs = NULL,
            updatedAtEpochMs = :updatedAtEpochMs
        WHERE id = :id
        """
    )
    suspend fun requeue(
        id: String,
        updatedAtEpochMs: Long,
        status: String = "QUEUED"
    )

    @Query("DELETE FROM transfer_queue_items")
    suspend fun clearAll()

    @Query("DELETE FROM transfer_queue_items WHERE sessionId = :sessionId")
    suspend fun clearBySession(sessionId: String)
}