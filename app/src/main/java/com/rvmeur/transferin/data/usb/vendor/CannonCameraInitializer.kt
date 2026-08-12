package com.rvmeur.transferin.data.usb.vendor

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.util.Log
import com.rvmeur.transferin.data.usb.CameraInitContext
import com.rvmeur.transferin.data.usb.CameraInitResult
import java.nio.ByteBuffer
import java.nio.ByteOrder

class CanonCameraInitializer : CameraVendorInitializer {

    companion object {
        private const val TAG = "CanonCameraInit"
        private const val CANON_VENDOR_ID = 0x04A9

        private const val PTP_CONTAINER_COMMAND = 1
        private const val PTP_CONTAINER_RESPONSE = 3

        private const val PTP_OC_OPEN_SESSION = 0x1002
        private const val PTP_OC_CLOSE_SESSION = 0x1003
        private const val EOS_OC_SET_REMOTE_MODE = 0x9114
        private const val EOS_OC_SET_EVENT_MODE = 0x9115

        private const val RESPONSE_OK = 0x2001

        private const val USB_TIMEOUT_MS = 5000
        private const val SESSION_ID = 1

        private const val REMOTE_MODE_ON = 1
        private const val REMOTE_MODE_OFF = 0
        private const val EVENT_MODE_ON = 1
    }

    private var activeConnection: UsbDeviceConnection? = null
    private var activeInterface: UsbInterface? = null
    private var activeOutEndpoint: UsbEndpoint? = null
    private var activeInEndpoint: UsbEndpoint? = null
    private var isSessionOpen: Boolean = false
    private var lastDeviceName: String? = null
    private var nextTransactionIdSeed: Int = 4

    override fun canHandle(vendorId: Int, productId: Int): Boolean {
        return vendorId == CANON_VENDOR_ID
    }

    override fun initialize(context: CameraInitContext): CameraInitResult {
        return initializeUnlocked(
            device = context.device,
            connection = context.connection
        )
    }

    fun initializeUnlocked(
        device: UsbDevice,
        connection: UsbDeviceConnection
    ): CameraInitResult {
        Log.d(TAG, ">>> Canon initializeUnlocked start: device=${device.deviceName}")

        if (isSessionOpen && lastDeviceName == device.deviceName) {
            Log.d(TAG, "Session already open for device=${device.deviceName}, skipping duplicate handshake")
            return CameraInitResult.ok("Canon session already initialized")
        }

        val usbInterface = findPtpInterface(device)
            ?: return CameraInitResult.fail("Canon init skipped: PTP interface not found")

        val outEndpoint = findBulkOutEndpoint(usbInterface)
            ?: return CameraInitResult.fail("Canon init skipped: bulk OUT endpoint not found")

        val inEndpoint = findBulkInEndpoint(usbInterface)
            ?: return CameraInitResult.fail("Canon init skipped: bulk IN endpoint not found")

        Log.d(
            TAG,
            "Canon interface=${usbInterface.id}, endpoints: out=${outEndpoint.address}, in=${inEndpoint.address}"
        )

        val claimed = connection.claimInterface(usbInterface, true)
        if (!claimed) {
            return CameraInitResult.fail("Canon init failed: claimInterface=false")
        }

        Log.d(TAG, "Interface claimed, starting Canon handshake")

        return try {
            sendPtpCommand(
                connection = connection,
                outEndpoint = outEndpoint,
                inEndpoint = inEndpoint,
                operationCode = PTP_OC_OPEN_SESSION,
                transactionId = 1,
                params = intArrayOf(SESSION_ID)
            )

            sendPtpCommand(
                connection = connection,
                outEndpoint = outEndpoint,
                inEndpoint = inEndpoint,
                operationCode = EOS_OC_SET_REMOTE_MODE,
                transactionId = 2,
                params = intArrayOf(REMOTE_MODE_ON)
            )

            sendPtpCommand(
                connection = connection,
                outEndpoint = outEndpoint,
                inEndpoint = inEndpoint,
                operationCode = EOS_OC_SET_EVENT_MODE,
                transactionId = 3,
                params = intArrayOf(EVENT_MODE_ON)
            )

            activeConnection = connection
            activeInterface = usbInterface
            activeOutEndpoint = outEndpoint
            activeInEndpoint = inEndpoint
            isSessionOpen = true
            lastDeviceName = device.deviceName
            nextTransactionIdSeed = 4

            Log.d(TAG, ">>> Canon handshake completed, session kept open for tethered mode")
            return CameraInitResult.ok("Canon handshake completed")
        } catch (t: Throwable) {
            Log.e(TAG, "Canon handshake error", t)
            safeReleaseInterface(connection, usbInterface)
            clearActiveSessionState()
            return CameraInitResult.fail("Canon handshake error: ${t.message}")
        }
    }

    fun getActivePtpSession(): CanonPtpSession? {
        val connection = activeConnection ?: return null
        val usbInterface = activeInterface ?: return null
        val outEndpoint = activeOutEndpoint ?: return null
        val inEndpoint = activeInEndpoint ?: return null
        val deviceName = lastDeviceName ?: return null
        if (!isSessionOpen) return null

        return CanonPtpSession(
            deviceName = deviceName,
            connection = connection,
            usbInterface = usbInterface,
            outEndpoint = outEndpoint,
            inEndpoint = inEndpoint,
            sessionId = SESSION_ID,
            nextTransactionId = nextTransactionIdSeed
        )
    }

    fun hasActiveSessionFor(deviceName: String?): Boolean {
        return isSessionOpen && lastDeviceName != null && lastDeviceName == deviceName
    }

