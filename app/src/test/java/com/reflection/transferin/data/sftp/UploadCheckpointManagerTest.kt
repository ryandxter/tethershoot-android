package com.reflection.transferin.data.sftp

import com.reflection.transferin.data.local.dao.TransferQueueDao
import com.reflection.transferin.data.local.entity.TransferQueueItemEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class UploadCheckpointManagerTest {

    private lateinit var queueDao: TransferQueueDao
    private lateinit var checkpointManager: UploadCheckpointManager

    @Before
    fun setUp() {
        queueDao = mockk(relaxed = true)
        checkpointManager = UploadCheckpointManager(queueDao)
    }

    @Test
    fun `getResumeOffset returns 0 when item is null`() = runTest {
        coEvery { queueDao.getById("item-1") } returns null
        val offset = checkpointManager.getResumeOffset("item-1")
        assertEquals(0L, offset)
    }

    @Test
    fun `getResumeOffset returns 0 when status is not UPLOADING`() = runTest {
        val item = createMockEntity(status = "QUEUED", bytesUploaded = 1000L)
        coEvery { queueDao.getById("item-1") } returns item
        val offset = checkpointManager.getResumeOffset("item-1")
        assertEquals(0L, offset)
    }

    @Test
    fun `getResumeOffset returns bytesUploaded when status is UPLOADING`() = runTest {
        val item = createMockEntity(status = "UPLOADING", bytesUploaded = 1000L)
        coEvery { queueDao.getById("item-1") } returns item
        val offset = checkpointManager.getResumeOffset("item-1")
        assertEquals(1000L, offset)
    }

    @Test
    fun `maybeSaveCheckpoint triggers save at 1MB boundary`() = runTest {
        checkpointManager.maybeSaveCheckpoint("item-1", 1_048_576L, 2_000_000L)
        coVerify(exactly = 1) {
            queueDao.updateProgress("item-1", "UPLOADING", 52, 1_048_576L, any())
        }
    }

    @Test
    fun `maybeSaveCheckpoint does NOT save when below both thresholds`() = runTest {
        // First checkpoint triggers because elapsed time is large (initial = 0)
        checkpointManager.maybeSaveCheckpoint("item-1", 1000L, 2_000_000L)
        
        // Reset mock for subsequent checks
        coEvery { queueDao.updateProgress(any(), any(), any(), any(), any()) } returns Unit
        
        checkpointManager.maybeSaveCheckpoint("item-1", 2000L, 2_000_000L)
        coVerify(exactly = 0) {
            queueDao.updateProgress("item-1", "UPLOADING", any(), 2000L, any())
        }
    }

    @Test
    fun `clearCheckpoint resets internal state`() = runTest {
        checkpointManager.maybeSaveCheckpoint("item-1", 1_048_576L, 2_000_000L)
        checkpointManager.clearCheckpoint("item-1")
        
        // After clear, even a small update will trigger because lastCheckpointTime/Bytes are reset
        checkpointManager.maybeSaveCheckpoint("item-1", 1000L, 2_000_000L)
        coVerify {
            queueDao.updateProgress("item-1", "UPLOADING", 0, 1000L, any())
        }
    }

    private fun createMockEntity(status: String, bytesUploaded: Long): TransferQueueItemEntity {
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
            status = status,
            progressPercent = 0,
            bytesUploaded = bytesUploaded,
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
