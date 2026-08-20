package com.rvmeur.transferin.data.usb.vendor.nikon

object NikonPtpConstants {
    const val VID_NIKON = 0x04B0

    // PTP Container Types
    const val CONTAINER_COMMAND = 1
    const val CONTAINER_DATA = 2
    const val CONTAINER_RESPONSE = 3
    const val CONTAINER_EVENT = 4

    // Standard PTP Operation Codes
    const val OC_OPEN_SESSION = 0x1002
    const val OC_CLOSE_SESSION = 0x1003
    const val OC_GET_STORAGE_IDS = 0x1004
    const val OC_GET_STORAGE_INFO = 0x1005
    const val OC_GET_OBJECT_HANDLES = 0x1007
    const val OC_GET_OBJECT_INFO = 0x1008
    const val OC_GET_OBJECT = 0x1009
    const val OC_GET_THUMB = 0x100A
    const val OC_GET_DEVICE_PROP_DESC = 0x1014
    const val OC_GET_DEVICE_PROP_VALUE = 0x1015
    const val OC_SET_DEVICE_PROP_VALUE = 0x1016

    // Nikon Vendor Operation Codes
    const val OC_NIKON_CHECK_EVENT = 0x90C0
    const val OC_NIKON_SET_EVENT_MODE = 0x90C1
    const val OC_NIKON_SEND_EVENT = 0x90C2
    const val OC_NIKON_KEEP_ALIVE = 0x90C3
    const val OC_NIKON_START_LIVE_VIEW = 0x9201
    const val OC_NIKON_END_LIVE_VIEW = 0x9202
    const val OC_NIKON_GET_LIVE_VIEW_IMAGE = 0x9203
    const val OC_NIKON_DEVICE_READY = 0x90C8

    // Response Codes
    const val RC_OK = 0x2001
    const val RC_GENERAL_ERROR = 0x2002
    const val RC_SESSION_NOT_OPEN = 0x2003
    const val RC_INVALID_TRANSACTION_ID = 0x2004
    const val RC_OPERATION_NOT_SUPPORTED = 0x2005
    const val RC_PARAMETER_NOT_SUPPORTED = 0x2006
    const val RC_DEVICE_BUSY = 0x2019
    const val RC_NIKON_HARDWARE_ERROR = 0xA001

    // Event Modes
    const val EVENT_MODE_ON = 0x0001
    const val EVENT_MODE_OFF = 0x0000

    // Event Codes
    const val EC_OBJECT_ADDED = 0x4002
    const val EC_OBJECT_REMOVED = 0x4003
    const val EC_STORE_ADDED = 0x4004
    const val EC_STORE_REMOVED = 0x4005
    const val EC_DEVICE_PROP_CHANGED = 0x4006
    const val EC_NIKON_OBJECT_ADDED_IN_SDRAM = 0xC101
    const val EC_NIKON_CAPTURE_COMPLETE_RECALCULATE = 0xC102
    const val EC_NIKON_RECORDING_INTERRUPTED = 0xC103

    // Storage IDs
    const val STORAGE_SLOT_1_PRIMARY = 0x00010001
    const val STORAGE_SLOT_2_SECONDARY = 0x00020001
    const val STORAGE_INTERNAL_RAM = 0x00030001
    const val STORAGE_ALL = 0x00000000
    const val STORAGE_ALL_ALT = 0xFFFFFFFF.toInt()

    const val DEFAULT_SESSION_ID = 1
    const val DEFAULT_TIMEOUT_MS = 5000
    const val EVENT_POLL_INTERVAL_MS = 25L
}
