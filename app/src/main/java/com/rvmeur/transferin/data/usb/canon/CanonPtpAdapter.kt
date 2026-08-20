package com.rvmeur.transferin.data.usb.canon

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.util.Log
import com.rvmeur.transferin.data.usb.core.DirectByteBufferPool
import com.rvmeur.transferin.data.usb.core.HardwareSessionState
import com.rvmeur.transferin.data.usb.core.UsbHardwareSession
import com.rvmeur.transferin.data.usb.core.UsbTransportClosedException
import com.rvmeur.transferin.data.usb.vendor.CameraTetherAdapter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Result of a raw PTP transaction over USB bulk endpoints.
 */
data class CanonPtpResponse(
    val responseCode: Int,
    val transactionId: Int,
    val params: List<Int>,
    val dataPayload: ByteArray?
) {
    val isOk: Boolean get() = responseCode == CanonPtpConstants.RC_OK

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as CanonPtpResponse
        if (responseCode != other.responseCode) return false
        if (transactionId != other.transactionId) return false
        if (params != other.params) return false
        if (dataPayload != null) {
            if (other.dataPayload == null) return false
            if (!dataPayload.contentEquals(other.dataPayload)) return false
        } else if (other.dataPayload != null) return false
        return true
    }

    override fun hashCode(): Int {
        var result = responseCode
        result = 31 * result + transactionId
        result = 31 * result + params.hashCode()
        result = 31 * result + (dataPayload?.contentHashCode() ?: 0)
        return result
    }
}

/**
 * Direct Canon EOS PTP Protocol Adapter (VID 0x04A9).
 *
 * Implements native EOS Remote and Event modes (`0x9114 = 1`, `0x9115 = 1`), continuous DIGIC
 * event queue draining via `0x9116 EOS_GetEvent` to prevent camera LCD "Busy" shutter lockups during
 * high-speed burst shooting, dual-slot storage enumeration (CFexpress + SD), and deterministic
 * 4-step clean teardown.
 */
