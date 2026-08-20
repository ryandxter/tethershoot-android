package com.rvmeur.transferin.data.usb

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.mtp.MtpConstants
import android.mtp.MtpDevice
import android.mtp.MtpObjectInfo
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.rvmeur.transferin.data.usb.vendor.CanonCameraInitializer
import com.rvmeur.transferin.data.usb.vendor.CanonPtpEnumerator
import com.rvmeur.transferin.domain.model.CameraMedia
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File

data class UsbDeviceInfo(
    val deviceName: String,
    val manufacturerName: String?,
    val productName: String?,
    val vendorId: Int,
    val productId: Int,
    val deviceClass: Int,
    val deviceSubclass: Int,
    val deviceProtocol: Int,
    val hasPermission: Boolean,
    val interfaceCount: Int,
    val interfaceSummary: String
)

data class CameraSessionState(
    val isOpening: Boolean = false,
    val isOpen: Boolean = false,
    val deviceName: String? = null,
    val deviceLabel: String? = null,
    val storageIds: List<Int> = emptyList(),
    val storageCount: Int = 0,
    val errorMessage: String? = null
)

data class UsbMonitorState(
    val connectedDevices: List<UsbDeviceInfo> = emptyList(),
    val cameraLikeDevices: List<UsbDeviceInfo> = emptyList(),
    val hasAnyUsbDevice: Boolean = false,
    val hasAnyPermittedUsbDevice: Boolean = false,
    val isLikelyCameraConnected: Boolean = false,
    val statusText: String = "Belum ada perangkat USB terdeteksi.",
    val cameraSession: CameraSessionState = CameraSessionState()
)

