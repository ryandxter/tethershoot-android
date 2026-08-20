package com.rvmeur.transferin.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.rvmeur.transferin.data.local.entity.CameraSyncItemEntity
import com.rvmeur.transferin.data.local.entity.CameraSyncStatus
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for transactional 5-stage Camera-to-Cloud synchronization operations.
 */
@Dao
interface CameraSyncDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfNotExists(item: CameraSyncItemEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: CameraSyncItemEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<CameraSyncItemEntity>)

    @Update
    suspend fun update(item: CameraSyncItemEntity)

    @Query("SELECT * FROM camera_sync_items WHERE compoundFingerprint = :fingerprint LIMIT 1")
    suspend fun getByFingerprint(fingerprint: String): CameraSyncItemEntity?

    @Query("SELECT * FROM camera_sync_items WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): CameraSyncItemEntity?

    @Query("SELECT * FROM camera_sync_items WHERE sessionId = :sessionId ORDER BY createdAtEpochMs DESC")
    fun observeAllForSession(sessionId: String): Flow<List<CameraSyncItemEntity>>

    @Query("SELECT * FROM camera_sync_items WHERE sessionId = :sessionId ORDER BY createdAtEpochMs DESC")
    suspend fun getAllForSession(sessionId: String): List<CameraSyncItemEntity>

    @Query("SELECT * FROM camera_sync_items WHERE status = :status ORDER BY createdAtEpochMs ASC")
    fun observeByStatus(status: CameraSyncStatus): Flow<List<CameraSyncItemEntity>>

    @Query("SELECT * FROM camera_sync_items WHERE status = :status ORDER BY createdAtEpochMs ASC")
    suspend fun getByStatus(status: CameraSyncStatus): List<CameraSyncItemEntity>

    @Query("SELECT * FROM camera_sync_items WHERE status = 'CONFIRMED_CLOUD' AND localFilePath IS NOT NULL ORDER BY confirmedAtEpochMs ASC")
    suspend fun getPurgeableItems(): List<CameraSyncItemEntity>

    @Query("UPDATE camera_sync_items SET status = :newStatus, updatedAtEpochMs = :now WHERE id = :id AND status = :expectedOldStatus")
    suspend fun transitionStatus(
        id: String,
        expectedOldStatus: CameraSyncStatus,
        newStatus: CameraSyncStatus,
        now: Long = System.currentTimeMillis()
    ): Int

    @Query("UPDATE camera_sync_items SET status = 'BUFFERED_LOCAL', localFilePath = :localFilePath, updatedAtEpochMs = :now WHERE id = :id")
    suspend fun markBufferedLocal(
        id: String,
        localFilePath: String,
        now: Long = System.currentTimeMillis()
    ): Int

    @Query("UPDATE camera_sync_items SET status = 'STREAMING_CLOUD', bytesUploaded = :bytesUploaded, updatedAtEpochMs = :now WHERE id = :id")
    suspend fun updateStreamingProgress(
        id: String,
        bytesUploaded: Long,
        now: Long = System.currentTimeMillis()
    ): Int

    @Query("UPDATE camera_sync_items SET status = 'CONFIRMED_CLOUD', remoteFileId = :remoteFileId, bytesUploaded = fileSizeBytes, confirmedAtEpochMs = :now, updatedAtEpochMs = :now WHERE id = :id")
    suspend fun markConfirmedCloud(
        id: String,
        remoteFileId: String,
        now: Long = System.currentTimeMillis()
    ): Int

    @Query("UPDATE camera_sync_items SET status = 'PURGED_LOCAL', localFilePath = NULL, purgedAtEpochMs = :now, updatedAtEpochMs = :now WHERE id = :id")
    suspend fun markPurgedLocal(
        id: String,
        now: Long = System.currentTimeMillis()
    ): Int

    @Query("UPDATE camera_sync_items SET status = 'FAILED', lastErrorMessage = :errorMessage, retryCount = retryCount + 1, updatedAtEpochMs = :now WHERE id = :id")
    suspend fun markFailed(
        id: String,
        errorMessage: String,
        now: Long = System.currentTimeMillis()
    ): Int

    @Query("UPDATE camera_sync_items SET status = 'CULLED_SKIPPED', cullingVerdict = :verdict, meanLuminance = :meanLum, entropyScore = :entropy, isCulled = 1, thumbnailPath = :thumbPath, updatedAtEpochMs = :now WHERE id = :id")
    suspend fun markCulledSkipped(
        id: String,
        verdict: String,
        meanLum: Double,
        entropy: Double,
        thumbPath: String?,
        now: Long = System.currentTimeMillis()
    ): Int

    @Query("UPDATE camera_sync_items SET status = 'FORCE_UPLOAD_PENDING', isCulled = 0, isManualUploadOverride = 1, updatedAtEpochMs = :now WHERE id = :id")
    suspend fun markForceUploadOverride(
        id: String,
        now: Long = System.currentTimeMillis()
    ): Int

    @Query("SELECT * FROM camera_sync_items WHERE sessionId = :sessionId AND isCulled = 1 ORDER BY createdAtEpochMs DESC")
    fun observeCulledForSession(sessionId: String): Flow<List<CameraSyncItemEntity>>

    @Query("SELECT * FROM camera_sync_items WHERE sessionId = :sessionId AND status = 'CULLED_SKIPPED' ORDER BY createdAtEpochMs DESC")
    fun observeSkippedForSession(sessionId: String): Flow<List<CameraSyncItemEntity>>

    @Query("SELECT * FROM camera_sync_items WHERE sessionId = :sessionId AND isCulled = 0 ORDER BY createdAtEpochMs DESC")
    fun observeOptimalForSession(sessionId: String): Flow<List<CameraSyncItemEntity>>

    @Query("SELECT COUNT(*) FROM camera_sync_items WHERE sessionId = :sessionId AND isCulled = 1")
    fun observeCulledCount(sessionId: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM camera_sync_items WHERE status = :status")
    fun observeCountByStatus(status: CameraSyncStatus): Flow<Int>

    @Query("SELECT COUNT(*) FROM camera_sync_items WHERE status = :status")
    suspend fun getCountByStatus(status: CameraSyncStatus): Int

    @Query("SELECT COUNT(*) FROM camera_sync_items WHERE sessionId = :sessionId")
    fun observeTotalCount(sessionId: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM camera_sync_items")
    fun observeTotalCount(): Flow<Int>

    @Query("SELECT SUM(bytesUploaded) FROM camera_sync_items")
    fun observeTotalBytesUploaded(): Flow<Long?>

    @Query("DELETE FROM camera_sync_items WHERE sessionId = :sessionId")
    suspend fun deleteBySession(sessionId: String): Int

    @Query("DELETE FROM camera_sync_items")
    suspend fun deleteAll(): Int
}
