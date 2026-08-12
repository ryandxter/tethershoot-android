package com.rvmeur.transferin.data.usb.vendor

import android.util.Log
import com.rvmeur.transferin.data.usb.CameraInitContext
import com.rvmeur.transferin.data.usb.CameraInitResult

/**
 * Generic initializer - fallback untuk device yang tidak punya vendor-specific initializer.
 *
 * CRITICAL: canHandle() harus return false agar tidak mengoverride initializer lain.
 * Initializer ini hanya sebagai fallback.
 */
class GenericCameraInitializer : CameraVendorInitializer {

    companion object {
        private const val TAG = "GenericCameraInit"
    }

    /**
     * Return false agar tidak mengoverride initializer lain.
     * GenericCameraInitializer hanya digunakan jika tidak ada initializer lain yang match.
     */
    override fun canHandle(vendorId: Int, productId: Int): Boolean = false

    override fun initialize(context: CameraInitContext): CameraInitResult {
        Log.d(
            TAG,
            "Generic init called for vendorId=${context.device.vendorId}, productId=${context.device.productId}"
        )
        // Generic tidak melakukan apapun - kamera tidak di-unlock
        // Ini fallback, tapi seharusnya tidak pernah dipanggil karena canHandle=false
        return CameraInitResult.ok("Generic initializer - no vendor-specific unlock")
    }
}
