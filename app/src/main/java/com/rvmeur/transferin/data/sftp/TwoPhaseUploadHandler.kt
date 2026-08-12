package com.rvmeur.transferin.data.sftp

import android.net.Uri
import com.rvmeur.transferin.data.local.dao.TransferQueueDao
import com.rvmeur.transferin.data.local.entity.TransferQueueItemEntity

class SizeMismatchException(val remoteSize: Long, val expectedSize: Long) : 
    Exception("Size mismatch: remote=$remoteSize, expected=$expectedSize")

class TwoPhaseUploadHandler(
    private val sftpClient: SftpClient,
    private val checkpointManager: UploadCheckpointManager,
    private val queueDao: TransferQueueDao
) {
    companion object {
        const val STALE_TMP_FILE_MAX_AGE_MS = 5 * 60 * 1000L // 5 minutes
    }

    suspend fun upload(
        queueItem: TransferQueueItemEntity,
        localUri: Uri,
        host: String,
        port: Int,
        username: String,
        password: String,
        remoteDirectory: String,
        remoteFileName: String
    ): Result<Unit> {
        val startOffset = checkpointManager.getResumeOffset(queueItem.id)
        if (startOffset == 0L) {
            cleanupTmpFile(host, port, username, password, remoteDirectory, remoteFileName)
        }

        val result = sftpClient.uploadWithTwoPhaseCommit(
            host = host,
            port = port,
            username = username,
            password = password,
            localUri = localUri,
            remoteDirectory = remoteDirectory,
            remoteFileName = remoteFileName,
            expectedSize = queueItem.fileSizeBytes,
            startOffset = startOffset,
            onProgress = { bytesTransferred, totalBytes ->
                kotlinx.coroutines.runBlocking {
                    checkpointManager.maybeSaveCheckpoint(queueItem.id, bytesTransferred, totalBytes)
                }
            }
        )

        return if (result.isSuccess) {
            checkpointManager.clearCheckpoint(queueItem.id)
            Result.success(Unit)
        } else {
            val exception = result.exceptionOrNull()
            if (exception is SizeMismatchException) {
                cleanupTmpFile(host, port, username, password, remoteDirectory, remoteFileName)
            }
            Result.failure(exception ?: Exception("Unknown upload error"))
        }
    }

    suspend fun cleanupTmpFile(
        host: String,
        port: Int,
        username: String,
        password: String,
        remoteDirectory: String,
        remoteFileName: String
    ): Result<Unit> {
        return sftpClient.deleteTmpFile(host, port, username, password, remoteDirectory, remoteFileName)
    }

    suspend fun staleTmpFileCleanup(
        host: String,
        port: Int,
        username: String,
        password: String,
        remoteDirectory: String
    ): Result<Unit> {
        return try {
            val result = sftpClient.listTmpFiles(host, port, username, password, remoteDirectory)
            if (result.isSuccess) {
                val files = result.getOrNull().orEmpty()
                val now = System.currentTimeMillis()
                var deleteCount = 0
                for (file in files) {
                    if (now - file.modifiedAt > STALE_TMP_FILE_MAX_AGE_MS) {
                        val originalName = file.name.removeSuffix(".tmp")
                        sftpClient.deleteTmpFile(host, port, username, password, remoteDirectory, originalName)
                        deleteCount++
                    }
                }
                println("TwoPhaseUploadHandler: Cleaned up $deleteCount stale tmp files in $remoteDirectory")
            }
            Result.success(Unit)
        } catch (e: Exception) {
            println("TwoPhaseUploadHandler: Error during stale tmp file cleanup: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun handleUploadRetry(
        queueItem: TransferQueueItemEntity,
        host: String,
        port: Int,
        username: String,
        password: String,
        remoteDirectory: String,
        remoteFileName: String
    ): Result<Unit> {
        cleanupTmpFile(host, port, username, password, remoteDirectory, remoteFileName)
        queueDao.requeue(queueItem.id, System.currentTimeMillis())
        checkpointManager.clearCheckpoint(queueItem.id)
        return Result.success(Unit)
    }
}
