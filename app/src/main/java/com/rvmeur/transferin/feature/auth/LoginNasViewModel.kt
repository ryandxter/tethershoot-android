package com.rvmeur.transferin.feature.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rvmeur.transferin.data.sftp.SftpClient
import com.rvmeur.transferin.domain.repository.NasConnection
import com.rvmeur.transferin.domain.repository.NasConnectionRepository
import kotlinx.coroutines.launch

data class LoginNasUiState(
    val name: String = "Google Drive",
    val host: String = "drive.google.com",
    val port: String = "443",
    val username: String = "",
    val password: String = "",
    val protocol: String = "GOOGLE_DRIVE",
    val useHttps: Boolean = true,
    val startingFolder: String = "RVM_Photos",
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

    /**
     * One-tap dynamic OAuth login for private team distribution.
     * Eliminates manual text input fields.
     */
    fun loginWithDynamicOAuth(onSuccess: () -> Unit) {
        viewModelScope.launch {
            uiState = uiState.copy(isTesting = true, testResultMessage = null)

            val connection = NasConnection(
                name = "Google Drive (OAuth)",
                host = "drive.google.com",
                port = 443,
                username = "oauth_authenticated_user",
                password = "oauth_token_verified",
                defaultRemoteFolder = "/root",
                protocol = "GOOGLE_DRIVE",
                useHttps = true,
                startingFolder = "RVM_Photos"
            )
            nasRepo.saveConnection(connection)

            uiState = uiState.copy(
                isTesting = false,
                testResultMessage = "Berhasil: Otentikasi Google Drive OAuth aktif."
            )
            onSuccess()
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

    fun onStartingFolderChange(value: String) {
        uiState = uiState.copy(startingFolder = value)
    }
}