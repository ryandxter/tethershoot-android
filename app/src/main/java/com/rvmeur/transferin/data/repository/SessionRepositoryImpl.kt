package com.rvmeur.transferin.data.repository

import com.rvmeur.transferin.data.local.dao.SessionDao
import com.rvmeur.transferin.data.local.entity.SessionEntity
import com.rvmeur.transferin.domain.model.ConnectionMode
import com.rvmeur.transferin.domain.model.SessionState
import com.rvmeur.transferin.domain.model.WatcherStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Suppress("unused")
class SessionRepositoryImpl(
    private val sessionDao: SessionDao
) {

    suspend fun createSession(
        sessionId: String,
        sessionLabel: String?,
        customerName: String?,
        sourceTreeUri: String,
        targetNasDirPath: String,
        startedAtEpochMs: Long,
        connectionMode: ConnectionMode = ConnectionMode.IMPORT_FALLBACK
    ) {
        val entity = SessionEntity(
            id = sessionId,
            sessionLabel = sessionLabel,
            customerName = customerName,
            sourceTreeUri = sourceTreeUri,
            targetNasDirPath = targetNasDirPath,

            // field baru: connectionMode & isActive
            connectionMode = connectionMode.name,
            isActive = true,

            status = SessionState.STARTING.name,
            watcherStatus = WatcherStatus.IDLE.name,

            acceptedFileCount = 0,
            queuedFileCount = 0,
            uploadingFileCount = 0,
            successFileCount = 0,
            failedFileCount = 0,
            skippedFileCount = 0,

            totalBytesDiscovered = 0L,
            totalBytesUploaded = 0L,

            lastScannedAtEpochMs = null,
            startedAtEpochMs = startedAtEpochMs,
            intakeStoppedAtEpochMs = null,
            completedAtEpochMs = null,

            createdAtEpochMs = startedAtEpochMs,
            updatedAtEpochMs = startedAtEpochMs
        )
        sessionDao.insert(entity)
    }

    suspend fun getById(sessionId: String): SessionEntity? {
        return sessionDao.getById(sessionId)
    }

    suspend fun getSessionById(sessionId: String): SessionEntity? {
        return getById(sessionId)
    }

    fun observeSession(sessionId: String): Flow<SessionEntity?> {
        return sessionDao.observeById(sessionId)
    }

    /**
     * Active-like: berdasarkan status RUNNING/STARTING/dll.
     * Dipertahankan untuk kompatibilitas lama.
     */
    suspend fun getLatestActiveLikeSession(): SessionEntity? {
        return sessionDao.getLatestActiveLikeSession()
    }

    fun observeLatestActiveLikeSession(): Flow<SessionEntity?> {
        return sessionDao.observeLatestActiveLikeSession()
    }

    suspend fun getAllSessions(): List<SessionEntity> {
        return sessionDao.getAll()
    }

    fun observeAllSessions(): Flow<List<SessionEntity>> {
        return sessionDao.observeAll()
    }

    suspend fun updateSourceTreeUri(
        sessionId: String,
        sourceTreeUri: String
    ) {
        val existing = sessionDao.getById(sessionId) ?: return
        sessionDao.insert(
            existing.copy(
                sourceTreeUri = sourceTreeUri,
                updatedAtEpochMs = System.currentTimeMillis()
            )
        )
    }

    suspend fun updateSessionState(
        sessionId: String,
        sessionState: SessionState,
        updatedAtEpochMs: Long
    ) {
        sessionDao.updateStatus(
            id = sessionId,
            status = sessionState.name,
            updatedAtEpochMs = updatedAtEpochMs
        )
    }

    suspend fun updateWatcherStatus(
        sessionId: String,
        watcherStatus: WatcherStatus,
        updatedAtEpochMs: Long
    ) {
        sessionDao.updateWatcherStatus(
            id = sessionId,
            watcherStatus = watcherStatus.name,
            updatedAtEpochMs = updatedAtEpochMs
        )
    }

    suspend fun updateLastScannedAt(
        sessionId: String,
        lastScannedAtEpochMs: Long,
        updatedAtEpochMs: Long
    ) {
        sessionDao.updateLastScannedAt(
            id = sessionId,
            lastScannedAtEpochMs = lastScannedAtEpochMs,
            updatedAtEpochMs = updatedAtEpochMs
        )
    }

    suspend fun markIntakeStopped(
        sessionId: String,
        updatedAtEpochMs: Long
    ) {
        sessionDao.stopIntake(
            id = sessionId,
            status = SessionState.UPLOAD_DRAINING.name,
            intakeStoppedAtEpochMs = updatedAtEpochMs,
            updatedAtEpochMs = updatedAtEpochMs
        )
    }

    suspend fun markSessionCompleted(
        sessionId: String,
        updatedAtEpochMs: Long
    ) {
        sessionDao.completeSession(
            id = sessionId,
            status = SessionState.COMPLETED.name,
            completedAtEpochMs = updatedAtEpochMs,
            updatedAtEpochMs = updatedAtEpochMs
        )
    }

    suspend fun markSessionFailed(
        sessionId: String,
        updatedAtEpochMs: Long
    ) {
        sessionDao.updateStatus(
            id = sessionId,
            status = SessionState.FAILED.name,
            updatedAtEpochMs = updatedAtEpochMs
        )
    }

    suspend fun updateCounters(
        sessionId: String,
        acceptedFileCount: Int,
        queuedFileCount: Int,
        uploadingFileCount: Int,
        successFileCount: Int,
        failedFileCount: Int,
        skippedFileCount: Int,
        totalBytesDiscovered: Long,
        totalBytesUploaded: Long,
        updatedAtEpochMs: Long
    ) {
        sessionDao.updateCounters(
            id = sessionId,
            acceptedFileCount = acceptedFileCount,
            queuedFileCount = queuedFileCount,
            uploadingFileCount = uploadingFileCount,
            successFileCount = successFileCount,
            failedFileCount = failedFileCount,
            skippedFileCount = skippedFileCount,
            totalBytesDiscovered = totalBytesDiscovered,
            totalBytesUploaded = totalBytesUploaded,
            updatedAtEpochMs = updatedAtEpochMs
        )
    }

    suspend fun deleteSession(sessionId: String) {
        sessionDao.deleteById(sessionId)
    }

    fun observeSessionState(sessionId: String): Flow<SessionState?> {
        return sessionDao.observeById(sessionId).map { entity ->
            entity?.status.toSessionStateOrNull()
        }
    }

    private fun String?.toSessionStateOrNull(): SessionState? {
        if (this.isNullOrBlank()) return null
        return SessionState.entries.firstOrNull { it.name == this }
    }

    // ==== tambahan untuk ProjectSessionManager ====

    /**
     * Sesi aktif berdasarkan flag isActive.
     * Dipakai ProjectSessionManager sebagai sumber ProjectSession.
     */
    suspend fun getActiveSessionOnce(): SessionEntity? {
        return sessionDao.getActiveSession()
    }

    /**
     * Flow sesi aktif berdasarkan flag isActive.
     */
    fun observeActiveSession(): Flow<SessionEntity?> {
        return sessionDao.observeActiveSession()
    }

    /**
     * Helper untuk men-set flag isActive dan connectionMode.
     */
    suspend fun updateActiveFlags(
        sessionId: String,
        isActive: Boolean,
        connectionMode: ConnectionMode?,
        updatedAtEpochMs: Long
    ) {
        val existing = sessionDao.getById(sessionId) ?: return
        sessionDao.insert(
            existing.copy(
                isActive = isActive,
                connectionMode = connectionMode?.name ?: existing.connectionMode,
                updatedAtEpochMs = updatedAtEpochMs
            )
        )
    }

    // di dalam class SessionRepositoryImpl

    /**
     * Helper sederhana untuk menyimpan SessionEntity,
     * dipakai oleh adapter domain.
     */
    suspend fun createOrUpdateFromEntity(entity: SessionEntity) {
        // karena SessionDao.insert pakai REPLACE, ini sudah cukup
        sessionDao.insert(entity)
    }

    /**
     * Deactivate ALL sessions. Used to clean up before creating a new session.
     */
    suspend fun deactivateAllSessions() {
        sessionDao.deactivateAllSessions()
    }
}