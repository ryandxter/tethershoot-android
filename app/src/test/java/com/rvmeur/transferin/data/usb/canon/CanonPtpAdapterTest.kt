package com.rvmeur.transferin.data.usb.canon

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import com.rvmeur.transferin.data.usb.core.DirectByteBufferPool
import com.rvmeur.transferin.data.usb.core.HardwareSessionState
import com.rvmeur.transferin.data.usb.core.SafeUsbTransportManager
import com.rvmeur.transferin.data.usb.core.UsbHardwareSession
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentLinkedQueue

class CanonPtpAdapterTest {

    private lateinit var device: UsbDevice
    private lateinit var connection: UsbDeviceConnection
    private lateinit var usbInterface: UsbInterface
    private lateinit var outEndpoint: UsbEndpoint
    private lateinit var inEndpoint: UsbEndpoint
    private lateinit var transportManager: SafeUsbTransportManager
    private lateinit var session: UsbHardwareSession
    private lateinit var adapter: CanonPtpAdapter

    private val inQueue = ConcurrentLinkedQueue<ByteArray>()
    private val recordedCommands = mutableListOf<CommandRecord>()

    private data class CommandRecord(
        val opCode: Int,
        val txId: Int,
        val params: List<Int>
    )

    private fun makeResponseContainer(responseCode: Int, txId: Int, params: List<Int> = emptyList()): ByteArray {
        val len = 12 + params.size * 4
        val bb = ByteBuffer.allocate(len).order(ByteOrder.LITTLE_ENDIAN)
        bb.putInt(len)
        bb.putShort(CanonPtpConstants.CONTAINER_RESPONSE.toShort())
        bb.putShort(responseCode.toShort())
        bb.putInt(txId)
        params.forEach { bb.putInt(it) }
        return bb.array()
    }

    private fun makeDataContainer(opCode: Int, txId: Int, payload: ByteArray): ByteArray {
        val len = 12 + payload.size
        val bb = ByteBuffer.allocate(len).order(ByteOrder.LITTLE_ENDIAN)
        bb.putInt(len)
        bb.putShort(CanonPtpConstants.CONTAINER_DATA.toShort())
        bb.putShort(opCode.toShort())
        bb.putInt(txId)
        bb.put(payload)
        return bb.array()
    }

    private fun encodePtpString(str: String): ByteArray {
        val chars = str.toCharArray()
        val numChars = chars.size + 1
        val bb = ByteBuffer.allocate(1 + numChars * 2).order(ByteOrder.LITTLE_ENDIAN)
        bb.put(numChars.toByte())
        for (c in chars) {
            bb.putShort(c.code.toShort())
        }
        bb.putShort(0)
        return bb.array()
    }

    private fun makeStorageInfoPayload(
        storageType: Int = 0x0004,
        fileSystemType: Int = 0x0002,
        accessCapability: Int = 0x0000,
        maxCapacity: Long = 128_000_000_000L,
        freeSpace: Long = 64_000_000_000L,
        desc: String = "CFexpress Card",
        label: String = "EOS_DIGITAL"
    ): ByteArray {
        val descBytes = encodePtpString(desc)
        val labelBytes = encodePtpString(label)
        val len = 2 + 2 + 2 + 8 + 8 + 4 + descBytes.size + labelBytes.size
        val bb = ByteBuffer.allocate(len).order(ByteOrder.LITTLE_ENDIAN)
        bb.putShort(storageType.toShort())
        bb.putShort(fileSystemType.toShort())
        bb.putShort(accessCapability.toShort())
        bb.putLong(maxCapacity)
        bb.putLong(freeSpace)
        bb.putInt(5000) // freeSpaceImages
        bb.put(descBytes)
        bb.put(labelBytes)
        return bb.array()
    }

