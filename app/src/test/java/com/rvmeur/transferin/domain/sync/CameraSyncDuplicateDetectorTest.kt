package com.rvmeur.transferin.domain.sync

import com.rvmeur.transferin.data.local.dao.CameraSyncDao
import com.rvmeur.transferin.data.local.dao.UploadedFingerprintDao
import com.rvmeur.transferin.data.local.entity.CameraSyncItemEntity
import com.rvmeur.transferin.data.local.entity.CameraSyncStatus
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraSyncDuplicateDetectorTest {

    private val mockSyncDao = mockk<CameraSyncDao>()
    private val mockUploadedFingerprintDao = mockk<UploadedFingerprintDao>()
    private val detector = CameraSyncDuplicateDetector(mockSyncDao, mockUploadedFingerprintDao)

    @Test
    fun `returns NewMedia when neither table has record`() = runBlocking {
        val fingerprint = "fp_new_123"
        coEvery { mockSyncDao.getByFingerprint(fingerprint) } returns null
        coEvery { mockUploadedFingerprintDao.hasUploaded(fingerprint) } returns false

        val verdict = detector.checkDuplicate(fingerprint)
        assertTrue(verdict is DuplicateVerdict.NewMedia)
    }

    @Test
    fun `returns AlreadyConfirmed when legacy uploaded table contains fingerprint`() = runBlocking {
        val fingerprint = "fp_legacy_123"
        coEvery { mockSyncDao.getByFingerprint(fingerprint) } returns null
        coEvery { mockUploadedFingerprintDao.hasUploaded(fingerprint) } returns true

        val verdict = detector.checkDuplicate(fingerprint)
        assertTrue(verdict is DuplicateVerdict.AlreadyConfirmed)
        assertEquals(null, (verdict as DuplicateVerdict.AlreadyConfirmed).remoteFileId)
    }

    @Test
    fun `returns AlreadyConfirmed with remoteFileId when status is CONFIRMED_CLOUD or PURGED_LOCAL`() = runBlocking {
        val fpConfirmed = "fp_conf"
        val itemConfirmed = createTestEntity(fpConfirmed, CameraSyncStatus.CONFIRMED_CLOUD, remoteId = "gdrive-123")
        coEvery { mockSyncDao.getByFingerprint(fpConfirmed) } returns itemConfirmed

        val verdictConfirmed = detector.checkDuplicate(fpConfirmed)
        assertTrue(verdictConfirmed is DuplicateVerdict.AlreadyConfirmed)
        assertEquals("gdrive-123", (verdictConfirmed as DuplicateVerdict.AlreadyConfirmed).remoteFileId)

        val fpPurged = "fp_purged"
        val itemPurged = createTestEntity(fpPurged, CameraSyncStatus.PURGED_LOCAL, remoteId = "gdrive-456")
        coEvery { mockSyncDao.getByFingerprint(fpPurged) } returns itemPurged

        val verdictPurged = detector.checkDuplicate(fpPurged)
        assertTrue(verdictPurged is DuplicateVerdict.AlreadyConfirmed)
        assertEquals("gdrive-456", (verdictPurged as DuplicateVerdict.AlreadyConfirmed).remoteFileId)
    }

    @Test
    fun `returns InProgress when item is DETECTED, BUFFERED_LOCAL, or STREAMING_CLOUD`() = runBlocking {
        val fpStreaming = "fp_stream"
        val item = createTestEntity(fpStreaming, CameraSyncStatus.STREAMING_CLOUD)
        coEvery { mockSyncDao.getByFingerprint(fpStreaming) } returns item

        val verdict = detector.checkDuplicate(fpStreaming)
        assertTrue(verdict is DuplicateVerdict.InProgress)
        assertEquals(CameraSyncStatus.STREAMING_CLOUD, (verdict as DuplicateVerdict.InProgress).status)
    }

    @Test
    fun `returns FailedRetryable when status is FAILED`() = runBlocking {
        val fpFailed = "fp_fail"
        val item = createTestEntity(fpFailed, CameraSyncStatus.FAILED, retryCount = 2, error = "Network timeout")
        coEvery { mockSyncDao.getByFingerprint(fpFailed) } returns item

        val verdict = detector.checkDuplicate(fpFailed)
        assertTrue(verdict is DuplicateVerdict.FailedRetryable)
        val failedVerdict = verdict as DuplicateVerdict.FailedRetryable
        assertEquals(2, failedVerdict.retryCount)
        assertEquals("Network timeout", failedVerdict.lastErrorMessage)
    }

    private fun createTestEntity(
        fingerprint: String,
        status: CameraSyncStatus,
        remoteId: String? = null,
        retryCount: Int = 0,
        error: String? = null
    ): CameraSyncItemEntity {
        return CameraSyncItemEntity(
            id = "id-$fingerprint",
            sessionId = "session-1",
            vendorId = 0x04CB,
            cameraSerialNumber = "FUJI123",
            storageId = 1,
            objectHandle = 10,
            filename = "DSCF0001.RAF",
            fileSizeBytes = 50_000_000L,
            captureTimestampMs = 1000L,
            compoundFingerprint = fingerprint,
            status = status,
            remoteFileId = remoteId,
            retryCount = retryCount,
            lastErrorMessage = error
        )
    }
}
