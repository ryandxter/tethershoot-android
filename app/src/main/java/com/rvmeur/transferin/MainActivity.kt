package com.rvmeur.transferin

import android.app.Application
import android.content.Intent
import android.util.Log
import android.os.Bundle
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.tooling.preview.Preview
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch
import com.rvmeur.transferin.data.local.db.DatabaseProvider
import com.rvmeur.transferin.data.repository.NasConnectionRepositoryImpl
import com.rvmeur.transferin.data.sftp.DelegatingSftpClient
import com.rvmeur.transferin.data.usb.DocumentFileScanner
import com.rvmeur.transferin.data.usb.FileStabilityChecker
import com.rvmeur.transferin.data.usb.UsbFileInfo
import com.rvmeur.transferin.data.usb.UsbFileInfoProvider
import com.rvmeur.transferin.data.usb.UsbHelper
import com.rvmeur.transferin.data.usb.UsbSourceOrchestrator
import com.rvmeur.transferin.domain.model.ConnectionMode
import com.rvmeur.transferin.domain.session.ImportCameraEngine
import com.rvmeur.transferin.domain.session.SourceWatcher
import com.rvmeur.transferin.feature.auth.LoginNasScreen
import com.rvmeur.transferin.feature.connectcamera.ConnectCameraScreen
import com.rvmeur.transferin.feature.connectcamera.ConnectCameraViewModel
import com.rvmeur.transferin.feature.connectcamera.ConnectCameraViewModelFactory
import com.rvmeur.transferin.feature.folder.SelectFolderScreen
import com.rvmeur.transferin.feature.folder.SelectFolderViewModel
import com.rvmeur.transferin.feature.folder.SelectFolderViewModelFactory
import com.rvmeur.transferin.feature.folder.SelectSourceFolderScreen
import com.rvmeur.transferin.feature.gallery.CameraGalleryScreen
import com.rvmeur.transferin.feature.gallery.CameraGalleryViewModel
import com.rvmeur.transferin.feature.gallery.CameraGalleryViewModelFactory
import com.rvmeur.transferin.feature.gallery.GalleryViewModel
import com.rvmeur.transferin.feature.gallery.GalleryViewModelFactory
import com.rvmeur.transferin.feature.gallery.UploadedGalleryScreen
import com.rvmeur.transferin.feature.sessionactive.SessionActiveScreen
import com.rvmeur.transferin.feature.sessionactive.SessionViewModel
import com.rvmeur.transferin.feature.sessionactive.SessionViewModelFactory
import com.rvmeur.transferin.feature.sessionsummary.SessionSummaryScreen
import com.rvmeur.transferin.ui.theme.TransferInTheme

private const val ROUTE_LOGIN_NAS = "loginNas"
private const val ROUTE_SELECT_FOLDER = "selectFolder"
private const val ROUTE_CONNECT_CAMERA = "connectCamera"
private const val ROUTE_SELECT_SOURCE_FOLDER = "selectSourceFolder"
private const val ROUTE_SESSION_ACTIVE = "sessionActive"
private const val ROUTE_SESSION_SUMMARY = "sessionSummary"
private const val ROUTE_GALLERY = "gallery"
private const val ROUTE_CAMERA_GALLERY = "cameraGallery"

private const val TAG_TRANSFER_APP = "TransferInApp"
private const val TAG_SESSION_ROUTE = "SessionActiveRoute"

class MainActivity : ComponentActivity() {

    private lateinit var usbHelper: UsbHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
        }

        usbHelper = UsbHelper(application)
        Log.d(TAG_TRANSFER_APP, "MainActivity.onCreate() - UsbHelper initialized")

        enableEdgeToEdge()
        setContent {
            TransferInTheme {
                TransferInApp(usbHelper = usbHelper)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        Log.d(TAG_TRANSFER_APP, "MainActivity.onNewIntent() called")
    }
}