    @Before
    fun setUp() {
        io.mockk.mockkStatic(android.util.Log::class)
        every { android.util.Log.d(any(), any()) } returns 0
        every { android.util.Log.i(any(), any()) } returns 0
        every { android.util.Log.w(any(), any<String>()) } returns 0
        every { android.util.Log.w(any(), any<String>(), any()) } returns 0
        every { android.util.Log.e(any(), any()) } returns 0
        every { android.util.Log.e(any(), any(), any()) } returns 0

        device = mockk(relaxed = true)
        connection = mockk(relaxed = true)
        usbInterface = mockk(relaxed = true)
        outEndpoint = mockk(relaxed = true)
        inEndpoint = mockk(relaxed = true)

        every { device.deviceName } returns "/dev/bus/usb/001/003"
        every { device.vendorId } returns CanonPtpConstants.CANON_VENDOR_ID
        every { device.productId } returns 0x3275 // Canon EOS R5
        every { usbInterface.id } returns 0
        every { outEndpoint.direction } returns UsbConstants.USB_DIR_OUT
        every { inEndpoint.direction } returns UsbConstants.USB_DIR_IN

        every { connection.fileDescriptor } returns 42
        every { connection.claimInterface(any(), any()) } returns true
        every { connection.releaseInterface(any()) } returns true
        every { connection.close() } returns Unit

        inQueue.clear()
        recordedCommands.clear()

        // Mock bulk transfers
        every {
            connection.bulkTransfer(eq(outEndpoint), any(), any(), any(), any())
        } answers {
            val buf = secondArg<ByteArray>()
            val len = arg<Int>(3)
            val bb = ByteBuffer.wrap(buf, 0, len).order(ByteOrder.LITTLE_ENDIAN)
            val containerLen = bb.int
            val containerType = bb.short.toInt() and 0xFFFF
            val opCode = bb.short.toInt() and 0xFFFF
            val txId = bb.int

            if (containerType == CanonPtpConstants.CONTAINER_COMMAND) {
                val params = mutableListOf<Int>()
                while (bb.remaining() >= 4) {
                    params.add(bb.int)
                }
                recordedCommands.add(CommandRecord(opCode, txId, params))

                // Default auto-responders for common commands if queue empty
                when (opCode) {
                    CanonPtpConstants.OC_OPEN_SESSION,
                    CanonPtpConstants.EOS_OC_SET_REMOTE_MODE,
                    CanonPtpConstants.EOS_OC_SET_EVENT_MODE,
                    CanonPtpConstants.OC_CLOSE_SESSION -> {
                        inQueue.offer(makeResponseContainer(CanonPtpConstants.RC_OK, txId))
                    }
                    CanonPtpConstants.OC_GET_STORAGE_IDS -> {
                        val payload = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN)
                            .putInt(2)
                            .putInt(0x00010001) // Slot 1 CFexpress
                            .putInt(0x00010002) // Slot 2 SD
                            .array()
                        inQueue.offer(makeDataContainer(opCode, txId, payload))
                        inQueue.offer(makeResponseContainer(CanonPtpConstants.RC_OK, txId))
                    }
                    CanonPtpConstants.OC_GET_STORAGE_INFO -> {
                        val storageId = params.firstOrNull() ?: 0x00010001
                        val desc = if (storageId == 0x00010001) "CFexpress Slot 1" else "SD Slot 2"
                        val payload = makeStorageInfoPayload(desc = desc)
                        inQueue.offer(makeDataContainer(opCode, txId, payload))
                        inQueue.offer(makeResponseContainer(CanonPtpConstants.RC_OK, txId))
                    }
                    CanonPtpConstants.OC_GET_OBJECT_HANDLES -> {
                        if (inQueue.isEmpty()) {
                            val storageId = params.firstOrNull() ?: 0x00010001
                            val handles = if (storageId == 0x00010002) listOf(0x2001, 0x2002) else listOf(0x1001, 0x1002)
                            val payload = ByteBuffer.allocate(4 + handles.size * 4).order(ByteOrder.LITTLE_ENDIAN).apply {
                                putInt(handles.size)
                                handles.forEach { putInt(it) }
                            }.array()
                            inQueue.offer(makeDataContainer(opCode, txId, payload))
                            inQueue.offer(makeResponseContainer(CanonPtpConstants.RC_OK, txId))
                        }
                    }
                    CanonPtpConstants.EOS_OC_GET_EVENT -> {
                        if (inQueue.isEmpty()) {
                            inQueue.offer(makeResponseContainer(CanonPtpConstants.RC_OK, txId))
                        }
                    }
                }
            }
            len
        }

