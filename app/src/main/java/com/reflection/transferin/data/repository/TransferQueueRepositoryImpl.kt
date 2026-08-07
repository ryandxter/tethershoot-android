package com.reflection.transferin.data.repository

import com.reflection.transferin.data.local.dao.TransferQueueDao
import com.reflection.transferin.data.local.entity.TransferQueueItemEntity
import kotlinx.coroutines.flow.Flow

@Suppress("unused")
class TransferQueueRepositoryImpl(
    private val dao: TransferQueueDao
) {

    suspend fun insert(items: List<TransferQueueItemEntity>) {
        dao.insert(items)
    }

    suspend fun insertAndReturnIds(items: List<TransferQueueItemEntity>): List<String> {
        dao.insert(items)
        return items.map { it.id }
    }

    suspend fun insertOne(item: TransferQueueItemEntity) {
        dao.insertOne(item)
    }

    suspend fun insertAndReturnFirstId(item: TransferQueueItemEntity): String {
        dao.insertOne(item)
        return item.id
    }

    suspend fun update(item: TransferQueueItemEntity) {
        dao.update(item)
    }

    suspend fun getById(id: String): TransferQueueItemEntity? {
        return dao.getById(id)
    }

    fun observeById(id: String): Flow<TransferQueueItemEntity?> {
        return dao.observeById(id)
    }

    suspend fun getAll(): List<TransferQueueItemEntity> {
        return dao.getAll()
    }

    fun observeAll(): Flow<List<TransferQueueItemEntity>> {
        return dao.observeAll()
    }

    suspend fun getBySession(sessionId: String): List<TransferQueueItemEntity> {
        return dao.getBySession(sessionId)
    }

    fun observeBySession(sessionId: String): Flow<List<TransferQueueItemEntity>> {
        return dao.observeBySession(sessionId)
    }

    suspend fun getByStatus(status: String): List<TransferQueueItemEntity> {
        return dao.getByStatus(status)
    }

    suspend fun getBySessionAndStatus(
        sessionId: String,
        status: String
    ): List<TransferQueueItemEntity> {
        return dao.getBySessionAndStatus(sessionId, status)
    }

    suspend fun getNextDispatchCandidate(): TransferQueueItemEntity? {
        return dao.getNextDispatchCandidate()
    }

    suspend fun getNextDispatchCandidateBySession(sessionId: String): TransferQueueItemEntity? {
        return dao.getNextDispatchCandidateBySession(sessionId)
    }

    suspend fun countByStatus(status: String): Int {
        return dao.countByStatus(status)
    }

    suspend fun countBySessionAndStatus(
        sessionId: String,
        status: String
    ): Int {
        return dao.countBySessionAndStatus(sessionId, status)
    }

    suspend fun countAllBySession(sessionId: String): Int {
        return dao.countAllBySession(sessionId)
    }

    suspend fun sumDiscoveredBytesBySession(sessionId: String): Long {
        return dao.sumDiscoveredBytesBySession(sessionId)
    }

    suspend fun sumUploadedBytesBySession(sessionId: String): Long {
        return dao.sumUploadedBytesBySession(sessionId)
    }

    suspend fun updateStatus(
        id: String,
        status: String,
        updatedAtEpochMs: Long
    ) {
        dao.updateStatus(
            id = id,
            status = status,
            updatedAtEpochMs = updatedAtEpochMs
        )
    }

    suspend fun updateProgress(
        id: String,
        status: String,
        progressPercent: Int,
        bytesUploaded: Long,
        updatedAtEpochMs: Long
    ) {
        dao.updateProgress(
            id = id,
            status = status,
            progressPercent = progressPercent,
            bytesUploaded = bytesUploaded,
            updatedAtEpochMs = updatedAtEpochMs
        )
    }

    suspend fun markDispatched(
        id: String,
        dispatchedAtEpochMs: Long,
        updatedAtEpochMs: Long
    ) {
        dao.markDispatched(
            id = id,
            dispatchedAtEpochMs = dispatchedAtEpochMs,
            updatedAtEpochMs = updatedAtEpochMs
        )
    }

    suspend fun markUploading(
        id: String,
        uploadStartedAtEpochMs: Long,
        updatedAtEpochMs: Long
    ) {
        dao.markUploading(
            id = id,
            uploadStartedAtEpochMs = uploadStartedAtEpochMs,
            updatedAtEpochMs = updatedAtEpochMs
        )
    }

    suspend fun markSuccess(
        id: String,
        completedAtEpochMs: Long,
        updatedAtEpochMs: Long
    ) {
        dao.markSuccess(
            id = id,
            completedAtEpochMs = completedAtEpochMs,
            updatedAtEpochMs = updatedAtEpochMs
        )
    }

    suspend fun markRetryScheduled(
        id: String,
        errorMessage: String?,
        updatedAtEpochMs: Long
    ) {
        dao.markRetryScheduled(
            id = id,
            errorMessage = errorMessage,
            updatedAtEpochMs = updatedAtEpochMs
        )
    }

    suspend fun markFailed(
        id: String,
        errorMessage: String?,
        completedAtEpochMs: Long,
        updatedAtEpochMs: Long
    ) {
        dao.markFailed(
            id = id,
            errorMessage = errorMessage,
            completedAtEpochMs = completedAtEpochMs,
            updatedAtEpochMs = updatedAtEpochMs
        )
    }

    suspend fun markCancelled(
        id: String,
        completedAtEpochMs: Long,
        updatedAtEpochMs: Long
    ) {
        dao.markCancelled(
            id = id,
            completedAtEpochMs = completedAtEpochMs,
            updatedAtEpochMs = updatedAtEpochMs
        )
    }

    suspend fun requeue(
        id: String,
        updatedAtEpochMs: Long
    ) {
        dao.requeue(
            id = id,
            updatedAtEpochMs = updatedAtEpochMs
        )
    }

    suspend fun clearAll() {
        dao.clearAll()
    }

    suspend fun clearBySession(sessionId: String) {
        dao.clearBySession(sessionId)
    }
}