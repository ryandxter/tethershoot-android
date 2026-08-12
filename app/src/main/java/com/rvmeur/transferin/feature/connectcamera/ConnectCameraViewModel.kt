package com.rvmeur.transferin.feature.connectcamera

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rvmeur.transferin.data.usb.CameraSessionState
import com.rvmeur.transferin.data.usb.UsbDeviceInfo
import com.rvmeur.transferin.data.usb.UsbHelper
import com.rvmeur.transferin.data.usb.UsbMonitorState
import com.rvmeur.transferin.domain.model.CameraMedia
import com.rvmeur.transferin.domain.model.ConnectionMode
import com.rvmeur.transferin.domain.model.SourceSelectionMode
import com.rvmeur.transferin.domain.repository.CameraMediaRepository
import com.rvmeur.transferin.domain.session.ProjectSessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ConnectCameraUiState(
    val isLoading: Boolean = true,
    val hasUsbDevice: Boolean = false,
    val hasPermittedUsbDevice: Boolean = false,
    val isLikelyCameraConnected: Boolean = false,
    val usbStatusText: String = "Memeriksa perangkat USB...",
    val connectedDevices: List<UsbDeviceInfo> = emptyList(),
    val cameraLikeDevices: List<UsbDeviceInfo> = emptyList(),
    val cameraSession: CameraSessionState = CameraSessionState(),

    // Default harus TETHERED untuk flow kamera baru
    val connectionMode: ConnectionMode = ConnectionMode.TETHERED,

    val sourceSelectionMode: SourceSelectionMode = SourceSelectionMode.BROWSE_FOLDER,
    val mediaList: List<CameraMedia> = emptyList(),
    val mediaCount: Int = 0,
    val isLoadingMedia: Boolean = false,
    val mediaError: String? = null,
    val activeStorageId: Int? = null
) {
    val primaryDevice: UsbDeviceInfo?
        get() = cameraLikeDevices.firstOrNull() ?: connectedDevices.firstOrNull()

    val canRequestPermission: Boolean
        get() = hasUsbDevice && !hasPermittedUsbDevice

    // Untuk flow baru, proceed cukup berdasarkan permission + kamera terdeteksi.
    // Unlock/session tethered akan ditangani di SessionViewModel saat masuk session active.
    val canProceedToTetheredFlow: Boolean
        get() = hasPermittedUsbDevice && isLikelyCameraConnected

    // Dipertahankan untuk kompatibilitas UI lama jika masih dipakai
    val canOpenCameraSession: Boolean
        get() = false

    val canProceed: Boolean
        get() = canProceedToTetheredFlow || cameraSession.isOpen

    val hasLoadedMedia: Boolean
        get() = mediaList.isNotEmpty()
}

