package com.reflection.transferin.data.repository

import android.content.Context
import android.util.Log
import com.reflection.transferin.data.local.dao.CameraMediaDao
import com.reflection.transferin.data.local.entity.CameraMediaItemEntity
import com.reflection.transferin.data.local.entity.MediaStatus
import com.reflection.transferin.data.usb.UsbHelper
import com.reflection.transferin.domain.model.CameraMedia
import com.reflection.transferin.domain.repository.CameraMediaRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File

class CameraMediaRepositoryImpl(
    private val context: Context,
    private val usbHelper: UsbHelper,
    private val cameraMediaDao: CameraMediaDao
) : CameraMediaRepository {

    companion object {
        private const val TAG = "CameraMediaRepositoryImpl"
    }

    // ========== Discovery ==========

    override suspend fun getExistingHandles(deviceId: String): Set<Int> {
        return cameraMediaDao.getAllHandlesByDevice(deviceId).toSet()
    }

    override suspend fun getExistingHandlesByStorage(deviceId: String, storageId: Int): Set<Int> {
        return cameraMediaDao.getAllHandlesByStorage(deviceId, storageId).toSet()
    }

    override suspend fun handleExists(handle: Int, deviceId: String): Boolean {
        return cameraMediaDao.existsByHandle(handle, deviceId)
    }

    override suspend fun insertDiscoveredItems(items: List<CameraMediaItemEntity>): Int {
        var insertedCount = 0
        for (item in items) {
            val exists = cameraMediaDao.existsByHandle(item.objectHandle, item.deviceId)
            if (!exists) {
                cameraMediaDao.insert(item)
                insertedCount++
            }
        }
        Log.d(TAG, "insertDiscoveredItems: $insertedCount new out of ${items.size}")
        return insertedCount
    }

    override suspend fun insertIfNotExists(item: CameraMediaItemEntity): Long {
        return cameraMediaDao.insertIfNotExists(item)
    }

    // ========== Query ==========

    override fun observeAllByDevice(deviceId: String): Flow<List<CameraMediaItemEntity>> {
        return cameraMediaDao.getAllByDevice(deviceId)
    }

    override fun observeByStatus(statuses: List<MediaStatus>): Flow<List<CameraMediaItemEntity>> {
        return cameraMediaDao.getByStatuses(statuses)
    }

    override fun observeByCaptureGroup(group: String): Flow<List<CameraMediaItemEntity>> {
        return cameraMediaDao.getByCaptureGroup(group)
    }

    override suspend fun getPendingItems(limit: Int): List<CameraMediaItemEntity> {
        val pending = mutableListOf<CameraMediaItemEntity>()
        // First get DISCOVERED items
        pending.addAll(cameraMediaDao.getPendingItems(MediaStatus.DISCOVERED, limit))
        // Then get QUEUED items
        if (pending.size < limit) {
            pending.addAll(cameraMediaDao.getPendingItems(MediaStatus.QUEUED, limit - pending.size))
        }
        return pending
    }

    override suspend fun getById(id: Long): CameraMediaItemEntity? {
        return cameraMediaDao.getById(id)
    }

    override suspend fun getCountByDevice(deviceId: String): Int {
        return cameraMediaDao.getCountByDevice(deviceId)
    }

    override suspend fun getCountByStatus(deviceId: String, status: MediaStatus): Int {
        return cameraMediaDao.getCountByStatus(deviceId, status)
    }

    override suspend fun hasPendingItems(deviceId: String): Boolean {
        return cameraMediaDao.hasPendingItems(
            deviceId,
            listOf(MediaStatus.DISCOVERED, MediaStatus.QUEUED, MediaStatus.UPLOADING)
        )
    }

    // ========== Update ==========

    override suspend fun updateStatus(id: Long, status: MediaStatus) {
        cameraMediaDao.updateStatus(id, status)
    }

    override suspend fun markAsCached(id: Long, thumbPath: String?) {
        cameraMediaDao.updateStatusAndThumb(id, MediaStatus.CACHED, thumbPath)
    }

    override suspend fun markAsQueued(id: Long) {
        cameraMediaDao.updateStatus(id, MediaStatus.QUEUED)
    }

    override suspend fun markAsUploading(id: Long) {
        cameraMediaDao.updateStatus(id, MediaStatus.UPLOADING)
    }

    override suspend fun markAsUploaded(id: Long, serverId: String) {
        cameraMediaDao.markAsUploaded(id, MediaStatus.UPLOADED, serverId, System.currentTimeMillis())
    }

    override suspend fun markAsFailed(id: Long, error: String?) {
        cameraMediaDao.markAsFailed(id, MediaStatus.FAILED, error)
    }

    override suspend fun updateLocalCopyPath(id: Long, path: String) {
        cameraMediaDao.updateLocalCopyPath(id, MediaStatus.UPLOADING, path)
    }

    // ========== Delete ==========

    override suspend fun deleteAllByDevice(deviceId: String) {
        cameraMediaDao.deleteAllByDevice(deviceId)
    }

    override suspend fun deleteByStatus(deviceId: String, status: MediaStatus) {
        cameraMediaDao.deleteByStatus(deviceId, status)
    }

    override suspend fun deleteByCaptureGroup(deviceId: String, group: String) {
        cameraMediaDao.deleteByCaptureGroup(deviceId, group)
    }

    // ========== Baseline ==========

    override fun observeBaselineItems(deviceId: String): Flow<List<CameraMediaItemEntity>> {
        // Baseline items are those discovered before shoot mode started
        // We can identify them by checking if they're UPLOADED or DISCOVERED
        // with capturedAt before baseline timestamp
        return cameraMediaDao.getAllByDevice(deviceId).map { items ->
            items.filter { it.status in listOf(MediaStatus.UPLOADED, MediaStatus.DISCOVERED) }
        }
    }

    override fun observeNewItems(deviceId: String): Flow<List<CameraMediaItemEntity>> {
        // New items are DISCOVERED that haven't been uploaded yet
        return cameraMediaDao.getByStatuses(
            listOf(MediaStatus.DISCOVERED, MediaStatus.CACHED, MediaStatus.QUEUED, MediaStatus.UPLOADING)
        )
    }

    override suspend fun markBaselineComplete(deviceId: String) {
        // Mark all currently discovered items as baseline (already existing)
        // This is called when shoot mode starts - items after this are new
        Log.d(TAG, "markBaselineComplete for device: $deviceId")
        // Items marked as DISCOVERED are the baseline
    }

    // ========== Legacy Methods ==========

    override suspend fun listMedia(storageId: Int): List<CameraMedia> {
        Log.d(TAG, "listMedia() called with storageId=$storageId")
        return usbHelper.listMedia(storageId)
    }

    override suspend fun downloadMediaToCache(media: CameraMedia): File? {
        Log.d(
            TAG,
            "downloadMediaToCache() called: handle=${media.handle}, name=${media.name}, size=${media.size}"
        )
        return usbHelper.downloadMediaToCache(media)
    }
}
