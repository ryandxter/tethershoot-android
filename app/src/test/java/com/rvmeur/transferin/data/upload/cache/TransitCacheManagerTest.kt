package com.rvmeur.transferin.data.upload.cache

import com.rvmeur.transferin.data.local.dao.CameraSyncDao
import com.rvmeur.transferin.data.local.entity.CameraSyncItemEntity
import com.rvmeur.transferin.data.local.entity.CameraSyncStatus
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class TransitCacheManagerTest {

    private lateinit var tempTransitDir: File
    private val mockDao = mockk<CameraSyncDao>(relaxed = true)

    @Before
    fun setUp() {
        tempTransitDir = Files.createTempDirectory("transit_test").toFile()
    }

    @After
    fun tearDown() {
        tempTransitDir.deleteRecursively()
    }

    @Test
    fun `file lifecycle creation, query, size tracking and deletion`() {
        val manager = TransitCacheManager(
            transitDir = tempTransitDir,
            cameraSyncDao = mockDao,
            maxCacheBytes = 10_000L
        )

        val file = manager.createTransitFile("fp_abc123", "raf")
        assertTrue(file.exists() || !file.exists()) // Created path
        file.writeBytes(ByteArray(500))

        assertEquals(500L, manager.getUsedBytes())

        val retrieved = manager.getTransitFile("fp_abc123")
        assertNotNull(retrieved)
        assertEquals(file.absolutePath, retrieved?.absolutePath)

        val deleted = manager.deleteTransitFile("fp_abc123")
        assertTrue(deleted)
        assertEquals(0L, manager.getUsedBytes())
        assertNull(manager.getTransitFile("fp_abc123"))
    }

    @Test
    fun `reserveSpace succeeds without eviction when within capacity`() {
        runBlocking {
            val manager = TransitCacheManager(
                transitDir = tempTransitDir,
                cameraSyncDao = mockDao,
                maxCacheBytes = 5000L
            )

            val file1 = manager.createTransitFile("fp_1", "jpg")
            file1.writeBytes(ByteArray(1000))

            val reserved = manager.reserveSpace(2000L)
            assertTrue(reserved)
            assertTrue(file1.exists())
        }
    }

    @Test
    fun `reserveSpace evicts ONLY CONFIRMED_CLOUD items when capacity exceeded`() {
        runBlocking {
            val manager = TransitCacheManager(
                transitDir = tempTransitDir,
                cameraSyncDao = mockDao,
                maxCacheBytes = 1000L
            )

            val confirmedFile = manager.createTransitFile("fp_confirmed", "raf")
            confirmedFile.writeBytes(ByteArray(600))

            val unconfirmedFile = manager.createTransitFile("fp_streaming", "raf")
            unconfirmedFile.writeBytes(ByteArray(400))

            val confirmedEntity = CameraSyncItemEntity(
                id = "item-1",
                sessionId = "session-1",
                vendorId = 0x04CB,
                cameraSerialNumber = "SN1",
                storageId = 1,
                objectHandle = 100,
                filename = "CONFIRMED.RAF",
                fileSizeBytes = 600L,
                captureTimestampMs = 1000L,
                compoundFingerprint = "fp_confirmed",
                status = CameraSyncStatus.CONFIRMED_CLOUD,
                localFilePath = confirmedFile.absolutePath,
                confirmedAtEpochMs = 1000L
            )

            coEvery { mockDao.getPurgeableItems() } returns listOf(confirmedEntity)
            coEvery { mockDao.markPurgedLocal("item-1", any()) } returns 1

            // 600 + 400 = 1000. Reserving 200 requires freeing 200 bytes.
            val reserved = manager.reserveSpace(200L)
            assertTrue(reserved)

            // Invariant check: Confirmed file was evicted, unconfirmed file remains intact!
            assertFalse(confirmedFile.exists())
            assertTrue(unconfirmedFile.exists())
            assertEquals(400L, manager.getUsedBytes())

            coVerify(exactly = 1) { mockDao.markPurgedLocal("item-1", any()) }
        }
    }

    @Test(expected = TransitCacheExhaustedException::class)
    fun `reserveSpace throws TransitCacheExhaustedException when full of unconfirmed items`() {
        runBlocking {
            val manager = TransitCacheManager(
                transitDir = tempTransitDir,
                cameraSyncDao = mockDao,
                maxCacheBytes = 1000L
            )

            val streamingFile = manager.createTransitFile("fp_streaming", "raf")
            streamingFile.writeBytes(ByteArray(900))

            // No purgeable items in DB
            coEvery { mockDao.getPurgeableItems() } returns emptyList()

            // 900 + 200 > 1000, no purgeable files -> must throw TransitCacheExhaustedException
            try {
                manager.reserveSpace(200L)
            } finally {
                // Assert unconfirmed file was never touched!
                assertTrue(streamingFile.exists())
                assertEquals(900L, manager.getUsedBytes())
            }
        }
    }

    @Test(expected = TransitCacheExhaustedException::class)
    fun `reserveSpace throws TransitCacheExhaustedException for oversized single allocation`() {
        runBlocking {
            val manager = TransitCacheManager(
                transitDir = tempTransitDir,
                cameraSyncDao = mockDao,
                maxCacheBytes = 1000L
            )

            manager.reserveSpace(1001L)
        }
    }
}
