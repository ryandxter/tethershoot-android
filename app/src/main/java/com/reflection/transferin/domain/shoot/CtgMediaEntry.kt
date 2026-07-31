package com.reflection.transferin.domain.shoot

/**
 * Immutable data class representing a single media entry parsed from Canon CTG database.
 *
 * MTP Format Codes (android.mtp.MtpConstants):
 * - 0x3001: FORMAT_MPEG           — MPEG video
 * - 0x3002: FORMAT_MPEG2          — MPEG-2 video
 * - 0x3003: FORMAT_MPEG3          — MPEG-3 audio/video
 * - 0x3004: FORMAT_MPEG4          — MPEG-4 video
 * - 0x3801: FORMAT_JFIF           — JPEG baseline (photos)
 * - 0x3802: FORMAT_JPEG           — JPEG exif
 * - 0x3803: FORMAT_TIFF_EP        — TIFF EP
 * - 0x3804: FORMAT_TIFF           — TIFF
 * - 0x3805: FORMAT_SCRIPT         — Script file
 * - 0x3806: FORMAT_ASF            — ASF (Windows Media)
 * - 0x3807: FORMAT_AVI            — AVI video
 * - 0x380A: FORMAT_WMA            — Windows Media Audio
 * - 0x380B: FORMAT_OGG            — OGG audio
 * - 0x380D: FORMAT_RAW            — RAW data
 * - 0x380F: FORMAT_DPOF           — DPOF file
 * - 0x3811: FORMAT_UNDEFINED_AUDIO
 * - 0x3812: FORMAT_UNDEFINED_VIDEO
 * - 0x3813: FORMAT_WMV            — Windows Media Video
 * - 0x3814: FORMAT_MP4_CONTAINER  — MP4 container
 * - 0x3000: FORMAT_ASSOCIATION    — Folder/directory
 *
 * @property handle MTP object handle (unique per session)
 * @property filename Original filename on camera
 * @property size File size in bytes
 * @property modifiedTime Last modified timestamp (milliseconds since epoch)
 * @property capturedTime Capture timestamp (milliseconds since epoch, if available)
 * @property formatCode MTP format code (e.g., 0x3801 for JPEG)
 */
data class CtgMediaEntry(
    val handle: Int,
    val filename: String,
    val size: Long,
    val modifiedTime: Long,
    val capturedTime: Long?,
    val formatCode: Int
) {
    init {
        require(handle > 0) { "handle must be positive, got: $handle" }
        require(filename.isNotBlank()) { "filename must not be blank" }
        require(size >= 0) { "size must be non-negative, got: $size" }
        require(modifiedTime >= 0) { "modifiedTime must be non-negative, got: $modifiedTime" }
    }

    /**
     * Immutable fingerprint that uniquely identifies this media across sessions.
     * Uses handle + filename + size to avoid collisions when camera resets handles.
     */
    val fingerprint: String
        get() = "$handle|$filename|$size"

    /**
     * Display-friendly file size formatting.
     */
    val displaySize: String
        get() = when {
            size >= 1_073_741_824 -> String.format("%.1f GB", size / 1_073_741_824.0)
            size >= 1_048_576 -> String.format("%.1f MB", size / 1_048_576.0)
            size >= 1024 -> String.format("%.1f KB", size / 1024.0)
            else -> "$size B"
        }

    /**
     * Check if this entry represents a photo (not a folder/association).
     * FORMAT_ASSOCIATION (0x3000) is used for folders in MTP.
     */
    val isPhoto: Boolean
        get() = formatCode != FORMAT_ASSOCIATION

    /**
     * Check if this entry represents a RAW image file.
     */
    val isRaw: Boolean
        get() = formatCode == FORMAT_RAW

    /**
     * Check if this entry represents a video file.
     */
    val isVideo: Boolean
        get() = formatCode in VIDEO_FORMAT_CODES

    companion object {
        /** MTP FORMAT_ASSOCIATION — folder/directory marker */
        const val FORMAT_ASSOCIATION = 0x3000

        /** MTP FORMAT_RAW — RAW image data */
        const val FORMAT_RAW = 0x380D

        /** JPEG format codes for photos */
        private val PHOTO_FORMAT_CODES = setOf(0x3801, 0x3802)

        /** Video format codes */
        private val VIDEO_FORMAT_CODES = setOf(
            0x3001, 0x3002, 0x3003, 0x3004, 0x3806, 0x3813, 0x3814
        )

        /**
         * Empty factory for testability.
         */
        fun empty(): CtgMediaEntry = CtgMediaEntry(
            handle = 0,
            filename = "",
            size = 0,
            modifiedTime = 0,
            capturedTime = null,
            formatCode = 0
        )
    }
}