    fun closeSession(
        device: UsbDevice,
        connection: UsbDeviceConnection
    ) {
        Log.d(TAG, ">>> Canon closeSession: device=${device.deviceName}")

        val usbInterface = activeInterface ?: findPtpInterface(device)
        val outEndpoint = activeOutEndpoint ?: usbInterface?.let { findBulkOutEndpoint(it) }
        val inEndpoint = activeInEndpoint ?: usbInterface?.let { findBulkInEndpoint(it) }

        if (usbInterface == null || outEndpoint == null || inEndpoint == null) {
            Log.w(TAG, "closeSession skipped: active Canon session resources not available")
            clearActiveSessionState()
            return
        }

        try {
            if (!isSessionOpen) {
                Log.d(TAG, "closeSession skipped: session already closed")
                return
            }

            sendPtpCommand(
                connection = connection,
                outEndpoint = outEndpoint,
                inEndpoint = inEndpoint,
                operationCode = EOS_OC_SET_REMOTE_MODE,
                transactionId = 100,
                params = intArrayOf(REMOTE_MODE_OFF)
            )

            sendPtpCommand(
                connection = connection,
                outEndpoint = outEndpoint,
                inEndpoint = inEndpoint,
                operationCode = PTP_OC_CLOSE_SESSION,
                transactionId = 101,
                params = intArrayOf(SESSION_ID)
            )

            Log.d(TAG, "Canon session closed cleanly")
        } catch (t: Throwable) {
            Log.w(TAG, "Error during Canon closeSession", t)
        } finally {
            safeReleaseInterface(connection, usbInterface)
            clearActiveSessionState()
            Log.d(TAG, "Interface released")
        }
    }

    private fun sendPtpCommand(
        connection: UsbDeviceConnection,
        outEndpoint: UsbEndpoint,
        inEndpoint: UsbEndpoint,
        operationCode: Int,
        transactionId: Int,
        params: IntArray = intArrayOf()
    ) {
        val packetLength = 12 + params.size * 4
        val commandPacket = ByteBuffer.allocate(packetLength)
            .order(ByteOrder.LITTLE_ENDIAN)
            .apply {
                putInt(packetLength)
                putShort(PTP_CONTAINER_COMMAND.toShort())
                putShort(operationCode.toShort())
                putInt(transactionId)
                params.forEach { putInt(it) }
            }
            .array()

        val sent = connection.bulkTransfer(
            outEndpoint,
            commandPacket,
            commandPacket.size,
            USB_TIMEOUT_MS
        )
        if (sent != commandPacket.size) {
            throw IllegalStateException("Bulk OUT failed: sent=$sent expected=${commandPacket.size}")
        }

        val responseBuffer = ByteArray(512)
        val received = connection.bulkTransfer(
            inEndpoint,
            responseBuffer,
            responseBuffer.size,
            USB_TIMEOUT_MS
        )
        if (received < 12) {
            throw IllegalStateException("Bulk IN invalid: received=$received")
        }

        val bb = ByteBuffer.wrap(responseBuffer, 0, received).order(ByteOrder.LITTLE_ENDIAN)
        bb.int
        val containerType = bb.short.toInt() and 0xFFFF
        val responseCode = bb.short.toInt() and 0xFFFF
        val responseTxId = bb.int

        if (containerType != PTP_CONTAINER_RESPONSE) {
            throw IllegalStateException("Expected response container, got type=$containerType")
        }

        if (responseTxId != transactionId) {
            throw IllegalStateException("Transaction mismatch: expected=$transactionId actual=$responseTxId")
        }

        if (responseCode != RESPONSE_OK) {
            throw IllegalStateException(
                "PTP command failed: op=0x${operationCode.toString(16)}, code=0x${responseCode.toString(16)}"
            )
        }
    }

    private fun findPtpInterface(device: UsbDevice): UsbInterface? {
        for (i in 0 until device.interfaceCount) {
            val intf = device.getInterface(i)
            if (intf.interfaceClass == UsbConstants.USB_CLASS_STILL_IMAGE) {
                return intf
            }
        }

        for (i in 0 until device.interfaceCount) {
            val intf = device.getInterface(i)
            if (intf.endpointCount > 0) {
                return intf
            }
        }

        return null
    }

    private fun findBulkOutEndpoint(usbInterface: UsbInterface): UsbEndpoint? {
        for (i in 0 until usbInterface.endpointCount) {
            val endpoint = usbInterface.getEndpoint(i)
            if (endpoint.type == UsbConstants.USB_ENDPOINT_XFER_BULK &&
                endpoint.direction == UsbConstants.USB_DIR_OUT
            ) {
                return endpoint
            }
        }
        return null
    }

    private fun findBulkInEndpoint(usbInterface: UsbInterface): UsbEndpoint? {
        for (i in 0 until usbInterface.endpointCount) {
            val endpoint = usbInterface.getEndpoint(i)
            if (endpoint.type == UsbConstants.USB_ENDPOINT_XFER_BULK &&
                endpoint.direction == UsbConstants.USB_DIR_IN
            ) {
                return endpoint
            }
        }
        return null
    }

    private fun safeReleaseInterface(
        connection: UsbDeviceConnection,
        usbInterface: UsbInterface?
    ) {
        if (usbInterface == null) return
        runCatching {
            connection.releaseInterface(usbInterface)
        }.onFailure {
            Log.w(TAG, "Failed to release interface", it)
        }
    }

    private fun clearActiveSessionState() {
        activeConnection = null
        activeInterface = null
        activeOutEndpoint = null
        activeInEndpoint = null
        isSessionOpen = false
        lastDeviceName = null
        nextTransactionIdSeed = 4
    }
}