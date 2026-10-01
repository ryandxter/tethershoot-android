package com.rvmeur.transferin.data.usb.vendor

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class CanonCameraInitializerTest {

    private lateinit var device: UsbDevice
    private lateinit var connection: UsbDeviceConnection
    private lateinit var usbInterface: UsbInterface
    private lateinit var outEndpoint: UsbEndpoint
    private lateinit var inEndpoint: UsbEndpoint

    private val sentCommands = mutableListOf<Pair<Int, Int>>() // opCode to txId

    @Before
    fun setUp() {
        sentCommands.clear()
        device = mockk(relaxed = true)
        connection = mockk(relaxed = true)
        usbInterface = mockk(relaxed = true)
        outEndpoint = mockk(relaxed = true)
        inEndpoint = mockk(relaxed = true)

        every { device.vendorId } returns 0x04A9
        every { device.deviceName } returns "Canon_EOS_R6"
        every { device.interfaceCount } returns 1
        every { device.getInterface(0) } returns usbInterface

        every { usbInterface.id } returns 0
        every { usbInterface.interfaceClass } returns UsbConstants.USB_CLASS_STILL_IMAGE
        every { usbInterface.endpointCount } returns 2
        every { usbInterface.getEndpoint(0) } returns outEndpoint
        every { usbInterface.getEndpoint(1) } returns inEndpoint

        every { outEndpoint.type } returns UsbConstants.USB_ENDPOINT_XFER_BULK
        every { outEndpoint.direction } returns UsbConstants.USB_DIR_OUT
        every { inEndpoint.type } returns UsbConstants.USB_ENDPOINT_XFER_BULK
        every { inEndpoint.direction } returns UsbConstants.USB_DIR_IN

        every { connection.claimInterface(usbInterface, true) } returns true
        every { connection.releaseInterface(usbInterface) } returns true

        every { connection.bulkTransfer(eq(outEndpoint), any(), any(), any()) } answers {
            val buf = secondArg<ByteArray>()
            val len = thirdArg<Int>()
            val bb = ByteBuffer.wrap(buf, 0, len).order(ByteOrder.LITTLE_ENDIAN)
            bb.int // length
            bb.short // containerType
            val opCode = bb.short.toInt() and 0xFFFF
            val txId = bb.int
            sentCommands.add(opCode to txId)
            len
        }

        every { connection.bulkTransfer(eq(inEndpoint), any(), any(), any()) } answers {
            val buf = secondArg<ByteArray>()
            val lastCmd = sentCommands.lastOrNull()
            val txId = lastCmd?.second ?: 1
            // PTP OK response: length=12, type=3 (response), code=0x2001 (OK), txId
            val resp = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN)
                .putInt(12)
                .putShort(3.toShort())
                .putShort(0x2001.toShort())
                .putInt(txId)
                .array()
            System.arraycopy(resp, 0, buf, 0, 12)
            12
        }
    }

    @Test
    fun `default initializer omits EOS_OC_SET_REMOTE_MODE to prevent locking camera shutter`() {
        val initializer = CanonCameraInitializer()
        val result = initializer.initializeUnlocked(device, connection)

        assertTrue("Initializer must succeed", result.success)

        val opCodes = sentCommands.map { it.first }
        // 0x1002 = OpenSession, 0x9115 = SetEventMode
        assertTrue("Must send OpenSession (0x1002)", opCodes.contains(0x1002))
        assertTrue("Must send SetEventMode (0x9115)", opCodes.contains(0x9115))
        assertFalse("Must NOT send EOS_OC_SET_REMOTE_MODE (0x9114) by default", opCodes.contains(0x9114))
    }

    @Test
    fun `initializer with enableRemoteMode=true sends EOS_OC_SET_REMOTE_MODE`() {
        val initializer = CanonCameraInitializer(enableRemoteMode = true)
        val result = initializer.initializeUnlocked(device, connection)

        assertTrue("Initializer must succeed", result.success)

        val opCodes = sentCommands.map { it.first }
        assertTrue("Must send OpenSession (0x1002)", opCodes.contains(0x1002))
        assertTrue("Must send EOS_OC_SET_REMOTE_MODE (0x9114)", opCodes.contains(0x9114))
        assertTrue("Must send SetEventMode (0x9115)", opCodes.contains(0x9115))
    }
}
