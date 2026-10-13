package com.rvmeur.transferin.data.usb

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import android.mtp.MtpDevice
import android.util.Log
import com.rvmeur.transferin.data.usb.vendor.CanonCameraInitializer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

data class CameraUnlockState(
    val status: UnlockStatus = UnlockStatus.IDLE,
    val deviceLabel: String? = null,
    val storageIds: List<Int> = emptyList(),
    val storageCount: Int = 0,
    val errorMessage: String? = null,
    val isTetheredMode: Boolean = false
)

enum class UnlockStatus {
    IDLE,
    REQUESTING_PERMISSION,
    OPENING_SESSION,
    RUNNING_HANDSHAKE,
    UNLOCK_SUCCESS,
    UNLOCK_FAILED,
    SESSION_CLOSED
}

/**
 * CameraUnlockOrchestrator
 *
 * Responsibilities:
 * 1. Resolve camera device
 * 2. Ensure USB permission
 * 3. Open UsbDeviceConnection
 * 4. Run Canon tethered handshake
 * 5. Open MTP session only after handshake
 * 6. Publish UNLOCK_SUCCESS only when storage is ready
 */
class CameraUnlockOrchestrator(
    private val context: Context,
    private val usbHelper: UsbHelper,
    private val permissionManager: UsbPermissionManager? = null
) {
    companion object {
        private const val TAG = "CameraUnlockOrch"
        private const val MTP_OPEN_TIMEOUT_MS = 8_000L
        private const val STORAGE_READY_RETRY_COUNT = 5
        private const val STORAGE_READY_RETRY_DELAY_MS = 300L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val sessionMutex = Mutex()

    private val usbManager: UsbManager by lazy {
        context.getSystemService(Context.USB_SERVICE) as UsbManager
    }

    private val _unlockState = MutableStateFlow(CameraUnlockState())
    val unlockState: StateFlow<CameraUnlockState> = _unlockState.asStateFlow()

    private var activeConnection: UsbDeviceConnection? = null
    private var activeMtpDevice: MtpDevice? = null
    private var activeDevice: UsbDevice? = null
    private var activeDeviceName: String? = null

    private val vendorInitializer = CanonCameraInitializer()
    private val isManagingPermission = permissionManager != null

    init {
        permissionManager?.let { manager ->
            manager.onPermissionGrantedWithDeviceReady = { device ->
                Log.d(TAG, ">>> Permission granted for ${device.deviceName}, initiating unlock")
                scope.launch {
                    openCameraSession(device)
                }
            }
            manager.onDeviceDetached = { device ->
                if (activeDeviceName == device.deviceName) {
                    Log.d(TAG, ">>> Active device detached: ${device.deviceName}, closing session")
                    closeSession()
                }
            }
            manager.startMonitoring()
        }
    }

    fun requestUnlock() {
        if (permissionManager != null) {
            val device = permissionManager.getAllCameraDevices().firstOrNull()
            if (device == null) {
                _unlockState.value = CameraUnlockState(
                    status = UnlockStatus.IDLE,
                    errorMessage = "Tidak ada kamera terdeteksi"
                )
                Log.w(TAG, "No camera devices found")
                return
            }

            if (activeDeviceName == device.deviceName && activeConnection != null) {
                Log.d(TAG, "requestUnlock: Session already active for ${device.deviceName}, keeping unlock state")
                val storageIds = activeMtpDevice?.storageIds?.toList().orEmpty()
                _unlockState.value = CameraUnlockState(
                    status = UnlockStatus.UNLOCK_SUCCESS,
                    deviceLabel = buildDeviceLabel(device),
                    storageIds = storageIds,
                    storageCount = storageIds.size,
                    errorMessage = null,
                    isTetheredMode = true
                )
                return
            }

            Log.d(TAG, ">>> requestUnlock for ${device.deviceName}")
            _unlockState.value = _unlockState.value.copy(
                status = UnlockStatus.REQUESTING_PERMISSION,
                deviceLabel = buildDeviceLabel(device),
                errorMessage = null
            )

            permissionManager.requestPermissionWithAutoUnlock(device)
            return
        }

        val device = findCameraDevice()
        if (device == null) {
            _unlockState.value = CameraUnlockState(
                status = UnlockStatus.IDLE,
                errorMessage = "Tidak ada kamera terdeteksi"
            )
            Log.w(TAG, "No camera devices found")
            return
        }

        scope.launch {
            openCameraSession(device)
        }
    }

    private fun findCameraDevice(): UsbDevice? {
        return usbManager.deviceList.values.firstOrNull { device ->
            usbManager.hasPermission(device) && isCameraLike(device)
        }
    }

    private fun isCameraLike(device: UsbDevice): Boolean {
        if (device.deviceClass == 6) return true
        val combined = "${device.manufacturerName.orEmpty()} ${device.productName.orEmpty()}".lowercase()
        val keywords = listOf("camera", "canon", "nikon", "sony", "eos", "mirrorless")
        return keywords.any { combined.contains(it) }
    }

    private fun buildDeviceLabel(device: UsbDevice): String {
        val manufacturer = device.manufacturerName?.takeIf { it.isNotBlank() } ?: "Unknown"
        val product = device.productName?.takeIf { it.isNotBlank() } ?: "Camera"
        return "$manufacturer $product"
    }

    fun isSessionOpen(): Boolean {
        return activeDevice != null && activeConnection != null
    }

    fun getActiveDevice(): UsbDevice? = activeDevice

    fun getActiveMtpDevice(): MtpDevice? = activeMtpDevice

    fun getActiveStorageIds(): List<Int> = unlockState.value.storageIds

    fun getFirstStorageId(): Int = unlockState.value.storageIds.firstOrNull() ?: 0

    private suspend fun openCameraSession(device: UsbDevice): Boolean = sessionMutex.withLock {
        Log.d(TAG, ">>> openCameraSession: ${device.deviceName}")

        if (activeDeviceName == device.deviceName && activeConnection != null) {
            Log.d(TAG, "Session already active for ${device.deviceName}, skipping reopen")
            val storageIds = activeMtpDevice?.storageIds?.toList().orEmpty()
            _unlockState.value = CameraUnlockState(
                status = UnlockStatus.UNLOCK_SUCCESS,
                deviceLabel = buildDeviceLabel(device),
                storageIds = storageIds,
                storageCount = storageIds.size,
                errorMessage = null,
                isTetheredMode = true
            )
            return true
        }

        _unlockState.value = _unlockState.value.copy(
            status = UnlockStatus.OPENING_SESSION,
            deviceLabel = buildDeviceLabel(device),
            errorMessage = null
        )

        closeSessionInternal()

        val connection = withContext(Dispatchers.IO) {
            usbManager.openDevice(device)
        } ?: run {
            failUnlock(
                message = "Gagal membuka koneksi USB",
                deviceLabel = buildDeviceLabel(device)
            )
            return false
        }

        _unlockState.value = _unlockState.value.copy(
            status = UnlockStatus.RUNNING_HANDSHAKE,
            deviceLabel = buildDeviceLabel(device),
            errorMessage = null
        )

        val isCanon = device.vendorId == 0x04A9
        val isFuji = device.vendorId == 0x04CB
        val handshakeResult = if (isCanon) {
            runCatching {
                vendorInitializer.initializeUnlocked(
                    device = device,
                    connection = connection
                )
            }.getOrElse {
                CameraInitResult.fail("Handshake error: ${it.message}")
            }
        } else if (isFuji) {
            runCatching {
                val fujiInit = usbHelper.getFujiInitializer()
                val initRes = fujiInit?.initialize(
                    CameraInitContext(
                        device = device,
                        connection = connection,
                        mtpDevice = null,
                        usbHelper = usbHelper
                    )
                )
                initRes ?: CameraInitResult.ok("Fuji initialized")
            }.getOrElse {
                CameraInitResult.fail("Fuji handshake error: ${it.message}")
            }
        } else {
            Log.d(TAG, "Other device detected (Vendor ID: ${device.vendorId}). Bypassing vendor-specific handshake.")
            CameraInitResult.ok("Bypassed vendor-specific handshake")
        }

        if (!handshakeResult.success) {
            runCatching { connection.close() }
            failUnlock(
                message = handshakeResult.message ?: "Handshake gagal",
                deviceLabel = buildDeviceLabel(device)
            )
            return false
        }

        activeConnection = connection
        activeMtpDevice = null
        activeDevice = device
        activeDeviceName = device.deviceName

        // Try opening MTP session, but do NOT fail if native PTP session is active
        val mtpOpened = openMtpSessionIfNeededNoLock()
        val hasNativeSession = isCanon || isFuji || usbHelper.getActiveSonyAdapter() != null

        if (!mtpOpened && !hasNativeSession) {
            failUnlock(
                message = "Handshake berhasil, tetapi sesi MTP tidak bisa dibuka",
                deviceLabel = buildDeviceLabel(device)
            )
            closeSessionInternal()
            return false
        }

        val storageIds = awaitStorageIdsNoLock()
        val resolvedStorageIds = if (storageIds.isNotEmpty()) {
            storageIds
        } else if (hasNativeSession) {
            usbHelper.getActiveStorageIds().ifEmpty { listOf(0x00010001) }
        } else {
            emptyList()
        }

        if (resolvedStorageIds.isEmpty()) {
            failUnlock(
                message = "Handshake berhasil, tetapi storage kamera belum siap",
                deviceLabel = buildDeviceLabel(device)
            )
            closeSessionInternal()
            return false
        }

        _unlockState.value = CameraUnlockState(
            status = UnlockStatus.UNLOCK_SUCCESS,
            deviceLabel = buildDeviceLabel(device),
            storageIds = resolvedStorageIds,
            storageCount = resolvedStorageIds.size,
            errorMessage = null,
            isTetheredMode = true
        )

        Log.d(
            TAG,
            ">>> Camera unlock success. device=${device.deviceName}, mtpOpened=$mtpOpened, storageIds=$resolvedStorageIds"
        )
        return true
    }

    suspend fun openMtpSessionIfNeeded(): Boolean = sessionMutex.withLock {
        openMtpSessionIfNeededNoLock()
    }

    private suspend fun openMtpSessionIfNeededNoLock(): Boolean {
        val device = activeDevice ?: return false
        val connection = activeConnection ?: return false

        if (activeMtpDevice != null) {
            Log.d(TAG, "MTP session already open for ${device.deviceName}")
            return true
        }

        Log.d(TAG, ">>> openMtpSessionIfNeeded for ${device.deviceName}")

        val mtpDevice = MtpDevice(device)
        val openedMtp = try {
            withContext(Dispatchers.IO) {
                withTimeout(MTP_OPEN_TIMEOUT_MS) {
                    mtpDevice.open(connection)
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "MTP open failed", t)
            false
        }

        if (!openedMtp) {
            return false
        }

        activeMtpDevice = mtpDevice
        usbHelper.setMtpDeviceOverride(mtpDevice, device.deviceName)
        return true
    }

    private suspend fun awaitStorageIdsNoLock(): List<Int> {
        val mtpDevice = activeMtpDevice ?: return emptyList()

        repeat(STORAGE_READY_RETRY_COUNT) { attempt ->
            val storageIds = runCatching {
                mtpDevice.storageIds?.toList().orEmpty()
            }.getOrElse {
                Log.w(TAG, "Failed reading storageIds on attempt=${attempt + 1}", it)
                emptyList()
            }

            if (storageIds.isNotEmpty()) {
                Log.d(TAG, "Storage IDs ready on attempt=${attempt + 1}: $storageIds")
                return storageIds
            }

            Log.w(
                TAG,
                "Storage IDs not ready on attempt=${attempt + 1}/$STORAGE_READY_RETRY_COUNT"
            )

            if (attempt < STORAGE_READY_RETRY_COUNT - 1) {
                delay(STORAGE_READY_RETRY_DELAY_MS)
            }
        }

        return emptyList()
    }

    fun closeSession() {
        scope.launch {
            sessionMutex.withLock {
                closeSessionInternal()
                _unlockState.value = CameraUnlockState(status = UnlockStatus.SESSION_CLOSED)
                Log.d(TAG, "Session closed")
            }
        }
    }

    suspend fun closeSessionSuspend() = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        sessionMutex.withLock {
            closeSessionInternal()
            _unlockState.value = CameraUnlockState(status = UnlockStatus.SESSION_CLOSED)
            Log.d(TAG, "Session closed via suspend")
        }
    }

    private fun closeSessionInternal() {
        val device = activeDevice
        val connection = activeConnection

        if (device != null && connection != null) {
            try {
                vendorInitializer.closeSession(device, connection)
            } catch (t: Throwable) {
                Log.w(TAG, "Error during Canon closeSession", t)
            }

            for (i in 0 until device.interfaceCount) {
                val usbInterface = device.getInterface(i)
                runCatching {
                    connection.releaseInterface(usbInterface)
                    Log.d(TAG, "Released interface $i in Orchestrator")
                }
            }
        }

        try {
            activeMtpDevice?.close()
        } catch (t: Throwable) {
            Log.w(TAG, "Error closing MTP device", t)
        } finally {
            activeMtpDevice = null
            usbHelper.setMtpDeviceOverride(null, null)
        }

        try {
            connection?.close()
        } catch (t: Throwable) {
            Log.w(TAG, "Error closing USB connection", t)
        }

        activeConnection = null
        activeDevice = null
        activeDeviceName = null
    }

    private fun failUnlock(
        message: String,
        deviceLabel: String? = null
    ) {
        _unlockState.value = CameraUnlockState(
            status = UnlockStatus.UNLOCK_FAILED,
            deviceLabel = deviceLabel,
            errorMessage = message
        )
        Log.e(TAG, "Unlock failed: $message")
    }

    fun shutdown() {
        scope.launch {
            sessionMutex.withLock {
                closeSessionInternal()
                if (isManagingPermission) {
                    permissionManager?.stopMonitoring()
                }
            }
        }
    }
}