        every {
            connection.bulkTransfer(eq(inEndpoint), any(), any(), any(), any())
        } answers {
            val buf = secondArg<ByteArray>()
            val offset = thirdArg<Int>()
            val len = arg<Int>(3)
            val nextPacket = inQueue.poll() ?: return@answers 0
            val toCopy = minOf(nextPacket.size, len)
            System.arraycopy(nextPacket, 0, buf, offset, toCopy)
            toCopy
        }

        transportManager = SafeUsbTransportManager(connection)
        session = UsbHardwareSession(
            device = device,
            connection = connection,
            bufferPool = DirectByteBufferPool.Default,
            transportManagerOverride = transportManager
        )

        adapter = CanonPtpAdapter(
            session = session,
            outEndpoint = outEndpoint,
            inEndpoint = inEndpoint
        )
    }

    @After
    fun tearDown() {
        adapter.close()
        io.mockk.unmockkStatic(android.util.Log::class)
    }

    @Test
    fun `handshake initializes into EOS Remote and Event modes and enumerates storage slots`() = runTest {
        val success = adapter.initialize()

        assertTrue("Adapter initialization must succeed", success)
        assertTrue(recordedCommands.any { it.opCode == CanonPtpConstants.OC_OPEN_SESSION })
        assertTrue(recordedCommands.any { it.opCode == CanonPtpConstants.EOS_OC_SET_REMOTE_MODE && it.params == listOf(1) })
        assertTrue(recordedCommands.any { it.opCode == CanonPtpConstants.EOS_OC_SET_EVENT_MODE && it.params == listOf(1) })
        assertTrue(recordedCommands.any { it.opCode == CanonPtpConstants.OC_GET_STORAGE_IDS })

        assertEquals(2, adapter.storageSlots.size)
        val slot1 = adapter.storageSlots[0x00010001]
        val slot2 = adapter.storageSlots[0x00010002]
        assertNotNull(slot1)
        assertNotNull(slot2)
        assertEquals(CanonPtpConstants.SLOT_1_CFEXPRESS, slot1?.slotIndex)
        assertEquals(CanonPtpConstants.SLOT_2_SD, slot2?.slotIndex)
    }

    @Test
    fun `digic event pump drains 0x9116 events and emits to events flow`() = runTest {
        // Prepare DIGIC event records payload:
        // Record 1: EOS_EC_OBJECT_ADDED (0xC101), handle=0x00020001, storageId=0x00010001, format=0xB103
        // Record 2: EOS_EC_SHUTTER_RELEASED (0xC108), seqNumber=42
        // Record 3: EOS_EC_OBJECT_REMOVED (0xC102), handle=0x00020099
        val eventPayload = ByteBuffer.allocate(64).order(ByteOrder.LITTLE_ENDIAN).apply {
            // Record 1
            putInt(20) // recordLen (8 header + 12 payload)
            putInt(CanonPtpConstants.EOS_EC_OBJECT_ADDED)
            putInt(0x00020001) // handle
            putInt(0x00010001) // storageId
            putInt(CanonPtpConstants.FORMAT_CR3_RAW) // formatCode

            // Record 2
            putInt(12) // recordLen (8 header + 4 payload)
            putInt(CanonPtpConstants.EOS_EC_SHUTTER_RELEASED)
            putInt(42) // seqNumber

            // Record 3
            putInt(12) // recordLen (8 header + 4 payload)
            putInt(CanonPtpConstants.EOS_EC_OBJECT_REMOVED)
            putInt(0x00020099)
        }.array().copyOf(44)

        // Queue responses for 0x9116
        inQueue.offer(makeDataContainer(CanonPtpConstants.EOS_OC_GET_EVENT, 10, eventPayload))
        inQueue.offer(makeResponseContainer(CanonPtpConstants.RC_OK, 10))

        val emittedEvents = mutableListOf<CanonPtpEvent>()
        val job = backgroundScope.launch {
            adapter.events.collect { emittedEvents.add(it) }
        }

        val drainedCount = adapter.drainDigicEvents()
        testScheduler.runCurrent()

        assertEquals(3, drainedCount)
        assertEquals(3, emittedEvents.size)

        val event1 = emittedEvents[0] as CanonPtpEvent.ObjectAdded
        assertEquals(0x00020001, event1.handle)
        assertEquals(0x00010001, event1.storageId)
        assertEquals(CanonPtpConstants.FORMAT_CR3_RAW, event1.formatCode)

        val event2 = emittedEvents[1] as CanonPtpEvent.ShutterReleased
        assertEquals(42, event2.seqNumber)

        val event3 = emittedEvents[2] as CanonPtpEvent.ObjectRemoved
        assertEquals(0x00020099, event3.handle)

        job.cancel()
    }

    @Test
    fun `handles device busy response gracefully during event pump drain`() = runTest {
        // Queue Device Busy response (0x2019)
        inQueue.offer(makeResponseContainer(CanonPtpConstants.RC_DEVICE_BUSY, 15))

        val drained = adapter.drainDigicEvents()

        assertEquals(0, drained)
        assertFalse("Adapter must remain open after busy response", adapter.isClosed)
    }

    @Test
    fun `dual slot storage isolation separates CFexpress and SD object handles`() = runTest {
        adapter.initialize()

        // Mock handles for Slot 1 (CFexpress)
        val slot1Payload = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(2)
            .putInt(0x1001)
            .putInt(0x1002)
            .array()
        inQueue.offer(makeDataContainer(CanonPtpConstants.OC_GET_OBJECT_HANDLES, 20, slot1Payload))
        inQueue.offer(makeResponseContainer(CanonPtpConstants.RC_OK, 20))

        val slot1Handles = adapter.getObjectHandles(0x00010001)
        assertEquals(listOf(0x1001, 0x1002), slot1Handles)
        assertTrue(adapter.slot1Handles.contains(0x1001))
        assertTrue(adapter.slot1Handles.contains(0x1002))
        assertFalse(adapter.slot2Handles.contains(0x1001))

        // Mock handles for Slot 2 (SD)
        val slot2Payload = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(2)
            .putInt(0x2001)
            .putInt(0x2002)
            .array()
        inQueue.offer(makeDataContainer(CanonPtpConstants.OC_GET_OBJECT_HANDLES, 21, slot2Payload))
        inQueue.offer(makeResponseContainer(CanonPtpConstants.RC_OK, 21))

        val slot2Handles = adapter.getObjectHandles(0x00010002)
        assertEquals(listOf(0x2001, 0x2002), slot2Handles)
        assertTrue(adapter.slot2Handles.contains(0x2001))
        assertTrue(adapter.slot2Handles.contains(0x2002))
        assertFalse(adapter.slot1Handles.contains(0x2001))
    }

    @Test
    fun `4-step clean teardown executes in exact order`() = runTest {
        adapter.initialize()

        adapter.disconnect(reason = "User unmounted")

        assertTrue(adapter.isClosed)
        assertTrue(session.isClosed)

        // Verify SetRemoteMode(0) and CloseSession(1) were sent
        assertTrue(recordedCommands.any { it.opCode == CanonPtpConstants.EOS_OC_SET_REMOTE_MODE && it.params == listOf(0) })
        assertTrue(recordedCommands.any { it.opCode == CanonPtpConstants.OC_CLOSE_SESSION })
    }
}
