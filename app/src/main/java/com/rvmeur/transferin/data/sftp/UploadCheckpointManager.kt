package com.rvmeur.transferin.data.sftp

import com.rvmeur.transferin.data.local.dao.TransferQueueDao
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class UploadCheckpointManager(
    private val queueDao: TransferQueueDao
) {
    companion object {
        const val CHECKPOINT_INTERVAL_BYTES = 1_048_576L // 1MB
        const val CHECKPOINT_INTERVAL_MS = 30_000L // 30s
    }

    private var lastCheckpointTime: Long = 0L
    private var lastCheckpointBytes: Long = 0L
    private val checkpointMutex = Mutex()

    suspend fun getResumeOffset(queueItemId: String): Long {
        val item = queueDao.getById(queueItemId)
        return if (item != null && item.status == "UPLOADING") {
            item.bytesUploaded
        } else {
            0L
        }
    }

    suspend fun hasCheckpoint(queueItemId: String): Boolean {
        return getResumeOffset(queueItemId) > 0L
    }

    suspend fun maybeSaveCheckpoint(queueItemId: String, bytesUploaded: Long, totalBytes: Long) {
        checkpointMutex.withLock {
            val now = System.currentTimeMillis()
            val bytesSinceLast = bytesUploaded - lastCheckpointBytes
            val timeSinceLast = now - lastCheckpointTime

            if (bytesSinceLast >= CHECKPOINT_INTERVAL_BYTES || timeSinceLast >= CHECKPOINT_INTERVAL_MS) {
                saveCheckpoint(queueItemId, bytesUploaded, totalBytes)
                lastCheckpointTime = now
                lastCheckpointBytes = bytesUploaded
            }
        }
    }

    suspend fun saveCheckpoint(queueItemId: String, bytesUploaded: Long, totalBytes: Long) {
        val progressPercent = if (totalBytes > 0L) {
            ((bytesUploaded.toDouble() / totalBytes.toDouble()) * 100).toInt().coerceIn(0, 100)
        } else {
            0
        }
        queueDao.updateProgress(
            id = queueItemId,
            status = "UPLOADING",
            progressPercent = progressPercent,
            bytesUploaded = bytesUploaded,
            updatedAtEpochMs = System.currentTimeMillis()
        )
    }

    suspend fun clearCheckpoint(queueItemId: String) {
        checkpointMutex.withLock {
            lastCheckpointTime = 0L
            lastCheckpointBytes = 0L
        }
    }
}
