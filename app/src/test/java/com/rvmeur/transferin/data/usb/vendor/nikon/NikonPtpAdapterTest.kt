package com.rvmeur.transferin.data.usb.vendor.nikon

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import com.rvmeur.transferin.data.usb.core.DirectByteBufferPool
import com.rvmeur.transferin.data.usb.core.SafeUsbTransportManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.ArrayDeque

class NikonPtpAdapterTest {

    private lateinit var device: UsbDevice
    private lateinit var connection: UsbDeviceConnection
    private lateinit var usbInterface: UsbInterface
    private lateinit var endpointOut: UsbEndpoint
    private lateinit var endpointIn: UsbEndpoint
    private lateinit var transportManager: SafeUsbTransportManager
    private lateinit var adapter: NikonPtpAdapter

    private val inPackets = ArrayDeque<ByteArray>()

    @Before
    fun setUp() {
        device = mockk(relaxed = true)
        connection = mockk(relaxed = true)
        usbInterface = mockk(relaxed = true)
        endpointOut = mockk(relaxed = true)
        endpointIn = mockk(relaxed = true)

        every { device.vendorId } returns NikonPtpConstants.VID_NIKON
        every { device.deviceName } returns "Nikon_Z8"
        every { connection.fileDescriptor } returns 20
        every { connection.claimInterface(any(), any()) } returns true
        every { connection.releaseInterface(any()) } returns true

        every { endpointOut.type } returns UsbConstants.USB_ENDPOINT_XFER_BULK
        every { endpointOut.direction } returns UsbConstants.USB_DIR_OUT
        every { endpointIn.type } returns UsbConstants.USB_ENDPOINT_XFER_BULK
        every { endpointIn.direction } returns UsbConstants.USB_DIR_IN

        every { connection.bulkTransfer(endpointOut, any<ByteArray>(), any(), any(), any()) } answers {
            val length = arg<Int>(3)
            length
        }

        every { connection.bulkTransfer(endpointIn, any<ByteArray>(), any(), any(), any()) } answers {
            val buffer = arg<ByteArray>(1)
            val offset = arg<Int>(2)
            val length = arg<Int>(3)

            if (inPackets.isNotEmpty()) {
                val nextPacket = inPackets.pollFirst()!!
                val copyLen = minOf(nextPacket.size, length)
                System.arraycopy(nextPacket, 0, buffer, offset, copyLen)
                copyLen
            } else {
                0
            }
        }

        transportManager = SafeUsbTransportManager(connection)

        adapter = NikonPtpAdapter(
            device = device,
            connection = connection,
            bufferPool = DirectByteBufferPool.Default,
            retryPolicy = NikonRetryPolicy(NikonRetryConfig(initialDelayMs = 5L, maxRetries = 3)),
            transportManagerOverride = transportManager,
            outEndpointOverride = endpointOut,
            inEndpointOverride = endpointIn,
            interfaceOverride = usbInterface
        )
    }

    @org.junit.After
    fun tearDown() {
        runCatching { adapter.close() }
    }

    private fun queueResponse(code: Int, txId: Int, params: List<Int> = emptyList()) {
        val len = 12 + params.size * 4
        val bb = ByteBuffer.allocate(len).order(ByteOrder.LITTLE_ENDIAN)
        bb.putInt(len)
        bb.putShort(NikonPtpConstants.CONTAINER_RESPONSE.toShort())
        bb.putShort(code.toShort())
        bb.putInt(txId)
        params.forEach { bb.putInt(it) }
        inPackets.add(bb.array())
    }

    private fun queueData(opCode: Int, txId: Int, data: ByteArray) {
        val len = 12 + data.size
        val bb = ByteBuffer.allocate(len).order(ByteOrder.LITTLE_ENDIAN)
        bb.putInt(len)
        bb.putShort(NikonPtpConstants.CONTAINER_DATA.toShort())
        bb.putShort(opCode.toShort())
        bb.putInt(txId)
        bb.put(data)
        inPackets.add(bb.array())
    }

    @Test
    fun `startSession executes OpenSession and SetEventMode(ON)`() = runTest {
        // Step 1: OpenSession tx=1 -> RC_OK
        queueResponse(NikonPtpConstants.RC_OK, 1)
        // Step 2: SetEventMode(ON) tx=2 -> RC_OK
        queueResponse(NikonPtpConstants.RC_OK, 2)

        val success = adapter.startSession()

        assertTrue(success)
        assertTrue(adapter.isSessionOpen)
        verify(atLeast = 1) { connection.claimInterface(any(), any()) }
    }

    @Test
    fun `event polling receives and emits Nikon ObjectAdded event`() = kotlinx.coroutines.runBlocking {
        // Handshake
        queueResponse(NikonPtpConstants.RC_OK, 1)
        queueResponse(NikonPtpConstants.RC_OK, 2)

        // Polled CheckEvent tx=3: 1 event record (0x4002 ObjectAdded, handle 0x10005)
        val eventPayload = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
            .putShort(1.toShort()) // 1 event record
            .putShort(NikonPtpConstants.EC_OBJECT_ADDED.toShort())
            .putInt(0x10005) // object handle
            .array()
        queueData(NikonPtpConstants.OC_NIKON_CHECK_EVENT, 3, eventPayload)
        queueResponse(NikonPtpConstants.RC_OK, 3)

        val received = kotlinx.coroutines.CompletableDeferred<NikonPtpEvent>()
        val job = launch(Dispatchers.Default) {
            received.complete(adapter.events.first())
        }

        val success = adapter.startSession()
        assertTrue(success)

        val event = withTimeout(3000L) {
            received.await()
        }

        assertNotNull(event)
        assertEquals(NikonPtpConstants.EC_OBJECT_ADDED, event.eventCode)
        assertEquals(0x10005, event.param1)
        job.cancel()
    }

    @Test
    fun `getStorageIds resolves discrete dual slots Slot 1 and Slot 2`() = runTest {
        val payload = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(2) // 2 storage IDs
            .putInt(NikonPtpConstants.STORAGE_SLOT_1_PRIMARY)
            .putInt(NikonPtpConstants.STORAGE_SLOT_2_SECONDARY)
            .array()
        queueData(NikonPtpConstants.OC_GET_STORAGE_IDS, 1, payload)
        queueResponse(NikonPtpConstants.RC_OK, 1)

        val storageIds = adapter.getStorageIds()
        assertEquals(2, storageIds.size)
        assertEquals(NikonPtpConstants.STORAGE_SLOT_1_PRIMARY, storageIds[0])
        assertEquals(NikonPtpConstants.STORAGE_SLOT_2_SECONDARY, storageIds[1])

        val slot1 = adapter.storageMapper.mapStorageId(storageIds[0])
        val slot2 = adapter.storageMapper.mapStorageId(storageIds[1])
        assertEquals(NikonStorageSlot.SLOT_1, slot1)
        assertEquals(NikonStorageSlot.SLOT_2, slot2)
    }

    @Test
    fun `closeSession turns off event mode and closes session cleanly`() = runTest {
        // Handshake
        queueResponse(NikonPtpConstants.RC_OK, 1)
        queueResponse(NikonPtpConstants.RC_OK, 2)
        adapter.startSession()

        // Teardown
        queueResponse(NikonPtpConstants.RC_OK, 3) // SetEventMode(OFF)
        queueResponse(NikonPtpConstants.RC_OK, 4) // CloseSession

        adapter.closeSession()

        verify(atLeast = 1) { connection.releaseInterface(usbInterface) }
    }
}
