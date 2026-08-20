package com.rvmeur.transferin.data.usb.vendor.nikon

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.util.Log
import com.rvmeur.transferin.data.usb.CameraInitContext
import com.rvmeur.transferin.data.usb.CameraInitResult
import com.rvmeur.transferin.data.usb.vendor.CameraVendorInitializer
import kotlinx.coroutines.runBlocking

/**
 * Camera vendor initializer for Nikon cameras (VID 0x04B0).
 */
class NikonCameraInitializer : CameraVendorInitializer {

    companion object {
        private const val TAG = "NikonCameraInit"
    }

    private var activeAdapter: NikonPtpAdapter? = null
    private var lastDeviceName: String? = null

    override fun canHandle(vendorId: Int, productId: Int): Boolean {
        return vendorId == NikonPtpConstants.VID_NIKON
    }

    override fun initialize(context: CameraInitContext): CameraInitResult {
        return initializeUnlocked(
            device = context.device,
            connection = context.connection
        )
    }

    fun initializeUnlocked(
        device: UsbDevice,
        connection: UsbDeviceConnection
    ): CameraInitResult {
        Log.d(TAG, ">>> Nikon initializeUnlocked start: device=${device.deviceName}")

        if (activeAdapter?.isSessionOpen == true && lastDeviceName == device.deviceName) {
            Log.d(TAG, "Session already open for device=${device.deviceName}, skipping duplicate handshake")
            return CameraInitResult.ok("Nikon session already initialized")
        }

        val adapter = NikonPtpAdapter(
            device = device,
            connection = connection
        )

        return try {
            val success = runBlocking {
                adapter.startSession()
            }

            if (success) {
                activeAdapter = adapter
                lastDeviceName = device.deviceName
                Log.d(TAG, ">>> Nikon handshake completed successfully")
                CameraInitResult.ok("Nikon handshake completed")
            } else {
                Log.e(TAG, "Nikon handshake failed")
                adapter.close()
                CameraInitResult.fail("Nikon PTP handshake failed")
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Error initializing Nikon camera", t)
            adapter.close()
            clearActiveState()
            CameraInitResult.fail("Nikon initialization error: ${t.message}")
        }
    }

    fun getActiveAdapter(): NikonPtpAdapter? = activeAdapter

    fun hasActiveSessionFor(deviceName: String?): Boolean {
        return activeAdapter?.isSessionOpen == true && lastDeviceName != null && lastDeviceName == deviceName
    }

    fun closeSession(
        device: UsbDevice,
        connection: UsbDeviceConnection
    ) {
        Log.d(TAG, ">>> Nikon closeSession: device=${device.deviceName}")
        val adapter = activeAdapter
        if (adapter != null) {
            runCatching {
                adapter.close()
            }.onFailure {
                Log.w(TAG, "Error closing Nikon adapter", it)
            }
        }
        clearActiveState()
    }

    private fun clearActiveState() {
        activeAdapter = null
        lastDeviceName = null
    }
}
