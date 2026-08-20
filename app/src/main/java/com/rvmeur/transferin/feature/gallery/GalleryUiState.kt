package com.rvmeur.transferin.feature.gallery

import com.rvmeur.transferin.domain.model.UploadStatus
import java.util.Locale

/**
 * UI State for the Uploaded Gallery Screen with Smart Culling support.
 */
data class GalleryUiState(
    val isLoading: Boolean = false,
    val uploadedItems: List<GalleryItem> = emptyList(),
    val allItems: List<GalleryItem> = emptyList(),
    val filterMode: FilterMode = FilterMode.ALL,
    val sortMode: SortMode = SortMode.NEWEST_FIRST,
    val selectedItems: Set<String> = emptySet(),
    val isSelectionMode: Boolean = false,
    val cullingMetrics: CullingMetrics = CullingMetrics(),
    val errorMessage: String? = null
)

/**
 * Summary metrics for smart culling and cloud quota savings.
 */
data class CullingMetrics(
    val totalCount: Int = 0,
    val optimalCount: Int = 0,
    val culledCount: Int = 0,
    val skippedCount: Int = 0,
    val quotaSavedBytes: Long = 0L
)

/**
 * A single item in the gallery with exposure & culling metadata.
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
    val isVideo: Boolean = false,
    val cullingVerdict: String = "OPTIMAL",
    val meanLuminance: Double = 128.0,
    val entropyScore: Double = 5.0,
    val isCulled: Boolean = false,
    val isManualUploadOverride: Boolean = false
) {
    val fileSizeFormatted: String
        get() = formatFileSize(fileSizeBytes)
}

enum class FilterMode {
    ALL,        // semua
    OPTIMAL,    // optimal
    CULLED,     // terpangkas
    SKIPPED,    // dilewati
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
        else -> String.format(Locale.US, "%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0))
    }
}
