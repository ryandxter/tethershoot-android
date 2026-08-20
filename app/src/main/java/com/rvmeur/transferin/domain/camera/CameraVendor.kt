package com.rvmeur.transferin.domain.camera

/**
 * Camera hardware vendor identifier and metadata.
 */
enum class CameraVendor(val vendorId: Int, val displayName: String) {
    FUJIFILM(0x04CB, "Fujifilm"),
    CANON(0x04A9, "Canon"),
    SONY(0x054C, "Sony"),
    NIKON(0x04B0, "Nikon"),
    GENERIC(0x0000, "Generic MTP");

    companion object {
        fun fromVendorId(vendorId: Int): CameraVendor =
            entries.firstOrNull { it.vendorId == vendorId } ?: GENERIC
    }
}
