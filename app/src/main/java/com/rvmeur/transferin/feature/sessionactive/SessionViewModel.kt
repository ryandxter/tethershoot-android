package com.rvmeur.transferin.feature.sessionactive

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rvmeur.transferin.data.repository.SessionRepositoryImpl
import com.rvmeur.transferin.data.repository.TransferQueueRepositoryImpl
import com.rvmeur.transferin.data.usb.CameraUnlockOrchestrator
import com.rvmeur.transferin.data.usb.PhotoWatcher
import com.rvmeur.transferin.data.usb.UnlockStatus
import com.rvmeur.transferin.data.usb.UsbHelper
import com.rvmeur.transferin.data.usb.UsbPermissionManager
import com.rvmeur.transferin.data.usb.UsbSourceOrchestrator
import com.rvmeur.transferin.domain.model.ConnectionMode
import com.rvmeur.transferin.domain.model.MediaJob
import com.rvmeur.transferin.domain.model.ProjectSession
import com.rvmeur.transferin.domain.model.SourceSelectionMode
import com.rvmeur.transferin.domain.model.UploadStatus
import com.rvmeur.transferin.domain.session.ImportCameraEngine
import com.rvmeur.transferin.domain.session.ProjectSessionManager
import com.rvmeur.transferin.domain.session.UploadQueueManager
import com.rvmeur.transferin.domain.shoot.ShootModeEngine
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

