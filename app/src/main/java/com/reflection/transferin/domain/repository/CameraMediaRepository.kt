package com.reflection.transferin.domain.repository

import com.reflection.transferin.data.local.entity.CameraMediaItemEntity
import com.reflection.transferin.data.local.entity.MediaStatus
import kotlinx.coroutines.flow.Flow

/**
 * Repository for camera media tracking - PiuFoto-style
 * Handles MTP object discovery, local DB storage, and upload queue
 */
interface CameraMediaRepository {

    // ========== Discovery ==========

    /**
     * Get all existing handles for a device - for baseline comparison
     */
    suspend fun getExistingHandles(deviceId: String): Set<Int>

    /**
     * Get all existing handles for a storage
     */
    suspend fun getExistingHandlesByStorage(deviceId: String, storageId: Int): Set<Int>

    /**
     * Check if handle exists
     */
    suspend fun handleExists(handle: Int, deviceId: String): Boolean

    /**
     * Insert new discovered items from MTP scan
     * Returns count of newly inserted items
     */
    suspend fun insertDiscoveredItems(items: List<CameraMediaItemEntity>): Int

    /**
     * Insert single item if not exists
     */
    suspend fun insertIfNotExists(item: CameraMediaItemEntity): Long

    // ========== Query ==========

    /**
     * Get all items for a device
     */
    fun observeAllByDevice(deviceId: String): Flow<List<CameraMediaItemEntity>>

    /**
     * Get all items by status
     */
    fun observeByStatus(statuses: List<MediaStatus>): Flow<List<CameraMediaItemEntity>>

    /**
     * Get items by capture group
     */
    fun observeByCaptureGroup(group: String): Flow<List<CameraMediaItemEntity>>

    /**
     * Get pending items for upload (QUEUED or DISCOVERED)
     */
    suspend fun getPendingItems(limit: Int): List<CameraMediaItemEntity>

    /**
     * Get item by ID
     */
    suspend fun getById(id: Long): CameraMediaItemEntity?

    /**
     * Get count by device
     */
    suspend fun getCountByDevice(deviceId: String): Int

    /**
     * Get count by status
     */
    suspend fun getCountByStatus(deviceId: String, status: MediaStatus): Int

    /**
     * Check if has pending items
     */
    suspend fun hasPendingItems(deviceId: String): Boolean

    // ========== Update ==========

    /**
     * Update item status
     */
    suspend fun updateStatus(id: Long, status: MediaStatus)

    /**
     * Mark item as cached (thumbnail ready)
     */
    suspend fun markAsCached(id: Long, thumbPath: String?)

    /**
     * Mark item as queued for upload
     */
    suspend fun markAsQueued(id: Long)

    /**
     * Mark item as uploading
     */
    suspend fun markAsUploading(id: Long)

    /**
     * Mark item as successfully uploaded
     */
    suspend fun markAsUploaded(id: Long, serverId: String)

    /**
     * Mark item as failed
     */
    suspend fun markAsFailed(id: Long, error: String?)

    /**
     * Update local copy path
     */
    suspend fun updateLocalCopyPath(id: Long, path: String)

    // ========== Delete ==========

    /**
     * Delete all items for a device
     */
    suspend fun deleteAllByDevice(deviceId: String)

    /**
     * Delete items by status
     */
    suspend fun deleteByStatus(deviceId: String, status: MediaStatus)

    /**
     * Delete items by capture group
     */
    suspend fun deleteByCaptureGroup(deviceId: String, group: String)

    // ========== Baseline ==========

    /**
     * Get baseline items (all existing at session start)
     * Used to determine what NOT to upload
     */
    fun observeBaselineItems(deviceId: String): Flow<List<CameraMediaItemEntity>>

    /**
     * Get new items (discovered after baseline was set)
     */
    fun observeNewItems(deviceId: String): Flow<List<CameraMediaItemEntity>>

    /**
     * Mark baseline as set - items before this are baseline
     */
    suspend fun markBaselineComplete(deviceId: String)

    // ========== Legacy Methods (for backward compatibility) ==========

    /**
     * List media for storage
     */
    suspend fun listMedia(storageId: Int): List<com.reflection.transferin.domain.model.CameraMedia>

    /**
     * Download media to cache
     */
    suspend fun downloadMediaToCache(media: com.reflection.transferin.domain.model.CameraMedia): java.io.File?
}
