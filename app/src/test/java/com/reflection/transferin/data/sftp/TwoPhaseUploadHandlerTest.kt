package com.reflection.transferin.data.sftp

import android.net.Uri
import com.reflection.transferin.data.local.entity.TransferQueueItemEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class TwoPhaseUploadHandlerTest {

    private lateinit var sftpClient: SftpClient
    private lateinit var checkpointManager: UploadCheckpointManager
    private lateinit var queueDao: com.reflection.transferin.data.local.dao.TransferQueueDao
    private lateinit var twoPhaseHandler: TwoPhaseUploadHandler
    private lateinit var localUri: Uri
    private lateinit var queueItem: TransferQueueItemEntity

    @Before
    fun setUp() {
        sftpClient = mockk(relaxed = true)
        checkpointManager = mockk(relaxed = true)
        queueDao = mockk(relaxed = true)
        twoPhaseHandler = TwoPhaseUploadHandler(sftpClient, checkpointManager, queueDao)
        localUri = mockk(relaxed = true)
        queueItem = createMockEntity()
    }

    @Test
    fun `upload succeeds - verify uploadWithTwoPhaseCommit called and checkpoint cleared`() = runTest {
        coEvery { checkpointManager.getResumeOffset(queueItem.id) } returns 0L
        coEvery { sftpClient.uploadWithTwoPhaseCommit(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns Result.success(Unit)

        val result = twoPhaseHandler.upload(queueItem, localUri, "host", 22, "user", "pass", "/dir", "file.jpg")

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) {
            sftpClient.uploadWithTwoPhaseCommit("host", 22, "user", "pass", localUri, "/dir", "file.jpg", 2_000_000L, 0L, any())
            checkpointManager.clearCheckpoint(queueItem.id)
        }
    }

    @Test
    fun `size mismatch - verify deleteTmpFile called and exception propagated`() = runTest {
        coEvery { checkpointManager.getResumeOffset(queueItem.id) } returns 500_000L
        coEvery { sftpClient.uploadWithTwoPhaseCommit(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns Result.failure(SizeMismatchException(1500L, 2_000_000L))

        val result = twoPhaseHandler.upload(queueItem, localUri, "host", 22, "user", "pass", "/dir", "file.jpg")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is SizeMismatchException)
        coVerify(exactly = 1) {
            sftpClient.deleteTmpFile("host", 22, "user", "pass", "/dir", "file.jpg")
        }
    }

    @Test
    fun `resume from checkpoint - verify getResumeOffset used`() = runTest {
        coEvery { checkpointManager.getResumeOffset(queueItem.id) } returns 500_000L
        coEvery { sftpClient.uploadWithTwoPhaseCommit(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns Result.success(Unit)

        val result = twoPhaseHandler.upload(queueItem, localUri, "host", 22, "user", "pass", "/dir", "file.jpg")

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) {
            sftpClient.uploadWithTwoPhaseCommit("host", 22, "user", "pass", localUri, "/dir", "file.jpg", 2_000_000L, 500_000L, any())
        }
        // Since startOffset > 0, deleteTmpFile is NOT called initially
        coVerify(exactly = 0) {
            sftpClient.deleteTmpFile(any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `cleanup on retry - verify deleteTmpFile called when startOffset is 0`() = runTest {
        coEvery { checkpointManager.getResumeOffset(queueItem.id) } returns 0L
        coEvery { sftpClient.uploadWithTwoPhaseCommit(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns Result.success(Unit)

        twoPhaseHandler.upload(queueItem, localUri, "host", 22, "user", "pass", "/dir", "file.jpg")

        coVerify(exactly = 1) {
            sftpClient.deleteTmpFile("host", 22, "user", "pass", "/dir", "file.jpg")
        }
    }

    @Test
    fun `checkpoint saved during progress callback`() = runTest {
        val progressCallbackSlot = slot<((Long, Long) -> Unit)>()
        coEvery { checkpointManager.getResumeOffset(queueItem.id) } returns 0L
        coEvery {
            sftpClient.uploadWithTwoPhaseCommit(any(), any(), any(), any(), any(), any(), any(), any(), any(), capture(progressCallbackSlot))
        } returns Result.success(Unit)

        twoPhaseHandler.upload(queueItem, localUri, "host", 22, "user", "pass", "/dir", "file.jpg")

        assertTrue(progressCallbackSlot.isCaptured)
        progressCallbackSlot.captured.invoke(100_000L, 2_000_000L)

        coVerify(exactly = 1) {
            checkpointManager.maybeSaveCheckpoint(queueItem.id, 100_000L, 2_000_000L)
        }
    }

    @Test
    fun `staleTmpFileCleanup deletes files older than 5 minutes`() = runTest {
        val now = System.currentTimeMillis()
        val staleFile = TmpFileInfo("old.tmp", "/remote/old.tmp", 1000L, now - (6 * 60 * 1000L))
        val freshFile = TmpFileInfo("new.tmp", "/remote/new.tmp", 2000L, now - (2 * 60 * 1000L))

        coEvery { sftpClient.listTmpFiles(any(), any(), any(), any(), any()) } returns Result.success(listOf(staleFile, freshFile))
        coEvery { sftpClient.deleteTmpFile(any(), any(), any(), any(), any(), any()) } returns Result.success(Unit)

        twoPhaseHandler.staleTmpFileCleanup("host", 22, "user", "pass", "/remote")

        coVerify(exactly = 1) {
            sftpClient.deleteTmpFile("host", 22, "user", "pass", "/remote", "old")
        }
        coVerify(exactly = 0) {
            sftpClient.deleteTmpFile("host", 22, "user", "pass", "/remote", "new")
        }
    }

    @Test
    fun `handleUploadRetry cleans up tmp file and requeues`() = runTest {
        coEvery { sftpClient.deleteTmpFile(any(), any(), any(), any(), any(), any()) } returns Result.success(Unit)
        coEvery { queueDao.requeue(any(), any()) } returns Unit

        twoPhaseHandler.handleUploadRetry(queueItem, "host", 22, "user", "pass", "/dir", "file.jpg")

        coVerify(exactly = 1) {
            sftpClient.deleteTmpFile("host", 22, "user", "pass", "/dir", "file.jpg")
            queueDao.requeue(queueItem.id, any())
            checkpointManager.clearCheckpoint(queueItem.id)
        }
    }

    private fun createMockEntity(): TransferQueueItemEntity {
        return TransferQueueItemEntity(
            id = "item-1",
            sessionId = "session-1",
            sourceUri = "content://media",
            sourceFingerprint = "fp",
            sourceFileName = "file.jpg",
            sourceRelativePath = null,
            sourceMimeType = "image/jpeg",
            fileType = "IMAGE",
            priority = 0,
            fileSizeBytes = 2_000_000L,
            lastModifiedEpochMs = null,
            targetNasDirPath = "/remote",
            targetFileName = "file.jpg",
            status = "QUEUED",
            progressPercent = 0,
            bytesUploaded = 0L,
            retryCount = 0,
            errorMessage = null,
            createdAtEpochMs = 0L,
            updatedAtEpochMs = 0L,
            dispatchedAtEpochMs = null,
            uploadStartedAtEpochMs = null,
            completedAtEpochMs = null
        )
    }
}
