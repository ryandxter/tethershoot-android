package com.rvmeur.transferin.data.usb.vendor.nikon

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.util.Log
import com.rvmeur.transferin.data.usb.core.DirectByteBufferPool
import com.rvmeur.transferin.data.usb.core.SafeUsbTransportManager
import com.rvmeur.transferin.data.usb.model.EmbeddedPreviewResult
import com.rvmeur.transferin.data.usb.model.ExifOrientationHelper
import com.rvmeur.transferin.data.usb.vendor.CameraTetherAdapter
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Nikon PTP Adapter (VID 0x04B0).
 *
 * Implements native USB PTP tethering for Nikon cameras with vendor event mode (0x90C0/0x90C1),
 * discrete dual-slot card mapping, and adaptive exponential backoff retry for EXPEED buffer flushes.
 */
class NikonPtpAdapter(
    val device: UsbDevice,
    val connection: UsbDeviceConnection,
    val bufferPool: DirectByteBufferPool = DirectByteBufferPool.Default,
    val retryPolicy: NikonRetryPolicy = NikonRetryPolicy(),
    transportManagerOverride: SafeUsbTransportManager? = null,
    outEndpointOverride: UsbEndpoint? = null,
    inEndpointOverride: UsbEndpoint? = null,
    interfaceOverride: UsbInterface? = null
) : CameraTetherAdapter {

    companion object {
        private const val TAG = "NikonPtpAdapter"
        private const val NIKON_SESSION_ID = 1
    }

    override val vendorId: Int = NikonPtpConstants.VID_NIKON

    val storageMapper = NikonStorageMapper()

    private val transportManager: SafeUsbTransportManager =
        transportManagerOverride ?: SafeUsbTransportManager(connection)

    private var usbInterface: UsbInterface? = interfaceOverride ?: findPtpInterface(device)
    private var outEndpoint: UsbEndpoint? = outEndpointOverride ?: usbInterface?.let { findBulkOutEndpoint(it) }
    private var inEndpoint: UsbEndpoint? = inEndpointOverride ?: usbInterface?.let { findBulkInEndpoint(it) }

    private val txIdCounter = AtomicInteger(1)
    private val isSessionOpenFlag = AtomicBoolean(false)
    val isSessionOpen: Boolean get() = isSessionOpenFlag.get()

    private val sessionJob: CompletableJob = SupervisorJob()
    private val sessionScope = CoroutineScope(sessionJob + Dispatchers.IO)
    private var eventPollJob: Job? = null

    private val _events = MutableSharedFlow<NikonPtpEvent>(replay = 16, extraBufferCapacity = 64)
    val events: SharedFlow<NikonPtpEvent> = _events.asSharedFlow()

    private var transport: NikonPtpTransport? = null

    init {
        if (outEndpoint != null && inEndpoint != null) {
            transport = NikonPtpTransport(
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
            Log.d(TAG, "Nikon session already open for ${device.deviceName}")
            return@withContext true
        }

        Log.d(TAG, "Starting Nikon PTP session for ${device.deviceName}...")

        val iface = usbInterface ?: findPtpInterface(device) ?: run {
            Log.e(TAG, "Failed to find PTP USB interface for Nikon device")
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

        val activeTransport = NikonPtpTransport(
            transportManager = transportManager,
            outEndpoint = outEp,
            inEndpoint = inEp,
            bufferPool = bufferPool
        )
        transport = activeTransport

        try {
            // Step 1: OpenSession (0x1002)
            retryPolicy.executeWithRetry("OpenSession") {
                val resp = activeTransport.transact(
                    operationCode = NikonPtpConstants.OC_OPEN_SESSION,
                    transactionId = nextTxId(),
                    params = intArrayOf(NIKON_SESSION_ID)
                )
                if (resp.responseCode != NikonPtpConstants.RC_OK) {
                    throw IOException("OpenSession failed: 0x${resp.responseCode.toString(16)}")
                }
            }

            // Step 2: SetEventMode (0x90C1) -> Mode 1 (ON)
            retryPolicy.executeWithRetry("SetEventMode(ON)") {
                val resp = activeTransport.transact(
                    operationCode = NikonPtpConstants.OC_NIKON_SET_EVENT_MODE,
                    transactionId = nextTxId(),
                    params = intArrayOf(NikonPtpConstants.EVENT_MODE_ON)
                )
                if (resp.responseCode != NikonPtpConstants.RC_OK) {
                    throw IOException("SetEventMode failed: 0x${resp.responseCode.toString(16)}")
                }
            }

            isSessionOpenFlag.set(true)
            startEventPolling(activeTransport)

            Log.i(TAG, "Nikon PTP session initialized successfully")
            true
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to initialize Nikon PTP session", t)
            safeReleaseInterface()
            false
        }
    }

    private fun startEventPolling(activeTransport: NikonPtpTransport) {
        eventPollJob?.cancel()
        eventPollJob = sessionScope.launch {
            Log.d(TAG, "Starting Nikon event polling loop...")
            while (isActive && isSessionOpenFlag.get()) {
                try {
                    val polledEvents = activeTransport.checkEvents(nextTxId())
                    for (event in polledEvents) {
                        Log.d(TAG, "Nikon event received: code=0x${event.eventCode.toString(16)}, param1=0x${event.param1.toString(16)}")
                        _events.emit(event)
                    }
                } catch (e: NikonDeviceBusyException) {
                    // EXPEED busy during event check; yield and retry next cycle
                } catch (e: Throwable) {
                    if (isActive && isSessionOpenFlag.get()) {
                        Log.v(TAG, "Event poll check encountered: ${e.message}")
                    }
                }
                delay(NikonPtpConstants.EVENT_POLL_INTERVAL_MS)
            }
        }
    }

    override suspend fun getStorageIds(): List<Int> = withContext(Dispatchers.IO) {
        val activeTransport = transport ?: return@withContext emptyList()
        try {
            retryPolicy.executeWithRetry("GetStorageIDs") {
                val resp = activeTransport.transact(
                    operationCode = NikonPtpConstants.OC_GET_STORAGE_IDS,
                    transactionId = nextTxId()
                )
                if (resp.responseCode != NikonPtpConstants.RC_OK) {
                    throw IOException("GetStorageIDs failed: 0x${resp.responseCode.toString(16)}")
                }
                decodeUInt32Array(resp.dataPayload)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Exception in Nikon getStorageIds", t)
            emptyList()
        }
    }

    override suspend fun getObjectHandles(storageId: Int): List<Int> = withContext(Dispatchers.IO) {
        val activeTransport = transport ?: return@withContext emptyList()
        try {
            retryPolicy.executeWithRetry("GetObjectHandles(storageId=$storageId)") {
                val resp = activeTransport.transact(
                    operationCode = NikonPtpConstants.OC_GET_OBJECT_HANDLES,
                    transactionId = nextTxId(),
                    params = intArrayOf(storageId, 0x0000, 0x0000)
                )
                if (resp.responseCode != NikonPtpConstants.RC_OK) {
                    throw IOException("GetObjectHandles failed: 0x${resp.responseCode.toString(16)}")
                }
                val handles = decodeUInt32Array(resp.dataPayload)
                Log.d(TAG, "Nikon getObjectHandles(storageId=$storageId): found ${handles.size} objects")
                handles
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Exception in Nikon getObjectHandles", t)
            emptyList()
        }
    }

    override suspend fun extractPreview(handle: Int): ByteArray? = withContext(Dispatchers.IO) {
        val activeTransport = transport ?: return@withContext null
        try {
            retryPolicy.executeWithRetry("GetThumb(handle=$handle)") {
                val resp = activeTransport.transact(
                    operationCode = NikonPtpConstants.OC_GET_THUMB,
                    transactionId = nextTxId(),
                    params = intArrayOf(handle)
                )
                if (resp.responseCode == NikonPtpConstants.RC_OK && resp.dataPayload != null) {
                    val data = resp.dataPayload
                    if (data.size >= 2 && data[0] == 0xFF.toByte() && data[1] == 0xD8.toByte()) {
                        return@executeWithRetry data
                    }
                }
                null
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to extract Nikon preview for handle $handle", t)
            null
        }
    }

    /**
     * Extracts unified [EmbeddedPreviewResult] representation for a Nikon object handle.
     */
    suspend fun extractUnifiedPreview(handle: Int): EmbeddedPreviewResult? = withContext(Dispatchers.IO) {
        val startNs = System.nanoTime()
        val data = extractPreview(handle) ?: return@withContext null
        val elapsedMs = (System.nanoTime() - startNs) / 1_000_000L
        val orientation = ExifOrientationHelper.extractOrientation(data)
        EmbeddedPreviewResult(
            jpegBytes = data,
            orientation = orientation,
            format = "NEF",
            parseTimeMs = elapsedMs
        )
    }

    override suspend fun closeSession() = withContext(Dispatchers.IO + NonCancellable) {
        if (!isSessionOpenFlag.compareAndSet(true, false)) {
            return@withContext
        }

        Log.d(TAG, "Executing Nikon clean teardown sequence...")
        eventPollJob?.cancel()

        val activeTransport = transport
        if (activeTransport != null) {
            // Step 1: SetEventMode(OFF)
            runCatching {
                activeTransport.transact(
                    operationCode = NikonPtpConstants.OC_NIKON_SET_EVENT_MODE,
                    transactionId = nextTxId(),
                    params = intArrayOf(NikonPtpConstants.EVENT_MODE_OFF)
                )
            }.onFailure { Log.w(TAG, "Error turning off event mode", it) }

            // Step 2: CloseSession
            runCatching {
                activeTransport.transact(
                    operationCode = NikonPtpConstants.OC_CLOSE_SESSION,
                    transactionId = nextTxId(),
                    params = intArrayOf(NIKON_SESSION_ID)
                )
            }.onFailure { Log.w(TAG, "Error closing Nikon session", it) }
        }

        sessionJob.cancel()
        safeReleaseInterface()
        Log.i(TAG, "Nikon session closed cleanly")
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
