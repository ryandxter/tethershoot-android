package com.rvmeur.transferin.feature.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rvmeur.transferin.domain.repository.NasConnection
import com.rvmeur.transferin.domain.repository.NasConnectionRepository
import com.rvmeur.transferin.data.sftp.SftpClient
import kotlinx.coroutines.launch

data class LoginNasUiState(
    val name: String = "",
    val host: String = "", // Client ID
    val port: String = "443",
    val username: String = "", // Refresh Token
    val password: String = "", // Client Secret
    val protocol: String = "GOOGLE_DRIVE",
    val useHttps: Boolean = true,
    val startingFolder: String = "RVM_Photos", // Target Folder Name
    val isTesting: Boolean = false,
    val testResultMessage: String? = null
)

class LoginNasViewModel(
    private val nasRepo: NasConnectionRepository,
    private val sftpClient: SftpClient
) : ViewModel() {

    var uiState by mutableStateOf(LoginNasUiState())
        private set

    init {
        viewModelScope.launch {
            nasRepo.getConnection()?.let { conn ->
                uiState = uiState.copy(
                    name = conn.name,
                    host = conn.host,
                    port = conn.port.toString(),
                    username = conn.username,
                    protocol = "GOOGLE_DRIVE",
                    useHttps = true,
                    startingFolder = conn.startingFolder.orEmpty()
                )
            }
        }
    }

    fun onNameChange(value: String) {
        uiState = uiState.copy(name = value)
    }

    fun onHostChange(value: String) {
        uiState = uiState.copy(host = value)
    }

    fun onPortChange(value: String) {
        uiState = uiState.copy(port = value)
    }

    fun onUsernameChange(value: String) {
        uiState = uiState.copy(username = value)
    }

    fun onPasswordChange(value: String) {
        uiState = uiState.copy(password = value)
    }

    fun onProtocolChange(value: String) {
        // No-op for Google Drive
    }

    fun onUseHttpsChange(value: Boolean) {
        // No-op for Google Drive
    }

    fun onStartingFolderChange(value: String) {
        uiState = uiState.copy(startingFolder = value)
    }

    fun onTestConnectionClick(onSuccess: () -> Unit) {
        val current = uiState
        val portInt = current.port.toIntOrNull() ?: 443

        viewModelScope.launch {
            uiState = current.copy(isTesting = true, testResultMessage = null)

            val result = sftpClient.testConnection(
                host = current.host,
                port = portInt,
                username = current.username,
                password = current.password
            )

            if (result.isSuccess) {
                val folderName = current.startingFolder.ifBlank { "RVM_Photos" }
                val dirsResult = sftpClient.listDirectories(
                    host = current.host,
                    port = portInt,
                    username = current.username,
                    password = current.password,
                    path = "root"
                )

                var folderId = "root"
                if (dirsResult.isSuccess) {
                    val existingFolder = dirsResult.getOrNull()?.find { it.name.equals(folderName, ignoreCase = true) }
                    if (existingFolder != null) {
                        folderId = existingFolder.fullPath
                    } else {
                        val createResult = sftpClient.createDirectory(
                            host = current.host,
                            port = portInt,
                            username = current.username,
                            password = current.password,
                            parentPath = "root",
                            folderName = folderName
                        )
                        if (createResult.isSuccess) {
                            folderId = createResult.getOrThrow()
                        }
                    }
                }

                val connection = NasConnection(
                    name = current.name.ifBlank { "Google Drive" },
                    host = current.host,
                    port = portInt,
                    username = current.username,
                    password = current.password,
                    defaultRemoteFolder = folderId,
                    protocol = "GOOGLE_DRIVE",
                    useHttps = true,
                    startingFolder = folderName
                )
                nasRepo.saveConnection(connection)

                uiState = uiState.copy(
                    isTesting = false,
                    testResultMessage = "Berhasil: Terkoneksi. Target folder: $folderName"
                )
                onSuccess()
            } else {
                val errorMsg = result.exceptionOrNull()?.message ?: "Unknown error"
                uiState = uiState.copy(
                    isTesting = false,
                    testResultMessage = "Gagal: $errorMsg"
                )
            }
        }
    }
}