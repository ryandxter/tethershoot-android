package com.rvmeur.transferin.data.usb.vendor.sony

object SonyPtpConstants {
    const val VID_SONY = 0x054C

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

    // Sony SDIO PTP Vendor Operation Codes
    const val OC_SONY_SDIO_CONNECT = 0x9280
    const val OC_SONY_GET_SDIO_EXT_DEVICE_INFO = 0x9281
    const val OC_SONY_SDIO_CLOSE = 0x9282

    // Sony Property Operation Codes
    const val OC_SONY_GET_DEVICE_PROP_DESC = 0x9801
    const val OC_SONY_GET_DEVICE_PROP_VALUE = 0x9802
    const val OC_SONY_SET_DEVICE_PROP_VALUE = 0x9803
    const val OC_SONY_GET_ALL_DEVICE_PROP_DATA = 0x9805

    // Standard & Sony Response Codes
    const val RC_OK = 0x2001
    const val RC_GENERAL_ERROR = 0x2002
    const val RC_SESSION_NOT_OPEN = 0x2003
    const val RC_INVALID_TRANSACTION_ID = 0x2004
    const val RC_OPERATION_NOT_SUPPORTED = 0x2005
    const val RC_PARAMETER_NOT_SUPPORTED = 0x2006
    const val RC_DEVICE_BUSY = 0x2019
    const val RC_SONY_REJECTED = 0xA001

    // Sony Device Property Codes
    const val DPC_SAVE_MEDIA = 0xD2C1
    const val DPC_SAVE_DESTINATION_ALT = 0xD2C2
    const val DPC_FOCUS_STATUS = 0xD2C3
    const val DPC_SHUTTER_BUTTON = 0xD2C7

    // Save Destination Property Values
    const val SAVE_DEST_PC_ONLY = 0x0001
    const val SAVE_DEST_PC_AND_CAMERA = 0x0002
    const val SAVE_DEST_CAMERA_ONLY = 0x0003

    // Handshake Constants
    const val SDIO_CONNECT_PHASE_1 = 1
    const val SDIO_CONNECT_PHASE_2 = 2
    const val DEFAULT_SESSION_ID = 1
    const val DEFAULT_TIMEOUT_MS = 5000
    const val STREAM_TIMEOUT_MS = 15000
}
