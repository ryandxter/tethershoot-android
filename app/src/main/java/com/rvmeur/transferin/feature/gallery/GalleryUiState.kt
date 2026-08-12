package com.rvmeur.transferin.feature.gallery

import com.rvmeur.transferin.domain.model.UploadStatus

/**
 * UI State for the Uploaded Gallery Screen.
 */
data class GalleryUiState(
    val isLoading: Boolean = false,
    val uploadedItems: List<GalleryItem> = emptyList(),
    val filterMode: FilterMode = FilterMode.ALL,
    val sortMode: SortMode = SortMode.NEWEST_FIRST,
    val selectedItems: Set<String> = emptySet(),
    val isSelectionMode: Boolean = false,
    val errorMessage: String? = null
)

/**
 * A single item in the gallery.
 */
data class GalleryItem(
    val id: String,
    val fileName: String,
    val filePath: String,
    val fileSizeBytes: Long,
    val uploadedAt: Long,
    val status: UploadStatus,
    val thumbnailPath: String? = null,
    val mimeType: String? = null,
    val isVideo: Boolean = false
) {
    val fileSizeFormatted: String
        get() = formatFileSize(fileSizeBytes)
}

enum class FilterMode {
    ALL,
    PHOTOS,
    VIDEOS,
    UPLOADED,
    FAILED
}

enum class SortMode {
    NEWEST_FIRST,
    OLDEST_FIRST,
    NAME_ASC,
    NAME_DESC,
    SIZE_DESC
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
        else -> String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0))
    }
}
