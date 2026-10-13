package com.rvmeur.transferin.data.usb

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.mtp.MtpDevice

data class CameraInitContext(
    val device: UsbDevice,
    val connection: UsbDeviceConnection,
    val mtpDevice: MtpDevice? = null,
    val usbHelper: UsbHelper
)