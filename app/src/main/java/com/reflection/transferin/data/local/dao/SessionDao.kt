package com.reflection.transferin.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.reflection.transferin.data.local.entity.SessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: SessionEntity)

    @Update
    suspend fun update(session: SessionEntity)

    @Query("SELECT * FROM sessions WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): SessionEntity?

    @Query("SELECT * FROM sessions WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<SessionEntity?>

    @Query("SELECT * FROM sessions ORDER BY createdAtEpochMs DESC")
    suspend fun getAll(): List<SessionEntity>

    @Query("SELECT * FROM sessions ORDER BY createdAtEpochMs DESC")
    fun observeAll(): Flow<List<SessionEntity>>

    @Query(
        """
        SELECT * FROM sessions
        WHERE status IN ('STARTING', 'RUNNING', 'STOPPING_INTAKE', 'UPLOAD_DRAINING')
        ORDER BY createdAtEpochMs DESC
        LIMIT 1
        """
    )
    suspend fun getLatestActiveLikeSession(): SessionEntity?

    @Query(
        """
        SELECT * FROM sessions
        WHERE status IN ('STARTING', 'RUNNING', 'STOPPING_INTAKE', 'UPLOAD_DRAINING')
        ORDER BY createdAtEpochMs DESC
        LIMIT 1
        """
    )
    fun observeLatestActiveLikeSession(): Flow<SessionEntity?>

    @Query(
        """
        UPDATE sessions
        SET status = :status,
            updatedAtEpochMs = :updatedAtEpochMs
        WHERE id = :id
        """
    )
    suspend fun updateStatus(
        id: String,
        status: String,
        updatedAtEpochMs: Long
    )

    @Query(
        """
        UPDATE sessions
        SET watcherStatus = :watcherStatus,
            updatedAtEpochMs = :updatedAtEpochMs
        WHERE id = :id
        """
    )
    suspend fun updateWatcherStatus(
        id: String,
        watcherStatus: String,
        updatedAtEpochMs: Long
    )

    @Query(
        """
        UPDATE sessions
        SET lastScannedAtEpochMs = :lastScannedAtEpochMs,
            updatedAtEpochMs = :updatedAtEpochMs
        WHERE id = :id
        """
    )
    suspend fun updateLastScannedAt(
        id: String,
        lastScannedAtEpochMs: Long,
        updatedAtEpochMs: Long
    )

    @Query(
        """
        UPDATE sessions
        SET status = :status,
            intakeStoppedAtEpochMs = :intakeStoppedAtEpochMs,
            updatedAtEpochMs = :updatedAtEpochMs
        WHERE id = :id
        """
    )
    suspend fun stopIntake(
        id: String,
        status: String,
        intakeStoppedAtEpochMs: Long,
        updatedAtEpochMs: Long
    )

    @Query(
        """
        UPDATE sessions
        SET status = :status,
            completedAtEpochMs = :completedAtEpochMs,
            updatedAtEpochMs = :updatedAtEpochMs
        WHERE id = :id
        """
    )
    suspend fun completeSession(
        id: String,
        status: String,
        completedAtEpochMs: Long,
        updatedAtEpochMs: Long
    )

    @Query(
        """
        UPDATE sessions
        SET acceptedFileCount = :acceptedFileCount,
            queuedFileCount = :queuedFileCount,
            uploadingFileCount = :uploadingFileCount,
            successFileCount = :successFileCount,
            failedFileCount = :failedFileCount,
            skippedFileCount = :skippedFileCount,
            totalBytesDiscovered = :totalBytesDiscovered,
            totalBytesUploaded = :totalBytesUploaded,
            updatedAtEpochMs = :updatedAtEpochMs
        WHERE id = :id
        """
    )
    suspend fun updateCounters(
        id: String,
        acceptedFileCount: Int,
        queuedFileCount: Int,
        uploadingFileCount: Int,
        successFileCount: Int,
        failedFileCount: Int,
        skippedFileCount: Int,
        totalBytesDiscovered: Long,
        totalBytesUploaded: Long,
        updatedAtEpochMs: Long
    )

    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun deleteById(id: String)

    // ==== BARU: sesi aktif berdasarkan flag isActive ====

    @Query(
        """
        SELECT * FROM sessions
        WHERE isActive = 1
        ORDER BY createdAtEpochMs DESC
        LIMIT 1
        """
    )
    suspend fun getActiveSession(): SessionEntity?

    @Query(
        """
        SELECT * FROM sessions
        WHERE isActive = 1
        ORDER BY createdAtEpochMs DESC
        LIMIT 1
        """
    )
    fun observeActiveSession(): Flow<SessionEntity?>

    @Query("UPDATE sessions SET isActive = 0")
    suspend fun deactivateAllSessions()
}