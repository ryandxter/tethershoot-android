package com.rvmeur.transferin.data.usb.vendor.nikon

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NikonStorageMapperTest {

    private lateinit var mapper: NikonStorageMapper

    @Before
    fun setUp() {
        mapper = NikonStorageMapper()
    }

    @Test
    fun `mapStorageId maps Slot 1 Slot 2 and Internal accurately`() {
        assertEquals(NikonStorageSlot.SLOT_1, mapper.mapStorageId(NikonPtpConstants.STORAGE_SLOT_1_PRIMARY))
        assertEquals(NikonStorageSlot.SLOT_1, mapper.mapStorageId(0x00010002))
        assertEquals(NikonStorageSlot.SLOT_2, mapper.mapStorageId(NikonPtpConstants.STORAGE_SLOT_2_SECONDARY))
        assertEquals(NikonStorageSlot.SLOT_2, mapper.mapStorageId(0x00020002))
        assertEquals(NikonStorageSlot.INTERNAL, mapper.mapStorageId(NikonPtpConstants.STORAGE_INTERNAL_RAM))
        assertEquals(NikonStorageSlot.ALL_STORAGES, mapper.mapStorageId(NikonPtpConstants.STORAGE_ALL))
        assertEquals(NikonStorageSlot.ALL_STORAGES, mapper.mapStorageId(NikonPtpConstants.STORAGE_ALL_ALT))
        assertEquals(NikonStorageSlot.UNKNOWN, mapper.mapStorageId(0x12345678))
    }

    @Test
    fun `partitionHandles separates handles without collisions`() {
        val input = mapOf(
            NikonPtpConstants.STORAGE_SLOT_1_PRIMARY to listOf(101, 102),
            NikonPtpConstants.STORAGE_SLOT_2_SECONDARY to listOf(201, 202)
        )

        val partitioned = mapper.partitionHandles(input)

        assertEquals(2, partitioned.size)
        assertEquals(listOf(101, 102), partitioned[NikonStorageSlot.SLOT_1])
        assertEquals(listOf(201, 202), partitioned[NikonStorageSlot.SLOT_2])
    }

    @Test
    fun `resolveSlotLabel returns human-readable descriptions`() {
        assertTrue(mapper.resolveSlotLabel(NikonPtpConstants.STORAGE_SLOT_1_PRIMARY).contains("Slot 1"))
        assertTrue(mapper.resolveSlotLabel(NikonPtpConstants.STORAGE_SLOT_2_SECONDARY).contains("Slot 2"))
    }
}
