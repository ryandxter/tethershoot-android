package com.rvmeur.transferin.feature.gallery

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rvmeur.transferin.data.local.dao.CameraSyncDao
import com.rvmeur.transferin.data.local.entity.CameraSyncItemEntity
import com.rvmeur.transferin.data.local.entity.CameraSyncStatus
import com.rvmeur.transferin.data.local.entity.TransferQueueItemEntity
import com.rvmeur.transferin.data.repository.TransferQueueRepositoryImpl
import com.rvmeur.transferin.domain.model.UploadStatus
import com.rvmeur.transferin.domain.sync.SyncOrchestrator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

/**
 * ViewModel for the Uploaded Gallery Screen with Smart Culling & Quota Management.
 */
class GalleryViewModel(
    application: Application,
    private val transferQueueRepository: TransferQueueRepositoryImpl? = null,
    private val cameraSyncDao: CameraSyncDao? = null,
    private val syncOrchestrator: SyncOrchestrator? = null
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(GalleryUiState())
    val uiState: StateFlow<GalleryUiState> = _uiState.asStateFlow()

    init {
        loadGalleryItems()
    }

    fun loadGalleryItems() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            try {
                val galleryItems = mutableListOf<GalleryItem>()

                // Load from TransferQueueRepository if provided
                transferQueueRepository?.let { repo ->
                    val items = repo.getAll()
                    galleryItems.addAll(items.map { it.toGalleryItem(getApplication()) })
                }

                // Load from CameraSyncDao if available
                cameraSyncDao?.let { dao ->
                    val syncItems = dao.getByStatus(CameraSyncStatus.CONFIRMED_CLOUD) +
                            dao.getByStatus(CameraSyncStatus.CULLED_SKIPPED) +
                            dao.getByStatus(CameraSyncStatus.STREAMING_CLOUD) +
                            dao.getByStatus(CameraSyncStatus.FORCE_UPLOAD_PENDING) +
                            dao.getByStatus(CameraSyncStatus.BUFFERED_LOCAL) +
                            dao.getByStatus(CameraSyncStatus.DETECTED) +
                            dao.getByStatus(CameraSyncStatus.FAILED)

                    val mapped = syncItems.distinctBy { it.id }.map { it.toGalleryItem() }
                    galleryItems.addAll(mapped)
                }

                val distinctItems = galleryItems.distinctBy { it.id }
                val metrics = computeMetrics(distinctItems)

                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        allItems = distinctItems,
                        uploadedItems = applyFiltersAndSort(distinctItems, state.filterMode, state.sortMode),
                        cullingMetrics = metrics
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Gagal memuat galeri: ${e.message}"
                    )
                }
            }
        }
    }

    fun setGalleryItems(items: List<GalleryItem>) {
        val metrics = computeMetrics(items)
        _uiState.update { state ->
            state.copy(
                allItems = items,
                uploadedItems = applyFiltersAndSort(items, state.filterMode, state.sortMode),
                cullingMetrics = metrics,
                isLoading = false
            )
        }
    }

    fun setFilterMode(mode: FilterMode) {
        _uiState.update { state ->
            state.copy(
                filterMode = mode,
                uploadedItems = applyFiltersAndSort(state.allItems, mode, state.sortMode)
            )
        }
    }

    fun setSortMode(mode: SortMode) {
        _uiState.update { state ->
            state.copy(
                sortMode = mode,
                uploadedItems = applyFiltersAndSort(state.allItems, state.filterMode, mode)
            )
        }
    }

    /**
     * 1-Tap Kinetic Force Upload manual override.
     */
    fun forceUpload(itemId: String) {
        viewModelScope.launch {
            // Optimistic update in UI
            _uiState.update { state ->
                val updatedAll = state.allItems.map { item ->
                    if (item.id == itemId) {
                        item.copy(
                            status = UploadStatus.UPLOADING,
                            isCulled = false,
                            isManualUploadOverride = true
                        )
                    } else item
                }
                state.copy(
                    allItems = updatedAll,
                    uploadedItems = applyFiltersAndSort(updatedAll, state.filterMode, state.sortMode),
                    cullingMetrics = computeMetrics(updatedAll)
                )
            }

            try {
                cameraSyncDao?.markForceUploadOverride(itemId)
                syncOrchestrator?.forceUpload(itemId)
            } catch (e: Exception) {
                // Log silently
            }
        }
    }

    fun toggleItemSelection(itemId: String) {
        _uiState.update { state ->
            val newSelection = if (itemId in state.selectedItems) {
                state.selectedItems - itemId
            } else {
                state.selectedItems + itemId
            }
            state.copy(
                selectedItems = newSelection,
                isSelectionMode = newSelection.isNotEmpty()
            )
        }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedItems = emptySet(), isSelectionMode = false) }
    }

    fun selectAll() {
        _uiState.update { state ->
            state.copy(
                selectedItems = state.uploadedItems.map { it.id }.toSet(),
                isSelectionMode = true
            )
        }
    }

    fun retryFailedItems() {
        viewModelScope.launch {
            val failedIds = _uiState.value.uploadedItems
                .filter { it.status == UploadStatus.FAILED }
                .map { it.id }

            failedIds.forEach { id ->
                try {
                    transferQueueRepository?.updateStatus(id, UploadStatus.QUEUED.name, System.currentTimeMillis())
                } catch (e: Exception) {
                    // Handle silently
                }
            }
            loadGalleryItems()
        }
    }

    fun deleteSelectedItems() {
        viewModelScope.launch {
            _uiState.value.selectedItems.forEach { id ->
                val item = _uiState.value.uploadedItems.find { it.id == id }
                item?.let {
                    runCatching {
                        File(it.filePath).delete()
                    }
                }
            }
            clearSelection()
            loadGalleryItems()
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun refresh() {
        loadGalleryItems()
    }

    private fun computeMetrics(items: List<GalleryItem>): CullingMetrics {
        val total = items.size
        val optimal = items.count { !it.isCulled && (it.cullingVerdict == "OPTIMAL" || it.cullingVerdict == "DYNAMIC_PROFILE_ADJUSTED") }
        val culled = items.count { it.isCulled || (it.cullingVerdict != "OPTIMAL" && it.cullingVerdict != "DYNAMIC_PROFILE_ADJUSTED") }
        val skipped = items.count { it.status == UploadStatus.SKIPPED || (it.isCulled && !it.isManualUploadOverride) }
        val savedBytes = items.filter { it.isCulled && !it.isManualUploadOverride }.sumOf { it.fileSizeBytes }
        return CullingMetrics(
            totalCount = total,
            optimalCount = optimal,
            culledCount = culled,
            skippedCount = skipped,
            quotaSavedBytes = savedBytes
        )
    }

    private fun applyFiltersAndSort(
        items: List<GalleryItem>,
        filter: FilterMode,
        sort: SortMode
    ): List<GalleryItem> {
        val filtered = when (filter) {
            FilterMode.ALL -> items
            FilterMode.OPTIMAL -> items.filter { !it.isCulled && (it.cullingVerdict == "OPTIMAL" || it.cullingVerdict == "DYNAMIC_PROFILE_ADJUSTED") }
            FilterMode.CULLED -> items.filter { it.isCulled || (it.cullingVerdict != "OPTIMAL" && it.cullingVerdict != "DYNAMIC_PROFILE_ADJUSTED") }
            FilterMode.SKIPPED -> items.filter { it.status == UploadStatus.SKIPPED || (it.isCulled && !it.isManualUploadOverride) }
            FilterMode.PHOTOS -> items.filter { !it.isVideo }
            FilterMode.VIDEOS -> items.filter { it.isVideo }
            FilterMode.UPLOADED -> items.filter { it.status == UploadStatus.SUCCESS }
            FilterMode.FAILED -> items.filter { it.status == UploadStatus.FAILED }
        }

        return when (sort) {
            SortMode.NEWEST_FIRST -> filtered.sortedByDescending { it.uploadedAt }
            SortMode.OLDEST_FIRST -> filtered.sortedBy { it.uploadedAt }
            SortMode.NAME_ASC -> filtered.sortedBy { it.fileName.lowercase() }
            SortMode.NAME_DESC -> filtered.sortedByDescending { it.fileName.lowercase() }
            SortMode.SIZE_DESC -> filtered.sortedByDescending { it.fileSizeBytes }
        }
    }
}

/**
 * Extension to convert CameraSyncItemEntity to GalleryItem
 */
fun CameraSyncItemEntity.toGalleryItem(): GalleryItem {
    val extension = filename.substringAfterLast('.', "").lowercase()
    val isVideo = extension in listOf("mp4", "mov", "m4v", "avi", "mkv", "3gp")

    val uploadStatus = when (status) {
        CameraSyncStatus.CONFIRMED_CLOUD, CameraSyncStatus.PURGED_LOCAL -> UploadStatus.SUCCESS
        CameraSyncStatus.FAILED -> UploadStatus.FAILED
        CameraSyncStatus.STREAMING_CLOUD -> UploadStatus.UPLOADING
        CameraSyncStatus.CULLED_SKIPPED -> UploadStatus.SKIPPED
        CameraSyncStatus.FORCE_UPLOAD_PENDING, CameraSyncStatus.BUFFERED_LOCAL, CameraSyncStatus.DETECTED -> UploadStatus.QUEUED
    }

    return GalleryItem(
        id = id,
        fileName = filename,
        filePath = thumbnailPath ?: localFilePath ?: "",
        fileSizeBytes = fileSizeBytes,
        uploadedAt = confirmedAtEpochMs ?: updatedAtEpochMs,
        status = uploadStatus,
        thumbnailPath = thumbnailPath,
        mimeType = if (isVideo) "video/$extension" else "image/$extension",
        isVideo = isVideo,
        cullingVerdict = cullingVerdict,
        meanLuminance = meanLuminance,
        entropyScore = entropyScore,
        isCulled = isCulled,
        isManualUploadOverride = isManualUploadOverride
    )
}

/**
 * Extension to convert TransferQueueItemEntity to GalleryItem
 */
fun TransferQueueItemEntity.toGalleryItem(context: android.content.Context? = null): GalleryItem {
    val extension = sourceFileName.substringAfterLast('.', "").lowercase()
    val isVideo = extension in listOf("mp4", "mov", "m4v", "avi", "mkv", "3gp")

    var resolvedThumb: String? = null
    if (context != null) {
        val thumbStorage = com.rvmeur.transferin.data.thumbnail.ThumbnailStorageManager(context)
        val file = thumbStorage.getThumbnailFile(id)
            ?: thumbStorage.getThumbnailFile(sourceFingerprint)
            ?: File(File(context.getExternalFilesDir(null), "camera_thumbnails"), "thumb_$id.jpg").takeIf { it.exists() && it.length() > 0 }
        resolvedThumb = file?.absolutePath
    }

    return GalleryItem(
        id = id,
        fileName = sourceFileName,
        filePath = resolvedThumb ?: sourceUri,
        fileSizeBytes = fileSizeBytes,
        uploadedAt = createdAtEpochMs,
        status = try {
            UploadStatus.valueOf(status)
        } catch (e: Exception) {
            UploadStatus.QUEUED
        },
        thumbnailPath = resolvedThumb,
        mimeType = if (isVideo) "video/$extension" else "image/$extension",
        isVideo = isVideo
    )
}
