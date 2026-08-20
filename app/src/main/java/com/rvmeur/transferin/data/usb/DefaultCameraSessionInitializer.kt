package com.rvmeur.transferin.data.usb

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.mtp.MtpDevice
import android.util.Log
import com.rvmeur.transferin.data.usb.vendor.CameraVendorInitializer
import com.rvmeur.transferin.data.usb.vendor.CanonCameraInitializer
import com.rvmeur.transferin.data.usb.vendor.GenericCameraInitializer
import com.rvmeur.transferin.data.usb.vendor.fuji.FujiCameraInitializer
import com.rvmeur.transferin.data.usb.vendor.nikon.NikonCameraInitializer
import com.rvmeur.transferin.data.usb.vendor.sony.SonyCameraInitializer

class DefaultCameraSessionInitializer(
    private val initializers: List<CameraVendorInitializer> = listOf(
        CanonCameraInitializer(),
        SonyCameraInitializer(),
        NikonCameraInitializer(),
        FujiCameraInitializer(),
        GenericCameraInitializer()
    )
) : CameraSessionInitializer {

    companion object {
        private const val TAG = "DefaultCameraInit"
    }

    private fun findCanonInitializer(): CanonCameraInitializer? {
        return initializers.firstOrNull { it is CanonCameraInitializer } as? CanonCameraInitializer
    }

    fun getCanonInitializer(): CanonCameraInitializer? = findCanonInitializer()

    private fun findSonyInitializer(): SonyCameraInitializer? {
        return initializers.firstOrNull { it is SonyCameraInitializer } as? SonyCameraInitializer
    }

    fun getSonyInitializer(): SonyCameraInitializer? = findSonyInitializer()

    private fun findNikonInitializer(): NikonCameraInitializer? {
        return initializers.firstOrNull { it is NikonCameraInitializer } as? NikonCameraInitializer
    }

    fun getNikonInitializer(): NikonCameraInitializer? = findNikonInitializer()

    private fun findFujiInitializer(): FujiCameraInitializer? {
        return initializers.firstOrNull { it is FujiCameraInitializer } as? FujiCameraInitializer
    }

    fun getFujiInitializer(): FujiCameraInitializer? = findFujiInitializer()

    override fun initialize(
        device: UsbDevice,
        connection: UsbDeviceConnection,
        mtpDevice: MtpDevice,
        usbHelper: UsbHelper
    ): CameraInitResult {
        val context = CameraInitContext(
            device = device,
            connection = connection,
            mtpDevice = mtpDevice,
            usbHelper = usbHelper
        )

        val vendorInitializer = initializers.firstOrNull {
            it !is GenericCameraInitializer &&
                    it.canHandle(device.vendorId, device.productId)
        }

        if (vendorInitializer != null) {
            Log.d(
                TAG,
                "Using vendor initializer: ${vendorInitializer.javaClass.simpleName} " +
                        "for vendorId=${device.vendorId}, productId=${device.productId}"
            )
            return runCatching {
                vendorInitializer.initialize(context)
            }.getOrElse { throwable ->
                Log.e(
                    TAG,
                    "Vendor initializer failed: ${vendorInitializer.javaClass.simpleName}",
                    throwable
                )
                CameraInitResult.fail(
                    "Vendor initializer failed: ${throwable.message ?: "unknown error"}"
                )
            }
        }

        val genericInitializer = initializers.firstOrNull { it is GenericCameraInitializer }
            ?: GenericCameraInitializer()

        Log.d(
            TAG,
            "No vendor-specific initializer for vendorId=${device.vendorId}, " +
                    "falling back to ${genericInitializer.javaClass.simpleName}"
        )

        return runCatching {
            genericInitializer.initialize(context)
        }.getOrElse { throwable ->
            Log.e(TAG, "Generic initializer failed", throwable)
            CameraInitResult.fail(
                "Generic initializer failed: ${throwable.message ?: "unknown error"}"
            )
        }
    }
}