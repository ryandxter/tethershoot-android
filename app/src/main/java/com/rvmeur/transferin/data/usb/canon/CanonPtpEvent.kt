package com.rvmeur.transferin.data.usb.canon

/**
 * Canon EOS PTP Events parsed from continuous DIGIC event pump (0x9116 EOS_GetEvent)
 * or standard PTP interrupt pipe notifications.
 */
sealed class CanonPtpEvent {
    /**
     * Emitted when a new object/photo is written to storage media (EOS_EC_OBJECT_ADDED 0xC101 or PTP_EC_OBJECT_ADDED 0x4002).
     */
    data class ObjectAdded(
        val handle: Int,
        val storageId: Int,
        val formatCode: Int
    ) : CanonPtpEvent()

    /**
     * Emitted when an object is removed from storage (EOS_EC_OBJECT_REMOVED 0xC102).
     */
    data class ObjectRemoved(
        val handle: Int
    ) : CanonPtpEvent()

    /**
     * Emitted when a camera property or setting changes (EOS_EC_PROPERTY_CHANGED 0xC103).
     */
    data class PropertyChanged(
        val propCode: Int,
        val value: ByteArray?
    ) : CanonPtpEvent() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as PropertyChanged
            if (propCode != other.propCode) return false
            if (value != null) {
                if (other.value == null) return false
                if (!value.contentEquals(other.value)) return false
            } else if (other.value != null) return false
            return true
        }

        override fun hashCode(): Int {
            var result = propCode
            result = 31 * result + (value?.contentHashCode() ?: 0)
            return result
        }
    }

    /**
     * Emitted when storage configuration changes (EOS_EC_STORAGE_ADDED 0xC105, 0xC106, 0xC10C).
     */
    data class StorageChanged(
        val storageId: Int
    ) : CanonPtpEvent()

    /**
     * Emitted when shutter release sequence finishes (EOS_EC_SHUTTER_RELEASED 0xC108).
     */
    data class ShutterReleased(
        val seqNumber: Int
    ) : CanonPtpEvent()

    /**
     * Emitted when device info or capabilities change (EOS_EC_DEV_INFO_CHANGED 0xC104).
     */
    object DeviceInfoChanged : CanonPtpEvent()
}
