package com.reflection.transferin.domain.session

import android.content.Context
import android.util.Log
import androidx.core.net.toUri
import com.reflection.transferin.data.usb.FileStabilityChecker
import com.reflection.transferin.data.usb.UsbHelper
import com.reflection.transferin.data.usb.UsbSourceOrchestrator
import com.reflection.transferin.domain.model.CameraRuntimeState
import com.reflection.transferin.domain.model.ConnectionMode
import com.reflection.transferin.domain.model.MediaJob
import com.reflection.transferin.domain.model.ProjectSession
import com.reflection.transferin.domain.model.UploadStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

/**
 * ImportEngine - Fallback Mode
 * Key: Full storage access for manual ingest.
 */
class ImportCameraEngine(
    private val appContext: Context,
    private val usbHelper: UsbHelper,
    private val usbSourceOrchestrator: UsbSourceOrchestrator,
    private val sourceWatcher: SourceWatcher,
    private val fileStabilityChecker: FileStabilityChecker
) : CameraEngine {

    private val scope = CoroutineScope(Dispatchers.IO + Job())

    private val _runtimeState = MutableStateFlow(CameraRuntimeState(statusText = "Import Engine Ready"))
    override val runtimeState: StateFlow<CameraRuntimeState> = _runtimeState.asStateFlow()

    private val _incomingMedia = MutableSharedFlow<MediaJob>(extraBufferCapacity = 64)
    override val incomingMedia = _incomingMedia.asSharedFlow()

    private var currentSession: ProjectSession? = null
    private var watcherJob: Job? = null

    init {
        // Start watching for discovered files from orchestrator
        watcherJob = scope.launch {
            sourceWatcher.newFiles.collect { discovered ->
                val job = MediaJob(
                    id = UUID.randomUUID().toString(),
                    projectId = currentSession?.id ?: "unknown",
                    localTempPath = discovered.fullPath,
                    originalFileName = discovered.fileName,
                    sourceUri = discovered.fullPath,
                    sourceFingerprint = "${discovered.fileName}|${discovered.sizeBytes}|${discovered.lastModifiedEpochMs}",
                    fileSizeBytes = discovered.sizeBytes,
                    lastModifiedEpochMs = discovered.lastModifiedEpochMs,
                    createdAtEpochMs = System.currentTimeMillis(),
                    uploadStatus = UploadStatus.QUEUED
                )
                _incomingMedia.emit(job)
            }
        }
    }

    override fun connect(projectSession: ProjectSession) {
        currentSession = projectSession
        // NOTE: Don't set canShoot = false here - that would indicate camera is locked
        // The actual MTP session should only be opened when user explicitly wants to access storage
        // For TETHERED mode, we intentionally don't open MTP session
        _runtimeState.value = CameraRuntimeState(
            isConnected = true,
            canShoot = true, // Camera stays usable until explicitly locked
            activeMode = ConnectionMode.IMPORT_FALLBACK,
            statusText = "Import Engine Ready"
        )
    }

    override fun disconnect() {
        currentSession = null
        _runtimeState.value = CameraRuntimeState(statusText = "Import Disconnected")
    }

    override fun refresh() {
        usbHelper.refreshUsbState()
    }

    fun setSourceSelectionMode(mode: com.reflection.transferin.domain.model.SourceSelectionMode) {
        usbSourceOrchestrator.setMode(mode)
    }

    // Bridge for legacy UI calls
    fun openMtpSessionIfNeeded() {
        android.util.Log.d("ImportCameraEngine", ">>> openMtpSessionIfNeeded called - THIS WILL LOCK CAMERA")
        if (!usbHelper.isSessionOpen()) {
            android.util.Log.d("ImportCameraEngine", ">>> Session not open, calling openFirstCameraSession()")
            usbHelper.openFirstCameraSession()
        } else {
            android.util.Log.d("ImportCameraEngine", ">>> Session already open")
        }
    }
    
    fun startLiveSession() {
        // Handle start
    }
    
    fun startLiveSessionHybrid() {
        // Handle hybrid
    }
}
