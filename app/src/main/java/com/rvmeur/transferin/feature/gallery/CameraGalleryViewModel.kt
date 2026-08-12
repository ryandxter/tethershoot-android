package com.rvmeur.transferin.feature.gallery

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rvmeur.transferin.data.local.dao.CameraMediaDao
import com.rvmeur.transferin.data.local.entity.CameraMediaItemEntity
import com.rvmeur.transferin.data.local.entity.MediaStatus
import com.rvmeur.transferin.data.repository.CameraMediaRepositoryImpl
import com.rvmeur.transferin.data.usb.UsbHelper
import com.rvmeur.transferin.domain.repository.CameraMediaRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * State for Camera Gallery UI
 */
data class CameraGalleryState(
    val items: List<CameraMediaItemEntity> = emptyList(),
    val isLoading: Boolean = false,
    val isShootModeActive: Boolean = false,
    val baselineCount: Int = 0,
    val newCount: Int = 0,
    val uploadedCount: Int = 0,
    val error: String? = null
) {
    val totalCount: Int
        get() = items.size

    val pendingCount: Int
        get() = items.count {
            it.status == MediaStatus.DISCOVERED ||
                    it.status == MediaStatus.CACHED ||
                    it.status == MediaStatus.QUEUED
        }

    val uploadingCount: Int
        get() = items.count { it.status == MediaStatus.UPLOADING }

    val failedCount: Int
        get() = items.count { it.status == MediaStatus.FAILED }
}

/**
 * ViewModel for Camera Gallery - PiuFoto style
 * Observes camera media items from local DB
 */
class CameraGalleryViewModel(
    application: Application,
    private val cameraMediaRepository: CameraMediaRepository,
    private val deviceId: String
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(CameraGalleryState())
    val uiState: StateFlow<CameraGalleryState> = _uiState.asStateFlow()

    private var observeItemsJob: Job? = null

    init {
        observeCameraItems()
    }

    private fun observeCameraItems() {
        observeItemsJob?.cancel()
        observeItemsJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            runCatching {
                cameraMediaRepository.observeAllByDevice(deviceId).collect { items ->
                    val baselineCount = items.count { it.status == MediaStatus.UPLOADED }
                    val newCount = items.count {
                        it.status == MediaStatus.DISCOVERED ||
                                it.status == MediaStatus.CACHED ||
                                it.status == MediaStatus.QUEUED
                    }
                    val uploadedCount = items.count { it.status == MediaStatus.UPLOADED }

                    _uiState.update { state ->
                        state.copy(
                            isLoading = false,
                            items = items,
                            baselineCount = baselineCount,
                            newCount = newCount,
                            uploadedCount = uploadedCount,
                            error = null
                        )
                    }
                }
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = throwable.message ?: "Gagal memuat galeri kamera"
                    )
                }
            }
        }
    }

    fun refresh() {
        observeCameraItems()
    }

    fun setShootModeActive(active: Boolean) {
        _uiState.update { it.copy(isShootModeActive = active) }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    companion object {
        fun create(
            application: Application,
            cameraMediaDao: CameraMediaDao,
            usbHelper: UsbHelper,
            deviceId: String
        ): CameraGalleryViewModel {
            val repository = CameraMediaRepositoryImpl(
                context = application,
                usbHelper = usbHelper,
                cameraMediaDao = cameraMediaDao
            )

            return CameraGalleryViewModel(
                application = application,
                cameraMediaRepository = repository,
                deviceId = deviceId
            )
        }
    }
}