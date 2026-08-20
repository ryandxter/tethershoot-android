package com.rvmeur.transferin.data.usb.vendor.sony

data class ArwPreviewInfo(
    val previewOffset: Int,
    val previewLength: Int,
    val width: Int = 0,
    val height: Int = 0,
    val isJpegValid: Boolean = true
)
