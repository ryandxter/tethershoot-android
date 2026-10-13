package com.rvmeur.transferin.domain.session

import android.content.Context
import android.net.Uri
import com.rvmeur.transferin.data.local.entity.TransferQueueItemEntity
import com.rvmeur.transferin.data.repository.TransferQueueRepositoryImpl
import com.rvmeur.transferin.data.worker.TransferUploadWorkScheduler
import com.rvmeur.transferin.domain.model.MediaJob
import com.rvmeur.transferin.domain.model.UploadStatus
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class UploadQueueManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var repository: TransferQueueRepositoryImpl
    private lateinit var scheduler: TransferUploadWorkScheduler
    private lateinit var queueManager: UploadQueueManager
    private lateinit var mockUri: Uri

    private val capturedEntities = mutableListOf<TransferQueueItemEntity>()

    @Before
    fun setUp() {
        io.mockk.mockkStatic(android.util.Log::class)
        every { android.util.Log.d(any(), any()) } returns 0
        every { android.util.Log.i(any(), any()) } returns 0
        every { android.util.Log.w(any(), any<String>()) } returns 0
        every { android.util.Log.e(any(), any()) } returns 0

        io.mockk.mockkStatic(Uri::class)
        every { Uri.parse(any()) } answers {
            val str = firstArg<String>()
            val u = mockk<Uri>(relaxed = true)
            every { u.scheme } returns if (str.startsWith("file://")) "file" else ""
            every { u.path } returns str.removePrefix("file://")
            every { u.toString() } returns str
            u
        }
        every { Uri.fromFile(any()) } answers {
            val file = firstArg<File>()
            val u = mockk<Uri>(relaxed = true)
            every { u.scheme } returns "file"
            every { u.path } returns file.absolutePath
            every { u.toString() } returns "file://${file.absolutePath}"
            u
        }

        val appDir = tempFolder.newFolder("app_storage")
        context = mockk(relaxed = true)
        every { context.cacheDir } returns appDir
        every { context.getExternalFilesDir(any()) } returns appDir
        every { context.contentResolver.openInputStream(any()) } returns null
        every { context.contentResolver.openFileDescriptor(any(), any()) } returns null
        every { context.contentResolver.query(any(), any(), any(), any(), any()) } returns null
        every { context.contentResolver.getType(any()) } returns null

        repository = mockk(relaxed = true)
        scheduler = mockk(relaxed = true)

        capturedEntities.clear()
        coEvery { repository.insertAndReturnFirstId(capture(capturedEntities)) } returns "queue-item-123"

        queueManager = UploadQueueManager(
            context = context,
            transferQueueRepository = repository,
            workSchedulerOverride = scheduler
        )
    }

    @After
    fun tearDown() {
        io.mockk.unmockkStatic(android.util.Log::class)
        io.mockk.unmockkStatic(Uri::class)
    }

    @Test
    fun `enqueueMediaJob preserves numeric prefix filenames 1 to 1 without stripping`() = runTest {
        val job = MediaJob(
            id = "job-1",
            projectId = "session-1",
            localTempPath = "/tmp/100_0001.JPG",
            originalFileName = "100_0001.JPG",
            sourceUri = "file:///tmp/100_0001.JPG",
            sourceFingerprint = "1|100_0001.JPG|5000000|123456",
            fileSizeBytes = 5000000L,
            lastModifiedEpochMs = 123456L,
            createdAtEpochMs = 123456L,
            uploadStatus = UploadStatus.QUEUED
        )

        queueManager.enqueueMediaJob(job, "session-1", "/DCIM")

        assertEquals(1, capturedEntities.size)
        val entity = capturedEntities.first()
        assertEquals("100_0001.JPG", entity.targetFileName)
        assertEquals("100_0001.JPG", entity.sourceFileName)
    }

    @Test
    fun `enqueueMediaJob preserves Canon CR3 numeric prefix filenames 1 to 1`() = runTest {
        val job = MediaJob(
            id = "job-2",
            projectId = "session-1",
            localTempPath = "/tmp/0001_0002.CR3",
            originalFileName = "0001_0002.CR3",
            sourceUri = "file:///tmp/0001_0002.CR3",
            sourceFingerprint = "2|0001_0002.CR3|25000000|123456",
            fileSizeBytes = 25000000L,
            lastModifiedEpochMs = 123456L,
            createdAtEpochMs = 123456L,
            uploadStatus = UploadStatus.QUEUED
        )

        queueManager.enqueueMediaJob(job, "session-1", "/DCIM")

        assertEquals(1, capturedEntities.size)
        val entity = capturedEntities.first()
        assertEquals("0001_0002.CR3", entity.targetFileName)
        assertEquals("0001_0002.CR3", entity.sourceFileName)
    }

    @Test
    fun `enqueueMediaJob preserves Sony ARW underscore prefix filenames 1 to 1`() = runTest {
        val job = MediaJob(
            id = "job-3",
            projectId = "session-1",
            localTempPath = "/tmp/_DSC0001.ARW",
            originalFileName = "_DSC0001.ARW",
            sourceUri = "file:///tmp/_DSC0001.ARW",
            sourceFingerprint = "3|_DSC0001.ARW|30000000|123456",
            fileSizeBytes = 30000000L,
            lastModifiedEpochMs = 123456L,
            createdAtEpochMs = 123456L,
            uploadStatus = UploadStatus.QUEUED
        )

        queueManager.enqueueMediaJob(job, "session-1", "/DCIM")

        assertEquals(1, capturedEntities.size)
        val entity = capturedEntities.first()
        assertEquals("_DSC0001.ARW", entity.targetFileName)
        assertEquals("_DSC0001.ARW", entity.sourceFileName)
    }

    @Test
    fun `enqueueMediaJob falls back to transit path regex when originalFileName is blank`() = runTest {
        val job = MediaJob(
            id = "job-4",
            projectId = "session-1",
            localTempPath = "/tmp/999_DSCF0042.RAF",
            originalFileName = "",
            sourceUri = "file:///tmp/999_DSCF0042.RAF",
            sourceFingerprint = "999|DSCF0042.RAF|45000000|123456",
            fileSizeBytes = 45000000L,
            lastModifiedEpochMs = 123456L,
            createdAtEpochMs = 123456L,
            uploadStatus = UploadStatus.QUEUED
        )

        queueManager.enqueueMediaJob(job, "session-1", "/DCIM")

        assertEquals(1, capturedEntities.size)
        val entity = capturedEntities.first()
        assertEquals("DSCF0042.RAF", entity.targetFileName)
        assertEquals("DSCF0042.RAF", entity.sourceFileName)
    }
}