class CanonPtpAdapter(
    val session: UsbHardwareSession,
    val outEndpoint: UsbEndpoint,
    val inEndpoint: UsbEndpoint,
    val bufferPool: DirectByteBufferPool = session.bufferPool
) : CameraTetherAdapter {

    override val vendorId: Int get() = CanonPtpConstants.CANON_VENDOR_ID

    override suspend fun startSession(): Boolean = initialize()

    override suspend fun closeSession() = disconnect("CameraTetherAdapter closeSession")

    companion object {
        private const val TAG = "CanonPtpAdapter"

        /**
         * Factory function to create and configure a [CanonPtpAdapter] for a connected Canon USB device.
         */
        fun create(
            device: UsbDevice,
            connection: UsbDeviceConnection,
            bufferPool: DirectByteBufferPool = DirectByteBufferPool.Default
        ): CanonPtpAdapter? {
            val ptpInterface = findPtpInterface(device) ?: return null
            val outEndpoint = findBulkOutEndpoint(ptpInterface) ?: return null
            val inEndpoint = findBulkInEndpoint(ptpInterface) ?: return null

            val session = UsbHardwareSession(
                device = device,
                connection = connection,
                bufferPool = bufferPool
            )

            if (!session.claimInterface(ptpInterface)) {
                session.close()
                return null
            }

            return CanonPtpAdapter(
                session = session,
                outEndpoint = outEndpoint,
                inEndpoint = inEndpoint,
                bufferPool = bufferPool
            )
        }

        private fun findPtpInterface(device: UsbDevice): UsbInterface? {
            for (i in 0 until device.interfaceCount) {
                val iface = device.getInterface(i)
                if (iface.interfaceClass == UsbConstants.USB_CLASS_STILL_IMAGE) {
                    return iface
                }
            }
            for (i in 0 until device.interfaceCount) {
                val iface = device.getInterface(i)
                if (iface.endpointCount >= 2) {
                    return iface
                }
            }
            return null
        }

        private fun findBulkOutEndpoint(usbInterface: UsbInterface): UsbEndpoint? {
            for (i in 0 until usbInterface.endpointCount) {
                val ep = usbInterface.getEndpoint(i)
                if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK &&
                    ep.direction == UsbConstants.USB_DIR_OUT
                ) {
                    return ep
                }
            }
            return null
        }

        private fun findBulkInEndpoint(usbInterface: UsbInterface): UsbEndpoint? {
            for (i in 0 until usbInterface.endpointCount) {
                val ep = usbInterface.getEndpoint(i)
                if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK &&
                    ep.direction == UsbConstants.USB_DIR_IN
                ) {
                    return ep
                }
            }
            return null
        }
    }

    private val transactionCounter = AtomicInteger(1)
    private val isClosedFlag = AtomicBoolean(false)
    val isClosed: Boolean get() = isClosedFlag.get()

    private val _events = MutableSharedFlow<CanonPtpEvent>(replay = 32, extraBufferCapacity = 128)
    val events: SharedFlow<CanonPtpEvent> = _events.asSharedFlow()

    private var eventPumpJob: Job? = null

    // Dual-slot storage mapping and isolated handle registries
    private val _storageSlots = ConcurrentHashMap<Int, CanonStorageInfo>()
    private val _slot1Handles = ConcurrentHashMap<Int, Boolean>()
    private val _slot2Handles = ConcurrentHashMap<Int, Boolean>()

    val storageSlots: Map<Int, CanonStorageInfo> get() = _storageSlots
    val slot1Handles: Set<Int> get() = _slot1Handles.keys
    val slot2Handles: Set<Int> get() = _slot2Handles.keys

    fun nextTransactionId(): Int = transactionCounter.getAndIncrement()

    /**
     * Initializes Canon EOS camera into Remote and Event modes and begins continuous DIGIC event pump.
     */
    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        if (isClosedFlag.get()) return@withContext false

        try {
            session.transitionTo(HardwareSessionState.Handshake("CANON_OPEN_SESSION"))

            // Step 1: OpenSession(sessionId = 1)
            val openRes = transact(
                operationCode = CanonPtpConstants.OC_OPEN_SESSION,
                params = intArrayOf(CanonPtpConstants.DEFAULT_SESSION_ID)
            )
            if (!openRes.isOk) {
                Log.e(TAG, "OpenSession failed with code=0x${openRes.responseCode.toString(16)}")
                return@withContext false
            }

            // Step 2: EOS_SetRemoteMode(1)
            session.transitionTo(HardwareSessionState.Handshake("CANON_REMOTE_MODE"))
            val remoteRes = transact(
                operationCode = CanonPtpConstants.EOS_OC_SET_REMOTE_MODE,
                params = intArrayOf(CanonPtpConstants.REMOTE_MODE_ON)
            )
            if (!remoteRes.isOk) {
                Log.w(TAG, "EOS_SetRemoteMode returned code=0x${remoteRes.responseCode.toString(16)}")
            }

            // Step 3: EOS_SetEventMode(1)
            session.transitionTo(HardwareSessionState.Handshake("CANON_EVENT_MODE"))
            val eventRes = transact(
                operationCode = CanonPtpConstants.EOS_OC_SET_EVENT_MODE,
                params = intArrayOf(CanonPtpConstants.EVENT_MODE_ON)
            )
            if (!eventRes.isOk) {
                Log.w(TAG, "EOS_SetEventMode returned code=0x${eventRes.responseCode.toString(16)}")
            }

            // Step 4 & 5: Enumerate storage slots
            refreshStorageSlots()

            // Step 6: Start DIGIC event pump coroutine
            startEventPump()

            session.transitionTo(HardwareSessionState.ActiveStreaming(activeTransfers = 0))
            Log.i(TAG, "Canon EOS PTP Adapter initialized successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Canon initialization failed", e)
            false
        }
    }

    /**
     * Continuous DIGIC Event Pump draining 0x9116 EOS_GetEvent ring buffer.
     */
    fun startEventPump() {
        if (eventPumpJob?.isActive == true) return

        eventPumpJob = session.sessionScope.launch(Dispatchers.IO) {
            Log.d(TAG, "DIGIC Event Pump started")
            while (isActive && !isClosed) {
                try {
                    val drainedCount = drainDigicEvents()
                    if (drainedCount == 0 && isActive && !isClosed) {
                        delay(CanonPtpConstants.EVENT_POLL_INTERVAL_MS)
                    }
                } catch (e: CancellationException) {
                    break
                } catch (e: UsbTransportClosedException) {
                    break
                } catch (e: Exception) {
                    if (isActive && !isClosed) {
                        delay(CanonPtpConstants.EVENT_BUSY_BACKOFF_MS)
                    }
                }
            }
            Log.d(TAG, "DIGIC Event Pump terminated")
        }
    }

    /**
     * Drains pending event records from the camera DIGIC event ring buffer via 0x9116 EOS_GetEvent.
     */
    suspend fun drainDigicEvents(): Int {
        if (isClosedFlag.get()) return 0

        val response = transact(
            operationCode = CanonPtpConstants.EOS_OC_GET_EVENT,
            timeoutMs = 2000
        )

        if (response.responseCode == CanonPtpConstants.RC_DEVICE_BUSY) {
            delay(CanonPtpConstants.EVENT_BUSY_BACKOFF_MS)
            return 0
        }

        if (!response.isOk || response.dataPayload == null || response.dataPayload.isEmpty()) {
            return 0
        }

        return parseAndEmitDigicEvents(response.dataPayload)
    }

    /**
     * Parses DIGIC event payload records and emits them to the [events] SharedFlow.
     */
    private suspend fun parseAndEmitDigicEvents(payload: ByteArray): Int {
        val bb = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)
        var eventCount = 0

        while (bb.remaining() >= 8) {
            val recordLength = bb.int
            if (recordLength < 8 || recordLength > bb.remaining() + 4) {
                break
            }

            val eventCode = bb.int
            val eventDataLength = recordLength - 8
            val startPos = bb.position()

            when (eventCode) {
                CanonPtpConstants.EOS_EC_OBJECT_ADDED, CanonPtpConstants.PTP_EC_OBJECT_ADDED -> {
                    if (eventDataLength >= 8) {
                        val handle = bb.int
                        val storageId = bb.int
                        val formatCode = if (eventDataLength >= 12) {
                            bb.int
                        } else if (eventDataLength >= 10) {
                            bb.short.toInt() and 0xFFFF
                        } else {
                            CanonPtpConstants.FORMAT_CR3_RAW
                        }

                        // Register handle to respective slot registry
                        val slotInfo = _storageSlots[storageId]
                        if (slotInfo != null && slotInfo.slotIndex == CanonPtpConstants.SLOT_2_SD) {
                            _slot2Handles[handle] = true
                        } else {
                            _slot1Handles[handle] = true
                        }

                        _events.emit(CanonPtpEvent.ObjectAdded(handle, storageId, formatCode))
                        eventCount++
                    }
                }
                CanonPtpConstants.EOS_EC_OBJECT_REMOVED -> {
                    if (eventDataLength >= 4) {
                        val handle = bb.int
                        _slot1Handles.remove(handle)
                        _slot2Handles.remove(handle)
                        _events.emit(CanonPtpEvent.ObjectRemoved(handle))
                        eventCount++
                    }
                }
                CanonPtpConstants.EOS_EC_PROPERTY_CHANGED -> {
                    if (eventDataLength >= 4) {
                        val propCode = bb.int
                        val valLength = eventDataLength - 4
                        val valBytes = if (valLength > 0 && bb.remaining() >= valLength) {
                            ByteArray(valLength).also { bb.get(it) }
                        } else null
                        _events.emit(CanonPtpEvent.PropertyChanged(propCode, valBytes))
                        eventCount++
                    }
                }
                CanonPtpConstants.EOS_EC_STORAGE_ADDED,
                CanonPtpConstants.EOS_EC_STORAGE_REMOVED,
                CanonPtpConstants.EOS_EC_STORAGE_INFO_CHANGED -> {
                    if (eventDataLength >= 4) {
                        val storageId = bb.int
                        refreshStorageSlots()
                        _events.emit(CanonPtpEvent.StorageChanged(storageId))
                        eventCount++
                    }
                }
                CanonPtpConstants.EOS_EC_SHUTTER_RELEASED -> {
                    val seqNumber = if (eventDataLength >= 4) bb.int else 0
                    _events.emit(CanonPtpEvent.ShutterReleased(seqNumber))
                    eventCount++
                }
                CanonPtpConstants.EOS_EC_DEV_INFO_CHANGED -> {
                    _events.emit(CanonPtpEvent.DeviceInfoChanged)
                    eventCount++
                }
            }

            val targetPos = startPos + eventDataLength
            if (targetPos in bb.position()..bb.limit()) {
                bb.position(targetPos)
            } else {
                break
            }
        }

        return eventCount
    }

    /**
     * Refreshes and maps connected storage IDs to Slot 1 (CFexpress) and Slot 2 (SD).
     */
    suspend fun refreshStorageSlots(): List<CanonStorageInfo> = withContext(Dispatchers.IO) {
        val ids = getStorageIds()
        val list = mutableListOf<CanonStorageInfo>()

        for ((index, id) in ids.withIndex()) {
            val info = getStorageInfo(id, fallbackSlotIndex = index)
            if (info != null) {
                _storageSlots[id] = info
                list.add(info)
            }
        }

        list
    }

    /**
     * Queries connected Storage IDs from the camera.
     */
    override suspend fun getStorageIds(): List<Int> = withContext(Dispatchers.IO) {
        val res = transact(
            operationCode = CanonPtpConstants.OC_GET_STORAGE_IDS
        )
        if (!res.isOk || res.dataPayload == null || res.dataPayload.size < 4) {
            return@withContext emptyList()
        }

        val bb = ByteBuffer.wrap(res.dataPayload).order(ByteOrder.LITTLE_ENDIAN)
        val count = bb.int
        val ids = mutableListOf<Int>()
        for (i in 0 until count) {
            if (bb.remaining() >= 4) {
                ids.add(bb.int)
            }
        }
        ids
    }

    /**
     * Queries descriptor information for a specific storage volume.
     */
    suspend fun getStorageInfo(storageId: Int, fallbackSlotIndex: Int = 0): CanonStorageInfo? = withContext(Dispatchers.IO) {
        val res = transact(
            operationCode = CanonPtpConstants.OC_GET_STORAGE_INFO,
            params = intArrayOf(storageId)
        )
        if (!res.isOk || res.dataPayload == null || res.dataPayload.size < 26) {
            return@withContext null
        }

        try {
            val bb = ByteBuffer.wrap(res.dataPayload).order(ByteOrder.LITTLE_ENDIAN)
            val storageType = bb.short.toInt() and 0xFFFF
            val fileSystemType = bb.short.toInt() and 0xFFFF
            val accessCapability = bb.short.toInt() and 0xFFFF
            val maxCapacity = bb.long
            val freeSpace = bb.long
            val freeSpaceImages = bb.int
            val storageDesc = decodePtpString(bb)
            val volumeLabel = decodePtpString(bb)

            val slotIndex = when {
                storageDesc.contains("CF", ignoreCase = true) || storageDesc.contains("Slot 1", ignoreCase = true) -> CanonPtpConstants.SLOT_1_CFEXPRESS
                storageDesc.contains("SD", ignoreCase = true) || storageDesc.contains("Slot 2", ignoreCase = true) -> CanonPtpConstants.SLOT_2_SD
                else -> fallbackSlotIndex
            }

            CanonStorageInfo(
                storageId = storageId,
                storageType = storageType,
                fileSystemType = fileSystemType,
                accessCapability = accessCapability,
                maxCapacityBytes = maxCapacity,
                freeSpaceBytes = freeSpace,
                storageDescription = storageDesc,
                volumeLabel = volumeLabel,
                slotIndex = slotIndex
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed parsing StorageInfo for storageId=0x${storageId.toString(16)}", e)
            null
        }
    }

    /**
     * Enumerate object handles isolated to a specific storage ID (CFexpress vs SD).
     */
    override suspend fun getObjectHandles(storageId: Int): List<Int> = getObjectHandles(
        storageId = storageId,
        formatCode = 0,
        parentHandle = 0
    )

    /**
     * Enumerate object handles with format and parent handle filters.
     */
    suspend fun getObjectHandles(
        storageId: Int = 0xFFFFFFFF.toInt(),
        formatCode: Int = 0,
        parentHandle: Int = 0
    ): List<Int> = withContext(Dispatchers.IO) {
        val res = transact(
            operationCode = CanonPtpConstants.OC_GET_OBJECT_HANDLES,
            params = intArrayOf(storageId, formatCode, parentHandle)
        )
        if (!res.isOk || res.dataPayload == null || res.dataPayload.size < 4) {
            return@withContext emptyList()
        }

        val bb = ByteBuffer.wrap(res.dataPayload).order(ByteOrder.LITTLE_ENDIAN)
        val count = bb.int
        val handles = mutableListOf<Int>()
        val isSlot2 = _storageSlots[storageId]?.slotIndex == CanonPtpConstants.SLOT_2_SD

        for (i in 0 until count) {
            if (bb.remaining() >= 4) {
                val handle = bb.int
                handles.add(handle)
                if (isSlot2) {
                    _slot2Handles[handle] = true
                } else {
                    _slot1Handles[handle] = true
                }
            }
        }
        handles
    }

    /**
     * Retrieves partial object bytes using 0x101B GetPartialObject.
     */
    suspend fun getPartialObject(
        handle: Int,
        offset: Long,
        length: Int
    ): ByteArray? = withContext(Dispatchers.IO) {
        val res = transact(
            operationCode = CanonPtpConstants.OC_GET_PARTIAL_OBJECT,
            params = intArrayOf(handle, offset.toInt(), length)
        )
        if (res.isOk) res.dataPayload else null
    }

    /**
     * Retrieves full object binary data via 0x1009 GetObject.
     */
    suspend fun getObject(handle: Int): ByteArray? = withContext(Dispatchers.IO) {
        val res = transact(
            operationCode = CanonPtpConstants.OC_GET_OBJECT,
            params = intArrayOf(handle)
        )
        if (res.isOk) res.dataPayload else null
    }

    /**
     * Retrieves thumbnail image via 0x100A GetThumb.
     */
    suspend fun getThumb(handle: Int): ByteArray? = withContext(Dispatchers.IO) {
        val res = transact(
            operationCode = CanonPtpConstants.OC_GET_THUMB,
            params = intArrayOf(handle)
        )
        if (res.isOk) res.dataPayload else null
    }

    /**
     * Extracts high-resolution JPEG preview from .CR3 header slice in <500ms returning structured result.
     */
    suspend fun extractCr3Preview(handle: Int, sliceSize: Int = 128 * 1024): Cr3PreviewResult? {
        val slice = getPartialObject(handle, 0L, sliceSize) ?: return null
        return Cr3PreviewParser.parsePreviewFromSlice(slice)
    }

    /**
     * Extracts raw embedded preview JPEG bytes satisfying [CameraTetherAdapter].
     */
    override suspend fun extractPreview(handle: Int): ByteArray? {
        return extractCr3Preview(handle)?.jpegBytes ?: getThumb(handle)
    }

    /**
     * Executes a thread-safe atomic PTP transaction over USB bulk pipes guarded by [SafeUsbTransportManager.ioMutex].
     */
    suspend fun transact(
        operationCode: Int,
        params: IntArray = intArrayOf(),
        dataOut: ByteArray? = null,
        timeoutMs: Int = CanonPtpConstants.DEFAULT_TIMEOUT_MS
    ): CanonPtpResponse = withContext(Dispatchers.IO) {
        session.transportManager.ioMutex.withLock {
            val txId = nextTransactionId()

            // 1. Send Command Container
            val cmdLen = 12 + params.size * 4
            val cmdPacket = ByteBuffer.allocate(cmdLen).order(ByteOrder.LITTLE_ENDIAN).apply {
                putInt(cmdLen)
                putShort(CanonPtpConstants.CONTAINER_COMMAND.toShort())
                putShort(operationCode.toShort())
                putInt(txId)
                params.forEach { putInt(it) }
            }.array()

            val sent = session.transportManager.safeBulkTransfer(outEndpoint, cmdPacket, cmdPacket.size, timeoutMs)
            if (sent != cmdPacket.size) {
                throw UsbTransportClosedException("Failed sending PTP command op=0x${operationCode.toString(16)}")
            }

            // 2. If dataOut is present, send Data Container
            if (dataOut != null && dataOut.isNotEmpty()) {
                val dataContainerLen = 12 + dataOut.size
                val dataPacket = ByteBuffer.allocate(dataContainerLen).order(ByteOrder.LITTLE_ENDIAN).apply {
                    putInt(dataContainerLen)
                    putShort(CanonPtpConstants.CONTAINER_DATA.toShort())
                    putShort(operationCode.toShort())
                    putInt(txId)
                    put(dataOut)
                }.array()
                session.transportManager.safeBulkTransfer(outEndpoint, dataPacket, dataPacket.size, timeoutMs)
            }

            // 3. Receive incoming containers (Data then Response, or Response directly)
            var dataPayload: ByteArray? = null
            var responseCode = CanonPtpConstants.RC_GENERAL_ERROR
            val responseParams = mutableListOf<Int>()

            val readBuffer = ByteArray(64 * 1024)

            while (true) {
                val received = session.transportManager.safeBulkTransfer(inEndpoint, readBuffer, readBuffer.size, timeoutMs)
                if (received < 12) {
                    break
                }

                val bb = ByteBuffer.wrap(readBuffer, 0, received).order(ByteOrder.LITTLE_ENDIAN)
                val containerLen = bb.int
                val containerType = bb.short.toInt() and 0xFFFF
                val code = bb.short.toInt() and 0xFFFF
                val rxTxId = bb.int

                when (containerType) {
                    CanonPtpConstants.CONTAINER_DATA -> {
                        val payloadLen = (containerLen - 12).coerceAtLeast(0)
                        val totalData = ByteArray(payloadLen)
                        val chunkLen = (received - 12).coerceAtMost(payloadLen)
                        if (chunkLen > 0) {
                            bb.get(totalData, 0, chunkLen)
                        }

                        var bytesRead = chunkLen
                        while (bytesRead < payloadLen) {
                            val remainingToRead = payloadLen - bytesRead
                            val nextChunk = ByteArray(remainingToRead.coerceAtMost(readBuffer.size))
                            val nextReceived = session.transportManager.safeBulkTransfer(
                                inEndpoint,
                                nextChunk,
                                nextChunk.size,
                                timeoutMs
                            )
                            if (nextReceived <= 0) break
                            System.arraycopy(nextChunk, 0, totalData, bytesRead, nextReceived)
                            bytesRead += nextReceived
                        }
                        dataPayload = totalData
                    }
                    CanonPtpConstants.CONTAINER_RESPONSE -> {
                        responseCode = code
                        while (bb.remaining() >= 4) {
                            responseParams.add(bb.int)
                        }
                        return@withLock CanonPtpResponse(
                            responseCode = responseCode,
                            transactionId = rxTxId,
                            params = responseParams,
                            dataPayload = dataPayload
                        )
                    }
                    else -> {
                        Log.w(TAG, "Unexpected PTP container type=$containerType, code=0x${code.toString(16)}")
                        break
                    }
                }
            }

            CanonPtpResponse(
                responseCode = responseCode,
                transactionId = txId,
                params = responseParams,
                dataPayload = dataPayload
            )
        }
    }

    private fun decodePtpString(buffer: ByteBuffer): String {
        if (buffer.remaining() < 1) return ""
        val numChars = buffer.get().toInt() and 0xFF
        if (numChars == 0) return ""
        val charBytes = numChars * 2
        if (buffer.remaining() < charBytes) return ""
        val chars = CharArray(numChars)
        for (i in 0 until numChars) {
            chars[i] = buffer.short.toInt().toChar()
        }
        return String(chars).trimEnd('\u0000')
    }

    /**
     * Executes deterministic 4-step clean teardown sequence restoring camera LCD to local shooting mode.
     */
    suspend fun disconnect(reason: String = "Normal disconnect") = withContext(Dispatchers.IO + NonCancellable) {
        if (isClosedFlag.compareAndSet(false, true)) {
            Log.i(TAG, "Beginning 4-step clean teardown for Canon camera. Reason: $reason")

            // Step 1: Cancel event pump coroutine
            runCatching {
                eventPumpJob?.cancel()
                eventPumpJob = null
                Log.d(TAG, "Step 1: Cancelled DIGIC Event Pump")
            }

            // Step 2: Send 0x9114 EOS_SetRemoteMode(0) (Remote Mode OFF)
            runCatching {
                transact(
                    operationCode = CanonPtpConstants.EOS_OC_SET_REMOTE_MODE,
                    params = intArrayOf(CanonPtpConstants.REMOTE_MODE_OFF),
                    timeoutMs = 1500
                )
                Log.d(TAG, "Step 2: Disabled EOS Remote Mode")
            }.onFailure {
                Log.w(TAG, "Failed setting EOS Remote Mode OFF during teardown", it)
            }

            // Step 3: Send 0x1003 CloseSession(1)
            runCatching {
                transact(
                    operationCode = CanonPtpConstants.OC_CLOSE_SESSION,
                    params = intArrayOf(CanonPtpConstants.DEFAULT_SESSION_ID),
                    timeoutMs = 1500
                )
                Log.d(TAG, "Step 3: Closed PTP session")
            }.onFailure {
                Log.w(TAG, "Failed sending CloseSession during teardown", it)
            }

            // Step 4: Release claimed USB interface and close session
            runCatching {
                session.disconnect("CanonPtpAdapter teardown: $reason")
                Log.d(TAG, "Step 4: Terminated hardware session")
            }.onFailure {
                Log.w(TAG, "Failed terminating hardware session", it)
            }

            Log.i(TAG, "Canon EOS PTP Adapter terminated cleanly")
        }
    }

    override fun close() {
        if (isClosedFlag.get()) return
        runBlocking {
            disconnect("AutoCloseable close()")
        }
    }
}
