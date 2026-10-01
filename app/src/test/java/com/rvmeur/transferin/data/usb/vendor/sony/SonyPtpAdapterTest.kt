package com.rvmeur.transferin.data.usb.vendor.sony

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
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.ArrayDeque

class SonyPtpAdapterTest {

    private lateinit var device: UsbDevice
    private lateinit var connection: UsbDeviceConnection
    private lateinit var usbInterface: UsbInterface
    private lateinit var endpointOut: UsbEndpoint
    private lateinit var endpointIn: UsbEndpoint
    private lateinit var transportManager: SafeUsbTransportManager
    private lateinit var adapter: SonyPtpAdapter

    private val inPackets = ArrayDeque<ByteArray>()

    @Before
    fun setUp() {
        device = mockk(relaxed = true)
        connection = mockk(relaxed = true)
        usbInterface = mockk(relaxed = true)
        endpointOut = mockk(relaxed = true)
        endpointIn = mockk(relaxed = true)

        every { device.vendorId } returns SonyPtpConstants.VID_SONY
        every { device.deviceName } returns "Sony_ILCE-7M4"
        every { connection.fileDescriptor } returns 10
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

        adapter = SonyPtpAdapter(
            device = device,
            connection = connection,
            bufferPool = DirectByteBufferPool.Default,
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
        bb.putShort(SonyPtpConstants.CONTAINER_RESPONSE.toShort())
        bb.putShort(code.toShort())
        bb.putInt(txId)
        params.forEach { bb.putInt(it) }
        inPackets.add(bb.array())
    }

    private fun queueData(opCode: Int, txId: Int, data: ByteArray) {
        val len = 12 + data.size
        val bb = ByteBuffer.allocate(len).order(ByteOrder.LITTLE_ENDIAN)
        bb.putInt(len)
        bb.putShort(SonyPtpConstants.CONTAINER_DATA.toShort())
        bb.putShort(opCode.toShort())
        bb.putInt(txId)
        bb.put(data)
        inPackets.add(bb.array())
    }

    @Test
    fun `startSession executes 3-phase SDIO handshake and DRAM safety check`() = runTest {
        // Step 1: OpenSession tx=1
        queueResponse(SonyPtpConstants.RC_OK, 1)
        // Step 2: SDIOConnect phase 1 tx=2
        queueResponse(SonyPtpConstants.RC_OK, 2)
        // Step 3: SDIOConnect phase 2 tx=3
        queueResponse(SonyPtpConstants.RC_OK, 3)
        // Step 4: GetSDIOExtDeviceInfo tx=4
        queueResponse(SonyPtpConstants.RC_OK, 4)
        // Step 5: DRAM Safety Check: GetProp 0xD2C1 tx=5 -> returns PC + Camera (0x0002)
        val propData = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(SonyPtpConstants.SAVE_DEST_PC_AND_CAMERA.toShort()).array()
        queueData(SonyPtpConstants.OC_SONY_GET_DEVICE_PROP_VALUE, 5, propData)
        queueResponse(SonyPtpConstants.RC_OK, 5)

        val success = adapter.startSession()

        assertTrue(success)
        assertTrue(adapter.isSessionOpen)
        assertEquals(SonyDramSafetyStatus.Safe, adapter.dramStatus.value)
    }

    @Test
    fun `startSession auto-remediates volatile PC ONLY mode to PC + Camera`() = runTest {
        // Step 1-4 Handshake
        queueResponse(SonyPtpConstants.RC_OK, 1)
        queueResponse(SonyPtpConstants.RC_OK, 2)
        queueResponse(SonyPtpConstants.RC_OK, 3)
        queueResponse(SonyPtpConstants.RC_OK, 4)

        // Step 5: DRAM check: First read returns PC ONLY (0x0001) tx=5
        val pcOnlyData = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(SonyPtpConstants.SAVE_DEST_PC_ONLY.toShort()).array()
        queueData(SonyPtpConstants.OC_SONY_GET_DEVICE_PROP_VALUE, 5, pcOnlyData)
        queueResponse(SonyPtpConstants.RC_OK, 5)

        // Step 6: SetProp 0xD2C1 to 0x0002 tx=6
        queueResponse(SonyPtpConstants.RC_OK, 6)

        // Step 7: Verify read tx=7 -> returns PC + Camera (0x0002)
        val safeData = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(SonyPtpConstants.SAVE_DEST_PC_AND_CAMERA.toShort()).array()
        queueData(SonyPtpConstants.OC_SONY_GET_DEVICE_PROP_VALUE, 7, safeData)
        queueResponse(SonyPtpConstants.RC_OK, 7)

        val success = adapter.startSession()

        assertTrue(success)
        val status = adapter.dramStatus.value
        assertTrue(status is SonyDramSafetyStatus.Remediated)
        val remediated = status as SonyDramSafetyStatus.Remediated
        assertEquals(SonyPtpConstants.SAVE_DEST_PC_ONLY, remediated.previousMode)
        assertEquals(SonyPtpConstants.SAVE_DEST_PC_AND_CAMERA, remediated.currentMode)
    }

    @Test
    fun `startSession emits Warning when PC Only mode cannot be remediated`() = runTest {
        // Step 1-4 Handshake
        queueResponse(SonyPtpConstants.RC_OK, 1)
        queueResponse(SonyPtpConstants.RC_OK, 2)
        queueResponse(SonyPtpConstants.RC_OK, 3)
        queueResponse(SonyPtpConstants.RC_OK, 4)

        // Step 5: Read returns PC Only tx=5
        val pcOnlyData = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(SonyPtpConstants.SAVE_DEST_PC_ONLY.toShort()).array()
        queueData(SonyPtpConstants.OC_SONY_GET_DEVICE_PROP_VALUE, 5, pcOnlyData)
        queueResponse(SonyPtpConstants.RC_OK, 5)

        // Step 6: SetProp fails tx=6 (0xD2C1 vendor op 0x9803)
        queueResponse(SonyPtpConstants.RC_GENERAL_ERROR, 6)
        // Fallback SetProp fails tx=7 (0xD2C1 std op 0x1016)
        queueResponse(SonyPtpConstants.RC_GENERAL_ERROR, 7)
        // Fallback SetProp fails tx=8 (0xD2C2 vendor op 0x9803)
        queueResponse(SonyPtpConstants.RC_GENERAL_ERROR, 8)
        // Fallback SetProp fails tx=9 (0xD2C2 std op 0x1016)
        queueResponse(SonyPtpConstants.RC_GENERAL_ERROR, 9)

        val success = adapter.startSession()

        assertTrue(success)
        val status = adapter.dramStatus.value
        assertTrue(status is SonyDramSafetyStatus.Warning)
        val warning = status as SonyDramSafetyStatus.Warning
        assertEquals(SonyPtpConstants.SAVE_DEST_PC_ONLY, warning.currentSaveDestination)
    }

    @Test
    fun `getStorageIds and getObjectHandles parse UInt32 array payloads`() = runTest {
        // GetStorageIds tx=1
        val storagePayload = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(2) // 2 storage IDs
            .putInt(0x00010001)
            .putInt(0x00020001)
            .array()
        queueData(SonyPtpConstants.OC_GET_STORAGE_IDS, 1, storagePayload)
        queueResponse(SonyPtpConstants.RC_OK, 1)

        val storages = adapter.getStorageIds()
        assertEquals(listOf(0x00010001, 0x00020001), storages)

        // GetObjectHandles tx=2
        val handlesPayload = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(3) // 3 object handles
            .putInt(101)
            .putInt(102)
            .putInt(103)
            .array()
        queueData(SonyPtpConstants.OC_GET_OBJECT_HANDLES, 2, handlesPayload)
        queueResponse(SonyPtpConstants.RC_OK, 2)

        val handles = adapter.getObjectHandles(0x00010001)
        assertEquals(listOf(101, 102, 103), handles)
    }

    @Test
    fun `closeSession executes SDIOClose then CloseSession in sequence`() = runTest {
        // Start session first
        queueResponse(SonyPtpConstants.RC_OK, 1)
        queueResponse(SonyPtpConstants.RC_OK, 2)
        queueResponse(SonyPtpConstants.RC_OK, 3)
        queueResponse(SonyPtpConstants.RC_OK, 4)
        val propData = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(2.toShort()).array()
        queueData(SonyPtpConstants.OC_SONY_GET_DEVICE_PROP_VALUE, 5, propData)
        queueResponse(SonyPtpConstants.RC_OK, 5)
        adapter.startSession()

        // Close teardown responses
        queueResponse(SonyPtpConstants.RC_OK, 6) // SDIOClose
        queueResponse(SonyPtpConstants.RC_OK, 7) // CloseSession

        adapter.closeSession()

        verify(atLeast = 1) { connection.releaseInterface(usbInterface) }
    }

    @Test
    fun `streamMedia streams object data into output stream`() = runTest {
        val testData = "Sony RAW Test Payload 123456789".toByteArray()
        queueData(SonyPtpConstants.OC_GET_OBJECT, 1, testData)
        queueResponse(SonyPtpConstants.RC_OK, 1)

        val outputStream = java.io.ByteArrayOutputStream()
        val result = adapter.streamMedia(handle = 555, sink = outputStream)

        assertTrue("streamMedia must succeed", result.isSuccess)
        assertEquals("Sony RAW Test Payload 123456789", outputStream.toString())
    }
}