class UsbHelper(
    private val context: Context,
    private val cameraSessionInitializer: CameraSessionInitializer = DefaultCameraSessionInitializer()
) {

    companion object {
        const val ACTION_USB_PERMISSION = "com.rvmeur.transferin.USB_PERMISSION"
        private const val TAG = "UsbHelper"
        private const val PATCH_TAG = "USB_PATCH_V3"
        private const val CANON_VENDOR_ID = 0x04A9

        private const val MTP_OPEN_TIMEOUT_MS = 8_000L
        private const val OBJECT_LIST_TIMEOUT_MS = 12_000L
        private const val OBJECT_INFO_TIMEOUT_MS = 1_500L
        private const val OBJECT_IMPORT_TIMEOUT_MS = 120_000L

        private const val STORAGE_READY_RETRY_COUNT = 5
        private const val STORAGE_READY_RETRY_DELAY_MS = 300L

        private val CAMERA_KEYWORDS = listOf(
            "camera", "canon", "nikon", "sony", "fujifilm",
            "lumix", "panasonic", "olympus", "leica", "gopro", "dji", "eos"
        )

        private val IMAGE_FILE_EXTENSIONS = setOf(
            "jpg", "jpeg", "png", "gif", "bmp", "tif", "tiff"
        )
        private val RAW_FILE_EXTENSIONS = setOf(
            "cr2", "cr3", "nef", "arw", "raf", "rw2", "orf", "dng", "pef"
        )
        private val VIDEO_FILE_EXTENSIONS = setOf(
            "mp4", "mov", "avi", "m4v", "3gp", "mts", "m2ts", "wmv"
        )
    }

    private val appContext = context.applicationContext

    private val usbManager: UsbManager by lazy {
        appContext.getSystemService(Context.USB_SERVICE) as UsbManager
    }

    private val helperScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val sessionMutex = Mutex()

    private val _monitorState = MutableStateFlow(UsbMonitorState())
    val monitorState: StateFlow<UsbMonitorState> = _monitorState.asStateFlow()

    private var isReceiverRegistered = false

    private var currentSessionConnection: UsbDeviceConnection? = null
    private var currentMtpDevice: MtpDevice? = null
    private var mtpEventDrainJob: kotlinx.coroutines.Job? = null
    private var mtpEventCancellationSignal: android.os.CancellationSignal? = null
    var activeMtpDeviceOverride: MtpDevice? = null

    fun setMtpDeviceOverride(mtpDevice: MtpDevice?, deviceName: String?) {
        activeMtpDeviceOverride = mtpDevice
        currentSessionDeviceName = deviceName
    }

    private val activeMtp: MtpDevice?
        get() = activeMtpDeviceOverride ?: currentMtpDevice
    private var currentSessionDeviceName: String? = null
    private var currentSessionState: CameraSessionState = CameraSessionState()

    private var pendingPermissionDeviceName: String? = null

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action.orEmpty()) {
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    val device = extractUsbDevice(intent)
                    Log.d(TAG, "USB attached: ${device?.deviceName}")
                    refresh()
                }

                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    val device = extractUsbDevice(intent)
                    if (device != null && device.deviceName == currentSessionDeviceName) {
                        helperScope.launch { closeCameraSessionInternalAndRefresh() }
                    } else {
                        refresh()
                    }
                }

                ACTION_USB_PERMISSION -> {
                    pendingPermissionDeviceName = null
                    refresh()
                }
            }
        }
    }

    init {
        Log.e(TAG, "$PATCH_TAG init")
        refresh()
    }

    fun startMonitoring() {
        if (!isReceiverRegistered) {
            val filter = IntentFilter().apply {
                addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
                addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
                addAction(ACTION_USB_PERMISSION)
            }
            ContextCompat.registerReceiver(
                appContext,
                usbReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            isReceiverRegistered = true
        }
        refresh()
    }

    fun stopMonitoring() {
        helperScope.launch { closeCameraSessionInternalAndRefresh() }
        if (isReceiverRegistered) {
            runCatching { appContext.unregisterReceiver(usbReceiver) }
            isReceiverRegistered = false
        }
    }

    fun refresh() {
        val state = buildMonitorState()
        _monitorState.value = state
        Log.d(
            TAG,
            "$PATCH_TAG USB Refresh: connected=${state.connectedDevices.size}, cameraOpen=${state.cameraSession.isOpen}, storageCount=${state.cameraSession.storageCount}"
        )
    }

    fun refreshUsbState() {
        refresh()
    }

    suspend fun refreshMtpHardware() = sessionMutex.withLock {
        val mtp = currentMtpDevice ?: return@withLock
        withContext(Dispatchers.IO) {
            runCatching {
                mtp.storageIds
                mtp.storageIds?.firstOrNull()?.let { mtp.getStorageInfo(it) }
            }.onFailure {
                Log.w(TAG, "refreshMtpHardware failed", it)
            }
        }
    }

    fun getConnectedDevices(): List<UsbDeviceInfo> {
        val deviceList = usbManager.deviceList
        return deviceList.values.map { it.toUsbDeviceInfo(usbManager.hasPermission(it)) }
    }

    fun hasAnyPermittedUsbDevice(): Boolean {
        return usbManager.deviceList.values.any { usbManager.hasPermission(it) }
    }

    fun requestPermissionForFirstCameraLikeDevice(): Boolean {
        val target = usbManager.deviceList.values.firstOrNull { it.isLikelyCamera() } ?: return false
        return requestPermission(target)
    }

    fun requestPermissionForFirstDevice(): Boolean {
        val target = usbManager.deviceList.values.firstOrNull() ?: return false
        return requestPermission(target)
    }

    fun openFirstCameraSession(): Boolean {
        Log.d(TAG, ">>> openFirstCameraSession() called")
        val target = usbManager.deviceList.values.firstOrNull {
            it.isLikelyCamera() && usbManager.hasPermission(it)
        } ?: run {
            Log.d(TAG, ">>> No camera device found with permission")
            return false
        }

        Log.d(TAG, ">>> Found camera: ${target.deviceName}, launching openCameraSessionInternal")
        helperScope.launch { openCameraSessionInternal(target) }
        return true
    }

    fun openCameraSession(deviceName: String): Boolean {
        val target = usbManager.deviceList.values.firstOrNull { it.deviceName == deviceName } ?: return false
        if (!usbManager.hasPermission(target)) return false
        helperScope.launch { openCameraSessionInternal(target) }
        return true
    }

    fun closeCameraSession() {
        helperScope.launch { closeCameraSessionInternalAndRefresh() }
    }

    suspend fun closeCameraSessionSuspend() {
        withContext(Dispatchers.IO + kotlinx.coroutines.NonCancellable) {
            closeCameraSessionInternalAndRefresh()
        }
    }

    fun isSessionOpen(): Boolean = (currentSessionState.isOpen || activeMtpDeviceOverride != null) && activeMtp != null

    fun getActiveStorageIds(): List<Int> = activeMtpDeviceOverride?.storageIds?.toList() ?: currentSessionState.storageIds

    fun getFirstStorageId(): Int = activeMtpDeviceOverride?.storageIds?.firstOrNull() ?: currentSessionState.storageIds.firstOrNull() ?: 0

    suspend fun getTotalObjectCount(storageId: Int): Int = sessionMutex.withLock {
        val handles = getAllObjectHandlesNoLock(storageId)
        val count = handles.size
        Log.d(
            TAG,
            "getTotalObjectCount(storageId=$storageId, canonVendor=${isCanonSession()}, canonNative=${hasActiveCanonNativeSession()}): count=$count"
        )
        count
    }

    suspend fun getLatestHandles(storageId: Int, limit: Int): List<Int> = sessionMutex.withLock {
        val handles = getAllObjectHandlesNoLock(storageId)
        val latest = if (handles.size <= limit) handles else handles.takeLast(limit)
        Log.d(
            TAG,
            "getLatestHandles(storageId=$storageId, limit=$limit, canonVendor=${isCanonSession()}, canonNative=${hasActiveCanonNativeSession()}): count=${latest.size}"
        )
        latest
    }

    suspend fun getAllObjectHandles(storageId: Int): List<Int> {
        Log.e(TAG, "$PATCH_TAG getAllObjectHandles(storageId=$storageId)")
        return getAllObjectHandlesNoLock(storageId)
    }

    private suspend fun getAllObjectHandlesNoLock(storageId: Int): List<Int> {
        val mtp = activeMtp ?: return emptyList()

        Log.e(
            TAG,
            "$PATCH_TAG ENTER getAllObjectHandlesNoLock(storageId=$storageId), device=$currentSessionDeviceName, canonSession=${isCanonSession()}, activeNative=${hasActiveCanonNativeSession()}"
        )

        val mtpHandles = withContext(Dispatchers.IO) {
            runCatching {
                withTimeout(OBJECT_LIST_TIMEOUT_MS) {
                    mtp.getObjectHandles(storageId, 0, 0)?.toList() ?: emptyList()
                }
            }.onFailure {
                Log.e(TAG, "getAllObjectHandles failed for storageId=$storageId", it)
            }.getOrDefault(emptyList())
        }

        Log.e(TAG, "$PATCH_TAG MTP handles count for storageId=$storageId -> ${mtpHandles.size}")

        if (mtpHandles.isNotEmpty()) {
            Log.d(
                TAG,
                "getAllObjectHandles(storageId=$storageId): using MTP handles count=${mtpHandles.size}"
            )
            return mtpHandles
        }

        if (isCanonSession()) {
            Log.w(TAG, "Canon session returned empty object handles via MtpDevice")
        }

        val canonInitializer = getCanonInitializer()
        Log.e(
            TAG,
            "$PATCH_TAG Canon initializer available=${canonInitializer != null}, currentSessionDeviceName=$currentSessionDeviceName"
        )

        val canonSession = canonInitializer?.getActivePtpSession()
        if (canonSession != null && canonSession.deviceName == currentSessionDeviceName) {
            return withContext(Dispatchers.IO) {
                runCatching {
                    val mergedHandles = linkedSetOf<Int>()

                    val enumeratorForStorageIds = CanonPtpEnumerator(canonSession)
                    val nativeStorageIds = runCatching { enumeratorForStorageIds.getStorageIds() }
                        .onFailure { Log.e(TAG, "Canon native getStorageIds failed", it) }
                        .getOrDefault(emptyList())

                    Log.e(
                        TAG,
                        "$PATCH_TAG Canon native storageIds=$nativeStorageIds, requestedStorageId=$storageId"
                    )

                    val candidateStorageIds = buildList {
                        add(storageId)
                        nativeStorageIds.forEach { nativeId ->
                            if (nativeId !in this) add(nativeId)
                        }
                    }

                    candidateStorageIds.forEach { candidateStorageId ->
                        val handles = runCatching {
                            CanonPtpEnumerator(canonSession).getObjectHandles(candidateStorageId)
                        }.onFailure {
                            Log.e(
                                TAG,
                                "Canon native getObjectHandles failed for candidateStorageId=$candidateStorageId",
                                it
                            )
                        }.getOrDefault(emptyList())

                        Log.e(
                            TAG,
                            "$PATCH_TAG Canon native getObjectHandles(candidateStorageId=$candidateStorageId): count=${handles.size}"
                        )

                        mergedHandles.addAll(handles)
                    }

                    if (mergedHandles.isEmpty() && nativeStorageIds.isEmpty()) {
                        Log.w(TAG, "$PATCH_TAG Canon native storageIds empty; trying raw requested storage only")
                        val retryHandles = runCatching {
                            CanonPtpEnumerator(canonSession).getObjectHandles(storageId)
                        }.getOrDefault(emptyList())
                        mergedHandles.addAll(retryHandles)
                    }

                    val finalHandles = mergedHandles.toList()
                    Log.e(
                        TAG,
                        "$PATCH_TAG Canon native merged handles for requestedStorageId=$storageId: count=${finalHandles.size}"
                    )
                    finalHandles
                }.onFailure {
                    Log.e(TAG, "Canon native enumeration failed for storageId=$storageId", it)
                }.getOrDefault(emptyList())
            }
        }

        if (hasActiveCanonNativeSession()) {
            Log.w(TAG, "Active Canon native session detected, but no matching PTP session snapshot available")
        }

        return emptyList()
    }
    suspend fun getObjectMetadata(handle: Int): CameraMedia? = sessionMutex.withLock {
        Log.e(TAG, "$PATCH_TAG getObjectMetadata(handle=$handle)")
        getObjectMetadataNoLock(handle)
    }

    private suspend fun getObjectMetadataNoLock(handle: Int): CameraMedia? {
        val mtp = activeMtp ?: return null

        val mtpMedia = withContext(Dispatchers.IO) {
            runCatching {
                val info = mtp.getObjectInfo(handle)
                if (info == null) {
                    Log.w(TAG, "getObjectMetadata(handle=$handle): MTP info is null")
                    null
                } else {
                    info.toCameraMedia(0, handle)
                }
            }.onFailure {
                Log.e(TAG, "getObjectMetadata MTP failed for handle=$handle", it)
            }.getOrNull()
        }

        if (mtpMedia != null) {
            return mtpMedia
        }

        val canonSession = getCanonInitializer()?.getActivePtpSession()
        if (canonSession != null && canonSession.deviceName == currentSessionDeviceName) {
            val canonMedia = withContext(Dispatchers.IO) {
                runCatching {
                    val objectInfo = CanonPtpEnumerator(canonSession).getObjectInfo(handle)
                    if (objectInfo == null) {
                        Log.w(TAG, "getObjectMetadata(handle=$handle): Canon native info is null")
                        null
                    } else {
                        CameraMedia(
                            storageId = objectInfo.storageId,
                            handle = handle,
                            name = objectInfo.fileName,
                            isFolder = objectInfo.objectFormat == MtpConstants.FORMAT_ASSOCIATION,
                            size = objectInfo.compressedSize,
                            parentHandle = objectInfo.parentObject,
                            formatCode = objectInfo.objectFormat,
                            modifiedTime = objectInfo.modificationDateEpochMillis
                                ?: objectInfo.captureDateEpochMillis
                        )
                    }
                }.onFailure {
                    Log.e(TAG, "getObjectMetadata Canon native failed for handle=$handle", it)
                }.getOrNull()
            }

            if (canonMedia != null) {
                Log.e(TAG, "$PATCH_TAG getObjectMetadata(handle=$handle): using Canon native metadata")
                return canonMedia
            }
        }

        if (hasActiveCanonNativeSession()) {
            Log.w(TAG, "getObjectMetadata(handle=$handle): Canon native session active but metadata unavailable")
        }

        return null
    }

    suspend fun listMedia(storageId: Int): List<CameraMedia> = sessionMutex.withLock {
        if (!currentSessionState.isOpen) {
            throw IllegalStateException("Sesi kamera belum siap dipakai.")
        }

        val handles = getAllObjectHandlesNoLock(storageId)

        val media = mutableListOf<CameraMedia>()
        handles.forEach { handle ->
            val info = getObjectMetadataNoLock(handle) ?: return@forEach
            if (info.isInterestingMedia()) {
                media += if (info.storageId != 0) info else info.copy(storageId = storageId)
            }
        }
        media.sortedBy { it.name.lowercase() }
    }

    suspend fun downloadMediaToCache(media: CameraMedia): File? = sessionMutex.withLock {
        val mtpDevice = activeMtp ?: throw IllegalStateException("Sesi kamera belum terbuka.")
        val transitDir = appContext.getExternalFilesDir("camera_transit") ?: return@withLock null
        val targetFile = File(transitDir, "${media.handle}_${media.name}")
        val success = withContext(Dispatchers.IO) {
            withTimeout(OBJECT_IMPORT_TIMEOUT_MS) {
                mtpDevice.importFile(media.handle, targetFile.absolutePath)
            }
        }
        return if (success) targetFile else null
    }

    private fun requestPermission(device: UsbDevice): Boolean {
        if (usbManager.hasPermission(device)) return true
        pendingPermissionDeviceName = device.deviceName
        val intent = Intent(ACTION_USB_PERMISSION).apply {
            setPackage(appContext.packageName)
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_MUTABLE
        } else {
            0
        }
        val permissionIntent = PendingIntent.getBroadcast(
            appContext,
            device.deviceId,
            intent,
            flags
        )
        usbManager.requestPermission(device, permissionIntent)
        return true
    }

    private suspend fun openCameraSessionInternal(device: UsbDevice): Boolean = sessionMutex.withLock {
        Log.d(TAG, "openCameraSessionInternal() start: ${device.deviceName}")
        closeCameraSessionInternalNoLock()

        currentSessionState = CameraSessionState(
            isOpening = true,
            deviceName = device.deviceName,
            deviceLabel = device.productName ?: device.manufacturerName ?: "Kamera"
        )
        refresh()

        val connection = withContext(Dispatchers.IO) {
            usbManager.openDevice(device)
        } ?: return failCameraSession(
            "Gagal membuka koneksi USB. Pastikan sistem MTP Android tidak aktif."
        )

        val mtpDevice = MtpDevice(device)
        val opened = withContext(Dispatchers.IO) {
            runCatching {
                withTimeout(MTP_OPEN_TIMEOUT_MS) {
                    mtpDevice.open(connection)
                }
            }.onFailure {
                Log.e(TAG, "Error opening MTP device", it)
            }.getOrDefault(false)
        }

        if (!opened) {
            runCatching { connection.close() }
            return failCameraSession(
                "Kamera terdeteksi, tetapi sesi MTP tidak bisa dibuka. Coba lepas dan pasang kembali kabel."
            )
        }

        val initResult = runCatching {
            Log.d(TAG, "Executing camera session initializer...")
            cameraSessionInitializer.initialize(
                device = device,
                connection = connection,
                mtpDevice = mtpDevice,
                usbHelper = this@UsbHelper
            )
        }.getOrElse {
            CameraInitResult.fail("Init error: ${it.message}")
        }

        val ids = awaitStorageIds(mtpDevice)

        // Drain any pre-existing/pending MTP events to keep the camera state synchronized
        val signal = android.os.CancellationSignal()
        mtpEventCancellationSignal = signal
        mtpEventDrainJob = kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            Log.d(TAG, "Starting MTP event drain loop for device: ${device.deviceName}")
            while (isActive) {
                try {
                    val event = mtpDevice.readEvent(signal)
                    if (event != null) {
                        Log.d(TAG, "MTP Event received: code=0x${Integer.toHexString(event.eventCode)}, parameter1=${event.parameter1}")
                    }
                } catch (e: android.os.OperationCanceledException) {
                    Log.d(TAG, "MTP event drain loop cancelled via signal")
                    break
                } catch (e: Exception) {
                    Log.w(TAG, "Error in MTP event drain loop", e)
                    break
                }
            }
            Log.d(TAG, "MTP event drain loop stopped")
        }

        currentSessionConnection = connection
        currentMtpDevice = mtpDevice
        currentSessionDeviceName = device.deviceName
        currentSessionState = CameraSessionState(
            isOpen = true,
            deviceName = device.deviceName,
            deviceLabel = device.productName ?: device.manufacturerName ?: "Kamera",
            storageIds = ids,
            storageCount = ids.size,
            errorMessage = if (!initResult.success) initResult.message else null
        )

        refresh()
        Log.e(
            TAG,
            "$PATCH_TAG Camera session opened successfully. device=${device.deviceName}, vendorId=${device.vendorId}, storageIds=$ids, initSuccess=${initResult.success}, initMessage=${initResult.message}, canonNative=${hasActiveCanonNativeSession()}"
        )
        return true
    }

    private suspend fun awaitStorageIds(mtpDevice: MtpDevice): List<Int> = withContext(Dispatchers.IO) {
        repeat(STORAGE_READY_RETRY_COUNT) { attempt ->
            val ids = runCatching {
                mtpDevice.storageIds?.toList().orEmpty()
            }.getOrElse {
                Log.w(TAG, "Failed reading storageIds on attempt=${attempt + 1}", it)
                emptyList()
            }

            if (ids.isNotEmpty()) {
                Log.d(TAG, "Storage IDs ready on attempt=${attempt + 1}: $ids")
                return@withContext ids
            }

            Log.w(
                TAG,
                "Storage IDs not ready on attempt=${attempt + 1}/$STORAGE_READY_RETRY_COUNT"
            )

            if (attempt < STORAGE_READY_RETRY_COUNT - 1) {
                kotlinx.coroutines.delay(STORAGE_READY_RETRY_DELAY_MS)
            }
        }

        emptyList()
    }

    private suspend fun closeCameraSessionInternalAndRefresh() {
        sessionMutex.withLock {
            closeCameraSessionInternalNoLock()
            currentSessionState = CameraSessionState()
            refresh()
        }
    }

    private fun closeCameraSessionInternalNoLock() {
        closeCanonNativeSessionIfNeeded()

        runCatching {
            mtpEventCancellationSignal?.cancel()
            mtpEventDrainJob?.cancel()
        }
        mtpEventCancellationSignal = null
        mtpEventDrainJob = null

        val connection = currentSessionConnection
        val deviceName = currentSessionDeviceName

        runCatching { currentMtpDevice?.close() }
            .onFailure { Log.w(TAG, "Failed to close MTP device", it) }

        if (connection != null && deviceName != null) {
            val usbDevice = usbManager.deviceList[deviceName]
            if (usbDevice != null) {
                for (i in 0 until usbDevice.interfaceCount) {
                    val usbInterface = usbDevice.getInterface(i)
                    runCatching {
                        connection.releaseInterface(usbInterface)
                        Log.d(TAG, "Released USB interface: $i")
                    }
                }
            }
        }

        runCatching { currentSessionConnection?.close() }
            .onFailure { Log.w(TAG, "Failed to close USB connection", it) }

        currentMtpDevice = null
        currentSessionConnection = null
        currentSessionDeviceName = null
    }

    private fun failCameraSession(message: String): Boolean {
        closeCanonNativeSessionIfNeeded()
        currentMtpDevice = null
        currentSessionConnection = null
        currentSessionDeviceName = null
        currentSessionState = CameraSessionState(errorMessage = message)
        refresh()
        Log.w(TAG, "Camera session failed: $message")
        return false
    }

    private fun buildMonitorState(): UsbMonitorState {
        val connected = getConnectedDevices()
        val cameraLike = connected.filter { isLikelyCamera(it) }

        return UsbMonitorState(
            connectedDevices = connected,
            cameraLikeDevices = cameraLike,
            hasAnyUsbDevice = connected.isNotEmpty(),
            hasAnyPermittedUsbDevice = hasAnyPermittedUsbDevice(),
            isLikelyCameraConnected = cameraLike.isNotEmpty(),
            statusText = buildStatusText(connected, cameraLike),
            cameraSession = currentSessionState
        )
    }

    private fun buildStatusText(
        connected: List<UsbDeviceInfo>,
        cameraLike: List<UsbDeviceInfo>
    ): String {
        if (currentSessionState.isOpen) {
            return "Sesi aktif: ${currentSessionState.deviceLabel}. ${currentSessionState.storageCount} storage."
        }

        if (currentSessionState.isOpening) {
            return "Sedang membuka sesi kamera..."
        }

        if (currentSessionState.errorMessage != null) {
            return currentSessionState.errorMessage!!
        }

        if (connected.isEmpty()) {
            return "Belum ada perangkat USB terdeteksi."
        }

        val permittedCount = connected.count { it.hasPermission }
        val preferred = cameraLike.firstOrNull() ?: connected.first()
        val label = "${preferred.manufacturerName ?: ""} ${preferred.productName ?: ""}"
            .trim()
            .ifBlank { "Unknown Device" }

        return when {
            connected.size == 1 && preferred.hasPermission ->
                "Kamera siap: $label."
            connected.size == 1 ->
                "Kamera terdeteksi: $label. Klik untuk izin akses."
            permittedCount == connected.size ->
                "${connected.size} perangkat terdeteksi. Izin akses siap."
            else ->
                "${connected.size} perangkat terdeteksi. $permittedCount izin siap."
        }
    }

    private fun isLikelyCamera(device: UsbDeviceInfo): Boolean {
        if (device.deviceClass == UsbConstants.USB_CLASS_STILL_IMAGE) return true
        val combined = "${device.manufacturerName ?: ""} ${device.productName ?: ""}".lowercase()
        return CAMERA_KEYWORDS.any { combined.contains(it) }
    }

    private fun UsbDevice.isLikelyCamera(): Boolean {
        if (deviceClass == UsbConstants.USB_CLASS_STILL_IMAGE) return true
        val combined = "${manufacturerName ?: ""} ${productName ?: ""}".lowercase()
        return CAMERA_KEYWORDS.any { combined.contains(it) }
    }

    private fun UsbDevice.toUsbDeviceInfo(hasPermission: Boolean) = UsbDeviceInfo(
        deviceName = deviceName.orEmpty(),
        manufacturerName = manufacturerName,
        productName = productName,
        vendorId = vendorId,
        productId = productId,
        deviceClass = deviceClass,
        deviceSubclass = deviceSubclass,
        deviceProtocol = deviceProtocol,
        hasPermission = hasPermission,
        interfaceCount = interfaceCount,
        interfaceSummary = "-"
    )

    private fun MtpObjectInfo.isInterestingObject(): Boolean {
        if (format == MtpConstants.FORMAT_ASSOCIATION) return true
        val ext = name.orEmpty().substringAfterLast('.', "").lowercase()
        return ext in IMAGE_FILE_EXTENSIONS || ext in RAW_FILE_EXTENSIONS || ext in VIDEO_FILE_EXTENSIONS
    }

    private fun MtpObjectInfo.toCameraMedia(storageId: Int, handleValue: Int) = CameraMedia(
        storageId = storageId,
        handle = handleValue,
        name = name.orEmpty(),
        isFolder = format == MtpConstants.FORMAT_ASSOCIATION,
        size = compressedSize.toLong(),
        parentHandle = parent,
        formatCode = format,
        modifiedTime = if (dateModified > 0) dateModified * 1000L else null
    )

    private fun CameraMedia.isInterestingMedia(): Boolean {
        if (isFolder) return true
        val ext = name.substringAfterLast('.', "").lowercase()
        return ext in IMAGE_FILE_EXTENSIONS || ext in RAW_FILE_EXTENSIONS || ext in VIDEO_FILE_EXTENSIONS
    }

    private fun extractUsbDevice(intent: Intent?) =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra(UsbManager.EXTRA_DEVICE) as? UsbDevice
        }

    private fun currentSessionUsbDevice(): UsbDevice? {
        val deviceName = currentSessionDeviceName ?: return null
        return usbManager.deviceList.values.firstOrNull { it.deviceName == deviceName }
    }

    private fun isCanonSession(): Boolean {
        return currentSessionUsbDevice()?.vendorId == CANON_VENDOR_ID
    }

    private fun getDefaultSessionInitializer(): DefaultCameraSessionInitializer? {
        return cameraSessionInitializer as? DefaultCameraSessionInitializer
    }

    private fun getCanonInitializer(): CanonCameraInitializer? {
        return getDefaultSessionInitializer()?.getCanonInitializer()
    }

    private fun hasActiveCanonNativeSession(): Boolean {
        val deviceName = currentSessionDeviceName ?: return false
        return getCanonInitializer()?.hasActiveSessionFor(deviceName) == true
    }

    private fun closeCanonNativeSessionIfNeeded() {
        val device = currentSessionUsbDevice() ?: return
        val connection = currentSessionConnection ?: return
        val canonInitializer = getCanonInitializer() ?: return

        if (!canonInitializer.hasActiveSessionFor(device.deviceName)) return

        runCatching {
            Log.d(TAG, "Closing active Canon native session for device=${device.deviceName}")
            canonInitializer.closeSession(device, connection)
        }.onFailure {
            Log.w(TAG, "Failed closing Canon native session", it)
        }
    }

    fun findPtpInterface(device: UsbDevice): UsbInterface? {
        repeat(device.interfaceCount) { index ->
            val usbInterface = device.getInterface(index)
            if (usbInterface.interfaceClass == UsbConstants.USB_CLASS_STILL_IMAGE) return usbInterface
        }
        return if (device.interfaceCount > 0) device.getInterface(0) else null
    }

    fun findBulkOutEndpoint(usbInterface: UsbInterface): UsbEndpoint? {
        repeat(usbInterface.endpointCount) { index ->
            val ep = usbInterface.getEndpoint(index)
            if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK &&
                ep.direction == UsbConstants.USB_DIR_OUT
            ) return ep
        }
        return null
    }

    fun findBulkInEndpoint(usbInterface: UsbInterface): UsbEndpoint? {
        repeat(usbInterface.endpointCount) { index ->
            val ep = usbInterface.getEndpoint(index)
            if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK &&
                ep.direction == UsbConstants.USB_DIR_IN
            ) return ep
        }
        return null
    }
}