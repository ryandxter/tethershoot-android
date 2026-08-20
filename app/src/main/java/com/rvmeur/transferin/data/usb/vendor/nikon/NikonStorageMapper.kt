package com.rvmeur.transferin.data.usb.vendor.nikon

/**
 * Logical slots for Nikon dual-slot camera storage engines.
 */
enum class NikonStorageSlot {
    SLOT_1,
    SLOT_2,
    INTERNAL,
    ALL_STORAGES,
    UNKNOWN
}

data class NikonStorageInfo(
    val storageId: Int,
    val slot: NikonStorageSlot,
    val label: String
)

/**
 * Nikon Dual-Slot Storage Mapper.
 *
 * Discretely maps Nikon storage IDs to independent card slots (Slot 1 CFexpress/SD, Slot 2 SD),
 * preventing object handle collisions during dual-card mirror or RAW+JPEG shooting.
 */
class NikonStorageMapper {

    /**
     * Resolves the [NikonStorageSlot] for a given 32-bit PTP storage ID.
     */
    fun mapStorageId(storageId: Int): NikonStorageSlot {
        if (storageId == NikonPtpConstants.STORAGE_ALL || storageId == NikonPtpConstants.STORAGE_ALL_ALT) {
            return NikonStorageSlot.ALL_STORAGES
        }

        val highWord = (storageId ushr 16) and 0xFFFF
        return when (highWord) {
            0x0001 -> NikonStorageSlot.SLOT_1
            0x0002 -> NikonStorageSlot.SLOT_2
            0x0003 -> NikonStorageSlot.INTERNAL
            else -> {
                when (storageId) {
                    NikonPtpConstants.STORAGE_SLOT_1_PRIMARY -> NikonStorageSlot.SLOT_1
                    NikonPtpConstants.STORAGE_SLOT_2_SECONDARY -> NikonStorageSlot.SLOT_2
                    NikonPtpConstants.STORAGE_INTERNAL_RAM, 0x00000001 -> NikonStorageSlot.INTERNAL
                    else -> NikonStorageSlot.UNKNOWN
                }
            }
        }
    }

    /**
     * Resolves a human-readable display label for a storage ID.
     */
    fun resolveSlotLabel(storageId: Int): String {
        return when (mapStorageId(storageId)) {
            NikonStorageSlot.SLOT_1 -> "Card Slot 1 (Primary)"
            NikonStorageSlot.SLOT_2 -> "Card Slot 2 (Secondary)"
            NikonStorageSlot.INTERNAL -> "Camera Internal Buffer"
            NikonStorageSlot.ALL_STORAGES -> "All Storage Slots"
            NikonStorageSlot.UNKNOWN -> "Storage (0x${storageId.toString(16)})"
        }
    }

    /**
     * Creates a [NikonStorageInfo] metadata object for a storage ID.
     */
    fun getStorageInfo(storageId: Int): NikonStorageInfo {
        val slot = mapStorageId(storageId)
        val label = resolveSlotLabel(storageId)
        return NikonStorageInfo(
            storageId = storageId,
            slot = slot,
            label = label
        )
    }

    /**
     * Partitions a mapping of storageId -> list of object handles into distinct slots,
     * maintaining separate namespaces to prevent collisions.
     */
    fun partitionHandles(handlesByStorage: Map<Int, List<Int>>): Map<NikonStorageSlot, List<Int>> {
        val partitioned = mutableMapOf<NikonStorageSlot, MutableList<Int>>()

        for ((storageId, handles) in handlesByStorage) {
            val slot = mapStorageId(storageId)
            val slotList = partitioned.getOrPut(slot) { mutableListOf() }
            slotList.addAll(handles)
        }

        return partitioned
    }
}
