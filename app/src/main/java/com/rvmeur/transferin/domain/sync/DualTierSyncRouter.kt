package com.rvmeur.transferin.domain.sync

/**
 * Sync tier classification for dual-tier camera media delivery.
 */
enum class SyncTier {
    /**
     * Tier 1: Instant low-latency JPEG preview extraction and upload (<3s delivery to cloud gallery).
     */
    TIER_1_PREVIEW,

    /**
     * Tier 2: Full raw or video binary stream archived in background.
     */
    TIER_2_ARCHIVE,

    /**
     * Direct single-stage sync for lightweight standard JPEG images without separate preview extraction.
     */
    SINGLE_TIER_DIRECT
}

/**
 * Routing instruction for synchronizing a camera media item.
 */
data class SyncPlan(
    val originalFilename: String,
    val primaryTier: SyncTier,
    val requiresDualTier: Boolean,
    val previewFilename: String? = null,
    val archiveFilename: String = originalFilename,
    val previewTargetSizeBytes: Long? = null
)

/**
 * Intelligent router determining optimal delivery tier (instant preview vs archival RAW)
 * based on file characteristics and network bandwidth.
 */
class DualTierSyncRouter {

    companion object {
        private val RAW_EXTENSIONS = setOf("raf", "cr2", "cr3", "nef", "arw", "dng", "raw", "rw2", "orf")
        private val VIDEO_EXTENSIONS = setOf("mp4", "mov", "avi", "m4v", "mkv")
        private const val LARGE_JPEG_THRESHOLD_BYTES = 5 * 1024 * 1024L // 5 MB
    }

    /**
     * Calculates the sync plan for a given media object.
     */
    fun route(
        filename: String,
        fileSizeBytes: Long
    ): SyncPlan {
        val extension = filename.substringAfterLast('.', "").lowercase()
        val baseName = filename.substringBeforeLast('.', filename)

        val isRaw = extension in RAW_EXTENSIONS
        val isVideo = extension in VIDEO_EXTENSIONS
        val isLargeJpeg = (extension == "jpg" || extension == "jpeg") && fileSizeBytes > LARGE_JPEG_THRESHOLD_BYTES

        return when {
            isRaw -> {
                SyncPlan(
                    originalFilename = filename,
                    primaryTier = SyncTier.TIER_1_PREVIEW,
                    requiresDualTier = true,
                    previewFilename = "${baseName}_preview.jpg",
                    archiveFilename = filename
                )
            }
            isVideo -> {
                SyncPlan(
                    originalFilename = filename,
                    primaryTier = SyncTier.TIER_1_PREVIEW,
                    requiresDualTier = true,
                    previewFilename = "${baseName}_thumbnail.jpg",
                    archiveFilename = filename
                )
            }
            isLargeJpeg -> {
                SyncPlan(
                    originalFilename = filename,
                    primaryTier = SyncTier.TIER_1_PREVIEW,
                    requiresDualTier = true,
                    previewFilename = "${baseName}_preview.jpg",
                    archiveFilename = filename
                )
            }
            else -> {
                // Lightweight JPEG or other media
                SyncPlan(
                    originalFilename = filename,
                    primaryTier = SyncTier.SINGLE_TIER_DIRECT,
                    requiresDualTier = false,
                    previewFilename = null,
                    archiveFilename = filename
                )
            }
        }
    }
}
