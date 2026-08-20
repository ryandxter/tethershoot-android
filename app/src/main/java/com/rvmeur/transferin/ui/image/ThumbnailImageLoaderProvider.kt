package com.rvmeur.transferin.ui.image

import android.content.Context
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import java.io.File

/**
 * Provides a dedicated, high-performance Coil [ImageLoader] configured specifically
 * for thumbnail rendering and gallery performance.
 *
 * Configured with:
 * - 25% JVM heap memory cache with strong reference retention.
 * - 250MB bounded LRU disk cache in `context.cacheDir/thumbnails_coil`.
 * - Crossfade transitions (200ms).
 * - Automatic EXIF Tag 0x0112 orientation normalization.
 */
object ThumbnailImageLoaderProvider {

    @Volatile
    private var instance: ImageLoader? = null

    /**
     * Gets or creates the singleton [ImageLoader] instance configured for thumbnail caching.
     */
    fun getImageLoader(context: Context): ImageLoader {
        return instance ?: synchronized(this) {
            instance ?: buildImageLoader(context.applicationContext).also { instance = it }
        }
    }

    private fun buildImageLoader(context: Context): ImageLoader {
        val diskCacheDir = File(context.cacheDir, "thumbnails_coil")
        if (!diskCacheDir.exists()) {
            diskCacheDir.mkdirs()
        }

        return ImageLoader.Builder(context)
            .memoryCache {
                MemoryCache.Builder(context)
                    .maxSizePercent(0.25)
                    .strongReferencesEnabled(true)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(diskCacheDir)
                    .maxSizeBytes(250L * 1024 * 1024)
                    .build()
            }
            .crossfade(200)
            .allowHardware(true)
            .allowRgb565(true)
            .build()
    }
}
