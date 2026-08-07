package com.reflection.transferin.feature.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reflection.transferin.domain.repository.NasConnection
import com.reflection.transferin.domain.repository.NasConnectionRepository
import com.reflection.transferin.data.sftp.SftpClient
import kotlinx.coroutines.launch

data class LoginNasUiState(
    val name: String = "",
    val host: String = "",
    val port: String = "22",
    val username: String = "",
    val password: String = "",
    val protocol: String = "SFTP",
    val useHttps: Boolean = false,
    val startingFolder: String = "",
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
        // Prefill dari koneksi terakhir jika ada
        viewModelScope.launch {
            nasRepo.getConnection()?.let { conn ->
                uiState = uiState.copy(
                    name = conn.name,
                    host = conn.host,
                    port = conn.port.toString(),
                    username = conn.username,
                    protocol = conn.protocol,
                    useHttps = conn.useHttps,
                    startingFolder = conn.startingFolder.orEmpty()
                    // password sengaja tidak diprefill demi keamanan
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
        val defaultPort = if (value == "WEBDAV") {
            if (uiState.useHttps) "5006" else "5005"
        } else {
            "22"
        }
        uiState = uiState.copy(protocol = value, port = defaultPort)
    }

    fun onUseHttpsChange(value: Boolean) {
        val defaultPort = if (uiState.protocol == "WEBDAV") {
            if (value) "5006" else "5005"
        } else {
            "22"
        }
        uiState = uiState.copy(useHttps = value, port = defaultPort)
    }

    fun onStartingFolderChange(value: String) {
        uiState = uiState.copy(startingFolder = value)
    }

    fun onTestConnectionClick(onSuccess: () -> Unit) {
        val current = uiState
        val portInt = current.port.toIntOrNull() ?: 22

        viewModelScope.launch {
            uiState = current.copy(isTesting = true, testResultMessage = null)

            // Simpan ke DB dulu supaya DelegatingSftpClient bisa membaca protokol dan opsi HTTPS/Starting folder yang benar
            val connection = NasConnection(
                name = current.name.ifBlank { "Default NAS" },
                host = current.host,
                port = portInt,
                username = current.username,
                password = current.password,
                defaultRemoteFolder = null,
                protocol = current.protocol,
                useHttps = current.useHttps,
                startingFolder = current.startingFolder.takeIf { it.isNotBlank() }
            )
            nasRepo.saveConnection(connection)

            val result = sftpClient.testConnection(
                host = current.host,
                port = portInt,
                username = current.username,
                password = current.password
            )

            if (result.isSuccess) {
                uiState = uiState.copy(
                    isTesting = false,
                    testResultMessage = "Berhasil test koneksi"
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