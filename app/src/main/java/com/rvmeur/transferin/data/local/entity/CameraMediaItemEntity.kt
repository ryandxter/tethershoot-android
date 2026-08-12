package com.rvmeur.transferin.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Status untuk item media kamera.
 *
 * Alur utama:
 * DISCOVERED -> CACHED -> QUEUED -> UPLOADING -> UPLOADED
 * atau FAILED jika upload gagal.
 */
enum class MediaStatus {
    DISCOVERED,
    CACHED,
    QUEUED,
    UPLOADING,
    UPLOADED,
    FAILED
}

/**
 * Entity item media kamera untuk tracking file hasil discovery dari kamera.
 *
 * Prinsip identitas:
 * - objectHandle unik di dalam konteks device
 * - karena itu index unik dibuat pada (deviceId, objectHandle)
 */
@Entity(
    tableName = "camera_media_items",
    indices = [
        Index(value = ["deviceId", "storageId"]),
        Index(value = ["deviceId", "objectHandle"], unique = true),
        Index(value = ["fileName"]),
        Index(value = ["status"]),
        Index(value = ["captureGroup"]),
        Index(value = ["discoveredAt"])
    ]
)
data class CameraMediaItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    // Device info
    val deviceId: String,
    val storageId: Int,

    // MTP object identity
    val objectHandle: Int,
    val parentHandle: Int?,

    // File info
    val fileName: String,
    val fileExtension: String,
    val format: Int,
    val sizeBytes: Long,
    val modifiedAt: Long,
    val captureGroup: String,

    // Local paths
    val thumbPath: String? = null,
    val localCopyPath: String? = null,

    // Status
    val status: MediaStatus = MediaStatus.DISCOVERED,
    val serverId: String? = null,

    // Upload tracking
    val retryCount: Int = 0,
    val lastError: String? = null,

    // Metadata
    val discoveredAt: Long = System.currentTimeMillis(),
    val uploadedAt: Long? = null
) {
    private val normalizedExtension: String
        get() = fileExtension.trim().lowercase()

    val isImage: Boolean
        get() = format == 0x3001 ||   // EXIF/JPEG
                format == 0x3002 ||   // TIFF/EP
                format == 0x3801 ||   // TIFF
                format == 0xB101      // DNG

    val isRaw: Boolean
        get() = normalizedExtension in setOf("cr2", "cr3", "nef", "arw", "raf", "dng", "raw")

    val isJpeg: Boolean
        get() = normalizedExtension in setOf("jpg", "jpeg")

    val isVideo: Boolean
        get() = normalizedExtension in setOf("mp4", "mov", "avi", "m4v")

    val isPhoto: Boolean
        get() = isImage || isJpeg || isRaw

    val isPreviewReady: Boolean
        get() = !thumbPath.isNullOrBlank()

    val isUploaded: Boolean
        get() = status == MediaStatus.UPLOADED

    val canRetryUpload: Boolean
        get() = status == MediaStatus.FAILED

    val displayName: String
        get() = fileName.substringBeforeLast('.', fileName)
}