import com.rvmeur.transferin.data.local.db.DatabaseProvider
import com.rvmeur.transferin.data.usb.CtgWatcher
import com.rvmeur.transferin.domain.shoot.CtgDatabaseParser
class SessionViewModel(
    private val application: Application,
    private val sessionRepository: SessionRepositoryImpl,
    private val transferQueueRepository: TransferQueueRepositoryImpl,
    private val projectSessionManager: ProjectSessionManager,
    private val uploadQueueManager: UploadQueueManager,
    private val usbHelper: UsbHelper,
    private val usbSourceOrchestrator: UsbSourceOrchestrator
) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "SessionViewModel"
        private const val PHOTO_WATCHER_INIT_RETRY_COUNT = 3
        private const val PHOTO_WATCHER_INIT_RETRY_DELAY_MS = 400L
    }

    private var activeSessionId: String? = null
    private var lastHandledSessionSignature: String? = null
    private var isPhotoWatcherStarted = false
    private var photoWatcherStartJob: Job? = null
    private var queueObservationJob: Job? = null

    private val _stats = MutableStateFlow(SessionStats())
    val stats: StateFlow<SessionStats> = _stats.asStateFlow()

    private val _summaryState = MutableStateFlow(SessionSummaryState())
    val summaryState: StateFlow<SessionSummaryState> = _summaryState.asStateFlow()

    private val _projectSession = MutableStateFlow<ProjectSession?>(null)
    val projectSession: StateFlow<ProjectSession?> = _projectSession.asStateFlow()

    private val permissionManager = UsbPermissionManager(application)
    private val cameraUnlockOrchestrator =
        CameraUnlockOrchestrator(application, usbHelper, permissionManager)

    private val photoWatcher = PhotoWatcher(
        usbHelper = usbHelper,
        activeStorageIdsProvider = { cameraUnlockOrchestrator.getActiveStorageIds() }
    )

    private val ctgDatabaseParser = CtgDatabaseParser(
        getAllObjectHandles = { usbHelper.getAllObjectHandles(it) },
        getObjectMetadata = { usbHelper.getObjectMetadata(it) },
        downloadToCache = { handle ->
            val meta = usbHelper.getObjectMetadata(handle) ?: return@CtgDatabaseParser null
            usbHelper.downloadMediaToCache(meta)
        },
        cacheDir = application.cacheDir
    )

    private val ctgWatcher = CtgWatcher(
        usbHelper = usbHelper,
        ctgParser = ctgDatabaseParser
    )

    private val uploadedFingerprintDao by lazy {
        DatabaseProvider.getDatabase(application).uploadedFingerprintDao()
    }

    private val _tetherState = MutableStateFlow(TetherState())
    val tetherState: StateFlow<TetherState> = _tetherState.asStateFlow()

    private val tetheredEngine = ShootModeEngine(application, usbHelper, usbSourceOrchestrator)
    val tetheredEngineInstance: ShootModeEngine
        get() = tetheredEngine

    private val importEngine = ImportCameraEngine(
        appContext = application,
        usbHelper = usbHelper,
        usbSourceOrchestrator = usbSourceOrchestrator,
        sourceWatcher = com.rvmeur.transferin.domain.session.SourceWatcher(),
        fileStabilityChecker = com.rvmeur.transferin.data.usb.FileStabilityChecker(
            object : com.rvmeur.transferin.data.usb.UsbFileInfoProvider {
                override suspend fun getFileInfo(uri: Uri): com.rvmeur.transferin.data.usb.UsbFileInfo? = null
            }
        )
    )

    private val _shootModeState = MutableStateFlow(ShootModeState())
    val shootModeState: StateFlow<ShootModeState> = _shootModeState.asStateFlow()

    private val _currentSourceSelectionMode =
        MutableStateFlow(SourceSelectionMode.BROWSE_FOLDER)
    val currentSourceSelectionMode: StateFlow<SourceSelectionMode> =
        _currentSourceSelectionMode.asStateFlow()

    private var currentTreeUri: Uri? = null

    init {
        observeActiveProjectSession()
        observeUnlockState()
        observeNewPhotos()
        observeEngineStates()
    }

    fun setTreeUri(uri: Uri) {
        currentTreeUri = uri
        photoWatcher.setTreeUri(uri)
        Log.d(TAG, ">>> Tree URI set: $uri")
    }

    private fun clearTreeUri() {
        currentTreeUri = null
        Log.d(TAG, ">>> Tree URI cleared for tethered auto-detect mode")
    }

    private fun normalizeSourcePath(path: String?): String {
        return path.orEmpty().trim()
    }

    private fun isEquivalentSessionActive(
        current: ProjectSession?,
        nasFolderPath: String,
        connectionMode: ConnectionMode,
        sourceTreeUri: String
    ): Boolean {
        if (current == null) return false
        if (current.connectionMode != connectionMode) return false

        val currentNas = current.targetNasPath.trim()
        val requestedNas = nasFolderPath.trim()

        val currentSource = normalizeSourcePath(current.sourceFolderPath)
        val requestedSource = normalizeSourcePath(sourceTreeUri)

        return currentNas == requestedNas && currentSource == requestedSource
    }

    private fun observeUnlockState() {
        viewModelScope.launch {
            cameraUnlockOrchestrator.unlockState.collect { state ->
                Log.d(TAG, ">>> Unlock state: ${state.status}, storageCount=${state.storageCount}")

                _tetherState.update {
                    it.copy(
                        isConnected = state.status == UnlockStatus.UNLOCK_SUCCESS,
                        isUnlocked = state.status == UnlockStatus.UNLOCK_SUCCESS,
                        statusText = when (state.status) {
                            UnlockStatus.UNLOCK_SUCCESS -> {
                                if (state.storageCount > 0) {
                                    "📸 Kamera Tersambung"
                                } else {
                                    "📸 Kamera tersambung, storage belum siap"
                                }
                            }
                            UnlockStatus.OPENING_SESSION -> "⏳ Membuka sesi..."
                            UnlockStatus.RUNNING_HANDSHAKE -> "🔐 Handshake..."
                            UnlockStatus.UNLOCK_FAILED -> "❌ Gagal: ${state.errorMessage}"
                            UnlockStatus.SESSION_CLOSED -> "Sesi kamera ditutup."
                            else -> "Siap."
                        },
                        storageName = if (state.storageCount > 0) {
                            "${state.storageCount} storage"
                        } else {
                            null
                        }
                    )
                }

                when (state.status) {
                    UnlockStatus.UNLOCK_SUCCESS -> {
                        if (state.storageCount > 0) {
                            if (!isPhotoWatcherStarted) {
                                startPhotoWatching()
                            }
                        } else {
                            Log.w(TAG, ">>> Unlock success but no storage available, PhotoWatcher not started")
                            stopPhotoWatching()
                        }
                    }

                    UnlockStatus.UNLOCK_FAILED,
                    UnlockStatus.SESSION_CLOSED -> {
                        stopPhotoWatching()
                    }

                    else -> Unit
                }
            }
        }
    }

    private suspend fun downloadMtpFile(handle: Int, name: String): java.io.File? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val mtpDevice = cameraUnlockOrchestrator.getActiveMtpDevice() ?: return@withContext null
        val transitDir = application.getExternalFilesDir("camera_transit") ?: return@withContext null
        if (!transitDir.exists()) {
            transitDir.mkdirs()
        }
        val targetFile = java.io.File(transitDir, "${handle}_$name")
        val success = runCatching {
            kotlinx.coroutines.withTimeout(30_000L) {
                mtpDevice.importFile(handle, targetFile.absolutePath)
            }
        }.getOrDefault(false)
        if (success) targetFile else null
    }

    private fun observeNewPhotos() {
        viewModelScope.launch {
            photoWatcher.newPhotos.collect { event ->
                Log.i(TAG, ">>> NEW PHOTO: ${event.fileName}, uri=${event.contentUri}")

                val sessionId = activeSessionId
                if (sessionId.isNullOrBlank()) {
                    Log.w(TAG, "No active session id, skip new photo enqueue")
                    return@collect
                }

                var localFilePath = ""
                var sourceUriString = event.contentUri.toString()

                if (event.contentUri == Uri.EMPTY || event.contentUri.toString().isBlank()) {
                    Log.d(TAG, ">>> PhotoWatcher emitted empty Uri, downloading via MTP direct...")
                    val localFile = downloadMtpFile(event.handle, event.fileName)
                    if (localFile == null) {
                        Log.e(TAG, ">>> Failed to download new photo ${event.fileName} via MTP")
                        return@collect
                    }
                    localFilePath = localFile.absolutePath
                    sourceUriString = Uri.fromFile(localFile).toString()
                }

                _tetherState.update {
                    it.copy(
                        detectedCount = it.detectedCount + 1,
                        statusText = "📷 ${event.fileName}"
                    )
                }

                val job = MediaJob(
                    id = UUID.randomUUID().toString(),
                    projectId = sessionId,
                    localTempPath = localFilePath,
                    originalFileName = event.fileName,
                    sourceUri = sourceUriString,
                    sourceFingerprint = "${event.handle}|${event.fileName}|${event.fileSize}|${event.modifiedTime}",
                    fileSizeBytes = event.fileSize,
                    lastModifiedEpochMs = event.modifiedTime,
                    createdAtEpochMs = System.currentTimeMillis(),
                    uploadStatus = UploadStatus.QUEUED
                )

                handleIncomingMedia(job)
            }
        }

        viewModelScope.launch {
            ctgWatcher.ctgDiffs.collect { diff ->
                Log.d(TAG, ">>> CTG Diff received: ${diff.summary}, added=${diff.added.size}")

                val sessionId = activeSessionId
                if (sessionId.isNullOrBlank()) {
                    Log.w(TAG, "No active session id, skip new photo enqueue")
                    return@collect
                }

                diff.added.forEach { entry ->
                    if (!entry.isPhoto) return@forEach

                    Log.i(TAG, ">>> NEW CTG PHOTO DETECTED: ${entry.filename}, handle=${entry.handle}, size=${entry.size}")

                    val fingerprint = entry.fingerprint
                    val isUploaded = runCatching {
                        uploadedFingerprintDao.hasUploaded(fingerprint)
                    }.getOrDefault(false)

                    if (isUploaded) {
                        Log.d(TAG, ">>> Skipping already uploaded CTG photo: ${entry.filename}")
                        return@forEach
                    }

                    _tetherState.update {
                        it.copy(
                            detectedCount = it.detectedCount + 1,
                            statusText = "📷 ${entry.filename}"
                        )
                    }

                    val localFile = downloadMtpFile(entry.handle, entry.filename)
                    if (localFile == null) {
                        Log.e(TAG, ">>> Failed to download new photo ${entry.filename} via MTP")
                        return@forEach
                    }

                    val job = MediaJob(
                        id = UUID.randomUUID().toString(),
                        projectId = sessionId,
                        localTempPath = localFile.absolutePath,
                        originalFileName = entry.filename,
                        sourceUri = Uri.fromFile(localFile).toString(),
                        sourceFingerprint = fingerprint,
                        fileSizeBytes = entry.size,
                        lastModifiedEpochMs = entry.modifiedTime,
                        createdAtEpochMs = System.currentTimeMillis(),
                        uploadStatus = UploadStatus.QUEUED
                    )

                    handleIncomingMedia(job)
                }
            }
        }
    }

    private fun startPhotoWatching() {
        val session = _projectSession.value
        if (session?.connectionMode != ConnectionMode.TETHERED) {
            Log.w(TAG, ">>> startPhotoWatching ignored because session is not TETHERED")
            return
        }

        val storageIds = cameraUnlockOrchestrator.getActiveStorageIds()
        if (storageIds.isEmpty()) {
            Log.w(TAG, ">>> startPhotoWatching ignored because storageIds is empty")
            _tetherState.update {
                it.copy(
                    isWatching = false,
                    statusText = "📸 Kamera tersambung, storage belum siap"
                )
            }
            return
        }

        if (currentTreeUri == null) {
            Log.w(TAG, ">>> startPhotoWatching without treeUri; auto-detect tethered mode active")
        }

        if (isPhotoWatcherStarted) {
            Log.d(TAG, ">>> PhotoWatcher already started, skip")
            return
        }

        if (photoWatcherStartJob?.isActive == true) {
            Log.d(TAG, ">>> PhotoWatcher start already in progress, skip")
            return
        }

        photoWatcherStartJob = viewModelScope.launch {
            photoWatcher.resetBaseline()
            ctgWatcher.resetBaseline()
            runCatching {
                val storageId = cameraUnlockOrchestrator.getFirstStorageId()
                if (storageId != 0) {
                    ctgWatcher.createBaselineSnapshot(storageId)
                }
                ctgWatcher.initialize()
                ctgWatcher.startWatching(pollIntervalMs = 2000L)
                Log.d(TAG, ">>> CtgWatcher initialized and watching started")
            }.onFailure {
                Log.e(TAG, ">>> Failed to initialize/start CtgWatcher", it)
            }

            isPhotoWatcherStarted = false

            val sessionId = activeSessionId
            val uploadedNames = if (!sessionId.isNullOrBlank()) {
                runCatching {
                    transferQueueRepository.getBySession(sessionId)
                        .map { it.targetFileName }
                        .toSet()
                }.getOrDefault(emptySet())
            } else {
                emptySet()
            }

            repeat(PHOTO_WATCHER_INIT_RETRY_COUNT) { attempt ->
                val initialized = runCatching {
                    photoWatcher.initialize(uploadedNames)
                }.getOrElse { throwable ->
                    Log.e(TAG, ">>> Failed to initialize PhotoWatcher on attempt=${attempt + 1}", throwable)
                    false
                }

                if (initialized) {
                    val started = runCatching {
                        photoWatcher.startWatching(pollIntervalMs = 2000L)
                    }.isSuccess

                    if (started) {
                        isPhotoWatcherStarted = true
                        _tetherState.update {
                            it.copy(
                                isWatching = true,
                                statusText = if (currentTreeUri != null) {
                                    "📡 Watching foto baru..."
                                } else {
                                    "📡 Watching foto baru... (auto-detect)"
                                }
                            )
                        }
                        Log.d(TAG, ">>> Photo watching started")
                        return@launch
                    } else {
                        Log.w(TAG, ">>> PhotoWatcher startWatching failed on attempt=${attempt + 1}")
                    }
                } else {
                    Log.w(TAG, ">>> PhotoWatcher initialize returned false on attempt=${attempt + 1}")
                }

                if (attempt < PHOTO_WATCHER_INIT_RETRY_COUNT - 1) {
                    delay(PHOTO_WATCHER_INIT_RETRY_DELAY_MS)
                }
            }

            isPhotoWatcherStarted = false
            _tetherState.update {
                it.copy(
                    isWatching = false,
                    statusText = "❌ PhotoWatcher belum siap"
                )
            }
            Log.w(TAG, ">>> PhotoWatcher failed to start after retries")
        }
    }

    private fun stopPhotoWatching() {
        photoWatcherStartJob?.cancel()
        photoWatcherStartJob = null

        runCatching {
            photoWatcher.stopWatching()
        }.onFailure {
            Log.w(TAG, ">>> stopPhotoWatching warning", it)
        }

        runCatching {
            ctgWatcher.stopWatching()
        }.onFailure {
            Log.w(TAG, ">>> stopPhotoWatching warning for ctgWatcher", it)
        }

        isPhotoWatcherStarted = false
        _tetherState.update { it.copy(isWatching = false) }
        Log.d(TAG, ">>> Photo watching stopped")
    }

    fun requestCameraUnlock() {
        val session = _projectSession.value
        if (session?.connectionMode != ConnectionMode.TETHERED) {
            Log.w(TAG, ">>> requestCameraUnlock ignored because active session is not TETHERED")
            return
        }

        Log.d(TAG, ">>> requestCameraUnlock")
        cameraUnlockOrchestrator.requestUnlock()
    }

    fun closeCameraSession() {
        Log.d(TAG, ">>> closeCameraSession")
        stopPhotoWatching()
        viewModelScope.launch {
            cameraUnlockOrchestrator.closeSessionSuspend()
            usbHelper.closeCameraSession()

            _tetherState.update {
                it.copy(
                    isConnected = false,
                    isUnlocked = false,
                    isWatching = false,
                    statusText = "Sesi kamera ditutup."
                )
            }
        }
    }

    private fun observeEngineStates() {
        viewModelScope.launch {
            tetheredEngine.runtimeState.collect { runtime ->
                _shootModeState.update {
                    it.copy(
                        isActive = runtime.isConnected,
                        statusText = runtime.statusText,
                        isBaselineReady = runtime.isConnected && !runtime.isReceiving
                    )
                }
            }
        }

        viewModelScope.launch {
            tetheredEngine.incomingMedia.collect { handleIncomingMedia(it) }
        }

        viewModelScope.launch {
            importEngine.incomingMedia.collect { handleIncomingMedia(it) }
        }
    }

    fun startShootMode(treeUri: Uri? = null) {
        if (treeUri != null) {
            setTreeUri(treeUri)
        }

        val session = _projectSession.value ?: return
        if (session.connectionMode != ConnectionMode.TETHERED) {
            Log.w(TAG, "startShootMode ignored because session is not TETHERED")
            return
        }

        val unlockState = cameraUnlockOrchestrator.unlockState.value
        if (unlockState.status != UnlockStatus.UNLOCK_SUCCESS) {
            Log.w(TAG, "startShootMode ignored because camera is not unlocked yet")
            return
        }

        tetheredEngine.connect(session)
    }

    fun stopShootMode() {
        tetheredEngine.disconnect()
    }

    private fun observeActiveProjectSession() {
        viewModelScope.launch {
            projectSessionManager.activeSession.collect { session ->
                Log.d(TAG, ">>> observeActiveProjectSession: session=$session")
                _projectSession.value = session

                if (session == null) {
                    activeSessionId = null
                    lastHandledSessionSignature = null
                    queueObservationJob?.cancel()
                    clearTreeUri()
                    stopPhotoWatching()
                    tetheredEngine.disconnect()
                    importEngine.disconnect()
                    return@collect
                }

                activeSessionId = session.id

                // Start observing transfer queue items reactively for the active session
                queueObservationJob?.cancel()
                queueObservationJob = viewModelScope.launch {
                    transferQueueRepository.observeBySession(session.id).collect { items ->
                        val pendingCount = items.count { it.status == "QUEUED" }
                        val uploadingCount = items.count { it.status == "UPLOADING" }
                        val successCount = items.count { it.status == "SUCCESS" }
                        val failedCount = items.count { it.status == "FAILED" }

                        _stats.value = SessionStats(
                            pending = pendingCount,
                            uploading = uploadingCount,
                            success = successCount,
                            failed = failedCount
                        )

                        _summaryState.value = _summaryState.value.copy(
                            successCount = successCount,
                            failedCount = failedCount
                        )
                    }
                }

                refreshSummaryForActiveSession()

                val sessionSignature = "${session.id}|${session.connectionMode}|${session.sourceFolderPath}"
                if (lastHandledSessionSignature == sessionSignature) {
                    Log.d(TAG, ">>> Session already handled: $sessionSignature")
                    return@collect
                }

                lastHandledSessionSignature = sessionSignature

                when (session.connectionMode) {
                    ConnectionMode.TETHERED -> {
                        Log.d(TAG, ">>> TETHERED mode - requesting camera unlock")

                        importEngine.disconnect()
                        stopPhotoWatching()

                        _tetherState.update {
                            it.copy(
                                detectedCount = 0,
                                isWatching = false,
                                statusText = "Menyiapkan mode tethered..."
                            )
                        }

                        if (session.sourceFolderPath.isNotBlank()) {
                            runCatching {
                                setTreeUri(Uri.parse(session.sourceFolderPath))
                            }.onFailure {
                                Log.w(TAG, "Invalid tree URI: ${session.sourceFolderPath}")
                                clearTreeUri()
                            }
                        } else {
                            clearTreeUri()
                        }

                        requestCameraUnlock()
                    }

                    ConnectionMode.IMPORT_FALLBACK -> {
                        Log.d(TAG, ">>> IMPORT_FALLBACK mode")

                        stopPhotoWatching()
                        tetheredEngine.disconnect()

                        if (session.sourceFolderPath.isNotBlank()) {
                            runCatching {
                                setTreeUri(Uri.parse(session.sourceFolderPath))
                            }.onFailure {
                                Log.w(TAG, "Invalid tree URI: ${session.sourceFolderPath}")
                                clearTreeUri()
                            }
                        } else {
                            clearTreeUri()
                        }

                        importEngine.connect(session)
                    }
                }
            }
        }
    }

    fun setActiveSession(sessionId: String) {
        activeSessionId = sessionId
        refreshStats()
        refreshSummaryForActiveSession()
    }

    fun startNewSession(
        customerName: String?,
        nasFolderPath: String,
        connectionMode: ConnectionMode,
        sourceTreeUri: String? = null
    ) {
        viewModelScope.launch {
            val resolvedSourceTreeUri = sourceTreeUri.orEmpty()
            val currentSession = _projectSession.value

            if (isEquivalentSessionActive(
                    current = currentSession,
                    nasFolderPath = nasFolderPath,
                    connectionMode = connectionMode,
                    sourceTreeUri = resolvedSourceTreeUri
                )
            ) {
                Log.d(
                    TAG,
                    ">>> startNewSession ignored: equivalent active session already exists " +
                            "(id=${currentSession?.id}, mode=${currentSession?.connectionMode}, target=$nasFolderPath, source=$resolvedSourceTreeUri)"
                )

                activeSessionId = currentSession?.id
                refreshStats()
                refreshSummaryForActiveSession()
                if (connectionMode == ConnectionMode.TETHERED) {
                    Log.d(TAG, ">>> Re-triggering camera unlock for existing TETHERED session")
                    requestCameraUnlock()
                }
                return@launch
            }

            projectSessionManager.deactivateAllSessions()

            val sessionId = UUID.randomUUID().toString()
            val now = System.currentTimeMillis()
            val sessionLabel = "Session $now"

            Log.d(
                TAG,
                ">>> startNewSession: connectionMode=$connectionMode, sourceTreeUri=$resolvedSourceTreeUri"
            )

            sessionRepository.createSession(
                sessionId = sessionId,
                sessionLabel = sessionLabel,
                customerName = customerName,
                sourceTreeUri = resolvedSourceTreeUri,
                targetNasDirPath = nasFolderPath,
                startedAtEpochMs = now,
                connectionMode = connectionMode
            )

            projectSessionManager.createOrUpdateSession(
                sessionId = sessionId,
                sessionLabel = sessionLabel,
                customerName = customerName,
                targetNasPath = nasFolderPath,
                sourceFolderPath = resolvedSourceTreeUri,
                connectionMode = connectionMode
            )

            projectSessionManager.setActive(true)
            activeSessionId = sessionId
            lastHandledSessionSignature = null

            when (connectionMode) {
                ConnectionMode.TETHERED -> {
                    if (resolvedSourceTreeUri.isNotBlank()) {
                        runCatching {
                            setTreeUri(Uri.parse(resolvedSourceTreeUri))
                        }.onFailure {
                            Log.w(TAG, "Invalid source tree URI: $resolvedSourceTreeUri")
                            clearTreeUri()
                        }
                    } else {
                        clearTreeUri()
                    }
                }

                ConnectionMode.IMPORT_FALLBACK -> {
                    if (resolvedSourceTreeUri.isNotBlank()) {
                        runCatching {
                            setTreeUri(Uri.parse(resolvedSourceTreeUri))
                        }.onFailure {
                            Log.w(TAG, "Invalid source tree URI: $resolvedSourceTreeUri")
                            clearTreeUri()
                        }
                    } else {
                        clearTreeUri()
                    }
                }
            }

            refreshStats()
            refreshSummaryForActiveSession()
        }
    }

    fun handleIncomingMedia(job: MediaJob) {
        val sessionId = activeSessionId ?: return
        val targetPath = _summaryState.value.nasFolderPath

        if (targetPath.isBlank()) {
            Log.w(TAG, ">>> handleIncomingMedia: target path blank, skipping")
            return
        }

        viewModelScope.launch {
            runCatching {
                uploadQueueManager.enqueueMediaJob(job, sessionId, targetPath)
            }.onSuccess {
                Log.d(TAG, ">>> MediaJob enqueued: ${job.originalFileName}")
                refreshStats()
                refreshSummaryForActiveSession()
            }.onFailure { e ->
                Log.e(TAG, ">>> Failed to enqueue MediaJob: ${e.message}", e)
            }
        }
    }

    fun refreshStats() {
        val sessionId = activeSessionId ?: return
        viewModelScope.launch {
            _stats.value = SessionStats(
                pending = transferQueueRepository.countBySessionAndStatus(sessionId, "QUEUED"),
                uploading = transferQueueRepository.countBySessionAndStatus(sessionId, "UPLOADING"),
                success = transferQueueRepository.countBySessionAndStatus(sessionId, "SUCCESS"),
                failed = transferQueueRepository.countBySessionAndStatus(sessionId, "FAILED")
            )
        }
    }

    fun refreshSummaryForActiveSession() {
        val sessionId = activeSessionId ?: return
        viewModelScope.launch {
            val session = sessionRepository.getById(sessionId)
            _summaryState.value = SessionSummaryState(
                customerName = session?.customerName,
                nasFolderPath = session?.targetNasDirPath ?: "",
                sourceTreeUri = session?.sourceTreeUri ?: "",
                successCount = transferQueueRepository.countBySessionAndStatus(sessionId, "SUCCESS"),
                failedCount = transferQueueRepository.countBySessionAndStatus(sessionId, "FAILED")
            )
        }
    }

    fun setSourceSelectionMode(mode: SourceSelectionMode) {
        _currentSourceSelectionMode.value = mode
    }

    override fun onCleared() {
        super.onCleared()
        stopPhotoWatching()
        cameraUnlockOrchestrator.shutdown()
        usbHelper.closeCameraSession()
        tetheredEngine.disconnect()
        importEngine.disconnect()
    }
}