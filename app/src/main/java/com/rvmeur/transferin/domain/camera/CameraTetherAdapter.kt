package com.rvmeur.transferin.domain.camera

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import java.io.OutputStream

/**
 * Unified vendor-agnostic interface for direct USB hardware tethering engines.
 */
interface CameraTetherAdapter : AutoCloseable {
    val vendor: CameraVendor
    val deviceName: String
    val connectionState: StateFlow<TetherState>

    fun canHandle(device: UsbDevice): Boolean
    fun observeEvents(): Flow<CameraTetherEvent>

    suspend fun initialize(device: UsbDevice, connection: UsbDeviceConnection): Result<TetherDeviceInfo>
    suspend fun terminateSession(): Result<Unit>
    suspend fun getObjectMetadata(handle: Int): Result<CameraMediaMetadata>
    suspend fun getPreview(handle: Int): Result<ByteArray>
    suspend fun streamMedia(handle: Int, sink: OutputStream, onProgress: (Long, Long) -> Unit): Result<Unit>
}
