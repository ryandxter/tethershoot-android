package com.rvmeur.transferin.domain.sync

import com.rvmeur.transferin.data.local.dao.CameraSyncDao
import com.rvmeur.transferin.data.local.entity.CameraSyncItemEntity
import com.rvmeur.transferin.data.local.entity.CameraSyncStatus
import com.rvmeur.transferin.data.thumbnail.ThumbnailStorageConfig
import com.rvmeur.transferin.data.thumbnail.ThumbnailStorageManager
import com.rvmeur.transferin.data.upload.streaming.DirectStreamingEngine
import com.rvmeur.transferin.data.upload.streaming.DirectStreamingResult
import com.rvmeur.transferin.domain.camera.CameraPictureProfile
import com.rvmeur.transferin.domain.culling.CullingAction
import com.rvmeur.transferin.domain.culling.CullingDecisionGate
import com.rvmeur.transferin.domain.culling.CullingPolicy
import com.rvmeur.transferin.domain.culling.CullingVerdict
import com.rvmeur.transferin.domain.exposure.ExposureAnalyzer
import com.rvmeur.transferin.domain.exposure.ExposureClassification
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.OutputStream

class SyncOrchestratorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var fakeDao: FakeCameraSyncDao
    private lateinit var thumbnailManager: ThumbnailStorageManager
    private lateinit var orchestrator: SyncOrchestrator

    // Synthetic valid raw RGB byte stream (black pixels: 0, 0, 0)
    private val pitchBlackJpegBytes = ByteArray(300) { 0 }

    // Synthetic optimal midtone pixels (128, 128, 128)
    private val optimalJpegBytes = ByteArray(300) { 128.toByte() }

    @Before
    fun setUp() {
        fakeDao = FakeCameraSyncDao()
        thumbnailManager = ThumbnailStorageManager(
            thumbnailsDir = tempFolder.newFolder("thumbs"),
            config = ThumbnailStorageConfig(maxCacheBytes = 10 * 1024 * 1024)
        )
        orchestrator = SyncOrchestrator(
            cameraSyncDao = fakeDao,
            thumbnailStorageManager = thumbnailManager,
            cullingDecisionGate = CullingDecisionGate()
        )
    }

    @Test
    fun `processFrame with pitch black misfire halts upload under AUTO_SKIP_UPLOAD`() = runTest {
        val item = CameraSyncItemEntity(
            id = "test-frame-1",
            sessionId = "session-1",
            vendorId = 0x04CB,
            cameraSerialNumber = "CAM001",
            storageId = 1,
            objectHandle = 100,
            filename = "DSCF0001.RAF",
            fileSizeBytes = 50_000_000L,
            captureTimestampMs = System.currentTimeMillis(),
            compoundFingerprint = "fp-test-1",
            status = CameraSyncStatus.DETECTED
        )
        fakeDao.insert(item)

        var streamed = false
        val result = orchestrator.processFrame(
            syncItem = item,
            previewBytes = pitchBlackJpegBytes,
            sessionPolicy = CullingPolicy.AUTO_SKIP_UPLOAD,
            fallbackSource = { streamed = true },
            directUploader = {
                streamed = true
                "remote-1"
            }
        )

        assertTrue(result is FrameProcessingResult.CulledSkipped)
        assertFalse("Tier 2 RAW upload must not be streamed on culled misfire", streamed)
        assertEquals(50_000_000L, orchestrator.quotaSavedBytes)

        val updated = fakeDao.getById(item.id)
        assertEquals(CameraSyncStatus.CULLED_SKIPPED, updated?.status)
        assertTrue(updated?.isCulled == true)
        assertEquals("UNDEREXPOSED_CRITICAL", updated?.cullingVerdict)
    }

    @Test
    fun `processFrame with pitch black misfire flags warning and streams under WARN_ONLY`() = runTest {
        val item = CameraSyncItemEntity(
            id = "test-frame-2",
            sessionId = "session-1",
            vendorId = 0x04CB,
            cameraSerialNumber = "CAM001",
            storageId = 1,
            objectHandle = 101,
            filename = "DSCF0002.RAF",
            fileSizeBytes = 45_000_000L,
            captureTimestampMs = System.currentTimeMillis(),
            compoundFingerprint = "fp-test-2",
            status = CameraSyncStatus.DETECTED
        )
        fakeDao.insert(item)

        val streamingEngine = DirectStreamingEngine()
        val warnOrchestrator = SyncOrchestrator(
            cameraSyncDao = fakeDao,
            thumbnailStorageManager = thumbnailManager,
            directStreamingEngine = streamingEngine,
            cullingDecisionGate = CullingDecisionGate()
        )

        var directUploaderCalled = false
        val result = warnOrchestrator.processFrame(
            syncItem = item,
            previewBytes = pitchBlackJpegBytes,
            sessionPolicy = CullingPolicy.WARN_ONLY,
            fallbackSource = { os -> os.write(byteArrayOf(1, 2, 3)) },
            directUploader = {
                directUploaderCalled = true
                "remote-file-warn-2"
            }
        )

        assertTrue(result is FrameProcessingResult.Uploaded)
        assertTrue("Direct uploader should be called under WARN_ONLY", directUploaderCalled)
        val updated = fakeDao.getById(item.id)
        assertEquals(CameraSyncStatus.CONFIRMED_CLOUD, updated?.status)
        assertEquals("remote-file-warn-2", updated?.remoteFileId)
    }

    @Test
    fun `forceUpload transitions culled item and triggers override`() = runTest {
        val item = CameraSyncItemEntity(
            id = "culled-item-1",
            sessionId = "session-1",
            vendorId = 0x04CB,
            cameraSerialNumber = "CAM001",
            storageId = 1,
            objectHandle = 102,
            filename = "DSCF0003.RAF",
            fileSizeBytes = 52_000_000L,
            captureTimestampMs = System.currentTimeMillis(),
            compoundFingerprint = "fp-test-3",
            status = CameraSyncStatus.CULLED_SKIPPED,
            isCulled = true
        )
        fakeDao.insert(item)

        val success = orchestrator.forceUpload("culled-item-1")
        assertTrue(success)

        val updated = fakeDao.getById("culled-item-1")
        assertEquals(CameraSyncStatus.FORCE_UPLOAD_PENDING, updated?.status)
        assertEquals(false, updated?.isCulled)
        assertEquals(true, updated?.isManualUploadOverride)
    }

    private class FakeCameraSyncDao : CameraSyncDao {
        val items = mutableMapOf<String, CameraSyncItemEntity>()

        override suspend fun insertIfNotExists(item: CameraSyncItemEntity): Long {
            if (items.containsKey(item.compoundFingerprint)) return -1L
            items[item.id] = item
            return 1L
        }

        override suspend fun insert(item: CameraSyncItemEntity): Long {
            items[item.id] = item
            return 1L
        }

        override suspend fun insertAll(items: List<CameraSyncItemEntity>) {
            items.forEach { this.items[it.id] = it }
        }

        override suspend fun update(item: CameraSyncItemEntity) {
            items[item.id] = item
        }

        override suspend fun getByFingerprint(fingerprint: String): CameraSyncItemEntity? {
            return items.values.find { it.compoundFingerprint == fingerprint }
        }

        override suspend fun getById(id: String): CameraSyncItemEntity? {
            return items[id]
        }

        override fun observeAllForSession(sessionId: String): Flow<List<CameraSyncItemEntity>> {
            return flowOf(items.values.filter { it.sessionId == sessionId })
        }

        override suspend fun getAllForSession(sessionId: String): List<CameraSyncItemEntity> {
            return items.values.filter { it.sessionId == sessionId }
        }

        override fun observeByStatus(status: CameraSyncStatus): Flow<List<CameraSyncItemEntity>> {
            return flowOf(items.values.filter { it.status == status })
        }

        override suspend fun getByStatus(status: CameraSyncStatus): List<CameraSyncItemEntity> {
            return items.values.filter { it.status == status }
        }

        override suspend fun getPurgeableItems(): List<CameraSyncItemEntity> {
            return items.values.filter { it.status == CameraSyncStatus.CONFIRMED_CLOUD && !it.localFilePath.isNullOrBlank() }
        }

        override suspend fun transitionStatus(
            id: String,
            expectedOldStatus: CameraSyncStatus,
            newStatus: CameraSyncStatus,
            now: Long
        ): Int {
            val item = items[id] ?: return 0
            items[id] = item.copy(status = newStatus, updatedAtEpochMs = now)
            return 1
        }

        override suspend fun markBufferedLocal(id: String, localFilePath: String, now: Long): Int {
            val item = items[id] ?: return 0
            items[id] = item.copy(status = CameraSyncStatus.BUFFERED_LOCAL, localFilePath = localFilePath, updatedAtEpochMs = now)
            return 1
        }

        override suspend fun updateStreamingProgress(id: String, bytesUploaded: Long, now: Long): Int {
            val item = items[id] ?: return 0
            items[id] = item.copy(status = CameraSyncStatus.STREAMING_CLOUD, bytesUploaded = bytesUploaded, updatedAtEpochMs = now)
            return 1
        }

        override suspend fun markConfirmedCloud(id: String, remoteFileId: String, now: Long): Int {
            val item = items[id] ?: return 0
            items[id] = item.copy(
                status = CameraSyncStatus.CONFIRMED_CLOUD,
                remoteFileId = remoteFileId,
                bytesUploaded = item.fileSizeBytes,
                confirmedAtEpochMs = now,
                updatedAtEpochMs = now
            )
            return 1
        }

        override suspend fun markPurgedLocal(id: String, now: Long): Int {
            val item = items[id] ?: return 0
            items[id] = item.copy(status = CameraSyncStatus.PURGED_LOCAL, localFilePath = null, purgedAtEpochMs = now, updatedAtEpochMs = now)
            return 1
        }

        override suspend fun markFailed(id: String, errorMessage: String, now: Long): Int {
            val item = items[id] ?: return 0
            items[id] = item.copy(status = CameraSyncStatus.FAILED, lastErrorMessage = errorMessage, retryCount = item.retryCount + 1, updatedAtEpochMs = now)
            return 1
        }

        override suspend fun markCulledSkipped(
            id: String,
            verdict: String,
            meanLum: Double,
            entropy: Double,
            thumbPath: String?,
            now: Long
        ): Int {
            val item = items[id] ?: return 0
            items[id] = item.copy(
                status = CameraSyncStatus.CULLED_SKIPPED,
                cullingVerdict = verdict,
                meanLuminance = meanLum,
                entropyScore = entropy,
                isCulled = true,
                thumbnailPath = thumbPath,
                updatedAtEpochMs = now
            )
            return 1
        }

        override suspend fun markForceUploadOverride(id: String, now: Long): Int {
            val item = items[id] ?: return 0
            items[id] = item.copy(
                status = CameraSyncStatus.FORCE_UPLOAD_PENDING,
                isCulled = false,
                isManualUploadOverride = true,
                updatedAtEpochMs = now
            )
            return 1
        }

        override fun observeCulledForSession(sessionId: String): Flow<List<CameraSyncItemEntity>> {
            return flowOf(items.values.filter { it.sessionId == sessionId && it.isCulled })
        }

        override fun observeSkippedForSession(sessionId: String): Flow<List<CameraSyncItemEntity>> {
            return flowOf(items.values.filter { it.sessionId == sessionId && it.status == CameraSyncStatus.CULLED_SKIPPED })
        }

        override fun observeOptimalForSession(sessionId: String): Flow<List<CameraSyncItemEntity>> {
            return flowOf(items.values.filter { it.sessionId == sessionId && !it.isCulled })
        }

        override fun observeCulledCount(sessionId: String): Flow<Int> {
            return flowOf(items.values.count { it.sessionId == sessionId && it.isCulled })
        }

        override fun observeCountByStatus(status: CameraSyncStatus): Flow<Int> {
            return flowOf(items.values.count { it.status == status })
        }

        override suspend fun getCountByStatus(status: CameraSyncStatus): Int {
            return items.values.count { it.status == status }
        }

        override fun observeTotalCount(sessionId: String): Flow<Int> {
            return flowOf(items.values.count { it.sessionId == sessionId })
        }

        override fun observeTotalCount(): Flow<Int> {
            return flowOf(items.size)
        }

        override fun observeTotalBytesUploaded(): Flow<Long?> {
            return flowOf(items.values.sumOf { it.bytesUploaded })
        }

        override suspend fun deleteBySession(sessionId: String): Int {
            val before = items.size
            items.entries.removeIf { it.value.sessionId == sessionId }
            return before - items.size
        }

        override suspend fun deleteAll(): Int {
            val count = items.size
            items.clear()
            return count
        }
    }
}
