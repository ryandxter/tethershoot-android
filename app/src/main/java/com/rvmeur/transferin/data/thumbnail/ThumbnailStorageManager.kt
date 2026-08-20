package com.rvmeur.transferin.data.thumbnail

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Manages persistent local thumbnail storage on private app storage.
 *
 * Implements two-stage atomic writes (.tmp -> fd.sync() -> renameTo) to prevent 0-byte
 * corruption placeholders, provides bounded 500MB LRU disk pruning, 3-day TTL eviction,
 * and complete session purge on logout.
 */
class ThumbnailStorageManager(
    val thumbnailsDir: File,
    val config: ThumbnailStorageConfig = ThumbnailStorageConfig()
) {
    constructor(
        context: Context,
        config: ThumbnailStorageConfig = ThumbnailStorageConfig()
    ) : this(
        thumbnailsDir = File(context.cacheDir, THUMBNAILS_SUBDIR),
        config = config
    )

    companion object {
        private const val TAG = "ThumbnailStorageManager"
        const val THUMBNAILS_SUBDIR = "thumbnails"
        private val JPEG_SOI = byteArrayOf(0xFF.toByte(), 0xD8.toByte())
    }

    init {
        if (!thumbnailsDir.exists()) {
            thumbnailsDir.mkdirs()
        }
    }

    /**
     * Atomically saves a JPEG thumbnail byte array to disk with physical sync and verification.
     *
     * @param fingerprint Unique SHA-256 or compound identifier for the image object.
     * @param jpegBytes Binary JPEG payload.
     * @return The persisted non-zero [File] on success, or null on failure.
     */
    @Synchronized
    fun saveThumbnail(fingerprint: String, jpegBytes: ByteArray): File? {
        if (fingerprint.isBlank()) {
            Log.w(TAG, "saveThumbnail rejected: blank fingerprint")
            return null
        }
        if (jpegBytes.size < 2 || jpegBytes[0] != JPEG_SOI[0] || jpegBytes[1] != JPEG_SOI[1]) {
            Log.w(TAG, "saveThumbnail rejected: payload is not a valid JPEG stream (size=${jpegBytes.size})")
            return null
        }

        if (!thumbnailsDir.exists()) {
            thumbnailsDir.mkdirs()
        }

        val finalFile = File(thumbnailsDir, "${fingerprint}.jpg")
        if (finalFile.exists() && finalFile.length() > 0L) {
            finalFile.setLastModified(System.currentTimeMillis())
            return finalFile
        }

        val tempFile = File(thumbnailsDir, "thumb_${fingerprint}_${System.nanoTime()}.tmp")

        try {
            FileOutputStream(tempFile).use { fos ->
                fos.write(jpegBytes)
                fos.flush()
                fos.fd.sync()
            }

            var renamed = tempFile.renameTo(finalFile)
            if (!renamed) {
                try {
                    Files.move(
                        tempFile.toPath(),
                        finalFile.toPath(),
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING
                    )
                    renamed = true
                } catch (e: Exception) {
                    try {
                        Files.move(
                            tempFile.toPath(),
                            finalFile.toPath(),
                            StandardCopyOption.REPLACE_EXISTING
                        )
                        renamed = true
                    } catch (e2: Exception) {
                        Log.e(TAG, "Failed fallback move for thumbnail: $fingerprint", e2)
                        renamed = false
                    }
                }
            }

            // Post-write verification
            if (!renamed || !finalFile.exists() || finalFile.length() <= 0L) {
                Log.e(TAG, "Thumbnail verification failed for $fingerprint (exists=${finalFile.exists()}, length=${finalFile.length()})")
                if (finalFile.exists()) finalFile.delete()
                if (tempFile.exists()) tempFile.delete()
                return null
            }

            // Clean up any remaining temp file
            if (tempFile.exists()) {
                tempFile.delete()
            }

            // Enforce LRU cache budget
            if (getUsedBytes() > config.maxCacheBytes) {
                pruneToSize(config.targetCacheBytes)
            }

            return finalFile
        } catch (t: Throwable) {
            Log.e(TAG, "Exception saving thumbnail for fingerprint: $fingerprint", t)
            if (tempFile.exists()) {
                tempFile.delete()
            }
            if (finalFile.exists() && finalFile.length() == 0L) {
                finalFile.delete()
            }
            return null
        }
    }

    /**
     * Retrieves an existing thumbnail file for [fingerprint], updating its LRU timestamp.
     * Cleans up 0-byte corrupt files if encountered.
     */
    @Synchronized
    fun getThumbnailFile(fingerprint: String): File? {
        if (fingerprint.isBlank()) return null
        val file = File(thumbnailsDir, "${fingerprint}.jpg")
        if (!file.exists()) return null

        if (file.length() <= 0L) {
            Log.w(TAG, "Pruning zero-byte corrupted thumbnail file: ${file.name}")
            file.delete()
            return null
        }

        file.setLastModified(System.currentTimeMillis())
        return file
    }

    /**
     * Returns true if a valid, non-empty thumbnail exists on disk for [fingerprint].
     */
    fun hasThumbnail(fingerprint: String): Boolean {
        if (fingerprint.isBlank()) return false
        val file = File(thumbnailsDir, "${fingerprint}.jpg")
        return file.exists() && file.length() > 0L
    }

    /**
     * Deletes a specific thumbnail file if present.
     */
    @Synchronized
    fun deleteThumbnail(fingerprint: String): Boolean {
        if (fingerprint.isBlank()) return false
        val file = File(thumbnailsDir, "${fingerprint}.jpg")
        return if (file.exists()) file.delete() else false
    }

    /**
     * Prunes thumbnails older than [maxAgeMs] (defaults to 3 days).
     *
     * @return Number of deleted stale files.
     */
    @Synchronized
    fun pruneStaleThumbnails(maxAgeMs: Long = config.maxTtlMs): Int {
        val now = System.currentTimeMillis()
        val files = thumbnailsDir.listFiles() ?: return 0
        var deletedCount = 0

        for (file in files) {
            val age = now - file.lastModified()
            if (age > maxAgeMs) {
                if (file.delete()) {
                    deletedCount++
                }
            }
        }
        return deletedCount
    }

    /**
     * Evicts oldest accessed thumbnail files according to LRU order until total disk
     * consumption is at or below [targetSizeBytes].
     *
     * @return Total bytes reclaimed.
     */
    @Synchronized
    fun pruneToSize(targetSizeBytes: Long = config.targetCacheBytes): Long {
        val files = thumbnailsDir.listFiles() ?: return 0L

        // Immediately clean up orphaned temp files
        for (file in files) {
            if (file.name.endsWith(".tmp")) {
                file.delete()
            }
        }

        val jpgFiles = (thumbnailsDir.listFiles() ?: emptyArray())
            .filter { it.isFile && it.name.endsWith(".jpg") }

        var currentBytes = jpgFiles.sumOf { it.length() }
        if (currentBytes <= targetSizeBytes) return 0L

        val sortedFiles = jpgFiles.sortedBy { it.lastModified() }
        var reclaimedBytes = 0L

        for (file in sortedFiles) {
            if (currentBytes <= targetSizeBytes) break
            val size = file.length()
            if (file.delete()) {
                currentBytes -= size
                reclaimedBytes += size
            }
        }

        return reclaimedBytes
    }

    /**
     * Purges all thumbnails and temporary staging files from disk.
     * Typically called on user logout or session teardown.
     */
    @Synchronized
    fun purgeAllThumbnails(): Boolean {
        val files = thumbnailsDir.listFiles() ?: return true
        var allClean = true
        for (file in files) {
            if (!file.delete()) {
                allClean = false
            }
        }
        return allClean
    }

    /**
     * Returns the total byte size occupied by all thumbnails and temp files in the storage directory.
     */
    fun getUsedBytes(): Long {
        val files = thumbnailsDir.listFiles() ?: return 0L
        return files.sumOf { it.length() }
    }
}
