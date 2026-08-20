package com.rvmeur.transferin.data.thumbnail

/**
 * Configuration options for [ThumbnailStorageManager].
 *
 * @property maxCacheBytes Maximum disk space in bytes allocated for thumbnail cache (defaults to 500MB).
 * @property maxTtlMs Maximum age in milliseconds before a thumbnail is considered stale (defaults to 3 days).
 * @property targetCacheBytes Target disk size in bytes to prune down to when [maxCacheBytes] is exceeded (defaults to 450MB).
 */
data class ThumbnailStorageConfig(
    val maxCacheBytes: Long = 500L * 1024 * 1024,
    val maxTtlMs: Long = 3L * 24 * 60 * 60 * 1000,
    val targetCacheBytes: Long = 450L * 1024 * 1024
)
