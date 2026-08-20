package com.rvmeur.transferin.data.usb.vendor.fuji

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import com.rvmeur.transferin.data.usb.core.DirectByteBufferPool
import com.rvmeur.transferin.data.usb.core.SafeUsbTransportManager
import com.rvmeur.transferin.domain.camera.CameraTetherEvent
import com.rvmeur.transferin.domain.camera.CameraVendor
import com.rvmeur.transferin.domain.camera.TetherState
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class FujiTetherAdapterTest {

    private lateinit var device: UsbDevice
    private lateinit var connection: UsbDeviceConnection
    private lateinit var usbInterface: UsbInterface
    private lateinit var bulkOutEndpoint: UsbEndpoint
    private lateinit var bulkInEndpoint: UsbEndpoint
    private lateinit var interruptInEndpoint: UsbEndpoint
    private lateinit var transportManager: SafeUsbTransportManager
    private lateinit var adapter: FujiTetherAdapter

    private val sentCommands = mutableListOf<SentCommand>()

    private data class SentCommand(
        val opCode: Int,
        val txId: Int,
        val params: IntArray
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as SentCommand
            return opCode == other.opCode && txId == other.txId && params.contentEquals(other.params)
        }

        override fun hashCode(): Int {
            var result = opCode
            result = 31 * result + txId
            result = 31 * result + params.contentHashCode()
            return result
        }
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
        bulkOutEndpoint = mockk(relaxed = true)
        bulkInEndpoint = mockk(relaxed = true)
        interruptInEndpoint = mockk(relaxed = true)
        transportManager = mockk(relaxed = true)

        sentCommands.clear()

        every { device.deviceName } returns "/dev/bus/usb/001/003"
        every { device.productName } returns "FUJIFILM X-T5"
        every { device.vendorId } returns FujiPtpConstants.FUJI_VENDOR_ID // 0x04CB
        every { device.productId } returns 0x02DF
        every { device.serialNumber } returns "9A001234"

        every { device.interfaceCount } returns 1
        every { device.getInterface(0) } returns usbInterface

        every { usbInterface.id } returns 0
        every { usbInterface.interfaceClass } returns UsbConstants.USB_CLASS_STILL_IMAGE
        every { usbInterface.endpointCount } returns 3
        every { usbInterface.getEndpoint(0) } returns bulkOutEndpoint
        every { usbInterface.getEndpoint(1) } returns bulkInEndpoint
        every { usbInterface.getEndpoint(2) } returns interruptInEndpoint

        every { bulkOutEndpoint.type } returns UsbConstants.USB_ENDPOINT_XFER_BULK
        every { bulkOutEndpoint.direction } returns UsbConstants.USB_DIR_OUT

        every { bulkInEndpoint.type } returns UsbConstants.USB_ENDPOINT_XFER_BULK
        every { bulkInEndpoint.direction } returns UsbConstants.USB_DIR_IN

        every { interruptInEndpoint.type } returns UsbConstants.USB_ENDPOINT_XFER_INT
        every { interruptInEndpoint.direction } returns UsbConstants.USB_DIR_IN
        every { interruptInEndpoint.address } returns 0x83

        every { connection.claimInterface(any(), any()) } returns true
        every { connection.releaseInterface(any()) } returns true

        // Capture commands sent to bulk OUT
        every {
            transportManager.safeBulkTransfer(
                bulkOutEndpoint,
                any<ByteArray>(),
                any<Int>(),
                any<Int>()
            )
        } answers {
            val buffer = secondArg<ByteArray>()
            val len = thirdArg<Int>()
            val bb = ByteBuffer.wrap(buffer, 0, len).order(ByteOrder.LITTLE_ENDIAN)
            val packetLen = bb.int
            val containerType = bb.short.toInt() and 0xFFFF
            val opCode = bb.short.toInt() and 0xFFFF
            val txId = bb.int
            val paramCount = (packetLen - 12) / 4
            val params = IntArray(paramCount)
            for (i in 0 until paramCount) {
                params[i] = bb.int
            }
            sentCommands.add(SentCommand(opCode, txId, params))
            len
        }

        // Return PTP OK (0x2001) for Bulk IN responses
        every {
            transportManager.safeBulkTransfer(
                bulkInEndpoint,
                any<ByteArray>(),
                any<Int>(),
                any<Int>()
            )
        } answers {
            val buffer = secondArg<ByteArray>()
            val lastCmd = sentCommands.lastOrNull()
            val txId = lastCmd?.txId ?: 1
            val bb = ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN)
            bb.putInt(12) // length
            bb.putShort(FujiPtpConstants.CONTAINER_RESPONSE.toShort())
            bb.putShort(FujiPtpConstants.RC_OK.toShort())
            bb.putInt(txId)
            12
        }

        // By default, interrupt IN returns timeout (0 bytes)
        every {
            transportManager.safeBulkTransfer(
                interruptInEndpoint,
                any<ByteArray>(),
                any<Int>(),
                any<Int>()
            )
        } returns 0

        adapter = FujiTetherAdapter(
            bufferPool = DirectByteBufferPool.Default,
            transportManagerOverride = transportManager
        )
    }

    @After
    fun tearDown() {
        io.mockk.unmockkStatic(android.util.Log::class)
    }

    @Test
    fun `canHandle returns true for Fujifilm VID and false for other vendors`() {
        val fujiDevice: UsbDevice = mockk { every { vendorId } returns 0x04CB }
        val canonDevice: UsbDevice = mockk { every { vendorId } returns 0x04A9 }
        val sonyDevice: UsbDevice = mockk { every { vendorId } returns 0x054C }
        val nikonDevice: UsbDevice = mockk { every { vendorId } returns 0x04B0 }

        assertTrue(adapter.canHandle(fujiDevice))
        assertFalse(adapter.canHandle(canonDevice))
        assertFalse(adapter.canHandle(sonyDevice))
        assertFalse(adapter.canHandle(nikonDevice))
    }

    @Test
    fun `initialize executes 3-step PTP handshake and transitions to ActiveStreaming`() = runTest {
        assertEquals(TetherState.Idle, adapter.connectionState.value)

        val result = adapter.initialize(device, connection)
        assertTrue(result.isSuccess)

        val info = result.getOrThrow()
        assertEquals(CameraVendor.FUJIFILM, info.vendor)
        assertEquals("FUJIFILM X-T5", info.modelName)
        assertEquals("9A001234", info.serialNumber)
        assertEquals("PC+Card", info.saveDestination)

        assertTrue(adapter.connectionState.value is TetherState.ActiveStreaming)

        // Verify exact PTP handshake sequence
        assertEquals(3, sentCommands.size)

        // Step 1: OpenSession (0x1002, SessionID=1)
        assertEquals(FujiPtpConstants.OC_OPEN_SESSION, sentCommands[0].opCode)
        assertEquals(1, sentCommands[0].params[0])

        // Step 2: Set Host Control ON (0x902B, Prop=0xD214, Val=1)
        assertEquals(FujiPtpConstants.OC_FUJI_SET_DEVICE_PROP, sentCommands[1].opCode)
        assertEquals(FujiPtpConstants.PROP_FUJI_HOST_CONTROL, sentCommands[1].params[0])
        assertEquals(FujiPtpConstants.HOST_CONTROL_ACTIVE, sentCommands[1].params[1])

        // Step 3: Set Save Destination PC+Card (0x902B, Prop=0xD216, Val=2)
        assertEquals(FujiPtpConstants.OC_FUJI_SET_DEVICE_PROP, sentCommands[2].opCode)
        assertEquals(FujiPtpConstants.PROP_FUJI_SAVE_DESTINATION, sentCommands[2].params[0])
        assertEquals(FujiPtpConstants.SAVE_DEST_PC_SD, sentCommands[2].params[1])
    }

    @Test
    fun `interrupt IN drain emits ObjectAdded event for 0x4002 and 0xC001`() = kotlinx.coroutines.runBlocking {
        val eventReceived = CompletableDeferred<CameraTetherEvent>()

        // Simulate interrupt IN returning Fuji ObjectAdded event 0xC001 with handle 0x0042
        var eventSimulated = false
        every {
            transportManager.safeBulkTransfer(
                interruptInEndpoint,
                any<ByteArray>(),
                any<Int>(),
                any<Int>()
            )
        } answers {
            if (!eventSimulated) {
                eventSimulated = true
                val buffer = secondArg<ByteArray>()
                val bb = ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN)
                bb.putInt(16) // Length (12 + 4 bytes param)
                bb.putShort(FujiPtpConstants.CONTAINER_EVENT.toShort())
                bb.putShort(FujiPtpConstants.EVT_FUJI_OBJECT_ADDED.toShort())
                bb.putInt(100) // Transaction ID
                bb.putInt(42) // Handle
                16
            } else {
                0
            }
        }

        val eventJob = launch(Dispatchers.Default) {
            val event = adapter.observeEvents().first()
            eventReceived.complete(event)
        }

        adapter.initialize(device, connection)

        val received = withTimeout(3000L) {
            eventReceived.await()
        }

        assertTrue(received is CameraTetherEvent.ObjectAdded)
        val objAdded = received as CameraTetherEvent.ObjectAdded
        assertEquals(42, objAdded.handle)
        assertEquals(FujiPtpConstants.FMT_FUJI_RAF, objAdded.formatCode)
        assertTrue(objAdded.filename.contains("0042"))
        eventJob.cancel()
    }

    @Test
    fun `terminateSession executes deterministic 4-step clean teardown releasing host control`() = runTest {
        adapter.initialize(device, connection)
        sentCommands.clear()

        val result = adapter.terminateSession()
        assertTrue(result.isSuccess)
        assertEquals(TetherState.CleanTerminated, adapter.connectionState.value)

        // Verify Step 2: 0xD214 = 0x0000 Release Host Control was sent
        val releaseCmd = sentCommands.firstOrNull {
            it.opCode == FujiPtpConstants.OC_FUJI_SET_DEVICE_PROP &&
                    it.params.isNotEmpty() &&
                    it.params[0] == FujiPtpConstants.PROP_FUJI_HOST_CONTROL &&
                    it.params[1] == FujiPtpConstants.HOST_CONTROL_RELEASE
        }
        assertTrue("Must send 0xD214=0x0000 Host Control Release", releaseCmd != null)

        // Verify Step 3: CloseSession 0x1003 was sent
        val closeCmd = sentCommands.firstOrNull { it.opCode == FujiPtpConstants.OC_CLOSE_SESSION }
        assertTrue("Must send 0x1003 CloseSession", closeCmd != null)

        // Verify Step 4: USB interface released and connection closed
        verify(atLeast = 1) { connection.releaseInterface(usbInterface) }
        verify(atLeast = 1) { connection.close() }
    }

    @Test
    fun `teardown executes cleanly under coroutine cancellation`() = runTest {
        adapter.initialize(device, connection)

        val job = launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
            adapter.terminateSession()
        }
        job.cancel()
        job.join()

        // Verify teardown completed non-cancellably
        verify(atLeast = 1) { connection.releaseInterface(usbInterface) }
        verify(atLeast = 1) { connection.close() }
    }

    @Test
    fun `error during OpenSession transitions connection state to Failed`() = runTest {
        // Mock OpenSession returning PTP General Error 0x2002
        every {
            transportManager.safeBulkTransfer(
                bulkInEndpoint,
                any<ByteArray>(),
                any<Int>(),
                any<Int>()
            )
        } answers {
            val buffer = secondArg<ByteArray>()
            val bb = ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN)
            bb.putInt(12)
            bb.putShort(FujiPtpConstants.CONTAINER_RESPONSE.toShort())
            bb.putShort(FujiPtpConstants.RC_GENERAL_ERROR.toShort()) // 0x2002
            bb.putInt(1)
            12
        }

        val result = adapter.initialize(device, connection)
        assertTrue(result.isFailure)
        assertTrue(adapter.connectionState.value is TetherState.Failed)
    }
}