class ConnectCameraViewModel(
    private val usbHelper: UsbHelper,
    private val cameraMediaRepository: CameraMediaRepository,
    private val projectSessionManager: ProjectSessionManager
) : ViewModel() {

    companion object {
        private const val TAG = "ConnectCameraVM"
    }

    private val _uiState = MutableStateFlow(ConnectCameraUiState())
    val uiState: StateFlow<ConnectCameraUiState> = _uiState.asStateFlow()

    private var lastLoadedStorageId: Int? = null
    private var isEjected = false

    init {
        observeUsbMonitor()
        ensureTetheredModeSelected()
        usbHelper.startMonitoring()
        refreshUsbState()
    }

    private fun ensureTetheredModeSelected() {
        if (_uiState.value.connectionMode == ConnectionMode.TETHERED) return

        _uiState.value = _uiState.value.copy(connectionMode = ConnectionMode.TETHERED)

        viewModelScope.launch {
            runCatching {
                projectSessionManager.setConnectionMode(ConnectionMode.TETHERED)
            }.onFailure { throwable ->
                Log.w(
                    TAG,
                    "Failed to persist default TETHERED mode: ${throwable.message}",
                    throwable
                )
            }
        }
    }

    fun selectConnectionMode(mode: ConnectionMode) {
        _uiState.value = _uiState.value.copy(connectionMode = mode)
        viewModelScope.launch {
            runCatching {
                projectSessionManager.setConnectionMode(mode)
            }.onFailure { throwable ->
                Log.w(
                    TAG,
                    "Failed to set connection mode=$mode: ${throwable.message}",
                    throwable
                )
            }
        }
    }

    fun selectTetheredMode() {
        selectConnectionMode(ConnectionMode.TETHERED)
    }

    fun setSourceSelectionMode(mode: SourceSelectionMode) {
        _uiState.value = _uiState.value.copy(sourceSelectionMode = mode)
        Log.d(TAG, "Source selection mode set to: $mode")
    }

    fun refreshUsbState() {
        _uiState.value = _uiState.value.copy(
            isLoading = true,
            usbStatusText = "Memeriksa perangkat USB..."
        )
        usbHelper.refreshUsbState()
    }

    fun requestUsbPermission(): Boolean {
        // Pastikan sebelum minta permission, mode sudah TETHERED
        if (_uiState.value.connectionMode != ConnectionMode.TETHERED) {
            selectTetheredMode()
        }

        isEjected = false

        val requested =
            usbHelper.requestPermissionForFirstCameraLikeDevice() ||
                    usbHelper.requestPermissionForFirstDevice()

        _uiState.value = if (requested) {
            _uiState.value.copy(
                isLoading = true,
                usbStatusText = "Meminta izin akses perangkat USB untuk mode tethered..."
            )
        } else {
            _uiState.value.copy(
                isLoading = false,
                usbStatusText = "Tidak ada perangkat USB yang bisa dimintai izin."
            )
        }

        return requested
    }

    /**
     * JANGAN pakai flow open session lama di connect screen.
     * Jalur ini berisiko memicu mode lama yang membuat kamera lock.
     * Unlock/session tethered harus dijalankan dari SessionViewModel / orchestrator.
     */
    fun openCameraSession(): Boolean {
        Log.w(
            TAG,
            "openCameraSession() is disabled for tethered-first flow. " +
                    "Use SessionViewModel tethered session flow instead."
        )

        _uiState.value = _uiState.value.copy(
            isLoading = false,
            usbStatusText = "Mode tethered siap. Lanjutkan ke sesi untuk unlock kamera."
        )
        return false
    }

    fun loadMediaFromActiveStorage() {
        val storageId = _uiState.value.cameraSession.storageIds.firstOrNull()

        if (storageId == null) {
            _uiState.value = _uiState.value.copy(
                isLoadingMedia = false,
                mediaError = "Storage kamera belum tersedia.",
                mediaList = emptyList(),
                mediaCount = 0,
                activeStorageId = null
            )
            return
        }

        if (_uiState.value.isLoadingMedia) return
        if (lastLoadedStorageId == storageId && _uiState.value.hasLoadedMedia) return

        loadMedia(storageId)
    }

    fun proceedToNextStep() {
        val state = _uiState.value
        if (!state.canProceedToTetheredFlow) {
            Log.d(
                TAG,
                "proceedToNextStep blocked: permission=${state.hasPermittedUsbDevice}, cameraDetected=${state.isLikelyCameraConnected}"
            )
            return
        }

        // Paksa tetap di mode tethered saat lanjut ke step berikutnya
        if (state.connectionMode != ConnectionMode.TETHERED) {
            _uiState.value = state.copy(connectionMode = ConnectionMode.TETHERED)
        }

        viewModelScope.launch {
            runCatching {
                projectSessionManager.setConnectionMode(ConnectionMode.TETHERED)
                projectSessionManager.setActive(true)
            }.onFailure { throwable ->
                Log.w(
                    TAG,
                    "Failed to activate tethered session flow: ${throwable.message}",
                    throwable
                )
            }
        }
    }

    fun getPrimaryDeviceLabel(): String? {
        return _uiState.value.primaryDevice?.let(::buildDeviceLabel)
    }

    fun getCameraSessionLabel(): String? {
        return _uiState.value.cameraSession.deviceLabel
    }

    fun canProceedToNextStep(): Boolean {
        return _uiState.value.canProceedToTetheredFlow
    }

    private fun observeUsbMonitor() {
        viewModelScope.launch {
            usbHelper.monitorState.collect { monitorState ->
                val previous = _uiState.value
                var updated = monitorState.toUiState(previous)

                if (!updated.hasUsbDevice) {
                    isEjected = false
                }

                if (isEjected) {
                    updated = updated.copy(
                        hasPermittedUsbDevice = false,
                        usbStatusText = "USB Ejected. Silakan ketuk 'Izinkan USB' untuk menghubungkan kembali."
                    )
                }

                _uiState.value = updated

                if (!updated.cameraSession.isOpen) {
                    lastLoadedStorageId = null
                    _uiState.value = _uiState.value.copy(
                        mediaList = emptyList(),
                        mediaCount = 0,
                        isLoadingMedia = false,
                        mediaError = null,
                        activeStorageId = null
                    )
                }

                if (updated.hasPermittedUsbDevice && updated.connectionMode != ConnectionMode.TETHERED) {
                    Log.d(TAG, "USB permission available, forcing TETHERED mode in UI state")
                    _uiState.value = _uiState.value.copy(connectionMode = ConnectionMode.TETHERED)
                }
            }
        }
    }

    private fun loadMedia(storageId: Int) {
        _uiState.value = _uiState.value.copy(
            isLoadingMedia = true,
            mediaError = null,
            activeStorageId = storageId
        )

        viewModelScope.launch {
            runCatching {
                cameraMediaRepository.listMedia(storageId)
            }.onSuccess { media ->
                lastLoadedStorageId = storageId
                _uiState.value = _uiState.value.copy(
                    isLoadingMedia = false,
                    mediaError = null,
                    mediaList = media,
                    mediaCount = media.size,
                    activeStorageId = storageId,
                    usbStatusText = buildUsbStatusWithMediaCount(
                        baseStatus = _uiState.value.usbStatusText,
                        mediaCount = media.size
                    )
                )
            }.onFailure { throwable ->
                _uiState.value = _uiState.value.copy(
                    isLoadingMedia = false,
                    mediaError = throwable.message ?: "Gagal membaca file kamera.",
                    mediaList = emptyList(),
                    mediaCount = 0,
                    activeStorageId = storageId
                )
            }
        }
    }

    private fun UsbMonitorState.toUiState(
        previous: ConnectCameraUiState
    ): ConnectCameraUiState {
        val nextStatus = when {
            hasAnyPermittedUsbDevice && isLikelyCameraConnected ->
                "Kamera siap untuk mode tethered. Lanjutkan untuk unlock dan scan gallery."
            else -> statusText
        }

        return previous.copy(
            isLoading = false,
            hasUsbDevice = hasAnyUsbDevice,
            hasPermittedUsbDevice = hasAnyPermittedUsbDevice,
            isLikelyCameraConnected = isLikelyCameraConnected,
            usbStatusText = nextStatus,
            connectedDevices = connectedDevices,
            cameraLikeDevices = cameraLikeDevices,
            cameraSession = cameraSession,
            connectionMode = ConnectionMode.TETHERED
        )
    }

    private fun buildDeviceLabel(device: UsbDeviceInfo): String {
        val manufacturer = device.manufacturerName?.takeIf { it.isNotBlank() }
        val product = device.productName?.takeIf { it.isNotBlank() }

        return when {
            manufacturer != null && product != null -> "$manufacturer $product"
            product != null -> product
            manufacturer != null -> manufacturer
            else -> "VID:${device.vendorId} PID:${device.productId}"
        }
    }

    private fun buildUsbStatusWithMediaCount(
        baseStatus: String,
        mediaCount: Int
    ): String {
        return if (mediaCount > 0) {
            "$baseStatus Ditemukan $mediaCount item di storage kamera."
        } else {
            "$baseStatus Tidak ada item media yang terdeteksi."
        }
    }

    fun ejectUsb() {
        Log.d(TAG, "ejectUsb() requested")
        isEjected = true
        usbHelper.closeCameraSession()
        refreshUsbState()
    }

    override fun onCleared() {
        super.onCleared()
    }
}