@Composable
fun TransferInApp(
    usbHelper: UsbHelper? = null
) {
    val isInPreview = LocalInspectionMode.current
    val context = LocalContext.current
    val application = context.applicationContext as? Application

    if (isInPreview || application == null) {
        PreviewFallbackApp()
        return
    }

    val navController = rememberNavController()

    val database = remember(application) {
        DatabaseProvider.getDatabase(application)
    }

    val nasRepository = remember(application) {
        NasConnectionRepositoryImpl(
            dao = database.nasConnectionDao()
        )
    }

    val resolvedUsbHelper = remember(application, usbHelper) {
        usbHelper ?: UsbHelper(application)
    }

    val sourceWatcher = remember {
        SourceWatcher()
    }

    val usbFileInfoProvider = remember(application) {
        object : UsbFileInfoProvider {
            override suspend fun getFileInfo(uri: android.net.Uri): UsbFileInfo? {
                return runCatching {
                    val doc = DocumentFile.fromSingleUri(application, uri)
                        ?: return null

                    val sizeBytes = doc.length()
                    val lastModifiedEpochMs = doc.lastModified()

                    UsbFileInfo(
                        sizeBytes = sizeBytes.coerceAtLeast(0L),
                        lastModifiedEpochMs = lastModifiedEpochMs.coerceAtLeast(0L)
                    )
                }.onFailure { throwable ->
                    Log.e(
                        TAG_TRANSFER_APP,
                        "UsbFileInfoProvider.getFileInfo() failed: uri=$uri, error=${throwable.message}",
                        throwable
                    )
                }.getOrNull()
            }
        }
    }

    val documentFileScanner = remember(application) {
        DocumentFileScanner(application)
    }

    val fileStabilityChecker = remember(usbFileInfoProvider) {
        FileStabilityChecker(
            fileInfoProvider = usbFileInfoProvider
        )
    }

    val usbSourceOrchestrator = remember(
        resolvedUsbHelper,
        documentFileScanner,
        fileStabilityChecker,
        sourceWatcher
    ) {
        UsbSourceOrchestrator(
            usbHelper = resolvedUsbHelper,
            documentFileScanner = documentFileScanner,
            fileStabilityChecker = fileStabilityChecker,
            sourceWatcher = sourceWatcher
        )
    }

    val sessionViewModel: SessionViewModel = viewModel(
        factory = SessionViewModelFactory(
            app = application,
            usbHelper = resolvedUsbHelper,
            usbSourceOrchestrator = usbSourceOrchestrator
        )
    )

    val importCameraEngine = remember(
        application,
        resolvedUsbHelper,
        usbSourceOrchestrator,
        sourceWatcher,
        fileStabilityChecker
    ) {
        ImportCameraEngine(
            appContext = application,
            usbHelper = resolvedUsbHelper,
            usbSourceOrchestrator = usbSourceOrchestrator,
            sourceWatcher = sourceWatcher,
            fileStabilityChecker = fileStabilityChecker
        )
    }

    var selectedTargetNasDirPath by rememberSaveable { mutableStateOf("/") }
    var selectedSourceTreeUri by rememberSaveable { mutableStateOf("") }
    var activeSessionId by rememberSaveable { mutableStateOf<String?>(null) }

    Scaffold(
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = ROUTE_LOGIN_NAS,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(ROUTE_LOGIN_NAS) {
                LoginNasScreen(
                    onLoginSuccess = {
                        Log.d(
                            TAG_TRANSFER_APP,
                            "Login NAS success -> navigate to select folder"
                        )
                        navController.navigate(ROUTE_SELECT_FOLDER) {
                            launchSingleTop = true
                        }
                    }
                )
            }

            composable(ROUTE_SELECT_FOLDER) {
                val selectFolderViewModel: SelectFolderViewModel = viewModel(
                    factory = SelectFolderViewModelFactory(
                        nasConnectionRepository = nasRepository,
                        sftpClient = DelegatingSftpClient(application),
                        sessionDao = database.sessionDao(),
                        usbSourceOrchestrator = usbSourceOrchestrator
                    )
                )

                val state by selectFolderViewModel.uiState.collectAsState()

                LaunchedEffect(state.folderSelected, state.currentPath) {
                    if (state.folderSelected) {
                        selectedTargetNasDirPath = state.currentPath
                        Log.d(
                            TAG_TRANSFER_APP,
                            "Target NAS folder selected: $selectedTargetNasDirPath"
                        )
                        selectFolderViewModel.consumeFolderSelected()
                        navController.navigate(ROUTE_CONNECT_CAMERA) {
                            launchSingleTop = true
                        }
                    }
                }

                SelectFolderScreen(
                    state = state,
                    onOpenFolder = selectFolderViewModel::openFolder,
                    onSelectCurrentFolder = selectFolderViewModel::selectCurrentFolder,
                    onCreateFolder = selectFolderViewModel::createFolder,
                    onGoUp = selectFolderViewModel::goUp,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(ROUTE_CONNECT_CAMERA) {
                val connectCameraViewModel: ConnectCameraViewModel = viewModel(
                    factory = ConnectCameraViewModelFactory(
                        context = application,
                        usbHelper = resolvedUsbHelper,
                        sessionDao = database.sessionDao(),
                        cameraMediaDao = database.cameraMediaDao()
                    )
                )

                val connectCameraState by connectCameraViewModel.uiState.collectAsState()

                LaunchedEffect(Unit) {
                    val connection = nasRepository.getConnection()

                    if (selectedTargetNasDirPath.isBlank() || selectedTargetNasDirPath == "/") {
                        selectedTargetNasDirPath = connection?.defaultRemoteFolder ?: "/"
                    }

                    Log.d(
                        TAG_TRANSFER_APP,
                        "ConnectCameraScreen loaded, targetNasDirPath=$selectedTargetNasDirPath, sourceTreeUri=$selectedSourceTreeUri"
                    )

                    connectCameraViewModel.selectTetheredMode()
                    connectCameraViewModel.refreshUsbState()
                }

                ConnectCameraScreen(
                    selectedNasFolder = selectedTargetNasDirPath,
                    selectedSourceFolder = selectedSourceTreeUri,
                    viewModel = connectCameraViewModel,
                    onSelectSourceFolder = {
                        Log.d(TAG_TRANSFER_APP, "Navigate to source folder picker")
                        navController.navigate(ROUTE_SELECT_SOURCE_FOLDER) {
                            launchSingleTop = true
                        }
                    },
                    onStartSession = {
                        val canStartSession =
                            selectedTargetNasDirPath.isNotBlank() &&
                                    selectedTargetNasDirPath != "/" &&
                                    selectedSourceTreeUri.isNotBlank() &&
                                    connectCameraState.cameraSession.isOpen

                        if (!canStartSession) {
                            Log.d(
                                TAG_TRANSFER_APP,
                                "Start import session blocked: targetNasDirPath=$selectedTargetNasDirPath, sourceTreeUri=$selectedSourceTreeUri, sessionOpen=${connectCameraState.cameraSession.isOpen}"
                            )
                            return@ConnectCameraScreen
                        }

                        sessionViewModel.setSourceSelectionMode(connectCameraState.sourceSelectionMode)

                        sessionViewModel.startNewSession(
                            customerName = null,
                            nasFolderPath = selectedTargetNasDirPath,
                            connectionMode = ConnectionMode.IMPORT_FALLBACK,
                            sourceTreeUri = selectedSourceTreeUri
                        )

                        Log.d(
                            TAG_TRANSFER_APP,
                            "Start import session -> navigate to session active, targetNasDirPath=$selectedTargetNasDirPath, sourceTreeUri=$selectedSourceTreeUri"
                        )

                        navController.navigate(ROUTE_SESSION_ACTIVE) {
                            launchSingleTop = true
                        }
                    },
                    onStartShootMode = { selectedUri ->
                        Log.d(TAG_TRANSFER_APP, "Starting TETHERED mode, URI: $selectedUri")

                        if (selectedTargetNasDirPath.isBlank() || selectedTargetNasDirPath == "/") {
                            Log.w(
                                TAG_TRANSFER_APP,
                                "Cannot start tethered mode: invalid target NAS folder=$selectedTargetNasDirPath"
                            )
                            return@ConnectCameraScreen
                        }

                        if (selectedUri != null) {
                            try {
                                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                                application.contentResolver.takePersistableUriPermission(selectedUri, takeFlags)
                            } catch (_: SecurityException) {
                                Log.w(TAG_TRANSFER_APP, "Could not get persistable permission")
                            }
                            selectedSourceTreeUri = selectedUri.toString()
                        } else {
                            selectedSourceTreeUri = ""
                        }

                        sessionViewModel.setSourceSelectionMode(connectCameraState.sourceSelectionMode)

                        Log.d(
                            TAG_TRANSFER_APP,
                            ">>> Creating session with TETHERED mode, sourceTreeUri=$selectedSourceTreeUri"
                        )

                        sessionViewModel.startNewSession(
                            customerName = null,
                            nasFolderPath = selectedTargetNasDirPath,
                            connectionMode = ConnectionMode.TETHERED,
                            sourceTreeUri = selectedSourceTreeUri
                        )

                        Log.d(
                            TAG_TRANSFER_APP,
                            ">>> TETHERED session created, navigating to ROUTE_SESSION_ACTIVE"
                        )

                        navController.navigate(ROUTE_SESSION_ACTIVE) {
                            launchSingleTop = true
                        }
                    },
                    onBack = { navController.popBackStack() }
                )
            }

            composable(ROUTE_SELECT_SOURCE_FOLDER) {
                SelectSourceFolderScreen(
                    currentSourceTreeUri = selectedSourceTreeUri,
                    onSourceFolderSelected = { uri ->
                        selectedSourceTreeUri = uri
                        Log.d(
                            TAG_TRANSFER_APP,
                            "Source folder selected: $selectedSourceTreeUri"
                        )
                        navController.popBackStack()
                    },
                    onBack = { navController.popBackStack() }
                )
            }

            composable(ROUTE_SESSION_ACTIVE) {
                val stats by sessionViewModel.stats.collectAsState()
                val projectSessionState by sessionViewModel.projectSession.collectAsState()
                val tetherState by sessionViewModel.tetherState.collectAsState()
                val shootModeState by sessionViewModel.shootModeState.collectAsState()
                val importEngineState by importCameraEngine.runtimeState.collectAsState()

                val activeCameraState = when (projectSessionState?.connectionMode) {
                    ConnectionMode.TETHERED -> com.rvmeur.transferin.domain.model.CameraRuntimeState(
                        isConnected = tetherState.isConnected,
                        canShoot = tetherState.isUnlocked,
                        isReceiving = tetherState.detectedCount > 0,
                        isUnlocked = tetherState.isUnlocked,
                        activeMode = ConnectionMode.TETHERED,
                        statusText = tetherState.statusText
                    )
                    else -> importEngineState
                }

                val isTetheredActive =
                    projectSessionState?.connectionMode == ConnectionMode.TETHERED &&
                            tetherState.isConnected

                LaunchedEffect(projectSessionState?.id) {
                    val session = projectSessionState ?: return@LaunchedEffect

                    if (activeSessionId != session.id) {
                        activeSessionId = session.id
                        sessionViewModel.setActiveSession(session.id)
                    }

                    importCameraEngine.setSourceSelectionMode(
                        sessionViewModel.currentSourceSelectionMode.value
                    )

                    Log.d(
                        TAG_SESSION_ROUTE,
                        "Connecting engine with sessionId=${session.id}, connectionMode=${session.connectionMode}, sourceFolderPath=${session.sourceFolderPath}"
                    )

                    when (session.connectionMode) {
                        ConnectionMode.TETHERED -> {
                            Log.d(TAG_SESSION_ROUTE, "TETHERED mode - engine will auto-connect")
                        }
                        ConnectionMode.IMPORT_FALLBACK -> {
                            Log.d(TAG_SESSION_ROUTE, "IMPORT_FALLBACK mode - engine will auto-connect")
                        }
                    }
                }

                LaunchedEffect(Unit) {
                    Log.d(TAG_SESSION_ROUTE, "incomingMedia collectors started")

                    launch {
                        sessionViewModel.tetheredEngineInstance.incomingMedia.collect { job ->
                            Log.d(
                                TAG_SESSION_ROUTE,
                                "TETHERED incomingMedia: ${job.originalFileName}"
                            )
                            sessionViewModel.handleIncomingMedia(job)
                        }
                    }

                    launch {
                        importCameraEngine.incomingMedia.collect { job ->
                            Log.d(
                                TAG_SESSION_ROUTE,
                                "IMPORT incomingMedia: ${job.originalFileName}"
                            )
                            sessionViewModel.handleIncomingMedia(job)
                        }
                    }
                }

                SessionActiveScreen(
                    cameraState = activeCameraState,
                    pendingCount = stats.pending,
                    uploadingCount = stats.uploading,
                    successCount = stats.success,
                    failedCount = stats.failed,
                    onStartImport = {
                        Log.d(TAG_SESSION_ROUTE, "onStartImport clicked")
                        importCameraEngine.openMtpSessionIfNeeded()
                        importCameraEngine.startLiveSession()
                        sessionViewModel.refreshStats()
                        Log.d(TAG_SESSION_ROUTE, "Live import session start requested")
                    },
                    onStartLiveHybrid = {
                        Log.d(TAG_SESSION_ROUTE, "onStartLiveHybrid clicked")
                        importCameraEngine.openMtpSessionIfNeeded()
                        importCameraEngine.startLiveSessionHybrid()
                        sessionViewModel.refreshStats()
                        Log.d(TAG_SESSION_ROUTE, "Hybrid live session start requested")
                    },
                    onStartShootMode = {
                        Log.d(TAG_SESSION_ROUTE, "TETHERED mode already active")
                    },
                    onStopShootMode = {
                        Log.d(TAG_SESSION_ROUTE, "onStopShootMode clicked")
                        sessionViewModel.closeCameraSession()
                        sessionViewModel.refreshStats()
                        Log.d(TAG_SESSION_ROUTE, "Tether mode stopped")
                    },
                    onFinish = {
                        Log.d(TAG_SESSION_ROUTE, "onFinish clicked")
                        if (isTetheredActive) {
                            sessionViewModel.closeCameraSession()
                        }
                        sessionViewModel.refreshSummaryForActiveSession()
                        navController.navigate(ROUTE_SESSION_SUMMARY) {
                            launchSingleTop = true
                        }
                    },
                    onOpenGallery = {
                        Log.d(TAG_SESSION_ROUTE, "onOpenGallery clicked")
                        navController.navigate(ROUTE_GALLERY) {
                            launchSingleTop = true
                        }
                    },
                    onBack = {
                        Log.d(TAG_SESSION_ROUTE, "onBack clicked")
                        if (isTetheredActive) {
                            sessionViewModel.closeCameraSession()
                        }
                        navController.popBackStack()
                    },
                    shootModeState = shootModeState,
                    tetherState = tetherState
                )
            }

            composable(ROUTE_GALLERY) {
                val transferQueueRepository = remember(application) {
                    com.rvmeur.transferin.data.repository.TransferQueueRepositoryImpl(
                        dao = database.transferQueueDao()
                    )
                }

                val galleryViewModel: GalleryViewModel = viewModel(
                    factory = GalleryViewModelFactory(
                        application = application,
                        transferQueueRepository = transferQueueRepository
                    )
                )

                UploadedGalleryScreen(
                    viewModel = galleryViewModel,
                    onNavigateBack = {
                        Log.d(TAG_TRANSFER_APP, "Gallery back clicked")
                        navController.popBackStack()
                    }
                )
            }

            composable(ROUTE_CAMERA_GALLERY) {
                val cameraGalleryViewModel: CameraGalleryViewModel = viewModel(
                    factory = CameraGalleryViewModelFactory(
                        application = application,
                        cameraMediaDao = database.cameraMediaDao(),
                        usbHelper = resolvedUsbHelper,
                        deviceId = activeSessionId ?: "current_device"
                    )
                )

                val state by cameraGalleryViewModel.uiState.collectAsState()

                CameraGalleryScreen(
                    mediaItems = state.items,
                    isLoading = state.isLoading,
                    isShootModeActive = state.isShootModeActive,
                    baselineCount = state.baselineCount,
                    newCount = state.newCount,
                    uploadedCount = state.uploadedCount,
                    onItemClick = { item ->
                        Log.d(TAG_TRANSFER_APP, "Camera gallery item clicked: ${item.fileName}")
                    },
                    onBack = {
                        Log.d(TAG_TRANSFER_APP, "Camera gallery back clicked")
                        navController.popBackStack()
                    }
                )
            }

            composable(ROUTE_SESSION_SUMMARY) {
                val summary by sessionViewModel.summaryState.collectAsState()
                val stats by sessionViewModel.stats.collectAsState()

                val totalFiles = stats.success + stats.failed
                val startTimeText: String? = null
                val endTimeText: String? = null

                SessionSummaryScreen(
                    customerName = summary.customerName,
                    nasFolderPath = summary.nasFolderPath,
                    totalFiles = totalFiles,
                    totalSuccess = summary.successCount,
                    totalFailed = summary.failedCount,
                    startTimeText = startTimeText,
                    endTimeText = endTimeText,
                    onDone = {
                        Log.d(
                            TAG_TRANSFER_APP,
                            "Session summary done -> reset sourceTreeUri"
                        )
                        selectedSourceTreeUri = ""
                        activeSessionId = null
                        navController.navigate(ROUTE_SELECT_FOLDER) {
                            popUpTo(ROUTE_SELECT_FOLDER) { inclusive = false }
                            launchSingleTop = true
                        }
                    },
                    onBack = {
                        Log.d(TAG_TRANSFER_APP, "Session summary back clicked")
                        navController.popBackStack()
                    }
                )
            }
        }
    }
}

@Composable
private fun PreviewFallbackApp() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Preview TransferInApp",
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Preview(showBackground = true)
@Composable
fun TransferInPreview() {
    TransferInTheme {
        TransferInApp()
    }
}