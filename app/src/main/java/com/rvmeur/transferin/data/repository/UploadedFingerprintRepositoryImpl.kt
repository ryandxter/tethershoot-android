package com.rvmeur.transferin.data.repository

import com.rvmeur.transferin.data.local.dao.UploadedFingerprintDao
import com.rvmeur.transferin.data.local.entity.UploadedFingerprintEntity
import com.rvmeur.transferin.domain.repository.UploadedFingerprintRepository
import kotlinx.coroutines.flow.Flow

/**
 * Implementation of [UploadedFingerprintRepository] using Room DAO.
 *
 * This implementation delegates to [UploadedFingerprintDao] for database
 * operations, providing a clean domain interface while leveraging Room's
 * efficient primary key lookups.
 *
 * ## Performance Notes
 * - hasUploaded(): O(1) primary key lookup
 * - filterNewFingerprints(): Single query with IN clause, returns existing set
 * - Session queries: Indexed on sessionId column
 *
 * @param dao The Room DAO for uploaded fingerprint persistence
 */
class UploadedFingerprintRepositoryImpl(
    private val dao: UploadedFingerprintDao
) : UploadedFingerprintRepository {

    override suspend fun hasUploaded(fingerprint: String): Boolean {
        return dao.hasUploaded(fingerprint)
    }

    override suspend fun filterNewFingerprints(fingerprints: List<String>): Set<String> {
        if (fingerprints.isEmpty()) {
            return emptySet()
        }
        val existing = dao.filterExisting(fingerprints)
        return fingerprints.toSet() - existing
    }

    override suspend fun recordUpload(entity: UploadedFingerprintEntity) {
        dao.insert(entity)
    }

    override suspend fun getSessionFingerprints(sessionId: String): Set<String> {
        return dao.getBySession(sessionId).toSet()
    }

    override suspend fun getAllFingerprints(): Set<String> {
        return dao.getAllFingerprints().toSet()
    }

    override suspend fun cleanupOldFingerprints(keepDays: Int) {
        val cutoff = System.currentTimeMillis() - (keepDays * 24 * 60 * 60 * 1000L)
        dao.deleteOlderThan(cutoff)
    }

    override fun observeTotalCount(): Flow<Int> {
        return dao.observeTotalCount()
    }

    override fun observeSessionCount(sessionId: String): Flow<Int> {
        return dao.observeCountBySession(sessionId)
    }
}
