package com.rvmeur.transferin.ui.image

import coil.request.ImageRequest

/**
 * Deterministic compound cache key generator for thumbnail image caching in Coil Compose.
 *
 * Avoids cache collisions and flickering across USB disconnects and camera reconnects.
 */
object ThumbnailCacheKey {

    /**
     * Builds a deterministic compound cache key.
     * Format: "thumb_{deviceId}_{objectHandle}_{fingerprint}"
     */
    fun build(
        fingerprint: String,
        deviceId: String? = null,
        objectHandle: Int? = null
    ): String {
        val cleanDevice = deviceId?.takeIf { it.isNotBlank() } ?: "local"
        val cleanHandle = objectHandle ?: 0
        return "thumb_${cleanDevice}_${cleanHandle}_$fingerprint"
    }
}

/**
 * Convenience extension to attach deterministic compound cache keys to Coil's [ImageRequest.Builder].
 */
fun ImageRequest.Builder.thumbnailCompoundKey(
    fingerprint: String,
    deviceId: String? = null,
    objectHandle: Int? = null
): ImageRequest.Builder {
    val key = ThumbnailCacheKey.build(fingerprint, deviceId, objectHandle)
    return memoryCacheKey(key).diskCacheKey(key)
}
