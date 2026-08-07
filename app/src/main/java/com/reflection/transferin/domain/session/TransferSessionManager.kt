package com.reflection.transferin.domain.session

import com.reflection.transferin.domain.model.TransferQueueItem
import com.reflection.transferin.session.model.SessionRuntimeState
import kotlinx.coroutines.flow.Flow

interface TransferSessionManager {

    suspend fun startSession(
        sourceTreeUri: String,
        targetNasDirPath: String,
        sessionLabel: String? = null
    ): String

    suspend fun stopSession(sessionId: String)

    suspend fun scanNow(sessionId: String)

    suspend fun dispatchNext()

    suspend fun dispatchNext(sessionId: String)

    suspend fun retryItem(queueItemId: String)

    suspend fun cancelItem(queueItemId: String)

    suspend fun markItemFailed(
        queueItemId: String,
        errorMessage: String?
    )

    suspend fun onWorkerStarted(queueItemId: String)

    suspend fun onWorkerProgress(
        queueItemId: String,
        progressPercent: Int,
        bytesUploaded: Long
    )

    suspend fun onWorkerSuccess(queueItemId: String)

    suspend fun onWorkerRetry(
        queueItemId: String,
        errorMessage: String?
    )

    suspend fun onWorkerFailure(
        queueItemId: String,
        errorMessage: String?
    )

    fun observeSession(sessionId: String): Flow<SessionRuntimeState>

    fun observeActiveSession(): Flow<SessionRuntimeState?>

    fun observePendingQueue(sessionId: String): Flow<List<TransferQueueItem>>
}