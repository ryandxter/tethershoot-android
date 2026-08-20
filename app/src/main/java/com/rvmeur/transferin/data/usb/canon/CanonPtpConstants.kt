package com.rvmeur.transferin.data.usb.canon

/**
 * Canon EOS vendor-specific PTP protocol constants, operation codes, event codes,
 * response codes, and container definitions for DIGIC image processors.
 */
object CanonPtpConstants {

    // Canon USB Vendor ID
    const val CANON_VENDOR_ID = 0x04A9
    const val VID_CANON = CANON_VENDOR_ID

    // Standard PTP Container Types
    const val CONTAINER_UNDEFINED = 0
    const val CONTAINER_COMMAND = 1
    const val CONTAINER_DATA = 2
    const val CONTAINER_RESPONSE = 3
    const val CONTAINER_EVENT = 4

    // Standard PTP Operation Codes (ISO 15740)
    const val OC_UNDEFINED = 0x1000
    const val OC_GET_DEVICE_INFO = 0x1001
    const val OC_OPEN_SESSION = 0x1002
    const val OC_CLOSE_SESSION = 0x1003
    const val OC_GET_STORAGE_IDS = 0x1004
    const val OC_GET_STORAGE_INFO = 0x1005
    const val OC_GET_NUM_OBJECTS = 0x1006
    const val OC_GET_OBJECT_HANDLES = 0x1007
    const val OC_GET_OBJECT_INFO = 0x1008
    const val OC_GET_OBJECT = 0x1009
    const val OC_GET_THUMB = 0x100A
    const val OC_GET_PARTIAL_OBJECT = 0x101B

    // Canon EOS Vendor Extension Operation Codes
    const val EOS_OC_GET_STORAGE_IDS = 0x9101
    const val EOS_OC_GET_STORAGE_INFO = 0x9102
    const val EOS_OC_GET_OBJECT_INFO = 0x9108
    const val EOS_OC_GET_OBJECT = 0x9107
    const val EOS_OC_SET_REMOTE_MODE = 0x9114   // Param: 1 = Remote Mode ON, 0 = OFF
    const val EOS_OC_SET_EVENT_MODE = 0x9115    // Param: 1 = Event Mode ON, 0 = OFF
    const val EOS_OC_GET_EVENT = 0x9116         // Polls DIGIC event ring buffer
    const val EOS_OC_TRANSFER_COMPLETE = 0x9117
    const val EOS_OC_RESET_TRANSFER = 0x9119
    const val EOS_OC_PCLK_INFO = 0x911A
    const val EOS_OC_SET_EVENT_PROC_MODE = 0x9127

    // Canon EOS DIGIC Event Codes
    const val EOS_EC_OBJECT_ADDED = 0xC101         // Object created/added on card
    const val EOS_EC_OBJECT_REMOVED = 0xC102       // Object removed
    const val EOS_EC_PROPERTY_CHANGED = 0xC103     // Device property changed
    const val EOS_EC_DEV_INFO_CHANGED = 0xC104     // Device info changed
    const val EOS_EC_STORAGE_ADDED = 0xC105        // Storage added
    const val EOS_EC_STORAGE_REMOVED = 0xC106      // Storage removed
    const val EOS_EC_SHUTTER_RELEASED = 0xC108     // Camera shutter released
    const val EOS_EC_BULB_EXPOSURE_TIME = 0xC10A   // Bulb exposure time update
    const val EOS_EC_STORAGE_INFO_CHANGED = 0xC10C // Storage info changed

    // Standard PTP Event Codes
    const val PTP_EC_OBJECT_ADDED = 0x4002         // Standard PTP ObjectAdded

    // PTP Response Codes
    const val RC_OK = 0x2001
    const val RC_GENERAL_ERROR = 0x2002
    const val RC_SESSION_NOT_OPEN = 0x2003
    const val RC_INVALID_TRANSACTION_ID = 0x2004
    const val RC_OPERATION_NOT_SUPPORTED = 0x2005
    const val RC_DEVICE_BUSY = 0x2019
    const val RC_TRANSACTION_CANCELLED = 0x201F

    // Mode Values
    const val REMOTE_MODE_ON = 1
    const val REMOTE_MODE_OFF = 0
    const val EVENT_MODE_ON = 1
    const val EVENT_MODE_OFF = 0

    // Common Format Codes
    const val FORMAT_EXIF_JPEG = 0x3801
    const val FORMAT_CR3_RAW = 0xB103
    const val FORMAT_CR2_RAW = 0xB101
    const val FORMAT_ASSOCIATION = 0x3001

    // Storage Types
    const val STORAGE_TYPE_UNDEFINED = 0x0000
    const val STORAGE_TYPE_FIXED_ROM = 0x0001
    const val STORAGE_TYPE_REMOVABLE_ROM = 0x0002
    const val STORAGE_TYPE_FIXED_RAM = 0x0003
    const val STORAGE_TYPE_REMOVABLE_RAM = 0x0004

    // Storage Slot Indices
    const val SLOT_1_CFEXPRESS = 0
    const val SLOT_2_SD = 1

    // Defaults
    const val DEFAULT_SESSION_ID = 1
    const val DEFAULT_TIMEOUT_MS = 5000
    const val EVENT_POLL_INTERVAL_MS = 40L
    const val EVENT_BUSY_BACKOFF_MS = 25L
}
