package com.rvmeur.transferin.domain.session

import com.rvmeur.transferin.data.local.entity.SessionEntity
import com.rvmeur.transferin.domain.model.ConnectionMode
import com.rvmeur.transferin.domain.model.ProjectSession
import com.rvmeur.transferin.domain.model.SessionState
import com.rvmeur.transferin.domain.model.WatcherStatus
import com.rvmeur.transferin.domain.repository.SessionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class ProjectSessionManagerImpl(
    private val sessionRepository: SessionRepository,
    private val nowEpochMsProvider: () -> Long = { System.currentTimeMillis() }
) : ProjectSessionManager {

    companion object {
        private const val TAG = "ProjectSessionManager"
    }

    override val activeSession: Flow<ProjectSession?> =
        sessionRepository.observeActiveSession()
            .map { entity -> entity?.toProjectSession() }
            .distinctUntilChanged()

    override suspend fun createOrUpdateSession(
        sessionId: String,
        sessionLabel: String?,
        customerName: String?,
        targetNasPath: String,
        sourceFolderPath: String,
        connectionMode: ConnectionMode
    ) {
        android.util.Log.d(
            TAG,
            ">>> createOrUpdateSession: sessionId=$sessionId, connectionMode=$connectionMode, sourceFolderPath=$sourceFolderPath"
        )

        val now = nowEpochMsProvider()
        val existingActive = sessionRepository.getActiveSessionOnce()

        if (existingActive != null && existingActive.id != sessionId) {
            android.util.Log.d(
                TAG,
                ">>> Found different active session=${existingActive.id}, deactivating all before saving new active session"
            )
            sessionRepository.deactivateAllSessions()
        }

        val entity = SessionEntity(
            id = sessionId,
            sessionLabel = sessionLabel,
            customerName = customerName,
            sourceTreeUri = sourceFolderPath,
            targetNasDirPath = targetNasPath,
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
            startedAtEpochMs = existingActive
                ?.takeIf { it.id == sessionId }
                ?.startedAtEpochMs
                ?: now,
            intakeStoppedAtEpochMs = null,
            completedAtEpochMs = null,
            createdAtEpochMs = existingActive
                ?.takeIf { it.id == sessionId }
                ?.createdAtEpochMs
                ?: now,
            updatedAtEpochMs = now
        )

        android.util.Log.d(
            TAG,
            ">>> Saving entity: id=${entity.id}, connectionMode=${entity.connectionMode}, isActive=${entity.isActive}"
        )
        sessionRepository.save(entity)
    }

    override suspend fun setConnectionMode(connectionMode: ConnectionMode) {
        val now = nowEpochMsProvider()
        val existing = sessionRepository.getActiveSessionOnce() ?: run {
            android.util.Log.w(TAG, ">>> setConnectionMode ignored, no active session")
            return
        }

        if (existing.connectionMode == connectionMode.name) {
            android.util.Log.d(TAG, ">>> setConnectionMode skipped, already ${connectionMode.name}")
            return
        }

        val updated = existing.copy(
            connectionMode = connectionMode.name,
            updatedAtEpochMs = now
        )

        android.util.Log.d(
            TAG,
            ">>> setConnectionMode: sessionId=${existing.id}, from=${existing.connectionMode}, to=${updated.connectionMode}"
        )
        sessionRepository.save(updated)
    }

    override suspend fun setActive(isActive: Boolean) {
        val now = nowEpochMsProvider()
        val existing = sessionRepository.getActiveSessionOnce() ?: run {
            android.util.Log.w(TAG, ">>> setActive($isActive) ignored, no active session")
            return
        }

        if (existing.isActive == isActive) {
            android.util.Log.d(TAG, ">>> setActive skipped, already $isActive for sessionId=${existing.id}")
            return
        }

        val updated = existing.copy(
            isActive = isActive,
            updatedAtEpochMs = now
        )

        android.util.Log.d(
            TAG,
            ">>> setActive: sessionId=${existing.id}, isActive=$isActive"
        )
        sessionRepository.save(updated)
    }

    override suspend fun deactivateAllSessions() {
        android.util.Log.d(TAG, ">>> deactivateAllSessions called")
        sessionRepository.deactivateAllSessions()
    }

    private fun SessionEntity.toProjectSession(): ProjectSession {
        android.util.Log.d(
            TAG,
            ">>> toProjectSession: id=$id, entity.connectionMode=$connectionMode, isActive=$isActive"
        )

        return ProjectSession(
            id = id,
            name = sessionLabel ?: "",
            customerName = customerName,
            targetNasPath = targetNasDirPath,
            sourceFolderPath = sourceTreeUri,
            connectionMode = runCatching { ConnectionMode.valueOf(connectionMode) }
                .getOrElse {
                    android.util.Log.w(
                        TAG,
                        ">>> Invalid connectionMode='$connectionMode', fallback to IMPORT_FALLBACK"
                    )
                    ConnectionMode.IMPORT_FALLBACK
                },
            isActive = isActive
        )
    }
}