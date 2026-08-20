package com.rvmeur.transferin.data.usb.vendor.sony

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import com.rvmeur.transferin.data.usb.core.DirectByteBufferPool
import com.rvmeur.transferin.data.usb.core.SafeUsbTransportManager
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.ArrayDeque

class SonyPtpDefragmenterTest {

    private lateinit var connection: UsbDeviceConnection
    private lateinit var endpointIn: UsbEndpoint
    private lateinit var transportManager: SafeUsbTransportManager
    private lateinit var defragmenter: SonyPtpDefragmenter

    private val inPackets = ArrayDeque<ByteArray>()

    @Before
    fun setUp() {
        connection = mockk(relaxed = true)
        endpointIn = mockk(relaxed = true)

        every { connection.fileDescriptor } returns 15
        every { endpointIn.type } returns UsbConstants.USB_ENDPOINT_XFER_BULK
        every { endpointIn.direction } returns UsbConstants.USB_DIR_IN

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
        defragmenter = SonyPtpDefragmenter(
            transportManager = transportManager,
            inEndpoint = endpointIn,
            bufferPool = DirectByteBufferPool.Default
        )
    }

    @Test
    fun `readContainer reassembles multi-packet payload across 3 chunks plus ZLP`() {
        val totalPayloadSize = 1000
        val expectedData = ByteArray(totalPayloadSize) { (it % 256).toByte() }

        // Packet 1: 12-byte header + first 400 bytes of payload (total 412 bytes)
        val packet1 = ByteBuffer.allocate(412).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(12 + totalPayloadSize)
            .putShort(SonyPtpConstants.CONTAINER_DATA.toShort())
            .putShort(0x1008.toShort()) // OpCode
            .putInt(10) // TxId
            .put(expectedData, 0, 400)
            .array()

        // Packet 2: Next 400 bytes
        val packet2 = ByteArray(400)
        System.arraycopy(expectedData, 400, packet2, 0, 400)

        // Packet 3: Final 200 bytes
        val packet3 = ByteArray(200)
        System.arraycopy(expectedData, 800, packet3, 0, 200)

        inPackets.add(packet1)
        inPackets.add(packet2)
        inPackets.add(packet3)

        val container = defragmenter.readContainer(expectedTxId = 10)

        assertTrue(container is SonyPtpContainer.Data)
        val dataContainer = container as SonyPtpContainer.Data
        assertEquals(0x1008, dataContainer.opCode)
        assertEquals(10, dataContainer.transactionId)
        assertEquals(totalPayloadSize, dataContainer.payload.size)
        assertArrayEquals(expectedData, dataContainer.payload)
    }

    @Test
    fun `readContainer parses Response container correctly`() {
        val respPacket = ByteBuffer.allocate(20).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(20)
            .putShort(SonyPtpConstants.CONTAINER_RESPONSE.toShort())
            .putShort(SonyPtpConstants.RC_OK.toShort())
            .putInt(42)
            .putInt(0x1001)
            .putInt(0x1002)
            .array()

        inPackets.add(respPacket)

        val container = defragmenter.readContainer(expectedTxId = 42)

        assertTrue(container is SonyPtpContainer.Response)
        val resp = container as SonyPtpContainer.Response
        assertEquals(SonyPtpConstants.RC_OK, resp.responseCode)
        assertEquals(42, resp.transactionId)
        assertEquals(listOf(0x1001, 0x1002), resp.params)
    }
}
