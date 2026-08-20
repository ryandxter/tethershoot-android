package com.rvmeur.transferin.data.usb.vendor.fuji

import android.util.Log
import com.rvmeur.transferin.data.usb.CameraInitContext
import com.rvmeur.transferin.data.usb.CameraInitResult
import com.rvmeur.transferin.data.usb.vendor.CameraVendorInitializer
import kotlinx.coroutines.runBlocking

/**
 * Camera vendor initializer for Fujifilm cameras (VID 0x04CB).
 * Dispatches connected Fujifilm devices to the native [FujiTetherAdapter] engine.
 */
class FujiCameraInitializer : CameraVendorInitializer {

    companion object {
        private const val TAG = "FujiCameraInit"
    }

    private var activeAdapter: FujiTetherAdapter? = null

    override fun canHandle(vendorId: Int, productId: Int): Boolean {
        return vendorId == FujiPtpConstants.FUJI_VENDOR_ID
    }

    override fun initialize(context: CameraInitContext): CameraInitResult {
        Log.d(TAG, ">>> FujiCameraInitializer starting for device=${context.device.deviceName}")

        return runCatching {
            val adapter = FujiTetherAdapter()
            val initResult = runBlocking {
                adapter.initialize(context.device, context.connection)
            }

            if (initResult.isSuccess) {
                activeAdapter = adapter
                Log.d(TAG, ">>> Fujifilm Native Tether Handshake completed successfully")
                CameraInitResult.ok("Fujifilm Native Tether Session Active")
            } else {
                val error = initResult.exceptionOrNull()
                Log.w(TAG, "Fuji tether initialization failed: ${error?.message}", error)
                CameraInitResult.fail("Fuji tether initialization failed: ${error?.message}")
            }
        }.getOrElse { throwable ->
            Log.e(TAG, "Unexpected error in FujiCameraInitializer", throwable)
            CameraInitResult.fail("Fuji init error: ${throwable.message}")
        }
    }

    fun getActiveAdapter(): FujiTetherAdapter? = activeAdapter

    fun closeActiveSession() {
        activeAdapter?.close()
        activeAdapter = null
    }
}
