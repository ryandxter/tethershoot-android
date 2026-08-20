package com.rvmeur.transferin.data.usb.canon

import kotlinx.coroutines.delay
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Snapshot of CTG file metadata used for two-stage stability verification.
 */
data class CtgMetadataSnapshot(
    val handle: Int,
    val name: String,
    val size: Long,
    val modifiedTime: Long
)

/**
 * Result of Stage 1 metadata invariance check.
 */
sealed class StabilityStage1Result {
    data class Stable(val snapshot: CtgMetadataSnapshot) : StabilityStage1Result()
    data class UnstableWriting(
        val reason: String,
        val first: CtgMetadataSnapshot,
        val second: CtgMetadataSnapshot
    ) : StabilityStage1Result()
    data class EmptyFile(val snapshot: CtgMetadataSnapshot) : StabilityStage1Result()
    data class NotFound(val reason: String) : StabilityStage1Result()
}

/**
 * Result of Stage 2 header integrity validation.
 */
sealed class StabilityStage2Result {
    data class Valid(
        val isSqlite: Boolean,
        val pageSize: Int
    ) : StabilityStage2Result()
    data class Corrupted(
        val reason: String
    ) : StabilityStage2Result()
}

/**
 * Final composite result of two-stage CTG stability verification.
 */
sealed class CtgVerificationResult {
    data class Success(
        val snapshot: CtgMetadataSnapshot,
        val isSqlite: Boolean,
        val pageSize: Int
    ) : CtgVerificationResult()

    data class Unstable(
        val reason: String,
        val attempts: Int
    ) : CtgVerificationResult()

    data class Corrupted(
        val reason: String
    ) : CtgVerificationResult()
}

/**
 * Two-stage stability verification engine for Canon .CTG catalog indexing files
 * (`AUTOMRIC.CTG`, `AUTOMSC1.CTG`, `CANON_MDC.CTG`, `CANON.CTG`).
 *
 * Prevents 0-byte corrupt reads and partial SQLite lock crashes (`sqlite3_busy`) during active
 * camera card write operations.
 */
