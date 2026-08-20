package com.rvmeur.transferin.domain.camera

/**
 * High-speed events emitted by camera tether adapters during active USB hardware sessions.
 */
sealed class CameraTetherEvent {
    /**
     * Emitted when a new photo or video object is captured and added to the camera storage/host queue.
     */
    data class ObjectAdded(
        val handle: Int,
        val filename: String,
        val formatCode: Int,
        val sizeBytes: Long = 0L,
        val captureTimestampMs: Long = System.currentTimeMillis(),
        val storageId: Int = 0
    ) : CameraTetherEvent()

    /**
     * Emitted when an object handle is removed or deleted on the camera storage.
     */
    data class ObjectRemoved(val handle: Int) : CameraTetherEvent()

    /**
     * Emitted when a camera device property (shutter, ISO, film simulation, etc.) changes.
     */
    data class DevicePropChanged(val propertyCode: Int, val value: Long) : CameraTetherEvent()

    /**
     * Emitted when camera busy state changes.
     */
    data class CameraStatusChanged(val isBusy: Boolean, val message: String? = null) : CameraTetherEvent()

    /**
     * Emitted on an error condition during tethering.
     */
    data class Error(val throwable: Throwable, val recoverable: Boolean = true) : CameraTetherEvent()
}
