package com.rvmeur.transferin.data.usb.vendor

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import com.rvmeur.transferin.data.usb.canon.CanonPtpAdapter
import com.rvmeur.transferin.data.usb.canon.CanonPtpConstants
import com.rvmeur.transferin.data.usb.vendor.nikon.NikonPtpAdapter
import com.rvmeur.transferin.data.usb.vendor.nikon.NikonPtpConstants
import com.rvmeur.transferin.data.usb.vendor.sony.SonyPtpAdapter
import com.rvmeur.transferin.data.usb.vendor.sony.SonyPtpConstants
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CameraTetherAdapterFactoryTest {

    private lateinit var factory: CameraTetherAdapterFactory
    private lateinit var connection: UsbDeviceConnection

    @Before
    fun setUp() {
        io.mockk.mockkStatic(android.util.Log::class)
        every { android.util.Log.d(any(), any()) } returns 0
        every { android.util.Log.i(any(), any()) } returns 0
        every { android.util.Log.w(any(), any<String>()) } returns 0
        every { android.util.Log.e(any(), any()) } returns 0

        factory = CameraTetherAdapterFactory.Default
        connection = mockk(relaxed = true)
        every { connection.claimInterface(any(), any()) } returns true
        every { connection.fileDescriptor } returns 10
    }

    @org.junit.After
    fun tearDown() {
        io.mockk.unmockkStatic(android.util.Log::class)
    }

    @Test
    fun `createAdapter returns SonyPtpAdapter for Sony vendorId`() {
        val device: UsbDevice = mockk(relaxed = true)
        every { device.vendorId } returns SonyPtpConstants.VID_SONY

        val adapter = factory.createAdapter(device, connection)
        assertNotNull(adapter)
        assertTrue(adapter is SonyPtpAdapter)
    }

    @Test
    fun `createAdapter returns NikonPtpAdapter for Nikon vendorId`() {
        val device: UsbDevice = mockk(relaxed = true)
        every { device.vendorId } returns NikonPtpConstants.VID_NIKON

        val adapter = factory.createAdapter(device, connection)
        assertNotNull(adapter)
        assertTrue(adapter is NikonPtpAdapter)
    }

    @Test
    fun `createAdapter returns CanonPtpAdapter for Canon vendorId`() {
        val device: UsbDevice = mockk(relaxed = true)
        val usbInterface: UsbInterface = mockk(relaxed = true)
        val outEp: UsbEndpoint = mockk(relaxed = true)
        val inEp: UsbEndpoint = mockk(relaxed = true)

        every { device.vendorId } returns CanonPtpConstants.CANON_VENDOR_ID
        every { device.interfaceCount } returns 1
        every { device.getInterface(0) } returns usbInterface
        every { usbInterface.interfaceClass } returns UsbConstants.USB_CLASS_STILL_IMAGE
        every { usbInterface.endpointCount } returns 2
        every { usbInterface.getEndpoint(0) } returns outEp
        every { usbInterface.getEndpoint(1) } returns inEp

        every { outEp.type } returns UsbConstants.USB_ENDPOINT_XFER_BULK
        every { outEp.direction } returns UsbConstants.USB_DIR_OUT
        every { inEp.type } returns UsbConstants.USB_ENDPOINT_XFER_BULK
        every { inEp.direction } returns UsbConstants.USB_DIR_IN

        val adapter = factory.createAdapter(device, connection)
        assertNotNull(adapter)
        assertTrue(adapter is CanonPtpAdapter)
    }

    @Test
    fun `createAdapter returns null for unsupported vendorId`() {
        val device: UsbDevice = mockk(relaxed = true)
        every { device.vendorId } returns 0x1234

        val adapter = factory.createAdapter(device, connection)
        assertNull(adapter)
    }
}
