package com.rvmeur.transferin.data.usb.core

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbInterface
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class UsbHardwareSessionTest {

    private lateinit var device: UsbDevice
    private lateinit var connection: UsbDeviceConnection
    private lateinit var usbInterface1: UsbInterface
    private lateinit var usbInterface2: UsbInterface
    private lateinit var transportManager: SafeUsbTransportManager
    private lateinit var session: UsbHardwareSession

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
        usbInterface1 = mockk(relaxed = true)
        usbInterface2 = mockk(relaxed = true)
        transportManager = mockk(relaxed = true)

        every { device.deviceName } returns "/dev/bus/usb/001/002"
        every { device.vendorId } returns 0x04CB // Fujifilm
        every { device.productId } returns 0x02DF
        every { usbInterface1.id } returns 0
        every { usbInterface2.id } returns 1

        every { connection.claimInterface(any(), any()) } returns true
        every { connection.releaseInterface(any()) } returns true
        coEvery { transportManager.beginDrainAndClose(any()) } returns true

        session = UsbHardwareSession(
            device = device,
            connection = connection,
            transportManagerOverride = transportManager
        )
    }

    @org.junit.After
    fun tearDown() {
        io.mockk.unmockkStatic(android.util.Log::class)
    }

    @Test
    fun `initial state is Connected with device metadata`() {
        val state = session.state.value
        assertTrue(state is HardwareSessionState.Connected)
        val connected = state as HardwareSessionState.Connected
        assertEquals("/dev/bus/usb/001/002", connected.deviceName)
        assertEquals(0x04CB, connected.vendorId)
        assertEquals(0x02DF, connected.productId)
        assertFalse(session.isClosed)
    }

    @Test
    fun `state transitions occur deterministically`() {
        session.transitionTo(HardwareSessionState.Handshake("PTP_INIT"))
        assertTrue(session.state.value is HardwareSessionState.Handshake)
        assertEquals("PTP_INIT", (session.state.value as HardwareSessionState.Handshake).stage)

        session.transitionTo(HardwareSessionState.ActiveStreaming(activeTransfers = 2))
        assertTrue(session.state.value is HardwareSessionState.ActiveStreaming)
        assertEquals(2, (session.state.value as HardwareSessionState.ActiveStreaming).activeTransfers)
    }

    @Test
    fun `claim and release interfaces tracked properly`() {
        val claimed1 = session.claimInterface(usbInterface1)
        val claimed2 = session.claimInterface(usbInterface2)

        assertTrue(claimed1)
        assertTrue(claimed2)
        assertEquals(2, session.claimedInterfaces.size)
        assertTrue(session.claimedInterfaces.contains(usbInterface1))
        assertTrue(session.claimedInterfaces.contains(usbInterface2))

        val released1 = session.releaseInterface(usbInterface1)
        assertTrue(released1)
        assertEquals(1, session.claimedInterfaces.size)
        assertFalse(session.claimedInterfaces.contains(usbInterface1))
    }

    @Test
    fun `disconnect executes 5-step clean teardown and transitions to CleanTerminated`() = runTest {
        session.claimInterface(usbInterface1)
        session.claimInterface(usbInterface2)

        var vendorTeardownExecuted = false
        session.onVendorTeardown = {
            vendorTeardownExecuted = true
        }

        session.disconnect(reason = "User unmounted")

        assertTrue(session.isClosed)
        assertTrue(session.state.value is HardwareSessionState.CleanTerminated)
        assertTrue("Vendor teardown callback must be executed", vendorTeardownExecuted)

        coVerify(exactly = 1) { transportManager.beginDrainAndClose(any()) }
        verify(exactly = 1) { connection.releaseInterface(usbInterface1) }
        verify(exactly = 1) { connection.releaseInterface(usbInterface2) }
        verify(exactly = 1) { connection.close() }
        assertEquals(0, session.claimedInterfaces.size)
    }

    @Test
    fun `disconnect is idempotent on duplicate calls`() = runTest {
        session.claimInterface(usbInterface1)

        session.disconnect("First call")
        session.disconnect("Second call")
        session.close()

        coVerify(exactly = 1) { transportManager.beginDrainAndClose(any()) }
        verify(exactly = 1) { connection.releaseInterface(usbInterface1) }
        verify(exactly = 1) { connection.close() }
    }

    @Test
    fun `teardown resilience - errors in interface release or vendor hook do not block connection close`() = runTest {
        session.claimInterface(usbInterface1)

        session.onVendorTeardown = {
            throw RuntimeException("Vendor teardown failed")
        }
        every { connection.releaseInterface(usbInterface1) } throws RuntimeException("Release interface failed")

        session.disconnect("Fault injection test")

        assertTrue(session.isClosed)
        assertTrue(session.state.value is HardwareSessionState.CleanTerminated)
        verify(exactly = 1) { connection.close() }
    }
}
