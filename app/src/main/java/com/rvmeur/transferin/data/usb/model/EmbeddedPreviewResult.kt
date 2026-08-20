package com.rvmeur.transferin.data.usb.model

/**
 * Unified representation of an extracted embedded JPEG preview from a camera RAW or JPEG file.
 *
 * @property jpegBytes Binary payload of the extracted JPEG preview (starts with 0xFFD8).
 * @property width Image width in pixels (0 if unknown).
 * @property height Image height in pixels (0 if unknown).
 * @property orientation EXIF orientation tag 0x0112 (1 = 0°, 3 = 180°, 6 = 90° CW, 8 = 270° CW).
 * @property format Container RAW format ("RAF", "CR3", "ARW", "NEF", "JPG").
 * @property parseTimeMs Elapsed wall-clock time in milliseconds to locate and extract the preview.
 */
data class EmbeddedPreviewResult(
    val jpegBytes: ByteArray,
    val width: Int = 0,
    val height: Int = 0,
    val orientation: Int = 1,
    val format: String = "",
    val parseTimeMs: Long = 0L
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as EmbeddedPreviewResult
        return jpegBytes.contentEquals(other.jpegBytes) &&
                width == other.width &&
                height == other.height &&
                orientation == other.orientation &&
                format == other.format
    }

    override fun hashCode(): Int {
        var result = jpegBytes.contentHashCode()
        result = 31 * result + width
        result = 31 * result + height
        result = 31 * result + orientation
        result = 31 * result + format.hashCode()
        return result
    }
}
