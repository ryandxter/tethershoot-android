package com.rvmeur.transferin.session.worker

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.net.toUri
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.WorkRequest
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit
import com.rvmeur.transferin.data.local.dao.NasConnectionDao
import com.rvmeur.transferin.data.local.dao.TransferQueueDao
import com.rvmeur.transferin.data.local.db.DatabaseProvider
import com.rvmeur.transferin.data.local.entity.TransferQueueItemEntity
import com.rvmeur.transferin.data.sftp.DelegatingSftpClient
import com.rvmeur.transferin.data.sftp.UploadCheckpointManager
import com.rvmeur.transferin.data.sftp.TwoPhaseUploadHandler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class TransferUploadWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        private const val TAG = "TransferUploadWorker"

        const val KEY_QUEUE_ITEM_ID = "key_queue_item_id"
        const val KEY_PROGRESS_PERCENT = "key_progress_percent"
        const val KEY_STATUS = "key_status"
        const val KEY_ERROR_MESSAGE = "key_error_message"
        const val KEY_SKIP_STALE_CLEANUP = "skip_stale_cleanup"

        /**
         * Pause state for USB disconnect handling.
         * When paused, uploads defer retry until USB reconnects.
         */
        @Volatile
        private var isPaused = false

        /**
         * Pauses all pending uploads.
         * Call this when USB disconnects to prevent uploads from starting.
         */
        fun pauseUploads() {
            isPaused = true
            android.util.Log.d(TAG, "Uploads paused due to USB disconnect")
        }

        /**
         * Resumes paused uploads.
         * Call this when USB reconnects to allow uploads to proceed.
         */
        fun resumeUploads() {
            isPaused = false
            android.util.Log.d(TAG, "Uploads resumed due to USB reconnect")
        }

        /**
         * Returns whether uploads are currently paused.
         */
        fun areUploadsPaused(): Boolean = isPaused
    }

    private val database = DatabaseProvider.getDatabase(appContext)
    private val queueDao: TransferQueueDao = database.transferQueueDao()
    private val nasDao: NasConnectionDao = database.nasConnectionDao()
    private val sftpClient = DelegatingSftpClient(appContext)
    private val checkpointManager = UploadCheckpointManager(queueDao)
    private val twoPhaseHandler = TwoPhaseUploadHandler(sftpClient, checkpointManager, queueDao)

    override suspend fun doWork(): Result {
        // Check if uploads are paused due to USB disconnect
        if (isPaused) {
            Log.d(TAG, "Uploads are paused (USB disconnected), deferring with exponential backoff")
            return Result.retry()
        }

        val queueItemId = inputData.getString(KEY_QUEUE_ITEM_ID).orEmpty()

        Log.d(TAG, "doWork() start: queueItemId=$queueItemId, runAttemptCount=$runAttemptCount")

        if (queueItemId.isBlank()) {
            Log.e(TAG, "doWork() failure: missing queue item id")
            return Result.failure(
                workDataOf(KEY_ERROR_MESSAGE to "Missing queue item id")
            )
        }

        val queueItem = queueDao.getById(queueItemId)
            ?: run {
                Log.e(TAG, "doWork() failure: queue item not found, id=$queueItemId")
                return Result.failure(
                    workDataOf(
                        KEY_QUEUE_ITEM_ID to queueItemId,
                        KEY_ERROR_MESSAGE to "Queue item not found"
                    )
                )
            }

        return try {
            val nasConnection = nasDao.getConnectionOrNull()
                ?: return failPermanently(
                    queueItem = queueItem,
                    message = "NAS connection not configured"
                )

            val localUri = resolveSourceUri(queueItem)
            val resolvedFileSize = resolveFileSizeBytes(
                sourceUri = localUri,
                fallback = queueItem.fileSizeBytes
            )

            val remoteDirectory = when {
                queueItem.targetNasDirPath.isNotBlank() -> queueItem.targetNasDirPath.trim()
                !nasConnection.defaultRemoteFolder.isNullOrBlank() -> nasConnection.defaultRemoteFolder.trim()
                else -> "/"
            }

            val remoteFileName = queueItem.targetFileName.trim()
            if (remoteFileName.isBlank()) {
                return failPermanently(
                    queueItem = queueItem,
                    message = "Target file name is blank"
                )
            }

            val dispatchedAt = System.currentTimeMillis()
            queueDao.markDispatched(
                id = queueItem.id,
                dispatchedAtEpochMs = dispatchedAt,
                updatedAtEpochMs = dispatchedAt
            )

            val uploadStartedAt = System.currentTimeMillis()
            queueDao.markUploading(
                id = queueItem.id,
                uploadStartedAtEpochMs = uploadStartedAt,
                updatedAtEpochMs = uploadStartedAt
            )

            queueDao.updateProgress(
                id = queueItem.id,
                status = "UPLOADING",
                progressPercent = 0,
                bytesUploaded = 0L,
                updatedAtEpochMs = uploadStartedAt
            )

            setProgress(
                workDataOf(
                    KEY_QUEUE_ITEM_ID to queueItem.id,
                    KEY_PROGRESS_PERCENT to 0,
                    KEY_STATUS to "UPLOADING"
                )
            )

            val skipCleanup = inputData.getBoolean(KEY_SKIP_STALE_CLEANUP, false)
            if (!skipCleanup) {
                try {
                    twoPhaseHandler.staleTmpFileCleanup(
                        host = nasConnection.host,
                        port = nasConnection.port,
                        username = nasConnection.username,
                        password = nasConnection.password,
                        remoteDirectory = remoteDirectory
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Stale tmp file cleanup failed, proceeding with upload: ${e.message}", e)
                }
            }

            val uploadResult = twoPhaseHandler.upload(
                queueItem = queueItem,
                localUri = localUri,
                host = nasConnection.host,
                port = nasConnection.port,
                username = nasConnection.username,
                password = nasConnection.password,
                remoteDirectory = remoteDirectory,
                remoteFileName = remoteFileName
            )

            val currentItem = queueDao.getById(queueItem.id)
            val latestUploadedBytes = currentItem?.bytesUploaded ?: 0L
            val latestProgressPercent = currentItem?.progressPercent ?: 0

            if (isStopped) {
                Log.w(TAG, "doWork() stopped after upload attempt: queueItemId=${queueItem.id}")
                return handleStoppedWorker(queueItem, "Worker stopped after upload attempt")
            }

            if (uploadResult.isSuccess) {
                val completedAt = System.currentTimeMillis()

                val finalUploadedBytes = resolvedFileSize
                    .coerceAtLeast(latestUploadedBytes)
                    .coerceAtLeast(0L)

                queueDao.updateProgress(
                    id = queueItem.id,
                    status = "SUCCESS",
                    progressPercent = 100,
                    bytesUploaded = finalUploadedBytes,
                    updatedAtEpochMs = completedAt
                )

                queueDao.markSuccess(
                    id = queueItem.id,
                    completedAtEpochMs = completedAt,
                    updatedAtEpochMs = completedAt
                )

                try {
                    val thumbDir = File(applicationContext.getExternalFilesDir(null), "camera_thumbnails")
                    val thumbFile = File(thumbDir, "thumb_${queueItem.id}.jpg")
                    if (thumbFile.exists() && thumbFile.length() > 0) {
                        val thumbUri = Uri.fromFile(thumbFile).toString()
                        queueDao.updateSourceUri(queueItem.id, thumbUri)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to update sourceUri with thumbnail", e)
                }

                cleanupLocalSource(localUri)

                setProgress(
                    workDataOf(
                        KEY_QUEUE_ITEM_ID to queueItem.id,
                        KEY_PROGRESS_PERCENT to 100,
                        KEY_STATUS to "SUCCESS"
                    )
                )

                Log.d(
                    TAG,
                    "doWork() success: queueItemId=${queueItem.id}, uploadedBytes=$finalUploadedBytes"
                )

                Result.success(
                    workDataOf(
                        KEY_QUEUE_ITEM_ID to queueItem.id,
                        KEY_PROGRESS_PERCENT to 100,
                        KEY_STATUS to "SUCCESS"
                    )
                )
            } else {
                val errorMessage = uploadResult.exceptionOrNull()?.message ?: "Upload failed"

                Log.e(
                    TAG,
                    "doWork() upload failed: queueItemId=${queueItem.id}, error=$errorMessage"
                )

                queueDao.updateProgress(
                    id = queueItem.id,
                    status = "RETRY_SCHEDULED",
                    progressPercent = latestProgressPercent.coerceIn(0, 99),
                    bytesUploaded = latestUploadedBytes.coerceAtLeast(0L),
                    updatedAtEpochMs = System.currentTimeMillis()
                )

                handleUploadFailure(
                    queueItem = queueItem,
                    message = errorMessage
                )
            }
        } catch (ce: CancellationException) {
            Log.w(TAG, "doWork() cancelled: queueItemId=${queueItem.id}, message=${ce.message}")
            handleStoppedWorker(queueItem, ce.message ?: "Worker cancelled")
        } catch (e: Exception) {
            Log.e(
                TAG,
                "doWork() exception: queueItemId=${queueItem.id}, error=${e.message}",
                e
            )
            handleUploadFailure(
                queueItem = queueItem,
                message = e.message ?: "Upload failed"
            )
        }
    }

    private suspend fun handleUploadFailure(
        queueItem: TransferQueueItemEntity,
        message: String
    ): Result {
        val updatedAt = System.currentTimeMillis()

        queueDao.markRetryScheduled(
            id = queueItem.id,
            errorMessage = message,
            updatedAtEpochMs = updatedAt
        )

        Log.w(
            TAG,
            "handleUploadFailure(): queueItemId=${queueItem.id}, message=$message -> RETRY"
        )

        return Result.retry()
    }

    private suspend fun handleStoppedWorker(
        queueItem: TransferQueueItemEntity,
        message: String
    ): Result {
        val updatedAt = System.currentTimeMillis()

        queueDao.markRetryScheduled(
            id = queueItem.id,
            errorMessage = message,
            updatedAtEpochMs = updatedAt
        )

        Log.w(
            TAG,
            "handleStoppedWorker(): queueItemId=${queueItem.id}, message=$message -> RETRY"
        )

        return Result.retry()
    }

    private suspend fun failPermanently(
        queueItem: TransferQueueItemEntity,
        message: String
    ): Result {
        val updatedAt = System.currentTimeMillis()

        queueDao.markFailed(
            id = queueItem.id,
            errorMessage = message,
            completedAtEpochMs = updatedAt,
            updatedAtEpochMs = updatedAt
        )

        Log.e(
            TAG,
            "failPermanently(): queueItemId=${queueItem.id}, message=$message"
        )

        return Result.failure(
            workDataOf(
                KEY_QUEUE_ITEM_ID to queueItem.id,
                KEY_STATUS to "FAILED",
                KEY_ERROR_MESSAGE to message
            )
        )
    }

    private fun resolveSourceUri(queueItem: TransferQueueItemEntity): Uri {
        val raw = queueItem.sourceUri.trim()
        require(raw.isNotBlank()) { "Source URI is blank" }

        return when {
            raw.startsWith("content://") -> raw.toUri()
            raw.startsWith("file://") -> raw.toUri()
            else -> Uri.fromFile(File(raw))
        }
    }

    private suspend fun resolveFileSizeBytes(
        sourceUri: Uri,
        fallback: Long
    ): Long = withContext(Dispatchers.IO) {
        if (fallback > 0L) return@withContext fallback

        runCatching {
            applicationContext.contentResolver
                .openAssetFileDescriptor(sourceUri, "r")
                ?.use { it.length }
        }.getOrNull()?.takeIf { it > 0L }
            ?: runCatching {
                if (sourceUri.scheme == "file") {
                    File(sourceUri.path.orEmpty()).length()
                } else {
                    0L
                }
            }.getOrDefault(0L)
    }

    private suspend fun cleanupLocalSource(sourceUri: Uri) = withContext(Dispatchers.IO) {
        if (sourceUri.scheme != "file") {
            Log.d(TAG, "cleanupLocalSource() skipped: non-file uri=$sourceUri")
            return@withContext
        }

        val path = sourceUri.path.orEmpty()
        if (path.isBlank()) {
            Log.d(TAG, "cleanupLocalSource() skipped: blank file path")
            return@withContext
        }

        runCatching {
            val file = File(path)
            if (file.exists()) {
                val deleted = file.delete()
                Log.d(
                    TAG,
                    "cleanupLocalSource() file delete result=$deleted, path=${file.absolutePath}"
                )
            } else {
                Log.d(TAG, "cleanupLocalSource() skipped: file does not exist, path=$path")
            }
        }.onFailure { throwable ->
            Log.e(
                TAG,
                "cleanupLocalSource() failed: uri=$sourceUri, error=${throwable.message}",
                throwable
            )
        }
    }
}