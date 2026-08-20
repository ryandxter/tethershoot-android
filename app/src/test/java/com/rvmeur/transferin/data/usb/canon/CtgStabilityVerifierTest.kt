package com.rvmeur.transferin.data.usb.canon

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class CtgStabilityVerifierTest {

    private val verifier = CtgStabilityVerifier(
        samplingIntervalMs = 10L, // Fast for unit tests
        timestampToleranceMs = 500L,
        maxRetries = 3
    )

    private fun createValidSqliteHeader(pageSize: Int = 4096): ByteArray {
        val header = ByteArray(100)
        // 0..15: SQLite format 3\0
        System.arraycopy(CtgStabilityVerifier.SQLITE3_MAGIC, 0, header, 0, 16)
        val bb = ByteBuffer.wrap(header).order(ByteOrder.BIG_ENDIAN)
        // 16..17: Page size
        bb.putShort(16, pageSize.toShort())
        // 18..19: Write/read format version
        header[18] = 1
        header[19] = 1
        return header
    }

    @Test
    fun `stage 1 invariance returns Stable when size and timestamp are settled`() = runTest {
        val snapshot = CtgMetadataSnapshot(
            handle = 101,
            name = "AUTOMRIC.CTG",
            size = 32768L,
            modifiedTime = 1700000000000L
        )

        val result = verifier.verifyInvariance { snapshot }

        assertTrue("Must be Stable", result is StabilityStage1Result.Stable)
        val stable = result as StabilityStage1Result.Stable
        assertEquals(32768L, stable.snapshot.size)
        assertEquals(101, stable.snapshot.handle)
    }

    @Test
    fun `stage 1 invariance returns UnstableWriting when size changes between samples`() = runTest {
        var callCount = 0
        val fetchMetadata: suspend () -> CtgMetadataSnapshot? = {
            callCount++
            if (callCount == 1) {
                CtgMetadataSnapshot(101, "AUTOMRIC.CTG", size = 16384L, modifiedTime = 1700000000000L)
            } else {
                CtgMetadataSnapshot(101, "AUTOMRIC.CTG", size = 32768L, modifiedTime = 1700000000000L)
            }
        }

        val result = verifier.verifyInvariance(fetchMetadata)

        assertTrue("Must be UnstableWriting", result is StabilityStage1Result.UnstableWriting)
        val unstable = result as StabilityStage1Result.UnstableWriting
        assertTrue(unstable.reason.contains("size changed"))
        assertEquals(16384L, unstable.first.size)
        assertEquals(32768L, unstable.second.size)
    }

    @Test
    fun `stage 1 invariance returns EmptyFile when size is 0 bytes`() = runTest {
        val emptySnapshot = CtgMetadataSnapshot(101, "AUTOMRIC.CTG", size = 0L, modifiedTime = 1700000000000L)

        val result = verifier.verifyInvariance { emptySnapshot }

        assertTrue("Must be EmptyFile", result is StabilityStage1Result.EmptyFile)
    }

    @Test
    fun `stage 1 invariance returns UnstableWriting when timestamp drifts beyond tolerance`() = runTest {
        var callCount = 0
        val fetchMetadata: suspend () -> CtgMetadataSnapshot? = {
            callCount++
            if (callCount == 1) {
                CtgMetadataSnapshot(101, "AUTOMRIC.CTG", size = 32768L, modifiedTime = 1700000000000L)
            } else {
                CtgMetadataSnapshot(101, "AUTOMRIC.CTG", size = 32768L, modifiedTime = 1700000005000L) // +5000ms drift
            }
        }

        val result = verifier.verifyInvariance(fetchMetadata)

        assertTrue("Must be UnstableWriting due to timestamp drift", result is StabilityStage1Result.UnstableWriting)
    }

    @Test
    fun `stage 2 header integrity validates valid SQLite 3 headers`() {
        val validHeader = createValidSqliteHeader(pageSize = 4096)

        val result = verifier.verifyHeaderIntegrity(validHeader)

        assertTrue("Must be Valid", result is StabilityStage2Result.Valid)
        val valid = result as StabilityStage2Result.Valid
        assertTrue("Must be SQLite", valid.isSqlite)
        assertEquals(4096, valid.pageSize)
    }

    @Test
    fun `stage 2 header integrity rejects corrupted or invalid page size SQLite headers`() {
        // Invalid page size 300 (not power of 2)
        val badPageSizeHeader = createValidSqliteHeader(pageSize = 300)
        val res1 = verifier.verifyHeaderIntegrity(badPageSizeHeader)
        assertTrue("Must be Corrupted", res1 is StabilityStage2Result.Corrupted)

        // Truncated header
        val shortHeader = byteArrayOf(0x53, 0x51, 0x4C)
        val res2 = verifier.verifyHeaderIntegrity(shortHeader)
        assertTrue("Must be Corrupted", res2 is StabilityStage2Result.Corrupted)

        // Random garbage header
        val garbageHeader = ByteArray(100) { 0xFF.toByte() }
        val res3 = verifier.verifyHeaderIntegrity(garbageHeader)
        assertTrue("Must be Corrupted", res3 is StabilityStage2Result.Corrupted)
    }

    @Test
    fun `stage 2 header integrity validates legacy Canon binary CTG headers`() {
        val canonBinaryHeader = ByteArray(100).apply {
            this[0] = 0x00
            this[1] = 0x00
            this[2] = 0x00
            this[3] = 0x01
        }

        val result = verifier.verifyHeaderIntegrity(canonBinaryHeader)

        assertTrue("Must be Valid", result is StabilityStage2Result.Valid)
        val valid = result as StabilityStage2Result.Valid
        assertFalse("Must be legacy binary, not SQLite", valid.isSqlite)
    }

    @Test
    fun `full verification pipeline succeeds when file stabilizes on retry`() = runTest {
        var metadataCalls = 0
        val fetchMetadata: suspend () -> CtgMetadataSnapshot? = {
            metadataCalls++
            if (metadataCalls <= 2) {
                // First verification cycle: actively growing
                CtgMetadataSnapshot(101, "AUTOMRIC.CTG", size = (metadataCalls * 1024).toLong(), modifiedTime = 1000L)
            } else {
                // Second cycle: settled
                CtgMetadataSnapshot(101, "AUTOMRIC.CTG", size = 8192L, modifiedTime = 2000L)
            }
        }

        val headerBytes = createValidSqliteHeader(pageSize = 4096)
        val fetchHeader: suspend (CtgMetadataSnapshot) -> ByteArray? = { headerBytes }

        val result = verifier.verifyStability(fetchMetadata, fetchHeader)

        assertTrue("Pipeline must succeed after settling", result is CtgVerificationResult.Success)
        val success = result as CtgVerificationResult.Success
        assertEquals(8192L, success.snapshot.size)
        assertTrue(success.isSqlite)
        assertEquals(4096, success.pageSize)
    }
}
