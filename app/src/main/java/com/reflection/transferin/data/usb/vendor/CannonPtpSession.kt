package com.reflection.transferin.data.usb.vendor

import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface

data class CanonPtpSession(
    val deviceName: String,
    val connection: UsbDeviceConnection,
    val usbInterface: UsbInterface,
    val outEndpoint: UsbEndpoint,
    val inEndpoint: UsbEndpoint,
    val sessionId: Int,
    val nextTransactionId: Int
)