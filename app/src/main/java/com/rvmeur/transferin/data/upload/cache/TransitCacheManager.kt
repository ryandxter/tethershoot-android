package com.rvmeur.transferin.data.upload.cache

import android.util.Log
import com.rvmeur.transferin.data.local.dao.CameraSyncDao
import com.rvmeur.transferin.data.local.entity.CameraSyncStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Manages the local transit disk storage cache (/cache/transit/) with a strict 5GB boundary.
 *
 * Unbreakable Invariant:
 * ONLY files in [CameraSyncStatus.CONFIRMED_CLOUD] status can be evicted/purged.
 * Unconfirmed files (DETECTED, BUFFERED_LOCAL, STREAMING_CLOUD, FAILED) are NEVER evicted.
 */
class TransitCacheManager(
    val transitDir: File,
    private val cameraSyncDao: CameraSyncDao,
    val maxCacheBytes: Long = DEFAULT_MAX_CACHE_BYTES
) {

    companion object {
        private const val TAG = "TransitCacheManager"
        const val DEFAULT_MAX_CACHE_BYTES: Long = 5L * 1024 * 1024 * 1024 // 5 GB
    }

    private val mutex = Mutex()

    init {
        if (!transitDir.exists()) {
            transitDir.mkdirs()
        }
    }

    /**
     * Atomically reserves [requiredBytes] in the transit cache directory.
     *
     * If current used bytes + [requiredBytes] exceeds [maxCacheBytes], triggers LRU eviction
     * of [CameraSyncStatus.CONFIRMED_CLOUD] files until enough space is freed.
     *
     * If space cannot be freed because all remaining files are unconfirmed, throws [TransitCacheExhaustedException].
     */
    suspend fun reserveSpace(requiredBytes: Long): Boolean = mutex.withLock {
        withContext(Dispatchers.IO) {
            require(requiredBytes >= 0L) { "requiredBytes must be non-negative: $requiredBytes" }

            if (requiredBytes > maxCacheBytes) {
                throw TransitCacheExhaustedException(
                    "Requested allocation ($requiredBytes bytes) exceeds total maximum cache boundary ($maxCacheBytes bytes)"
                )
            }

            var currentUsed = getUsedBytes()
            if (currentUsed + requiredBytes <= maxCacheBytes) {
                return@withContext true
            }

            Log.i(
                TAG,
                "Transit cache over threshold (used: $currentUsed, required: $requiredBytes, max: $maxCacheBytes). Purging confirmed items..."
            )

            val purgeableCandidates = cameraSyncDao.getPurgeableItems()
            if (purgeableCandidates.isEmpty()) {
                throw TransitCacheExhaustedException(
                    "Transit cache full (${currentUsed}B / ${maxCacheBytes}B) and no CONFIRMED_CLOUD items available for eviction."
                )
            }

            for (candidate in purgeableCandidates) {
                // Strict invariant assertion: only CONFIRMED_CLOUD can be purged
                if (candidate.status == CameraSyncStatus.CONFIRMED_CLOUD) {
                    val filePath = candidate.localFilePath
                    if (!filePath.isNullOrBlank()) {
                        val file = File(filePath)
                        if (file.exists()) {
                            val deleted = file.delete()
                            Log.d(TAG, "Purged confirmed local file: $filePath (deleted=$deleted)")
                        }
                    } else {
                        // Also try to find file by fingerprint in transitDir
                        deleteTransitFile(candidate.compoundFingerprint)
                    }

                    cameraSyncDao.markPurgedLocal(candidate.id)

                    currentUsed = getUsedBytes()
                    if (currentUsed + requiredBytes <= maxCacheBytes) {
                        Log.i(TAG, "Sufficient transit space freed. New usage: $currentUsed bytes")
                        return@withContext true
                    }
                }
            }

            currentUsed = getUsedBytes()
            if (currentUsed + requiredBytes > maxCacheBytes) {
                throw TransitCacheExhaustedException(
                    "Transit cache full (${currentUsed}B / ${maxCacheBytes}B) after purging all available CONFIRMED_CLOUD items."
                )
            }

            return@withContext true
        }
    }

    /**
     * Creates and returns a staging [File] in the transit directory with the given [fingerprint] and [extension].
     */
    fun createTransitFile(fingerprint: String, extension: String): File {
        if (!transitDir.exists()) {
            transitDir.mkdirs()
        }
        val cleanExt = extension.trim().trimStart('.')
        val filename = if (cleanExt.isEmpty()) fingerprint else "${fingerprint}.${cleanExt}"
        return File(transitDir, filename)
    }

    /**
     * Finds an existing transit file by [fingerprint].
     */
    fun getTransitFile(fingerprint: String, extension: String? = null): File? {
        if (!transitDir.exists()) return null
        if (!extension.isNullOrBlank()) {
            val cleanExt = extension.trim().trimStart('.')
            val target = File(transitDir, "${fingerprint}.${cleanExt}")
            if (target.exists()) return target
        }

        val matching = transitDir.listFiles { file ->
            file.name.startsWith(fingerprint)
        }
        return matching?.firstOrNull()
    }

    /**
     * Deletes any transit files associated with [fingerprint].
     */
    fun deleteTransitFile(fingerprint: String): Boolean {
        if (!transitDir.exists()) return false
        val matching = transitDir.listFiles { file ->
            file.name.startsWith(fingerprint)
        } ?: return false

        var anyDeleted = false
        for (file in matching) {
            if (file.delete()) {
                anyDeleted = true
            }
        }
        return anyDeleted
    }

    /**
     * Returns total byte size of all files currently residing in [transitDir].
     */
    fun getUsedBytes(): Long {
        if (!transitDir.exists()) return 0L
        val files = transitDir.listFiles() ?: return 0L
        var total = 0L
        for (f in files) {
            if (f.isFile) {
                total += f.length()
            }
        }
        return total
    }

    /**
     * Clears all transit files in the directory.
     */
    fun clearAll() {
        if (!transitDir.exists()) return
        val files = transitDir.listFiles() ?: return
        for (file in files) {
            file.delete()
        }
    }
}
