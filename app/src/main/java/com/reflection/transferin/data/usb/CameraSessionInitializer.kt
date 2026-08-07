package com.reflection.transferin.data.usb

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.mtp.MtpDevice

interface CameraSessionInitializer {
    fun initialize(
        device: UsbDevice,
        connection: UsbDeviceConnection,
        mtpDevice: MtpDevice,
        usbHelper: UsbHelper
    ): CameraInitResult
}