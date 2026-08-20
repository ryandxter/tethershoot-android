package com.rvmeur.transferin.data.usb.canon

/**
 * Descriptor for Canon camera storage media (e.g. CFexpress or SD card slots).
 *
 * @property storageId Unique 32-bit PTP storage ID (e.g., 0x00010001 for Slot 1, 0x00010002 for Slot 2).
 * @property storageType PTP storage type (Fixed ROM, Removable RAM, etc.).
 * @property fileSystemType PTP file system type (Generic Hierarchical, DCF, etc.).
 * @property accessCapability PTP access capability (Read-Write, Read-Only, etc.).
 * @property maxCapacityBytes Maximum capacity of the storage volume in bytes.
 * @property freeSpaceBytes Available free space on the storage volume in bytes.
 * @property storageDescription Textual descriptor reported by camera firmware (e.g., "CFexpress", "SD").
 * @property volumeLabel Volume label of the storage partition.
 * @property slotIndex Slot index: 0 for Slot 1 (CFexpress), 1 for Slot 2 (SD).
 */
data class CanonStorageInfo(
    val storageId: Int,
    val storageType: Int,
    val fileSystemType: Int,
    val accessCapability: Int,
    val maxCapacityBytes: Long,
    val freeSpaceBytes: Long,
    val storageDescription: String,
    val volumeLabel: String,
    val slotIndex: Int // 0 for Slot 1 (CFexpress), 1 for Slot 2 (SD)
)
