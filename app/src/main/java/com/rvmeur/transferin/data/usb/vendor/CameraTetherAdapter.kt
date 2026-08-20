package com.rvmeur.transferin.data.usb.vendor

/**
 * Unified interface for multi-vendor camera tethering adapters (Sony, Nikon, Canon, Fuji).
 *
 * Exposes common tethering lifecycle, storage query, handle retrieval, and preview extraction.
 */
interface CameraTetherAdapter : AutoCloseable {
    val vendorId: Int

    /**
     * Initializes and unlocks the camera hardware session via vendor-specific handshakes.
     * @return true if handshake and initialization succeeded.
     */
    suspend fun startSession(): Boolean

    /**
     * Retrieves all available storage IDs (e.g. Card Slot 1, Card Slot 2, Internal RAM).
     */
    suspend fun getStorageIds(): List<Int>

    /**
     * Retrieves all object handles stored under [storageId].
     */
    suspend fun getObjectHandles(storageId: Int): List<Int>

    /**
     * Extracts an embedded preview or thumbnail byte array for the specified [handle].
     */
    suspend fun extractPreview(handle: Int): ByteArray?

    /**
     * Executes non-cancellable vendor teardown sequence.
     */
    suspend fun closeSession()
}
