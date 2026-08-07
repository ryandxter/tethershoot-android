package com.reflection.transferin.data.usb.vendor

import com.reflection.transferin.data.usb.CameraInitContext
import com.reflection.transferin.data.usb.CameraInitResult

interface CameraVendorInitializer {
    fun canHandle(vendorId: Int, productId: Int): Boolean
    fun initialize(context: CameraInitContext): CameraInitResult
}