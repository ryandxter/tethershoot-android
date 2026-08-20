package com.rvmeur.transferin.data.usb.vendor

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.util.Log
import com.rvmeur.transferin.data.usb.canon.CanonPtpAdapter
import com.rvmeur.transferin.data.usb.canon.CanonPtpConstants
import com.rvmeur.transferin.data.usb.core.DirectByteBufferPool
import com.rvmeur.transferin.data.usb.vendor.nikon.NikonPtpAdapter
import com.rvmeur.transferin.data.usb.vendor.nikon.NikonPtpConstants
import com.rvmeur.transferin.data.usb.vendor.sony.SonyPtpAdapter
import com.rvmeur.transferin.data.usb.vendor.sony.SonyPtpConstants

/**
 * Multi-Vendor Camera Tether Adapter Factory.
 *
 * Dynamically resolves and instantiates the appropriate [CameraTetherAdapter] for a connected
 * camera based on its USB Vendor ID (Sony Alpha, Nikon, Canon, etc.).
 */
class CameraTetherAdapterFactory {

    companion object {
        private const val TAG = "CameraAdapterFactory"
        val Default: CameraTetherAdapterFactory by lazy { CameraTetherAdapterFactory() }
    }

    /**
     * Creates a vendor-specific [CameraTetherAdapter] for the given [device] and [connection].
     */
    fun createAdapter(
        device: UsbDevice,
        connection: UsbDeviceConnection,
        bufferPool: DirectByteBufferPool = DirectByteBufferPool.Default
    ): CameraTetherAdapter? {
        Log.d(TAG, "Creating tether adapter for vendorId=0x${device.vendorId.toString(16)}, productId=0x${device.productId.toString(16)}")

        return when (device.vendorId) {
            SonyPtpConstants.VID_SONY -> {
                Log.i(TAG, "Instantiating SonyPtpAdapter for ${device.deviceName}")
                SonyPtpAdapter(
                    device = device,
                    connection = connection,
                    bufferPool = bufferPool
                )
            }

            NikonPtpConstants.VID_NIKON -> {
                Log.i(TAG, "Instantiating NikonPtpAdapter for ${device.deviceName}")
                NikonPtpAdapter(
                    device = device,
                    connection = connection,
                    bufferPool = bufferPool
                )
            }

            CanonPtpConstants.CANON_VENDOR_ID -> {
                Log.i(TAG, "Instantiating CanonPtpAdapter for ${device.deviceName}")
                CanonPtpAdapter.create(
                    device = device,
                    connection = connection,
                    bufferPool = bufferPool
                )
            }

            else -> {
                Log.w(TAG, "No vendor-specific tether adapter available for vendorId=0x${device.vendorId.toString(16)}")
                null
            }
        }
    }
}
