package com.rvmeur.transferin.data.usb.core

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.nio.ByteBuffer

class SafeUsbTransportManagerTest {

    private lateinit var connection: UsbDeviceConnection
    private lateinit var endpointIn: UsbEndpoint
    private lateinit var endpointOut: UsbEndpoint
    private lateinit var transportManager: SafeUsbTransportManager

    @Before
    fun setUp() {
        connection = mockk(relaxed = true)
        endpointIn = mockk(relaxed = true)
        endpointOut = mockk(relaxed = true)

        every { connection.fileDescriptor } returns 42
        every { endpointIn.direction } returns UsbConstants.USB_DIR_IN
        every { endpointOut.direction } returns UsbConstants.USB_DIR_OUT

        transportManager = SafeUsbTransportManager(connection)
    }

    @Test
    fun `safeBulkTransfer byte array transfers data and resets activeIoCount`() {
        val buffer = ByteArray(64)
        every { connection.bulkTransfer(endpointIn, buffer, 0, 64, 1000) } returns 64

        val result = transportManager.safeBulkTransfer(endpointIn, buffer, 64, 1000)

        assertEquals(64, result)
        assertEquals(0, transportManager.activeIoCount)
        verify(exactly = 1) { connection.bulkTransfer(endpointIn, buffer, 0, 64, 1000) }
    }

    @Test
    fun `safeBulkTransfer byte buffer transfers data correctly`() {
        val directBuffer = ByteBuffer.allocateDirect(128)
        every { connection.bulkTransfer(endpointIn, any<ByteArray>(), 0, 128, 1000) } answers {
            val arr = secondArg<ByteArray>()
            arr[0] = 0x55
            64
        }

        val result = transportManager.safeBulkTransfer(endpointIn, directBuffer, 128, 1000)

        assertEquals(64, result)
        assertEquals(0, transportManager.activeIoCount)
    }

    @Test
    fun `safeControlTransfer transfers data successfully`() {
        val buffer = ByteArray(16)
        every { connection.controlTransfer(0x21, 1, 0, 0, buffer, 0, 16, 500) } returns 16

        val result = transportManager.safeControlTransfer(0x21, 1, 0, 0, buffer, 16, 500)

        assertEquals(16, result)
        assertEquals(0, transportManager.activeIoCount)
        verify(exactly = 1) { connection.controlTransfer(0x21, 1, 0, 0, buffer, 0, 16, 500) }
    }

    @Test
    fun `transfer on invalid file descriptor throws UsbTransportClosedException`() {
        every { connection.fileDescriptor } returns -1

        try {
            transportManager.safeBulkTransfer(endpointIn, ByteArray(32), 32, 1000)
            fail("Expected UsbTransportClosedException when fd == -1")
        } catch (e: UsbTransportClosedException) {
            assertTrue(transportManager.isTerminated)
            assertEquals(0, transportManager.activeIoCount)
        }
    }

    @Test
    fun `beginDrainAndClose marks terminated and rejects subsequent transfers`() = runTest {
        assertFalse(transportManager.isTerminated)

        val drained = transportManager.beginDrainAndClose(500L)
        assertTrue(drained)
        assertTrue(transportManager.isTerminated)

        try {
            transportManager.safeBulkTransfer(endpointIn, ByteArray(32), 32, 1000)
            fail("Expected UsbTransportClosedException on terminated transport")
        } catch (e: UsbTransportClosedException) {
            assertEquals(0, transportManager.activeIoCount)
        }
    }

    @Test
    fun `beginDrainAndClose awaits in-flight transfers`() = runTest {
        val buffer = ByteArray(64)
        val inFlightLatch = java.util.concurrent.CountDownLatch(1)
        val releaseTransferLatch = java.util.concurrent.CountDownLatch(1)

        every { connection.bulkTransfer(endpointIn, buffer, 0, 64, any()) } answers {
            inFlightLatch.countDown()
            releaseTransferLatch.await(2, java.util.concurrent.TimeUnit.SECONDS)
            64
        }

        // Start transfer on IO dispatcher
        val deferredTransfer = async(Dispatchers.IO) {
            transportManager.safeBulkTransfer(endpointIn, buffer, 64, 1000)
        }

        // Await until transfer is actively inside the transfer method
        assertTrue(inFlightLatch.await(2, java.util.concurrent.TimeUnit.SECONDS))
        assertEquals(1, transportManager.activeIoCount)

        // Launch drain on IO dispatcher
        val drainJob = async(Dispatchers.IO) {
            transportManager.beginDrainAndClose(1000L)
        }

        // Release in-flight transfer
        releaseTransferLatch.countDown()

        val transferResult = deferredTransfer.await()
        assertEquals(64, transferResult)

        val drained = drainJob.await()
        assertTrue(drained)
        assertEquals(0, transportManager.activeIoCount)
    }
}
