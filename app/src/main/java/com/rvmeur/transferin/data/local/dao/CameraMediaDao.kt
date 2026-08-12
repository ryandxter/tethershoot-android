package com.rvmeur.transferin.data.local.dao

import androidx.room.*
import com.rvmeur.transferin.data.local.entity.CameraMediaItemEntity
import com.rvmeur.transferin.data.local.entity.MediaStatus
import kotlinx.coroutines.flow.Flow

/**
 * DAO for camera media items - PiuFoto-style local tracking
 */
@Dao
interface CameraMediaDao {

    // ========== Insert Operations ==========

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: CameraMediaItemEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<CameraMediaItemEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfNotExists(item: CameraMediaItemEntity): Long

    // ========== Query Operations ==========

    @Query("SELECT * FROM camera_media_items WHERE deviceId = :deviceId ORDER BY modifiedAt DESC")
    fun getAllByDevice(deviceId: String): Flow<List<CameraMediaItemEntity>>

    @Query("SELECT * FROM camera_media_items WHERE deviceId = :deviceId AND storageId = :storageId ORDER BY modifiedAt DESC")
    fun getAllByStorage(deviceId: String, storageId: Int): Flow<List<CameraMediaItemEntity>>

    @Query("SELECT * FROM camera_media_items WHERE deviceId = :deviceId AND status = :status ORDER BY modifiedAt DESC")
    fun getAllByStatus(deviceId: String, status: MediaStatus): Flow<List<CameraMediaItemEntity>>

    @Query("SELECT * FROM camera_media_items WHERE objectHandle = :handle AND deviceId = :deviceId LIMIT 1")
    suspend fun getByHandle(handle: Int, deviceId: String): CameraMediaItemEntity?

    @Query("SELECT objectHandle FROM camera_media_items WHERE deviceId = :deviceId")
    suspend fun getAllHandlesByDevice(deviceId: String): List<Int>

    @Query("SELECT objectHandle FROM camera_media_items WHERE deviceId = :deviceId AND storageId = :storageId")
    suspend fun getAllHandlesByStorage(deviceId: String, storageId: Int): List<Int>

    @Query("SELECT * FROM camera_media_items WHERE status IN (:statuses) ORDER BY discoveredAt ASC")
    fun getByStatuses(statuses: List<MediaStatus>): Flow<List<CameraMediaItemEntity>>

    @Query("SELECT * FROM camera_media_items WHERE status = :status ORDER BY discoveredAt ASC LIMIT :limit")
    suspend fun getPendingItems(status: MediaStatus, limit: Int): List<CameraMediaItemEntity>

    @Query("SELECT COUNT(*) FROM camera_media_items WHERE deviceId = :deviceId")
    suspend fun getCountByDevice(deviceId: String): Int

    @Query("SELECT COUNT(*) FROM camera_media_items WHERE deviceId = :deviceId AND status = :status")
    suspend fun getCountByStatus(deviceId: String, status: MediaStatus): Int

    @Query("SELECT * FROM camera_media_items WHERE captureGroup = :group ORDER BY modifiedAt ASC")
    fun getByCaptureGroup(group: String): Flow<List<CameraMediaItemEntity>>

    // ========== Update Operations ==========

    @Update
    suspend fun update(item: CameraMediaItemEntity)

    @Query("UPDATE camera_media_items SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: MediaStatus)

    @Query("UPDATE camera_media_items SET status = :status, thumbPath = :thumbPath WHERE id = :id")
    suspend fun updateStatusAndThumb(id: Long, status: MediaStatus, thumbPath: String?)

    @Query("UPDATE camera_media_items SET status = :status, serverId = :serverId, uploadedAt = :uploadedAt WHERE id = :id")
    suspend fun markAsUploaded(id: Long, status: MediaStatus, serverId: String, uploadedAt: Long)

    @Query("UPDATE camera_media_items SET status = :status, retryCount = retryCount + 1, lastError = :error WHERE id = :id")
    suspend fun markAsFailed(id: Long, status: MediaStatus, error: String?)

    @Query("UPDATE camera_media_items SET status = :status, localCopyPath = :path WHERE id = :id")
    suspend fun updateLocalCopyPath(id: Long, status: MediaStatus, path: String)

    // ========== Delete Operations ==========

    @Delete
    suspend fun delete(item: CameraMediaItemEntity)

    @Query("DELETE FROM camera_media_items WHERE deviceId = :deviceId")
    suspend fun deleteAllByDevice(deviceId: String)

    @Query("DELETE FROM camera_media_items WHERE deviceId = :deviceId AND status = :status")
    suspend fun deleteByStatus(deviceId: String, status: MediaStatus)

    @Query("DELETE FROM camera_media_items WHERE deviceId = :deviceId AND captureGroup = :group")
    suspend fun deleteByCaptureGroup(deviceId: String, group: String)

    // ========== Utility Operations ==========

    @Query("SELECT * FROM camera_media_items WHERE id = :id")
    suspend fun getById(id: Long): CameraMediaItemEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM camera_media_items WHERE objectHandle = :handle AND deviceId = :deviceId)")
    suspend fun existsByHandle(handle: Int, deviceId: String): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM camera_media_items WHERE deviceId = :deviceId AND status IN (:statuses))")
    suspend fun hasPendingItems(deviceId: String, statuses: List<MediaStatus>): Boolean

    @Query("SELECT * FROM camera_media_items WHERE fileName LIKE :pattern AND deviceId = :deviceId ORDER BY modifiedAt DESC LIMIT 1")
    suspend fun getLatestByPattern(deviceId: String, pattern: String): CameraMediaItemEntity?
}
