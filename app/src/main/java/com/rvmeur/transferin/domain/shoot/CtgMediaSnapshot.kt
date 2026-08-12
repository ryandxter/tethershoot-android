package com.rvmeur.transferin.domain.shoot

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.UUID

/**
 * Immutable snapshot of camera media state captured at session start.
 *
 * Once created, this snapshot cannot be modified. Create a new snapshot
 * when the camera state changes (e.g., new photo taken).
 *
 * Used for comparing current camera state against a baseline to detect
 * which files are new and need to be uploaded.
 *
 * Design:
 * - entries Map uses fingerprint as key for O(1) contains/get operations
 * - SHA-256 checksum of sorted fingerprints for integrity verification
 * - Factory methods ensure valid state at construction
 *
 * @property id Unique identifier (UUID) for this snapshot
 * @property capturedAt Timestamp when snapshot was created (millis since epoch)
 * @property storageId MTP storage ID
 * @property cameraId Camera device identifier (if available)
 * @property mediaCount Total number of media entries
 * @property entries Immutable map of fingerprint -> CtgMediaEntry
 * @property checksum SHA-256 of sorted fingerprints for integrity
 */
data class CtgMediaSnapshot(
    val id: String,
    val capturedAt: Long,
    val storageId: Int,
    val cameraId: String?,
    val mediaCount: Int,
    val entries: Map<String, CtgMediaEntry>,
    val checksum: String
) {
    init {
        require(capturedAt > 0) { "capturedAt must be positive, got: $capturedAt" }
        require(mediaCount >= 0) { "mediaCount cannot be negative, got: $mediaCount" }
        require(mediaCount == entries.size) {
            "mediaCount ($mediaCount) must match entries size (${entries.size})"
        }
    }

    /**
     * Check if a specific file is in this snapshot.
     * Uses fingerprint-based lookup for O(1) operation.
     *
     * @param fingerprint The fingerprint to check (format: "handle|filename|size")
     * @return true if the fingerprint exists in this snapshot
     */
    fun contains(fingerprint: String): Boolean = fingerprint in entries

    /**
     * Get entry by fingerprint.
     *
     * @param fingerprint The fingerprint to look up
     * @return The matching CtgMediaEntry, or null if not found
     */
    fun get(fingerprint: String): CtgMediaEntry? = entries[fingerprint]

    /**
     * Get all entries as a list.
     * Order is deterministic (sorted by fingerprint internally).
     *
     * @return List of all CtgMediaEntry in this snapshot
     */
    fun allEntries(): List<CtgMediaEntry> = entries.values.toList()

    /**
     * Get fingerprints sorted by filename for stable checksum calculation.
     */
    private fun sortedFingerprints(): List<String> =
        entries.values.sortedBy { it.filename }.map { it.fingerprint }

    companion object {
        /**
         * Create snapshot from parsed CTG database.
         *
         * @param ctgDb The parsed CTG database containing media records
         * @param storageId MTP storage ID
         * @param cameraId Camera device identifier
         * @return New snapshot with all entries from database
         * @throws IllegalArgumentException if ctgDb.records is empty
         */
        fun fromCtgDatabase(
            ctgDb: CtgDatabase,
            storageId: Int,
            cameraId: String?
        ): CtgMediaSnapshot {
            require(ctgDb.records.isNotEmpty()) {
                "Use createEmpty() for cameras with no media"
            }

            val entries = ctgDb.records.associate { record ->
                record.fingerprint to record
            }

            val checksum = generateChecksum(entries.keys.sorted())

            return CtgMediaSnapshot(
                id = UUID.randomUUID().toString(),
                capturedAt = System.currentTimeMillis(),
                storageId = storageId,
                cameraId = cameraId,
                mediaCount = entries.size,
                entries = entries,
                checksum = checksum
            )
        }

        /**
         * Create empty snapshot for cameras with no media.
         *
         * @param storageId MTP storage ID
         * @param cameraId Camera device identifier
         * @return Empty snapshot with zero media count
         */
        fun createEmpty(storageId: Int, cameraId: String?): CtgMediaSnapshot {
            return CtgMediaSnapshot(
                id = UUID.randomUUID().toString(),
                capturedAt = System.currentTimeMillis(),
                storageId = storageId,
                cameraId = cameraId,
                mediaCount = 0,
                entries = emptyMap(),
                checksum = generateChecksum(emptyList())
            )
        }

        /**
         * Generate SHA-256 checksum from sorted fingerprints.
         *
         * @param sortedFingerprints List of fingerprints (must be pre-sorted)
         * @return Hex-encoded SHA-256 hash
         */
        private fun generateChecksum(sortedFingerprints: List<String>): String {
            val data = sortedFingerprints.joinToString("|")
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(data.toByteArray(StandardCharsets.UTF_8))
            return hash.joinToString("") { "%02x".format(it) }
        }
    }
}
