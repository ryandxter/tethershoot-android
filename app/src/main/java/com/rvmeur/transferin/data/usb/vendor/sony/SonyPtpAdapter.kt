package com.rvmeur.transferin.data.usb.vendor.sony

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.util.Log
import com.rvmeur.transferin.data.usb.core.DirectByteBufferPool
import com.rvmeur.transferin.data.usb.core.SafeUsbTransportManager
import com.rvmeur.transferin.data.usb.vendor.CameraTetherAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Sony Alpha PTP Adapter (VID 0x054C).
 *
 * Implements native USB PTP tethering with 3-phase SDIO handshake, multi-packet defragmentation,
 * DRAM safety guard (preventing volatile image loss), and TIFF SubIFD ARW preview parsing.
 */
class SonyPtpAdapter(
    val device: UsbDevice,
    val connection: UsbDeviceConnection,
    val bufferPool: DirectByteBufferPool = DirectByteBufferPool.Default,
    transportManagerOverride: SafeUsbTransportManager? = null,
    outEndpointOverride: UsbEndpoint? = null,
    inEndpointOverride: UsbEndpoint? = null,
    interfaceOverride: UsbInterface? = null
) : CameraTetherAdapter {

    companion object {
        private const val TAG = "SonyPtpAdapter"
        private const val SONY_SESSION_ID = 1
    }

    override val vendorId: Int = SonyPtpConstants.VID_SONY

    private val transportManager: SafeUsbTransportManager =
        transportManagerOverride ?: SafeUsbTransportManager(connection)

    private var usbInterface: UsbInterface? = interfaceOverride ?: findPtpInterface(device)
    private var outEndpoint: UsbEndpoint? = outEndpointOverride ?: usbInterface?.let { findBulkOutEndpoint(it) }
    private var inEndpoint: UsbEndpoint? = inEndpointOverride ?: usbInterface?.let { findBulkInEndpoint(it) }

    private val txIdCounter = AtomicInteger(1)
    private val isSessionOpenFlag = AtomicBoolean(false)

    val isSessionOpen: Boolean get() = isSessionOpenFlag.get()

    private val dramSafetyGuard = SonyDramSafetyGuard()
    private val arwParser = ArwPreviewParser()

    private val _dramStatus = MutableStateFlow<SonyDramSafetyStatus?>(null)
    val dramStatus: StateFlow<SonyDramSafetyStatus?> = _dramStatus.asStateFlow()

    private var transport: SonyPtpTransport? = null

    init {
        if (outEndpoint != null && inEndpoint != null) {
            transport = SonyPtpTransport(
                transportManager = transportManager,
                outEndpoint = outEndpoint!!,
                inEndpoint = inEndpoint!!,
                bufferPool = bufferPool
            )
        }
    }

    private fun nextTxId(): Int = txIdCounter.getAndIncrement()

    override suspend fun startSession(): Boolean = withContext(Dispatchers.IO) {
        if (isSessionOpenFlag.get()) {
            Log.d(TAG, "Sony session already open for ${device.deviceName}")
            return@withContext true
        }

        Log.d(TAG, "Starting Sony SDIO PTP handshake for ${device.deviceName}...")

        val iface = usbInterface ?: findPtpInterface(device) ?: run {
            Log.e(TAG, "Failed to find PTP USB interface for Sony device")
            return@withContext false
        }
        usbInterface = iface

        val claimed = connection.claimInterface(iface, true)
        if (!claimed) {
            Log.e(TAG, "Failed to claim USB interface ${iface.id}")
            return@withContext false
        }

        val outEp = outEndpoint ?: findBulkOutEndpoint(iface) ?: run {
            Log.e(TAG, "Failed to find Bulk OUT endpoint")
            return@withContext false
        }
        val inEp = inEndpoint ?: findBulkInEndpoint(iface) ?: run {
            Log.e(TAG, "Failed to find Bulk IN endpoint")
            return@withContext false
        }
        outEndpoint = outEp
        inEndpoint = inEp

        val activeTransport = SonyPtpTransport(
            transportManager = transportManager,
            outEndpoint = outEp,
            inEndpoint = inEp,
            bufferPool = bufferPool
        )
        transport = activeTransport

        try {
            // Step 1: Open standard PTP Session
            Log.d(TAG, "Handshake Step 1: OpenSession(sessionId=$SONY_SESSION_ID)")
            val openSessionResp = activeTransport.transact(
                operationCode = SonyPtpConstants.OC_OPEN_SESSION,
                transactionId = nextTxId(),
                params = intArrayOf(SONY_SESSION_ID)
            )
            if (openSessionResp.responseCode != SonyPtpConstants.RC_OK) {
                Log.e(TAG, "OpenSession failed: 0x${openSessionResp.responseCode.toString(16)}")
                return@withContext false
            }

            // Step 2: Sony SDIOConnect Phase 1 (1, 0, 0)
            Log.d(TAG, "Handshake Step 2: SDIOConnect Phase 1 (1, 0, 0)")
            val sdio1Resp = activeTransport.transact(
                operationCode = SonyPtpConstants.OC_SONY_SDIO_CONNECT,
                transactionId = nextTxId(),
                params = intArrayOf(SonyPtpConstants.SDIO_CONNECT_PHASE_1, 0, 0)
            )
            if (sdio1Resp.responseCode != SonyPtpConstants.RC_OK) {
                Log.e(TAG, "SDIOConnect Phase 1 failed: 0x${sdio1Resp.responseCode.toString(16)}")
                return@withContext false
            }

            // Step 3: Sony SDIOConnect Phase 2 (2, 0, 0)
            Log.d(TAG, "Handshake Step 3: SDIOConnect Phase 2 (2, 0, 0)")
            val sdio2Resp = activeTransport.transact(
                operationCode = SonyPtpConstants.OC_SONY_SDIO_CONNECT,
                transactionId = nextTxId(),
                params = intArrayOf(SonyPtpConstants.SDIO_CONNECT_PHASE_2, 0, 0)
            )
            if (sdio2Resp.responseCode != SonyPtpConstants.RC_OK) {
                Log.e(TAG, "SDIOConnect Phase 2 failed: 0x${sdio2Resp.responseCode.toString(16)}")
                return@withContext false
            }

            // Step 4: GetSDIOExtDeviceInfo
            Log.d(TAG, "Handshake Step 4: GetSDIOExtDeviceInfo")
            val extInfoResp = activeTransport.transact(
                operationCode = SonyPtpConstants.OC_SONY_GET_SDIO_EXT_DEVICE_INFO,
                transactionId = nextTxId(),
                params = intArrayOf(0x00000000)
            )
            Log.d(TAG, "GetSDIOExtDeviceInfo response: 0x${extInfoResp.responseCode.toString(16)}")

            // Step 5: Enforce DRAM Safety Guard (prevent volatile PC Only DRAM loss)
            val safetyStatus = dramSafetyGuard.verifyAndEnforceSafety(
                transport = activeTransport,
                transactionIdProvider = ::nextTxId
            )
            _dramStatus.value = safetyStatus
            Log.i(TAG, "Sony DRAM Safety Status: $safetyStatus")

            isSessionOpenFlag.set(true)
            Log.i(TAG, "Sony Alpha SDIO PTP handshake completed successfully")
            true
        } catch (t: Throwable) {
            Log.e(TAG, "Error during Sony SDIO handshake", t)
            safeReleaseInterface()
            false
        }
    }

    override suspend fun getStorageIds(): List<Int> = withContext(Dispatchers.IO) {
        val activeTransport = transport ?: return@withContext emptyList()
        try {
            val response = activeTransport.transact(
                operationCode = SonyPtpConstants.OC_GET_STORAGE_IDS,
                transactionId = nextTxId()
            )
            if (response.responseCode != SonyPtpConstants.RC_OK) {
                Log.e(TAG, "GetStorageIds failed: 0x${response.responseCode.toString(16)}")
                return@withContext emptyList()
            }
            decodeUInt32Array(response.dataPayload)
        } catch (t: Throwable) {
            Log.e(TAG, "Exception in getStorageIds", t)
            emptyList()
        }
    }

    override suspend fun getObjectHandles(storageId: Int): List<Int> = withContext(Dispatchers.IO) {
        val activeTransport = transport ?: return@withContext emptyList()
        try {
            val response = activeTransport.transact(
                operationCode = SonyPtpConstants.OC_GET_OBJECT_HANDLES,
                transactionId = nextTxId(),
                params = intArrayOf(storageId, 0x0000, 0x0000)
            )
            if (response.responseCode != SonyPtpConstants.RC_OK) {
                Log.e(TAG, "GetObjectHandles failed: 0x${response.responseCode.toString(16)}")
                return@withContext emptyList()
            }
            val handles = decodeUInt32Array(response.dataPayload)
            Log.d(TAG, "Sony getObjectHandles(storageId=$storageId): found ${handles.size} objects")
            handles
        } catch (t: Throwable) {
            Log.e(TAG, "Exception in getObjectHandles", t)
            emptyList()
        }
    }

    override suspend fun extractPreview(handle: Int): ByteArray? = withContext(Dispatchers.IO) {
        val activeTransport = transport ?: return@withContext null
        try {
            // Attempt 1: GetThumb (0x100A)
            val thumbResp = activeTransport.transact(
                operationCode = SonyPtpConstants.OC_GET_THUMB,
                transactionId = nextTxId(),
                params = intArrayOf(handle)
            )
            if (thumbResp.responseCode == SonyPtpConstants.RC_OK && thumbResp.dataPayload != null) {
                val thumbData = thumbResp.dataPayload
                if (thumbData.size >= 2 && thumbData[0] == 0xFF.toByte() && thumbData[1] == 0xD8.toByte()) {
                    return@withContext thumbData
                }
            }

            // Attempt 2: GetObject (0x1009) and parse via ArwPreviewParser
            val objResp = activeTransport.transact(
                operationCode = SonyPtpConstants.OC_GET_OBJECT,
                transactionId = nextTxId(),
                params = intArrayOf(handle)
            )
            if (objResp.responseCode == SonyPtpConstants.RC_OK && objResp.dataPayload != null) {
                return@withContext arwParser.extractPreview(objResp.dataPayload)
            }

            null
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to extract preview for handle $handle", t)
            null
        }
    }

    override suspend fun closeSession() = withContext(Dispatchers.IO + NonCancellable) {
        if (!isSessionOpenFlag.compareAndSet(true, false)) {
            return@withContext
        }

        Log.d(TAG, "Executing Sony clean teardown sequence...")
        val activeTransport = transport
        if (activeTransport != null) {
            // Step 1: SDIOClose
            runCatching {
                activeTransport.transact(
                    operationCode = SonyPtpConstants.OC_SONY_SDIO_CLOSE,
                    transactionId = nextTxId(),
                    params = intArrayOf(0, 0, 0)
                )
            }.onFailure { Log.w(TAG, "SDIOClose error", it) }

            // Step 2: CloseSession
            runCatching {
                activeTransport.transact(
                    operationCode = SonyPtpConstants.OC_CLOSE_SESSION,
                    transactionId = nextTxId(),
                    params = intArrayOf(SONY_SESSION_ID)
                )
            }.onFailure { Log.w(TAG, "CloseSession error", it) }
        }

        safeReleaseInterface()
        Log.i(TAG, "Sony session closed cleanly")
    }

    override fun close() {
        if (isSessionOpenFlag.get()) {
            runBlocking {
                closeSession()
            }
        }
    }

    private fun safeReleaseInterface() {
        val iface = usbInterface ?: return
        runCatching {
            connection.releaseInterface(iface)
        }.onFailure {
            Log.w(TAG, "Error releasing interface", it)
        }
    }

    private fun decodeUInt32Array(payload: ByteArray?): List<Int> {
        if (payload == null || payload.size < 4) return emptyList()
        val bb = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)
        val count = bb.int
        if (count <= 0) return emptyList()

        val list = ArrayList<Int>(minOf(count, 10000))
        repeat(count) {
            if (bb.remaining() >= 4) {
                list.add(bb.int)
            }
        }
        return list
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
}
