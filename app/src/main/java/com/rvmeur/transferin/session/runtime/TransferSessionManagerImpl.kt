package com.rvmeur.transferin.session.runtime

import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.rvmeur.transferin.data.local.entity.SessionEntity
import com.rvmeur.transferin.data.repository.SessionRepositoryImpl
import com.rvmeur.transferin.data.repository.TransferQueueRepositoryImpl
import com.rvmeur.transferin.domain.model.SessionState
import com.rvmeur.transferin.domain.model.WatcherStatus
import com.rvmeur.transferin.session.model.SessionRuntimeState
import com.rvmeur.transferin.session.worker.TransferUploadWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import java.util.UUID

@Suppress("unused")
class TransferSessionManagerImpl(
    private val sessionRepository: SessionRepositoryImpl,
    private val queueRepository: TransferQueueRepositoryImpl,
    private val workManager: WorkManager
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _activeSessionId = MutableStateFlow<String?>(null)
    val activeSessionId: StateFlow<String?> = _activeSessionId.asStateFlow()

    suspend fun startSession(
        sourceTreeUri: String,
        targetNasDirPath: String,
        sessionLabel: String? = null,
        customerName: String? = null
    ): String {
        val now = System.currentTimeMillis()
        val sessionId = UUID.randomUUID().toString()

        sessionRepository.createSession(
            sessionId = sessionId,
            sessionLabel = sessionLabel,
            customerName = customerName,
            sourceTreeUri = sourceTreeUri,
            targetNasDirPath = targetNasDirPath,
            startedAtEpochMs = now
        )

        sessionRepository.updateWatcherStatus(
            sessionId = sessionId,
            watcherStatus = WatcherStatus.IDLE,
            updatedAtEpochMs = now
        )

        sessionRepository.updateSessionState(
            sessionId = sessionId,
            sessionState = SessionState.RUNNING,
            updatedAtEpochMs = now
        )

        _activeSessionId.value = sessionId
        refreshSessionCounters(sessionId)
        return sessionId
    }

    suspend fun stopSession(sessionId: String) {
        val now = System.currentTimeMillis()

        sessionRepository.updateWatcherStatus(
            sessionId = sessionId,
            watcherStatus = WatcherStatus.STOPPED,
            updatedAtEpochMs = now
        )

        sessionRepository.markIntakeStopped(
            sessionId = sessionId,
            updatedAtEpochMs = now
        )

        refreshSessionCounters(sessionId)
    }

    suspend fun completeSessionIfDrained(sessionId: String) {
        val queued = queueRepository.countBySessionAndStatus(sessionId, "QUEUED")
        val retry = queueRepository.countBySessionAndStatus(sessionId, "RETRY_SCHEDULED")
        val uploading = queueRepository.countBySessionAndStatus(sessionId, "UPLOADING")
        val dispatched = queueRepository.countBySessionAndStatus(sessionId, "DISPATCHED")

        if (queued == 0 && retry == 0 && uploading == 0 && dispatched == 0) {
            sessionRepository.markSessionCompleted(
                sessionId = sessionId,
                updatedAtEpochMs = System.currentTimeMillis()
            )

            if (_activeSessionId.value == sessionId) {
                _activeSessionId.value = null
            }
        }
    }

    suspend fun refreshSessionCounters(sessionId: String) {
        val accepted =
            queueRepository.countBySessionAndStatus(sessionId, "QUEUED") +
                    queueRepository.countBySessionAndStatus(sessionId, "DISPATCHED") +
                    queueRepository.countBySessionAndStatus(sessionId, "UPLOADING") +
                    queueRepository.countBySessionAndStatus(sessionId, "SUCCESS") +
                    queueRepository.countBySessionAndStatus(sessionId, "FAILED") +
                    queueRepository.countBySessionAndStatus(sessionId, "RETRY_SCHEDULED") +
                    queueRepository.countBySessionAndStatus(sessionId, "CANCELLED")

        val queued =
            queueRepository.countBySessionAndStatus(sessionId, "QUEUED") +
                    queueRepository.countBySessionAndStatus(sessionId, "RETRY_SCHEDULED")

        val uploading =
            queueRepository.countBySessionAndStatus(sessionId, "UPLOADING") +
                    queueRepository.countBySessionAndStatus(sessionId, "DISPATCHED")

        val success = queueRepository.countBySessionAndStatus(sessionId, "SUCCESS")
        val failed = queueRepository.countBySessionAndStatus(sessionId, "FAILED")
        val skipped = queueRepository.countBySessionAndStatus(sessionId, "CANCELLED")

        val discoveredBytes = queueRepository.sumDiscoveredBytesBySession(sessionId)
        val uploadedBytes = queueRepository.sumUploadedBytesBySession(sessionId)

        sessionRepository.updateCounters(
            sessionId = sessionId,
            acceptedFileCount = accepted,
            queuedFileCount = queued,
            uploadingFileCount = uploading,
            successFileCount = success,
            failedFileCount = failed,
            skippedFileCount = skipped,
            totalBytesDiscovered = discoveredBytes,
            totalBytesUploaded = uploadedBytes,
            updatedAtEpochMs = System.currentTimeMillis()
        )
    }

    suspend fun dispatchNextIfNeeded(sessionId: String): Boolean {
        val candidate = queueRepository.getNextDispatchCandidateBySession(sessionId) ?: return false
        val now = System.currentTimeMillis()

        queueRepository.markDispatched(
            id = candidate.id,
            dispatchedAtEpochMs = now,
            updatedAtEpochMs = now
        )

        enqueueUploadWork(
            sessionId = sessionId,
            queueItemId = candidate.id
        )

        refreshSessionCounters(sessionId)
        return true
    }

    suspend fun onUploadStarted(sessionId: String, queueItemId: String) {
        val now = System.currentTimeMillis()

        queueRepository.markUploading(
            id = queueItemId,
            uploadStartedAtEpochMs = now,
            updatedAtEpochMs = now
        )

        refreshSessionCounters(sessionId)
    }

    suspend fun onUploadProgress(
        sessionId: String,
        queueItemId: String,
        progressPercent: Int,
        bytesUploaded: Long
    ) {
        queueRepository.updateProgress(
            id = queueItemId,
            status = "UPLOADING",
            progressPercent = progressPercent,
            bytesUploaded = bytesUploaded,
            updatedAtEpochMs = System.currentTimeMillis()
        )

        refreshSessionCounters(sessionId)
    }

    suspend fun onUploadSuccess(sessionId: String, queueItemId: String) {
        val now = System.currentTimeMillis()

        queueRepository.markSuccess(
            id = queueItemId,
            completedAtEpochMs = now,
            updatedAtEpochMs = now
        )

        refreshSessionCounters(sessionId)
        completeSessionIfDrained(sessionId)
    }

    suspend fun onUploadRetry(
        sessionId: String,
        queueItemId: String,
        errorMessage: String?
    ) {
        queueRepository.markRetryScheduled(
            id = queueItemId,
            errorMessage = errorMessage,
            updatedAtEpochMs = System.currentTimeMillis()
        )

        refreshSessionCounters(sessionId)
    }

    suspend fun onUploadFailed(
        sessionId: String,
        queueItemId: String,
        errorMessage: String?
    ) {
        val now = System.currentTimeMillis()

        queueRepository.markFailed(
            id = queueItemId,
            errorMessage = errorMessage,
            completedAtEpochMs = now,
            updatedAtEpochMs = now
        )

        refreshSessionCounters(sessionId)
        completeSessionIfDrained(sessionId)
    }

    fun observeSessionEntity(sessionId: String): Flow<SessionEntity> {
        return sessionRepository.observeSession(sessionId).filterNotNull()
    }

    fun observeRuntimeState(sessionId: String): Flow<SessionRuntimeState> {
        val sessionFlow = sessionRepository.observeSession(sessionId).filterNotNull()
        val queueFlow = queueRepository.observeBySession(sessionId)

        return combine(sessionFlow, queueFlow) { session, items ->
            SessionRuntimeState(
                sessionId = session.id,
                sessionLabel = session.sessionLabel,
                customerName = session.customerName,
                sourceTreeUri = session.sourceTreeUri,
                targetNasDirPath = session.targetNasDirPath,
                sessionState = session.status,
                watcherStatus = session.watcherStatus,
                acceptedFileCount = session.acceptedFileCount,
                queuedFileCount = session.queuedFileCount,
                uploadingFileCount = session.uploadingFileCount,
                successFileCount = session.successFileCount,
                failedFileCount = session.failedFileCount,
                skippedFileCount = session.skippedFileCount,
                totalBytesDiscovered = session.totalBytesDiscovered,
                totalBytesUploaded = session.totalBytesUploaded,
                activeItems = items.size,
                lastUpdatedAtEpochMs = session.updatedAtEpochMs
            )
        }
    }

    fun observeActiveSessionRuntime(): Flow<SessionRuntimeState?> {
        return activeSessionId.flatMapLatest { sessionId ->
            if (sessionId.isNullOrBlank()) {
                flowOf(null)
            } else {
                observeRuntimeState(sessionId)
            }
        }
    }

    fun setActiveSession(sessionId: String?) {
        _activeSessionId.value = sessionId
    }

    fun refreshCountersAsync(sessionId: String) {
        scope.launch {
            refreshSessionCounters(sessionId)
        }
    }

    private fun enqueueUploadWork(
        sessionId: String,
        queueItemId: String
    ) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<TransferUploadWorker>()
            .setConstraints(constraints)
            .setInputData(
                workDataOf(
                    KEY_SESSION_ID to sessionId,
                    KEY_QUEUE_ITEM_ID to queueItemId
                )
            )
            .addTag("transfer-upload")
            .addTag("session:$sessionId")
            .addTag("queue:$queueItemId")
            .build()

        workManager.enqueueUniqueWork(
            "upload:$queueItemId",
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    companion object {
        const val KEY_SESSION_ID = "key_session_id"
        const val KEY_QUEUE_ITEM_ID = "key_queue_item_id"
    }
}