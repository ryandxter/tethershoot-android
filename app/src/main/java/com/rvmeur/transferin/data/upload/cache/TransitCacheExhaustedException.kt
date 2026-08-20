package com.rvmeur.transferin.data.upload.cache

import java.io.IOException

/**
 * Thrown when the transit cache reaches its 5GB boundary and cannot evict any files
 * because all remaining cached media are unconfirmed (actively buffering, streaming, or pending cloud confirmation).
 */
class TransitCacheExhaustedException(
    message: String = "Transit cache exhausted: 5GB capacity reached and no CONFIRMED_CLOUD items available for eviction."
) : IOException(message)
