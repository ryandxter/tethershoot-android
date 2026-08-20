package com.rvmeur.transferin.data.usb.vendor.sony

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.util.Log
import com.rvmeur.transferin.data.usb.CameraInitContext
import com.rvmeur.transferin.data.usb.CameraInitResult
import com.rvmeur.transferin.data.usb.vendor.CameraVendorInitializer
import kotlinx.coroutines.runBlocking

/**
 * Camera vendor initializer for Sony cameras (VID 0x054C).
 */
class SonyCameraInitializer : CameraVendorInitializer {

    companion object {
        private const val TAG = "SonyCameraInit"
    }

    private var activeAdapter: SonyPtpAdapter? = null
    private var lastDeviceName: String? = null

    override fun canHandle(vendorId: Int, productId: Int): Boolean {
        return vendorId == SonyPtpConstants.VID_SONY
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
        Log.d(TAG, ">>> Sony initializeUnlocked start: device=${device.deviceName}")

        if (activeAdapter?.isSessionOpen == true && lastDeviceName == device.deviceName) {
            Log.d(TAG, "Session already open for device=${device.deviceName}, skipping duplicate handshake")
            return CameraInitResult.ok("Sony session already initialized")
        }

        val adapter = SonyPtpAdapter(
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
                Log.d(TAG, ">>> Sony handshake completed successfully")
                CameraInitResult.ok("Sony handshake completed")
            } else {
                Log.e(TAG, "Sony handshake failed")
                adapter.close()
                CameraInitResult.fail("Sony SDIO handshake failed")
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Error initializing Sony camera", t)
            adapter.close()
            clearActiveState()
            CameraInitResult.fail("Sony initialization error: ${t.message}")
        }
    }

    fun getActiveAdapter(): SonyPtpAdapter? = activeAdapter

    fun hasActiveSessionFor(deviceName: String?): Boolean {
        return activeAdapter?.isSessionOpen == true && lastDeviceName != null && lastDeviceName == deviceName
    }

    fun closeSession(
        device: UsbDevice,
        connection: UsbDeviceConnection
    ) {
        Log.d(TAG, ">>> Sony closeSession: device=${device.deviceName}")
        val adapter = activeAdapter
        if (adapter != null) {
            runCatching {
                adapter.close()
            }.onFailure {
                Log.w(TAG, "Error closing Sony adapter", it)
            }
        }
        clearActiveState()
    }

    private fun clearActiveState() {
        activeAdapter = null
        lastDeviceName = null
    }
}
