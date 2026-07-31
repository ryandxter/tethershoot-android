package com.reflection.transferin.domain.shoot

/**
 * Composite fingerprint for cross-session media deduplication.
 *
 * ## Fingerprint Format
 * The fingerprint uses the stable format: `{handle}|{name}|{size}`
 * - **handle**: CTG storage handle (Int)
 * - **name**: Lowercase, trimmed filename
 * - **size**: File size in bytes (Long)
 *
 * ## Separator Rationale
 * The pipe character `|` is used as separator because it is unambiguous in filenames.
 * Windows, macOS, and Linux all prohibit `|` in filenames, making this a safe delimiter.
 *
 * ## Usage
 * - Fingerprints are generated from [com.reflection.transferin.domain.model.CameraMedia]
 *   using handle + normalized name + size
 * - Stored as primary key in [com.reflection.transferin.data.local.entity.UploadedFingerprintEntity]
 * - Used for O(1) duplicate detection across sessions
 *
 * ## Example
 * ```
 * val media = CameraMedia(handle=123, name="IMG_0001.JPG", size=1234567, ...)
 * val fingerprint = MediaFingerprint(media.handle, media.name, media.size)
 * val storageKey = fingerprint.toStorageKey() // "123|img_0001.jpg|1234567"
 * ```
 *
 * @see com.reflection.transferin.data.local.entity.UploadedFingerprintEntity
 * @see com.reflection.transferin.domain.shoot.SmartUploadFilter
 * @see com.reflection.transferin.domain.shoot.NewShotDetector
 */
data class MediaFingerprint(
    val handle: Int,
    val filename: String,
    val sizeBytes: Long
) {
    /**
     * Converts this fingerprint to a storage key string.
     *
     * Normalization steps:
     * 1. Trim whitespace from filename
     * 2. Convert to lowercase
     *
     * @return Storage key in format "{handle}|{normalizedName}|{sizeBytes}"
     */
    fun toStorageKey(): String {
        val normalized = filename.trim().lowercase()
        return "$handle|$normalized|$sizeBytes"
    }

    companion object {
        /**
         * Parses a storage key string back to a [MediaFingerprint].
         *
         * @param key Storage key in format "{handle}|{name}|{size}"
         * @return [MediaFingerprint] if parsing succeeds, null otherwise
         */
        fun fromStorageKey(key: String): MediaFingerprint? {
            val parts = key.split("|")
            if (parts.size != 3) {
                return null
            }
            return try {
                MediaFingerprint(
                    handle = parts[0].toInt(),
                    filename = parts[1],
                    sizeBytes = parts[2].toLong()
                )
            } catch (e: NumberFormatException) {
                null
            }
        }
    }
}
