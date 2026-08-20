package com.rvmeur.transferin.domain.camera

/**
 * Camera device details and capabilities discovered during initial PTP handshake.
 */
data class TetherDeviceInfo(
    val vendor: CameraVendor,
    val modelName: String,
    val serialNumber: String = "",
    val firmwareVersion: String = "",
    val supportedOperations: Set<Int> = emptySet(),
    val saveDestination: String = "PC+Card"
)
