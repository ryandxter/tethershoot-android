package com.rvmeur.transferin.domain.camera

/**
 * Metadata for a captured media object exposed by a tethered camera.
 */
data class CameraMediaMetadata(
    val handle: Int,
    val filename: String,
    val formatCode: Int,
    val sizeBytes: Long,
    val width: Int = 0,
    val height: Int = 0,
    val captureDate: Long = System.currentTimeMillis(),
    val shutterSpeed: String? = null,
    val aperture: String? = null,
    val iso: Int? = null,
    val exposureBias: String? = null,
    val filmSimulation: FujiFilmSimulation? = null
)
