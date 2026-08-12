package com.rvmeur.transferin.data.usb.vendor

import com.rvmeur.transferin.data.usb.CameraInitContext
import com.rvmeur.transferin.data.usb.CameraInitResult

interface CameraVendorInitializer {
    fun canHandle(vendorId: Int, productId: Int): Boolean
    fun initialize(context: CameraInitContext): CameraInitResult
}