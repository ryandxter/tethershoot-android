package com.reflection.transferin.domain.repository

import com.reflection.transferin.data.local.entity.UploadedFingerprintEntity
import kotlinx.coroutines.flow.Flow

/**
 * Repository for managing uploaded fingerprints across sessions.
 *
 * ## Responsibilities
 * - **Recording uploads**: Persist fingerprint records when files are successfully uploaded
 * - **Duplicate detection**: Check if a fingerprint has been previously uploaded
 * - **Batch filtering**: Filter a list of fingerprints to find only new ones
 * - **Session management**: Query fingerprints by session for scoped deduplication
 * - **Cleanup**: Remove old fingerprints based on retention policy
 * - **Observability**: Provide reactive streams for fingerprint counts
 *
 * ## Thread Safety
 * All suspend functions are safe to call from coroutines. The repository
 * delegates to [com.reflection.transferin.data.local.dao.UploadedFingerprintDao]
 * which handles Room's threading requirements.
 *
 * @see SmartUploadFilter for higher-level filtering operations
 * @see NewShotDetector for baseline comparison operations
 */
interface UploadedFingerprintRepository {

    /**
     * Check if a fingerprint has been previously uploaded.
     *
     * @param fingerprint The fingerprint string to check
     * @return true if fingerprint exists in database, false otherwise
     */
    suspend fun hasUploaded(fingerprint: String): Boolean

    /**
     * Filter a list of fingerprints, returning only the new ones not yet uploaded.
     *
     * This is more efficient than checking each fingerprint individually when
     * processing multiple items.
     *
     * @param fingerprints List of fingerprint strings to filter
     * @return Set of fingerprints that are NOT in the database (i.e., new uploads)
     */
    suspend fun filterNewFingerprints(fingerprints: List<String>): Set<String>

    /**
     * Record a successful upload.
     *
     * @param entity The upload fingerprint entity to persist
     */
    suspend fun recordUpload(entity: UploadedFingerprintEntity)

    /**
     * Get all fingerprints for a specific session.
     *
     * @param sessionId The session identifier
     * @return Set of fingerprint strings from the session
     */
    suspend fun getSessionFingerprints(sessionId: String): Set<String>

    /**
     * Get all fingerprints from all sessions.
     *
     * @return Set of all fingerprint strings in the database
     */
    suspend fun getAllFingerprints(): Set<String>

    /**
     * Clean up fingerprints older than the specified number of days.
     *
     * @param keepDays Number of days to retain fingerprints (default: 30)
     */
    suspend fun cleanupOldFingerprints(keepDays: Int = 30)

    /**
     * Observe total fingerprint count across all sessions.
     *
     * @return Flow emitting the total count
     */
    fun observeTotalCount(): Flow<Int>

    /**
     * Observe fingerprint count for a specific session.
     *
     * @param sessionId The session identifier
     * @return Flow emitting the session's fingerprint count
     */
    fun observeSessionCount(sessionId: String): Flow<Int>
}
