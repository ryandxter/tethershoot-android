package com.reflection.transferin.feature.gallery

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.reflection.transferin.data.local.entity.TransferQueueItemEntity
import com.reflection.transferin.data.repository.TransferQueueRepositoryImpl
import com.reflection.transferin.domain.model.UploadStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

/**
 * ViewModel for the Uploaded Gallery Screen.
 */
class GalleryViewModel(
    application: Application,
    private val transferQueueRepository: TransferQueueRepositoryImpl
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(GalleryUiState())
    val uiState: StateFlow<GalleryUiState> = _uiState.asStateFlow()

    init {
        loadGalleryItems()
    }

    private fun loadGalleryItems() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            try {
                val items = transferQueueRepository.getAll()
                val galleryItems = items.map { item -> item.toGalleryItem() }
                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        uploadedItems = applyFiltersAndSort(galleryItems, state.filterMode, state.sortMode)
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

    fun setFilterMode(mode: FilterMode) {
        viewModelScope.launch {
            val items = transferQueueRepository.getAll()
            val galleryItems = items.map { item -> item.toGalleryItem() }
            _uiState.update { state ->
                state.copy(
                    filterMode = mode,
                    uploadedItems = applyFiltersAndSort(galleryItems, mode, state.sortMode)
                )
            }
        }
    }

    fun setSortMode(mode: SortMode) {
        viewModelScope.launch {
            val items = transferQueueRepository.getAll()
            val galleryItems = items.map { item -> item.toGalleryItem() }
            _uiState.update { state ->
                state.copy(
                    sortMode = mode,
                    uploadedItems = applyFiltersAndSort(galleryItems, state.filterMode, mode)
                )
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
                    transferQueueRepository.updateStatus(id, UploadStatus.QUEUED.name, System.currentTimeMillis())
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

    private fun applyFiltersAndSort(
        items: List<GalleryItem>,
        filter: FilterMode,
        sort: SortMode
    ): List<GalleryItem> {
        val filtered = when (filter) {
            FilterMode.ALL -> items
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
 * Extension to convert TransferQueueItemEntity to GalleryItem
 */
fun TransferQueueItemEntity.toGalleryItem(): GalleryItem {
    val extension = sourceFileName.substringAfterLast('.', "").lowercase()
    val isVideo = extension in listOf("mp4", "mov", "m4v", "avi", "mkv", "3gp")

    return GalleryItem(
        id = id,
        fileName = sourceFileName,
        filePath = sourceUri,
        fileSizeBytes = fileSizeBytes,
        uploadedAt = createdAtEpochMs,
        status = try {
            UploadStatus.valueOf(status)
        } catch (e: Exception) {
            UploadStatus.QUEUED
        },
        mimeType = if (isVideo) "video/$extension" else "image/$extension",
        isVideo = isVideo
    )
}