class CtgStabilityVerifier(
    private val samplingIntervalMs: Long = DEFAULT_SAMPLING_INTERVAL_MS,
    private val timestampToleranceMs: Long = DEFAULT_TIMESTAMP_TOLERANCE_MS,
    private val maxRetries: Int = DEFAULT_MAX_RETRIES
) {

    companion object {
        const val DEFAULT_SAMPLING_INTERVAL_MS = 250L
        const val DEFAULT_TIMESTAMP_TOLERANCE_MS = 1000L
        const val DEFAULT_MAX_RETRIES = 3

        // SQLite 3 Magic bytes: "SQLite format 3\000" (16 bytes)
        val SQLITE3_MAGIC = byteArrayOf(
            0x53, 0x51, 0x4C, 0x69, 0x74, 0x65, 0x20, 0x66,
            0x6F, 0x72, 0x6D, 0x61, 0x74, 0x20, 0x33, 0x00
        )

        // Legacy Canon binary CTG table headers
        private val CANON_BINARY_MAGIC = byteArrayOf(0x00, 0x00, 0x00, 0x01)
    }

    /**
     * Executes Stage 1 invariance check by taking two metadata snapshots separated by [samplingIntervalMs].
     */
    suspend fun verifyInvariance(
        fetchMetadata: suspend () -> CtgMetadataSnapshot?
    ): StabilityStage1Result {
        val snapshot1 = fetchMetadata() ?: return StabilityStage1Result.NotFound("Initial metadata snapshot unavailable")

        if (snapshot1.size <= 0L) {
            return StabilityStage1Result.EmptyFile(snapshot1)
        }

        delay(samplingIntervalMs)

        val snapshot2 = fetchMetadata() ?: return StabilityStage1Result.NotFound("Second metadata snapshot unavailable")

        if (snapshot2.size <= 0L) {
            return StabilityStage1Result.EmptyFile(snapshot2)
        }

        // Check if size is actively growing
        if (snapshot1.size != snapshot2.size) {
            return StabilityStage1Result.UnstableWriting(
                reason = "File size changed during sampling: ${snapshot1.size} -> ${snapshot2.size} bytes",
                first = snapshot1,
                second = snapshot2
            )
        }

        // Check if timestamp is drifting beyond tolerance
        val timeDiff = Math.abs(snapshot1.modifiedTime - snapshot2.modifiedTime)
        if (timeDiff > timestampToleranceMs) {
            return StabilityStage1Result.UnstableWriting(
                reason = "Modification timestamp changed by ${timeDiff}ms (tolerance: ${timestampToleranceMs}ms)",
                first = snapshot1,
                second = snapshot2
            )
        }

        return StabilityStage1Result.Stable(snapshot2)
    }

    /**
     * Executes Stage 2 header integrity check on downloaded or cached file header bytes.
     */
    fun verifyHeaderIntegrity(headerBytes: ByteArray): StabilityStage2Result {
        if (headerBytes.size < 16) {
            return StabilityStage2Result.Corrupted("Header too short: ${headerBytes.size} bytes (minimum 16 bytes required)")
        }

        // Check for SQLite 3 format
        val isSqlite = isSqliteHeader(headerBytes)
        if (isSqlite) {
            if (headerBytes.size < 32) {
                return StabilityStage2Result.Corrupted("SQLite header truncated: ${headerBytes.size} bytes (minimum 32 required)")
            }

            val buffer = ByteBuffer.wrap(headerBytes).order(ByteOrder.BIG_ENDIAN)
            // Page size is uint16 at offset 16
            val rawPageSize = buffer.getShort(16).toInt() and 0xFFFF
            val pageSize = if (rawPageSize == 1) 65536 else rawPageSize

            // Validate power of 2 between 512 and 65536
            val isValidPageSize = (pageSize in 512..65536) && (pageSize and (pageSize - 1) == 0)
            if (!isValidPageSize) {
                return StabilityStage2Result.Corrupted("Invalid SQLite page size: $pageSize")
            }

            // Validate change counter / file format version at offset 18..19 (1 or 2)
            val writeVersion = headerBytes[18].toInt() and 0xFF
            val readVersion = headerBytes[19].toInt() and 0xFF
            if (writeVersion > 2 || readVersion > 2) {
                return StabilityStage2Result.Corrupted("Unsupported SQLite format version: write=$writeVersion, read=$readVersion")
            }

            return StabilityStage2Result.Valid(isSqlite = true, pageSize = pageSize)
        }

        // Check for legacy Canon binary CTG header
        if (isCanonBinaryHeader(headerBytes)) {
            return StabilityStage2Result.Valid(isSqlite = false, pageSize = 512)
        }

        return StabilityStage2Result.Corrupted("Header does not match SQLite 3 or Canon binary format")
    }

    /**
     * Executes Stage 2 integrity check on a [File].
     */
    fun verifyFileIntegrity(file: File): StabilityStage2Result {
        if (!file.exists() || file.length() < 16L) {
            return StabilityStage2Result.Corrupted("File does not exist or is smaller than 16 bytes")
        }

        val header = ByteArray(100.coerceAtMost(file.length().toInt()))
        try {
            FileInputStream(file).use { input ->
                val read = input.read(header)
                if (read < 16) {
                    return StabilityStage2Result.Corrupted("Failed to read minimum 16 bytes header from file")
                }
            }
            return verifyHeaderIntegrity(header)
        } catch (e: Exception) {
            return StabilityStage2Result.Corrupted("I/O error reading header: ${e.message}")
        }
    }

    /**
     * Full two-stage verification pipeline with retry support.
     *
     * @param fetchMetadata Function providing latest CTG metadata from camera.
     * @param fetchHeader Function providing header bytes (or downloading file).
     */
    suspend fun verifyStability(
        fetchMetadata: suspend () -> CtgMetadataSnapshot?,
        fetchHeader: suspend (snapshot: CtgMetadataSnapshot) -> ByteArray?
    ): CtgVerificationResult {
        var lastReason = "Unknown error"

        for (attempt in 1..maxRetries) {
            when (val stage1 = verifyInvariance(fetchMetadata)) {
                is StabilityStage1Result.Stable -> {
                    val headerBytes = fetchHeader(stage1.snapshot)
                    if (headerBytes == null || headerBytes.isEmpty()) {
                        lastReason = "Failed to fetch header bytes for stable CTG snapshot"
                        if (attempt < maxRetries) {
                            delay(samplingIntervalMs)
                            continue
                        }
                        return CtgVerificationResult.Corrupted(lastReason)
                    }

                    when (val stage2 = verifyHeaderIntegrity(headerBytes)) {
                        is StabilityStage2Result.Valid -> {
                            return CtgVerificationResult.Success(
                                snapshot = stage1.snapshot,
                                isSqlite = stage2.isSqlite,
                                pageSize = stage2.pageSize
                            )
                        }
                        is StabilityStage2Result.Corrupted -> {
                            lastReason = stage2.reason
                            if (attempt < maxRetries) {
                                delay(samplingIntervalMs)
                                continue
                            }
                            return CtgVerificationResult.Corrupted(stage2.reason)
                        }
                    }
                }
                is StabilityStage1Result.UnstableWriting -> {
                    lastReason = stage1.reason
                    if (attempt < maxRetries) {
                        delay(samplingIntervalMs)
                        continue
                    }
                }
                is StabilityStage1Result.EmptyFile -> {
                    lastReason = "CTG file is 0 bytes (empty allocation)"
                    if (attempt < maxRetries) {
                        delay(samplingIntervalMs)
                        continue
                    }
                }
                is StabilityStage1Result.NotFound -> {
                    lastReason = stage1.reason
                    if (attempt < maxRetries) {
                        delay(samplingIntervalMs)
                        continue
                    }
                }
            }
        }

        return CtgVerificationResult.Unstable(reason = lastReason, attempts = maxRetries)
    }

    private fun isSqliteHeader(header: ByteArray): Boolean {
        if (header.size < 16) return false
        for (i in 0 until 16) {
            if (header[i] != SQLITE3_MAGIC[i]) return false
        }
        return true
    }

    private fun isCanonBinaryHeader(header: ByteArray): Boolean {
        if (header.size < 4) return false
        // Canon CTG binary index files start with 0x00000001 or 0x00000002
        if (header[0] == 0.toByte() && header[1] == 0.toByte() && header[2] == 0.toByte() &&
            (header[3] == 1.toByte() || header[3] == 2.toByte())
        ) {
            return true
        }
        return false
    }
}
