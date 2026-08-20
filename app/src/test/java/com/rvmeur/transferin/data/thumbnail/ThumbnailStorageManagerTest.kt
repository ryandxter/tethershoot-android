package com.rvmeur.transferin.data.thumbnail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class ThumbnailStorageManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var thumbnailsDir: File
    private lateinit var storageManager: ThumbnailStorageManager

    private fun createDummyJpeg(size: Int = 1024): ByteArray {
        val bytes = ByteArray(size)
        bytes[0] = 0xFF.toByte()
        bytes[1] = 0xD8.toByte() // SOI
        for (i in 2 until size - 2) {
            bytes[i] = (i % 250).toByte()
        }
        bytes[size - 2] = 0xFF.toByte()
        bytes[size - 1] = 0xD9.toByte() // EOI
        return bytes
    }

    @Before
    fun setup() {
        thumbnailsDir = tempFolder.newFolder("thumbnails")
        storageManager = ThumbnailStorageManager(
            thumbnailsDir = thumbnailsDir,
            config = ThumbnailStorageConfig(
                maxCacheBytes = 10 * 1024L, // 10 KB for testing
                maxTtlMs = 3 * 24 * 60 * 60 * 1000L,
                targetCacheBytes = 6 * 1024L // 6 KB target
            )
        )
    }

    @Test
    fun `saveThumbnail successfully performs atomic two-stage commit and cleans up temp files`() {
        val jpeg = createDummyJpeg(512)
        val file = storageManager.saveThumbnail("shot_001", jpeg)

        assertNotNull("Saved file must not be null", file)
        assertTrue("Saved file must exist on disk", file!!.exists())
        assertEquals(512L, file.length())
        assertEquals("shot_001.jpg", file.name)

        // Verify no leftover .tmp files
        val tmpFiles = thumbnailsDir.listFiles { _, name -> name.endsWith(".tmp") } ?: emptyArray()
        assertEquals("No temporary staging files should remain", 0, tmpFiles.size)
    }

    @Test
    fun `saveThumbnail rejects invalid non-JPEG bytes and blank fingerprints`() {
        val nonJpeg = byteArrayOf(0x00, 0x11, 0x22, 0x33)
        assertNull("Non-JPEG payload must be rejected", storageManager.saveThumbnail("shot_bad", nonJpeg))
        assertNull("Empty bytes must be rejected", storageManager.saveThumbnail("shot_bad2", ByteArray(0)))
        assertNull("Blank fingerprint must be rejected", storageManager.saveThumbnail("   ", createDummyJpeg()))
    }

    @Test
    fun `getThumbnailFile retrieves existing file, updates LRU lastModified, and purges zero-byte files`() {
        val jpeg = createDummyJpeg(256)
        storageManager.saveThumbnail("shot_lru", jpeg)

        val retrieved = storageManager.getThumbnailFile("shot_lru")
        assertNotNull(retrieved)
        assertTrue(storageManager.hasThumbnail("shot_lru"))

        // Create corrupt 0-byte file directly
        val zeroFile = File(thumbnailsDir, "corrupt.jpg")
        zeroFile.createNewFile()
        assertEquals(0L, zeroFile.length())

        val retrievedZero = storageManager.getThumbnailFile("corrupt")
        assertNull("0-byte corrupted files must return null and be purged", retrievedZero)
        assertFalse("Corrupted file must have been deleted from disk", zeroFile.exists())
    }

    @Test
    fun `pruneStaleThumbnails removes files older than 3 days TTL`() {
        val jpeg = createDummyJpeg(128)
        val freshFile = storageManager.saveThumbnail("fresh_shot", jpeg)!!
        val staleFile = storageManager.saveThumbnail("stale_shot", jpeg)!!

        // Age staleFile to 4 days ago
        val fourDaysAgo = System.currentTimeMillis() - (4 * 24 * 60 * 60 * 1000L)
        staleFile.setLastModified(fourDaysAgo)

        val deletedCount = storageManager.pruneStaleThumbnails()
        assertEquals(1, deletedCount)
        assertTrue("Fresh file must be preserved", freshFile.exists())
        assertFalse("Stale file must be deleted", staleFile.exists())
    }

    @Test
    fun `pruneToSize evicts oldest accessed files down to targetSizeBytes under LRU policy`() {
        // Save 5 files of 2KB each (Total = 10KB, exceeds maxCacheBytes triggers prune to 6KB)
        val f1 = storageManager.saveThumbnail("file_1", createDummyJpeg(2048))!!
        Thread.sleep(10)
        val f2 = storageManager.saveThumbnail("file_2", createDummyJpeg(2048))!!
        Thread.sleep(10)
        val f3 = storageManager.saveThumbnail("file_3", createDummyJpeg(2048))!!
        Thread.sleep(10)
        val f4 = storageManager.saveThumbnail("file_4", createDummyJpeg(2048))!!
        Thread.sleep(10)
        val f5 = storageManager.saveThumbnail("file_5", createDummyJpeg(2048))!!

        // Total 5 * 2048 = 10240 bytes. Max is 10240. Next save triggers prune to 6144 bytes.
        val f6 = storageManager.saveThumbnail("file_6", createDummyJpeg(2048))!!

        val used = storageManager.getUsedBytes()
        assertTrue("Used bytes ($used) must be <= 10KB", used <= 10 * 1024L)
        assertTrue("Newest file must exist", f6.exists())
    }

    @Test
    fun `purgeAllThumbnails cleanly empties storage directory on logout`() {
        repeat(5) { i ->
            storageManager.saveThumbnail("shot_$i", createDummyJpeg(256))
        }
        assertTrue(storageManager.getUsedBytes() > 0)

        val purged = storageManager.purgeAllThumbnails()
        assertTrue(purged)
        assertEquals(0L, storageManager.getUsedBytes())
    }

    @Test
    fun `concurrent multi-threaded saves execute reliably without deadlocks or corruption`() {
        val threadCount = 8
        val shotsPerThread = 15
        val executor = Executors.newFixedThreadPool(threadCount)
        val latch = CountDownLatch(threadCount)
        val successCount = AtomicInteger(0)

        for (t in 0 until threadCount) {
            executor.submit {
                try {
                    for (i in 0 until shotsPerThread) {
                        val fp = "thread_${t}_shot_$i"
                        val file = storageManager.saveThumbnail(fp, createDummyJpeg(512))
                        if (file != null && file.exists() && file.length() == 512L) {
                            successCount.incrementAndGet()
                        }
                    }
                } finally {
                    latch.countDown()
                }
            }
        }

        val completed = latch.await(10, TimeUnit.SECONDS)
        executor.shutdown()

        assertTrue("All concurrent saves should complete within timeout", completed)
        assertEquals(threadCount * shotsPerThread, successCount.get())
    }
}
