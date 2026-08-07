package com.reflection.transferin.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.reflection.transferin.data.local.entity.UploadedFingerprintEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for uploaded fingerprint persistence and deduplication.
 *
 * Key operations:
 * - hasUploaded(): O(1) primary key lookup for duplicate detection
 * - filterExisting(): Batch check for multiple fingerprints
 * - Session-based queries for upload tracking per session
 * - Cleanup operations for old fingerprints
 */
@Dao
interface UploadedFingerprintDao {

    /**
     * Check if a fingerprint exists (O(1) primary key lookup).
     */
    @Query("SELECT EXISTS(SELECT 1 FROM uploaded_fingerprints WHERE fingerprint = :fingerprint)")
    suspend fun hasUploaded(fingerprint: String): Boolean

    /**
     * Batch check: returns set of fingerprints that already exist.
     */
    @Query("SELECT fingerprint FROM uploaded_fingerprints WHERE fingerprint IN (:fingerprints)")
    suspend fun filterExisting(fingerprints: List<String>): List<String>

    /**
     * Insert a single fingerprint record.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: UploadedFingerprintEntity)

    /**
     * Batch insert multiple fingerprint records.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<UploadedFingerprintEntity>)

    /**
     * Get all fingerprints for a session.
     */
    @Query("SELECT fingerprint FROM uploaded_fingerprints WHERE session_id = :sessionId")
    suspend fun getBySession(sessionId: String): List<String>

    /**
     * Get all fingerprints in the database.
     */
    @Query("SELECT fingerprint FROM uploaded_fingerprints")
    suspend fun getAllFingerprints(): List<String>

    /**
     * Count fingerprints for a session.
     */
    @Query("SELECT COUNT(*) FROM uploaded_fingerprints WHERE session_id = :sessionId")
    suspend fun countBySession(sessionId: String): Int

    /**
     * Delete all fingerprints for a session.
     */
    @Query("DELETE FROM uploaded_fingerprints WHERE session_id = :sessionId")
    suspend fun deleteBySession(sessionId: String)

    /**
     * Delete fingerprints older than timestamp (for cleanup).
     */
    @Query("DELETE FROM uploaded_fingerprints WHERE uploaded_at < :beforeTimestamp")
    suspend fun deleteOlderThan(beforeTimestamp: Long)

    /**
     * Observe total fingerprint count.
     */
    @Query("SELECT COUNT(*) FROM uploaded_fingerprints")
    fun observeTotalCount(): Flow<Int>

    /**
     * Observe fingerprint count for a session.
     */
    @Query("SELECT COUNT(*) FROM uploaded_fingerprints WHERE session_id = :sessionId")
    fun observeCountBySession(sessionId: String): Flow<Int>
}
