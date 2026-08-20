package com.rvmeur.transferin.data.usb.vendor.fuji

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.util.Log
import com.rvmeur.transferin.data.usb.core.DirectByteBufferPool
import com.rvmeur.transferin.data.usb.core.SafeUsbTransportManager
import com.rvmeur.transferin.domain.camera.CameraMediaMetadata
import com.rvmeur.transferin.domain.camera.CameraTetherAdapter
import com.rvmeur.transferin.domain.camera.CameraTetherEvent
import com.rvmeur.transferin.domain.camera.CameraVendor
import com.rvmeur.transferin.domain.camera.TetherDeviceInfo
import com.rvmeur.transferin.domain.camera.TetherState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Native Fujifilm PTP Tether Adapter (VID 0x04CB).
 *
 * Implements direct PTP Bulk and Interrupt endpoint communication, Host Control handshakes,
 * sub-15ms interrupt event ingestion, and deterministic non-cancellable 4-step teardown
 * to eliminate camera LCD "BUSY" firmware freezes.
 */
class FujiTetherAdapter(
    private val bufferPool: DirectByteBufferPool = DirectByteBufferPool.Default,
    private val transportManagerOverride: SafeUsbTransportManager? = null
) : CameraTetherAdapter {

    companion object {
        private const val TAG = "FujiTetherAdapter"
        private const val DEFAULT_TIMEOUT_MS = 5000
        private const val PARTIAL_HEADER_SIZE = 131072L // 128 KB
    }

    override val vendor: CameraVendor = CameraVendor.FUJIFILM
    override var deviceName: String = "Fujifilm Camera"
        private set

    private val _connectionState = MutableStateFlow<TetherState>(TetherState.Idle)
    override val connectionState: StateFlow<TetherState> = _connectionState.asStateFlow()

    private val _events = MutableSharedFlow<CameraTetherEvent>(
        replay = 16,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    private var activeDevice: UsbDevice? = null
    private var activeConnection: UsbDeviceConnection? = null
    private var activeInterface: UsbInterface? = null

    private var bulkOutEndpoint: UsbEndpoint? = null
    private var bulkInEndpoint: UsbEndpoint? = null
    private var interruptInEndpoint: UsbEndpoint? = null

    private var transportManager: SafeUsbTransportManager? = null

    private val sessionJob = SupervisorJob()
    private val sessionScope = CoroutineScope(sessionJob + Dispatchers.IO)
    private var interruptJob: Job? = null

    private val isClosedFlag = AtomicBoolean(false)
    private val txIdCounter = AtomicInteger(1)

    override fun canHandle(device: UsbDevice): Boolean {
        return device.vendorId == FujiPtpConstants.FUJI_VENDOR_ID
    }

    override fun observeEvents(): Flow<CameraTetherEvent> = _events.asSharedFlow()

    override suspend fun initialize(
        device: UsbDevice,
        connection: UsbDeviceConnection
    ): Result<TetherDeviceInfo> = withContext(Dispatchers.IO) {
        runCatching {
            if (!canHandle(device)) {
                throw IllegalArgumentException("Unsupported device VID: 0x${device.vendorId.toString(16)}")
            }

            _connectionState.value = TetherState.Connecting
            activeDevice = device
            activeConnection = connection
            deviceName = device.productName ?: "Fujifilm ${device.deviceName}"

            val usbInterface = findPtpInterface(device)
                ?: throw IllegalStateException("No PTP / Still Image interface found for ${device.deviceName}")
            activeInterface = usbInterface

            val bulkOut = findBulkOutEndpoint(usbInterface)
                ?: throw IllegalStateException("No bulk OUT endpoint found on interface ${usbInterface.id}")
            val bulkIn = findBulkInEndpoint(usbInterface)
                ?: throw IllegalStateException("No bulk IN endpoint found on interface ${usbInterface.id}")
            val interruptIn = findInterruptInEndpoint(usbInterface)
                ?: findInterruptInEndpointAny(device)
                ?: throw IllegalStateException("No interrupt IN endpoint found on device ${device.deviceName}")

            bulkOutEndpoint = bulkOut
            bulkInEndpoint = bulkIn
            interruptInEndpoint = interruptIn

            val claimed = connection.claimInterface(usbInterface, true)
            if (!claimed) {
                throw IllegalStateException("Failed to claim USB interface ${usbInterface.id}")
            }

            val tm = transportManagerOverride ?: SafeUsbTransportManager(connection)
            transportManager = tm

            // Step 1: OpenSession (0x1002, SessionID = 1)
            _connectionState.value = TetherState.Handshake("0x1002 OpenSession")
            val openSessionResp = executePtpCommand(FujiPtpConstants.OC_OPEN_SESSION, intArrayOf(1))
            if (!openSessionResp.isOk) {
                throw IllegalStateException("OpenSession failed with code 0x${openSessionResp.responseCode.toString(16)}")
            }

            // Step 2: Engage Fuji Host Control (0xD214 = 0x0001)
            _connectionState.value = TetherState.Handshake("0x902B HostControl")
            val hostControlResp = executePtpCommand(
                FujiPtpConstants.OC_FUJI_SET_DEVICE_PROP,
                intArrayOf(FujiPtpConstants.PROP_FUJI_HOST_CONTROL, FujiPtpConstants.HOST_CONTROL_ACTIVE)
            )
            if (!hostControlResp.isOk) {
                Log.w(TAG, "Host Control 0xD214 returned code 0x${hostControlResp.responseCode.toString(16)}, proceeding")
            }

            // Step 3: Set Fuji Save Destination (0xD216 = 0x0002 [PC + SD Card])
            _connectionState.value = TetherState.Handshake("0x902B SaveDestination")
            val saveDestResp = executePtpCommand(
                FujiPtpConstants.OC_FUJI_SET_DEVICE_PROP,
                intArrayOf(FujiPtpConstants.PROP_FUJI_SAVE_DESTINATION, FujiPtpConstants.SAVE_DEST_PC_SD)
            )
            if (!saveDestResp.isOk) {
                Log.w(TAG, "Save Destination 0xD216 returned code 0x${saveDestResp.responseCode.toString(16)}, proceeding")
            }

            // Step 4: Launch Interrupt IN Event Drain Loop
            startInterruptDrainLoop(interruptIn, tm)

            _connectionState.value = TetherState.ActiveStreaming(0)

            TetherDeviceInfo(
                vendor = CameraVendor.FUJIFILM,
                modelName = deviceName,
                serialNumber = device.serialNumber ?: "",
                firmwareVersion = "FujiPtp-1.0",
                saveDestination = "PC+Card"
            )
        }.onFailure { error ->
            Log.e(TAG, "Fuji tether initialization failed", error)
            _connectionState.value = TetherState.Failed(error)
        }
    }

    private fun startInterruptDrainLoop(endpoint: UsbEndpoint, tm: SafeUsbTransportManager) {
        interruptJob = sessionScope.launch {
            val interruptBuffer = ByteArray(64)
            while (isActive && !isClosedFlag.get()) {
                try {
                    val received = tm.safeBulkTransfer(endpoint, interruptBuffer, interruptBuffer.size, 1000)
                    if (received >= 12) {
                        val event = FujiPtpConstants.parseEventPacket(interruptBuffer, received)
                        if (event != null) {
                            handlePtpEvent(event)
                        }
                    } else {
                        delay(10)
                    }
                } catch (e: Exception) {
                    if (!isActive || isClosedFlag.get()) break
                    delay(10)
                }
            }
        }
    }

    private fun handlePtpEvent(event: PtpEvent) {
        when (event.eventCode) {
            FujiPtpConstants.EVT_OBJECT_ADDED,
            FujiPtpConstants.EVT_FUJI_OBJECT_ADDED -> {
                val handle = if (event.params.isNotEmpty()) event.params[0] else 0
                val tetherEvent = CameraTetherEvent.ObjectAdded(
                    handle = handle,
                    filename = "DSCF_${String.format("%04d", handle and 0xFFFF)}.RAF",
                    formatCode = FujiPtpConstants.FMT_FUJI_RAF,
                    captureTimestampMs = System.currentTimeMillis()
                )
                _events.tryEmit(tetherEvent)
            }
            FujiPtpConstants.EVT_DEVICE_PROP_CHANGED -> {
                val propCode = if (event.params.isNotEmpty()) event.params[0] else 0
                _events.tryEmit(CameraTetherEvent.DevicePropChanged(propertyCode = propCode, value = 0L))
            }
        }
    }

    override suspend fun terminateSession(): Result<Unit> = withContext(Dispatchers.IO + NonCancellable) {
        if (!isClosedFlag.compareAndSet(false, true)) {
            Log.d(TAG, "terminateSession() already invoked")
            return@withContext Result.success(Unit)
        }

        _connectionState.value = TetherState.Disconnecting("User requested disconnect")
        Log.i(TAG, "Beginning 4-step clean teardown for Fuji camera $deviceName")

        withTimeoutOrNull(2500L) {
            // Step 1: Cancel Interrupt Event Drain Job
            runCatching {
                interruptJob?.cancel()
                interruptJob?.join()
                sessionJob.cancel()
                Log.d(TAG, "Step 1: Cancelled interrupt event drain coroutines")
            }

            // Step 2: Release Fuji Host Control Mode (0xD214 = 0x0000)
            // Critical: Unlocks camera LCD / returns firmware to live shooting mode
            runCatching {
                executePtpCommand(
                    FujiPtpConstants.OC_FUJI_SET_DEVICE_PROP,
                    intArrayOf(FujiPtpConstants.PROP_FUJI_HOST_CONTROL, FujiPtpConstants.HOST_CONTROL_RELEASE),
                    timeoutMs = 1000
                )
                Log.d(TAG, "Step 2: Fuji Host Control released (0xD214 = 0)")
            }.onFailure {
                Log.w(TAG, "Step 2: Failed to release Fuji host control", it)
            }

            // Step 3: CloseSession (0x1003, SessionID = 1)
            runCatching {
                executePtpCommand(
                    FujiPtpConstants.OC_CLOSE_SESSION,
                    intArrayOf(1),
                    timeoutMs = 1000
                )
                Log.d(TAG, "Step 3: PTP session closed (0x1003)")
            }.onFailure {
                Log.w(TAG, "Step 3: Failed to close PTP session", it)
            }

            // Step 4: Release claimed USB interface & close connection
            runCatching {
                activeInterface?.let { iface ->
                    activeConnection?.releaseInterface(iface)
                    Log.d(TAG, "Step 4: Released USB interface ${iface.id}")
                }
                activeConnection?.close()
                Log.d(TAG, "Step 4: Closed UsbDeviceConnection")
            }.onFailure {
                Log.w(TAG, "Step 4: Failed releasing USB connection", it)
            }
        }

        _connectionState.value = TetherState.CleanTerminated
        Log.i(TAG, "Fuji tether adapter terminated cleanly")
        Result.success(Unit)
    }

    override suspend fun getPreview(handle: Int): Result<ByteArray> = withContext(Dispatchers.IO) {
        runCatching {
            // First attempt to fetch the first 128KB header
            val headerBytes = fetchPartialObject(handle, offset = 0L, length = PARTIAL_HEADER_SIZE)
            val headerInfo = RafPreviewExtractor.parseRafHeader(headerBytes).getOrNull()

            if (headerInfo != null) {
                val jpegOffset = headerInfo.jpegOffset
                val jpegLength = headerInfo.jpegLength

                if (jpegOffset + jpegLength <= headerBytes.size) {
                    // Preview JPEG is completely inside initial 128KB slice
                    return@runCatching RafPreviewExtractor.extractEmbeddedJpeg(headerBytes).getOrThrow()
                }

                // If preview JPEG extends beyond initial 128KB, fetch targeted slice
                val jpegBytes = fetchPartialObject(handle, offset = jpegOffset, length = jpegLength)
                if (jpegBytes.size >= 2 && jpegBytes[0] == 0xFF.toByte() && jpegBytes[1] == 0xD8.toByte()) {
                    return@runCatching jpegBytes
                }
            }

            // Fallback: fetch thumb via 0x100A
            fetchThumb(handle)
        }
    }

    override suspend fun getObjectMetadata(handle: Int): Result<CameraMediaMetadata> = withContext(Dispatchers.IO) {
        runCatching {
            val headerBytes = fetchPartialObject(handle, offset = 0L, length = PARTIAL_HEADER_SIZE)
            val previewResult = RafPreviewExtractor.extractPreviewResult(headerBytes).getOrNull()

            CameraMediaMetadata(
                handle = handle,
                filename = "DSCF_${String.format("%04d", handle and 0xFFFF)}.RAF",
                formatCode = FujiPtpConstants.FMT_FUJI_RAF,
                sizeBytes = 0L,
                width = previewResult?.width ?: 0,
                height = previewResult?.height ?: 0,
                filmSimulation = previewResult?.filmSimulation
            )
        }
    }

    override suspend fun streamMedia(
        handle: Int,
        sink: OutputStream,
        onProgress: (Long, Long) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val tm = transportManager ?: throw IllegalStateException("Transport not initialized")
            val outEp = bulkOutEndpoint ?: throw IllegalStateException("Bulk OUT endpoint missing")
            val inEp = bulkInEndpoint ?: throw IllegalStateException("Bulk IN endpoint missing")

            val txId = nextTxId()
            val cmdPacket = FujiPtpConstants.buildCommandPacket(FujiPtpConstants.OC_GET_OBJECT, txId, intArrayOf(handle))
            val sent = tm.safeBulkTransfer(outEp, cmdPacket, cmdPacket.size, DEFAULT_TIMEOUT_MS)
            if (sent != cmdPacket.size) {
                throw IllegalStateException("Failed to send GetObject command: sent=$sent expected=${cmdPacket.size}")
            }

            val pooled = bufferPool.acquire(DirectByteBufferPool.LARGE_BUFFER_SIZE)
            pooled.use {
                val tempArray = ByteArray(DirectByteBufferPool.LARGE_BUFFER_SIZE)
                var totalRead = 0L

                val initialRead = tm.safeBulkTransfer(inEp, tempArray, tempArray.size, DEFAULT_TIMEOUT_MS * 2)
                if (initialRead < 12) {
                    throw IllegalStateException("Invalid data container header: $initialRead bytes")
                }

                val bb = ByteBuffer.wrap(tempArray, 0, initialRead).order(ByteOrder.LITTLE_ENDIAN)
                val containerLength = bb.int.toLong() and 0xFFFFFFFFL
                val containerType = bb.short.toInt() and 0xFFFF
                val opCode = bb.short.toInt() and 0xFFFF
                val respTxId = bb.int

                if (containerType != FujiPtpConstants.CONTAINER_DATA) {
                    throw IllegalStateException("Expected data container (2), got type $containerType")
                }

                val payloadLength = containerLength - 12
                val firstChunkDataLen = initialRead - 12
                if (firstChunkDataLen > 0) {
                    sink.write(tempArray, 12, firstChunkDataLen)
                    totalRead += firstChunkDataLen
                    onProgress(totalRead, payloadLength)
                }

                while (totalRead < payloadLength) {
                    val toRead = (payloadLength - totalRead).coerceAtMost(tempArray.size.toLong()).toInt()
                    val bytes = tm.safeBulkTransfer(inEp, tempArray, toRead, DEFAULT_TIMEOUT_MS * 2)
                    if (bytes <= 0) break
                    sink.write(tempArray, 0, bytes)
                    totalRead += bytes
                    onProgress(totalRead, payloadLength)
                }
                sink.flush()

                // Read response container
                val respBuffer = ByteArray(512)
                val respRead = tm.safeBulkTransfer(inEp, respBuffer, respBuffer.size, DEFAULT_TIMEOUT_MS)
                if (respRead >= 12) {
                    val resp = FujiPtpConstants.parseResponsePacket(respBuffer, respRead)
                    if (!resp.isOk) {
                        throw IllegalStateException("GetObject completed with error code 0x${resp.responseCode.toString(16)}")
                    }
                }
            }
        }
    }

    private fun fetchPartialObject(handle: Int, offset: Long, length: Long): ByteArray {
        val tm = transportManager ?: throw IllegalStateException("Transport not initialized")
        val outEp = bulkOutEndpoint ?: throw IllegalStateException("Bulk OUT endpoint missing")
        val inEp = bulkInEndpoint ?: throw IllegalStateException("Bulk IN endpoint missing")

        val txId = nextTxId()
        val cmdPacket = FujiPtpConstants.buildCommandPacket(
            FujiPtpConstants.OC_GET_PARTIAL_OBJECT,
            txId,
            intArrayOf(handle, offset.toInt(), length.toInt())
        )

        val sent = tm.safeBulkTransfer(outEp, cmdPacket, cmdPacket.size, DEFAULT_TIMEOUT_MS)
        if (sent != cmdPacket.size) {
            throw IllegalStateException("Bulk OUT failed: sent=$sent expected=${cmdPacket.size}")
        }

        // Read data container
        val buffer = ByteArray((length + 12).toInt().coerceAtMost(DirectByteBufferPool.SMALL_BUFFER_SIZE * 2))
        val read = tm.safeBulkTransfer(inEp, buffer, buffer.size, DEFAULT_TIMEOUT_MS)
        if (read < 12) {
            throw IllegalStateException("Invalid data response for GetPartialObject: read=$read")
        }

        val bb = ByteBuffer.wrap(buffer, 0, read).order(ByteOrder.LITTLE_ENDIAN)
        val totalLength = bb.int
        val containerType = bb.short.toInt() and 0xFFFF
        val opCode = bb.short.toInt() and 0xFFFF
        val respTxId = bb.int

        val payload = if (read > 12) buffer.copyOfRange(12, read) else ByteArray(0)

        // Read response container
        val respBuffer = ByteArray(512)
        val respRead = tm.safeBulkTransfer(inEp, respBuffer, respBuffer.size, DEFAULT_TIMEOUT_MS)
        if (respRead >= 12) {
            val resp = FujiPtpConstants.parseResponsePacket(respBuffer, respRead)
            if (!resp.isOk) {
                Log.w(TAG, "GetPartialObject returned code 0x${resp.responseCode.toString(16)}")
            }
        }

        return payload
    }

    private fun fetchThumb(handle: Int): ByteArray {
        val tm = transportManager ?: throw IllegalStateException("Transport not initialized")
        val outEp = bulkOutEndpoint ?: throw IllegalStateException("Bulk OUT endpoint missing")
        val inEp = bulkInEndpoint ?: throw IllegalStateException("Bulk IN endpoint missing")

        val txId = nextTxId()
        val cmdPacket = FujiPtpConstants.buildCommandPacket(
            FujiPtpConstants.OC_GET_THUMB,
            txId,
            intArrayOf(handle)
        )

        val sent = tm.safeBulkTransfer(outEp, cmdPacket, cmdPacket.size, DEFAULT_TIMEOUT_MS)
        if (sent != cmdPacket.size) {
            throw IllegalStateException("Bulk OUT failed: sent=$sent expected=${cmdPacket.size}")
        }

        val buffer = ByteArray(DirectByteBufferPool.SMALL_BUFFER_SIZE)
        val read = tm.safeBulkTransfer(inEp, buffer, buffer.size, DEFAULT_TIMEOUT_MS)
        if (read < 12) {
            throw IllegalStateException("Invalid data response for GetThumb: read=$read")
        }

        val payload = if (read > 12) buffer.copyOfRange(12, read) else ByteArray(0)

        // Read response container
        val respBuffer = ByteArray(512)
        tm.safeBulkTransfer(inEp, respBuffer, respBuffer.size, DEFAULT_TIMEOUT_MS)

        return payload
    }

    private fun executePtpCommand(
        opCode: Int,
        params: IntArray = intArrayOf(),
        timeoutMs: Int = DEFAULT_TIMEOUT_MS
    ): PtpResponse {
        val tm = transportManager ?: throw IllegalStateException("Transport not initialized")
        val outEp = bulkOutEndpoint ?: throw IllegalStateException("Bulk OUT endpoint missing")
        val inEp = bulkInEndpoint ?: throw IllegalStateException("Bulk IN endpoint missing")

        val txId = nextTxId()
        val cmdPacket = FujiPtpConstants.buildCommandPacket(opCode, txId, params)

        val sent = tm.safeBulkTransfer(outEp, cmdPacket, cmdPacket.size, timeoutMs)
        if (sent != cmdPacket.size) {
            throw IllegalStateException("Bulk OUT command failed: sent=$sent expected=${cmdPacket.size}")
        }

        val responseBuffer = ByteArray(512)
        val received = tm.safeBulkTransfer(inEp, responseBuffer, responseBuffer.size, timeoutMs)
        if (received < 12) {
            throw IllegalStateException("Bulk IN response failed: received=$received (minimum 12)")
        }

        return FujiPtpConstants.parseResponsePacket(responseBuffer, received)
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
            if (intf.endpointCount >= 2) {
                return intf
            }
        }
        return null
    }

    private fun findBulkOutEndpoint(usbInterface: UsbInterface): UsbEndpoint? {
        for (i in 0 until usbInterface.endpointCount) {
            val ep = usbInterface.getEndpoint(i)
            if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK && ep.direction == UsbConstants.USB_DIR_OUT) {
                return ep
            }
        }
        return null
    }

    private fun findBulkInEndpoint(usbInterface: UsbInterface): UsbEndpoint? {
        for (i in 0 until usbInterface.endpointCount) {
            val ep = usbInterface.getEndpoint(i)
            if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK && ep.direction == UsbConstants.USB_DIR_IN) {
                return ep
            }
        }
        return null
    }

    private fun findInterruptInEndpoint(usbInterface: UsbInterface): UsbEndpoint? {
        for (i in 0 until usbInterface.endpointCount) {
            val ep = usbInterface.getEndpoint(i)
            if (ep.type == UsbConstants.USB_ENDPOINT_XFER_INT && ep.direction == UsbConstants.USB_DIR_IN) {
                return ep
            }
        }
        return null
    }

    private fun findInterruptInEndpointAny(device: UsbDevice): UsbEndpoint? {
        for (i in 0 until device.interfaceCount) {
            val intf = device.getInterface(i)
            for (j in 0 until intf.endpointCount) {
                val ep = intf.getEndpoint(j)
                if (ep.type == UsbConstants.USB_ENDPOINT_XFER_INT && ep.direction == UsbConstants.USB_DIR_IN) {
                    return ep
                }
            }
        }
        return null
    }

    private fun nextTxId(): Int = txIdCounter.getAndIncrement()

    override fun close() {
        if (isClosedFlag.get()) return
        runBlocking {
            terminateSession()
        }
    }
}
