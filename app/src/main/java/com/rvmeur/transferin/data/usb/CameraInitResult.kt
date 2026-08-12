package com.rvmeur.transferin.data.usb

data class CameraInitResult(
    val success: Boolean,
    val message: String? = null
) {
    companion object {
        fun ok(message: String? = null) = CameraInitResult(true, message)
        fun fail(message: String) = CameraInitResult(false, message)
    }
}