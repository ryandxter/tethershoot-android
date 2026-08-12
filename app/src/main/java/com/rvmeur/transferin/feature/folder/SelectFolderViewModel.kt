package com.rvmeur.transferin.feature.folder

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rvmeur.transferin.data.sftp.SftpClient
import com.rvmeur.transferin.data.usb.UsbSourceOrchestrator
import com.rvmeur.transferin.domain.model.ConnectionMode
import com.rvmeur.transferin.domain.repository.NasConnectionRepository
import com.rvmeur.transferin.domain.session.ProjectSessionManager
import com.rvmeur.transferin.domain.session.RemoteFolder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SelectFolderViewModel(
    private val nasConnectionRepository: NasConnectionRepository,
    private val sftpClient: SftpClient,
    private val projectSessionManager: ProjectSessionManager,
    private val usbSourceOrchestrator: UsbSourceOrchestrator
) : ViewModel() {

    private val _uiState = MutableStateFlow(FolderUiState(isLoading = true))
    val uiState: StateFlow<FolderUiState> = _uiState.asStateFlow()

    init {
        loadInitialFolder()
    }

    fun loadInitialFolder() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    errorMessage = null,
                    folderSelected = false
                )
            }

            val connection = nasConnectionRepository.getConnection()
            if (connection == null) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Koneksi Google Drive belum tersedia."
                    )
                }
                return@launch
            }

            val initialPath = connection.defaultRemoteFolder?.takeIf { it.isNotBlank() }
                ?: sftpClient.getInitialPath(
                    host = connection.host,
                    port = connection.port,
                    username = connection.username,
                    password = connection.password
                ).getOrElse { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = error.message ?: "Gagal mengambil path awal."
                        )
                    }
                    return@launch
                }

            loadFolders(initialPath)
        }
    }

    fun resetFoldersState() {
        _uiState.update {
            FolderUiState(
                isLoading = false,
                currentPath = "",
                folders = emptyList(),
                folderSelected = false
            )
        }
    }

    fun openFolder(folder: RemoteFolder) {
        loadFolders(folder.fullPath)
    }

    fun goUp() {
        val currentPath = _uiState.value.currentPath
        if (currentPath.isBlank() || currentPath == "/") return

        val parentPath = buildParentPath(currentPath)
        loadFolders(parentPath)
    }

    fun createFolder(folderName: String) {
        viewModelScope.launch {
            val connection = nasConnectionRepository.getConnection()
            if (connection == null) {
                _uiState.update {
                    it.copy(errorMessage = "Koneksi Google Drive tidak ditemukan.")
                }
                return@launch
            }

            val safeFolderName = folderName.trim()
            if (safeFolderName.isBlank()) {
                _uiState.update {
                    it.copy(errorMessage = "Nama folder tidak boleh kosong.")
                }
                return@launch
            }

            _uiState.update {
                it.copy(
                    isLoading = true,
                    errorMessage = null,
                    folderSelected = false
                )
            }

            sftpClient.createDirectory(
                host = connection.host,
                port = connection.port,
                username = connection.username,
                password = connection.password,
                parentPath = _uiState.value.currentPath,
                folderName = safeFolderName
            ).onSuccess {
                loadFolders(_uiState.value.currentPath)
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = error.message ?: "Gagal membuat folder."
                    )
                }
            }
        }
    }

    fun selectCurrentFolder() {
        viewModelScope.launch {
            val currentPath = _uiState.value.currentPath
            if (currentPath.isBlank()) {
                _uiState.update {
                    it.copy(errorMessage = "Folder tujuan Google Drive belum dipilih.")
                }
                return@launch
            }

            if (!_uiState.value.isWritable) {
                _uiState.update {
                    it.copy(errorMessage = "Tidak dapat memilih folder ini karena Anda tidak memiliki hak akses tulis.")
                }
                return@launch
            }

            val connection = nasConnectionRepository.getConnection()
            if (connection == null) {
                _uiState.update {
                    it.copy(errorMessage = "Koneksi Google Drive tidak ditemukan.")
                }
                return@launch
            }

            nasConnectionRepository.updateDefaultRemoteFolder(currentPath)

            val sessionId = "${connection.host}:$currentPath"

            projectSessionManager.createOrUpdateSession(
                sessionId = sessionId,
                sessionLabel = "Session ${connection.host}",
                customerName = null,
                targetNasPath = currentPath,
                sourceFolderPath = _uiState.value.selectedSourceTreeUri.orEmpty(),
                connectionMode = ConnectionMode.IMPORT_FALLBACK
            )

            _uiState.update {
                it.copy(
                    folderSelected = true,
                    errorMessage = null
                )
            }
        }
    }

    fun consumeFolderSelected() {
        _uiState.update {
            it.copy(folderSelected = false)
        }
    }

    fun setSelectedSourceTree(treeUri: Uri) {
        _uiState.update {
            it.copy(
                selectedSourceTreeUri = treeUri.toString(),
                errorMessage = null
            )
        }
    }

    fun loadCameraFiles(treeUri: Uri) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoadingCameraFiles = true,
                    errorMessage = null,
                    selectedSourceTreeUri = treeUri.toString()
                )
            }

            try {
                val files = usbSourceOrchestrator.listCameraFiles(treeUri)

                _uiState.update { state ->
                    val validSelectedIds = state.selectedCameraFileIds.intersect(
                        files.map { file -> file.id }.toSet()
                    )

                    state.copy(
                        isLoadingCameraFiles = false,
                        cameraFiles = files,
                        selectedCameraFileIds = validSelectedIds,
                        errorMessage = null
                    )
                }
            } catch (throwable: Throwable) {
                _uiState.update {
                    it.copy(
                        isLoadingCameraFiles = false,
                        errorMessage = throwable.message ?: "Gagal memuat file kamera."
                    )
                }
            }
        }
    }

    fun scanCameraFilesOnce() {
        val sourceTreeUri = _uiState.value.selectedSourceTreeUri
        if (sourceTreeUri.isNullOrBlank()) {
            _uiState.update {
                it.copy(errorMessage = "Source folder kamera belum dipilih.")
            }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isScanningCameraFiles = true,
                    errorMessage = null
                )
            }

            try {
                val treeUri = Uri.parse(sourceTreeUri)
                // Tidak ada scanOnce di UsbSourceOrchestrator, gunakan listCameraFiles
                val files = usbSourceOrchestrator.listCameraFiles(treeUri)

                _uiState.update { state ->
                    val validSelectedIds = state.selectedCameraFileIds.intersect(
                        files.map { file -> file.id }.toSet()
                    )

                    state.copy(
                        isScanningCameraFiles = false,
                        cameraFiles = files,
                        selectedCameraFileIds = validSelectedIds,
                        errorMessage = null
                    )
                }
            } catch (throwable: Throwable) {
                _uiState.update {
                    it.copy(
                        isScanningCameraFiles = false,
                        errorMessage = throwable.message ?: "Gagal scan file kamera."
                    )
                }
            }
        }
    }

    fun reloadCameraFiles() {
        val sourceTreeUri = _uiState.value.selectedSourceTreeUri
        if (sourceTreeUri.isNullOrBlank()) {
            _uiState.update {
                it.copy(errorMessage = "Source folder kamera belum dipilih.")
            }
            return
        }

        loadCameraFiles(Uri.parse(sourceTreeUri))
    }

    fun toggleCameraFileSelection(fileId: String) {
        _uiState.update { state ->
            val next = state.selectedCameraFileIds.toMutableSet()
            if (!next.add(fileId)) {
                next.remove(fileId)
            }

            state.copy(selectedCameraFileIds = next)
        }
    }

    fun clearCameraFileSelection() {
        _uiState.update {
            it.copy(selectedCameraFileIds = emptySet())
        }
    }

    fun startTransferSelectedFiles() {
        viewModelScope.launch {
            val state = _uiState.value

            if (state.isTransferringFiles) {
                return@launch
            }

            if (state.selectedSourceTreeUri.isNullOrBlank()) {
                _uiState.update {
                    it.copy(errorMessage = "Source folder kamera belum dipilih.")
                }
                return@launch
            }

            if (state.currentPath.isBlank()) {
                _uiState.update {
                    it.copy(errorMessage = "Folder tujuan Google Drive belum dipilih.")
                }
                return@launch
            }

            val selectedFiles = getSelectedCameraFiles()
            if (selectedFiles.isEmpty()) {
                _uiState.update {
                    it.copy(errorMessage = "Belum ada file kamera yang dipilih.")
                }
                return@launch
            }

            val connection = nasConnectionRepository.getConnection()
            if (connection == null) {
                _uiState.update {
                    it.copy(errorMessage = "Koneksi Google Drive tidak ditemukan.")
                }
                return@launch
            }

            _uiState.update {
                it.copy(
                    isTransferringFiles = true,
                    currentTransferFileName = null,
                    transferredFilesCount = 0,
                    failedFilesCount = 0,
                    totalFilesToTransfer = selectedFiles.size,
                    transferProgressBytes = 0L,
                    transferTotalBytes = 0L,
                    errorMessage = null
                )
            }

            val failedFiles = mutableListOf<String>()
            val targetPath = state.currentPath
            var transferredCount = 0
            var failedCount = 0

            for (file in selectedFiles) {
                val fileUri = runCatching { Uri.parse(file.uri) }.getOrNull()
                if (fileUri == null) {
                    failedFiles += file.displayName
                    failedCount += 1
                    _uiState.update {
                        it.copy(
                            failedFilesCount = failedCount
                        )
                    }
                    continue
                }

                if (!file.isStable) {
                    failedFiles += file.displayName
                    failedCount += 1
                    _uiState.update {
                        it.copy(
                            failedFilesCount = failedCount
                        )
                    }
                    continue
                }

                _uiState.update {
                    it.copy(
                        currentTransferFileName = file.displayName,
                        transferProgressBytes = 0L,
                        transferTotalBytes = file.sizeBytes.coerceAtLeast(0L)
                    )
                }

                val uploadResult = sftpClient.uploadFileFromUri(
                    host = connection.host,
                    port = connection.port,
                    username = connection.username,
                    password = connection.password,
                    localUri = fileUri,
                    remoteDirectory = targetPath,
                    remoteFileName = file.displayName,
                    onProgress = { bytesTransferred, totalBytes ->
                        _uiState.update { currentState ->
                            currentState.copy(
                                currentTransferFileName = file.displayName,
                                transferProgressBytes = bytesTransferred.coerceAtLeast(0L),
                                transferTotalBytes = totalBytes.takeIf { it > 0L }
                                    ?: file.sizeBytes.coerceAtLeast(0L)
                            )
                        }
                    }
                )

                if (uploadResult.isSuccess) {
                    transferredCount += 1
                    _uiState.update {
                        it.copy(
                            transferredFilesCount = transferredCount
                        )
                    }
                } else {
                    failedFiles += file.displayName
                    failedCount += 1
                    _uiState.update {
                        it.copy(
                            failedFilesCount = failedCount
                        )
                    }
                }
            }

            val allSucceeded = failedFiles.isEmpty()

            _uiState.update {
                it.copy(
                    isTransferringFiles = false,
                    currentTransferFileName = null,
                    transferProgressBytes = 0L,
                    transferTotalBytes = 0L,
                    selectedCameraFileIds = if (allSucceeded) emptySet() else it.selectedCameraFileIds,
                    errorMessage = if (allSucceeded) null else buildTransferFailureMessage(failedFiles)
                )
            }
        }
    }

    fun clearErrorMessage() {
        _uiState.update {
            it.copy(errorMessage = null)
        }
    }

    private fun loadFolders(path: String) {
        viewModelScope.launch {
            val connection = nasConnectionRepository.getConnection()
            if (connection == null) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Koneksi Google Drive tidak ditemukan."
                    )
                }
                return@launch
            }

            _uiState.update {
                it.copy(
                    isLoading = true,
                    errorMessage = null,
                    folderSelected = false
                )
            }

            sftpClient.listDirectories(
                host = connection.host,
                port = connection.port,
                username = connection.username,
                password = connection.password,
                path = path
            ).onSuccess { folders ->
                val isWritable = sftpClient.checkWritePermission(
                    host = connection.host,
                    port = connection.port,
                    username = connection.username,
                    password = connection.password,
                    directoryPath = path
                ).getOrDefault(true)

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        currentPath = path,
                        folders = folders,
                        isWritable = isWritable,
                        errorMessage = if (!isWritable) "Peringatan: Folder ini bersifat Read-Only (tidak bisa menulis)." else null
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = error.message ?: "Gagal memuat folder."
                    )
                }
            }
        }
    }

    private fun getSelectedCameraFiles(): List<UsbSourceOrchestrator.CameraPickItem> {
        val state = _uiState.value
        if (state.cameraFiles.isEmpty() || state.selectedCameraFileIds.isEmpty()) {
            return emptyList()
        }

        return state.cameraFiles.filter { it.id in state.selectedCameraFileIds }
    }

    private fun buildTransferFailureMessage(failedFiles: List<String>): String {
        if (failedFiles.isEmpty()) return "Transfer gagal."

        return if (failedFiles.size == 1) {
            "Gagal transfer file: ${failedFiles.first()}"
        } else {
            "Gagal transfer ${failedFiles.size} file: ${failedFiles.joinToString(limit = 3)}"
        }
    }

    private fun buildParentPath(path: String): String {
        if (path == "/") return "/"

        val normalized = path.trimEnd('/')
        val lastSlashIndex = normalized.lastIndexOf('/')

        return when {
            lastSlashIndex <= 0 -> "/"
            else -> normalized.substring(0, lastSlashIndex)
        }
    }
}