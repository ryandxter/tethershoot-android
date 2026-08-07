package com.reflection.transferin.feature.auth

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.reflection.transferin.ui.theme.AccentGold
import com.reflection.transferin.ui.theme.CardSurface
import com.reflection.transferin.ui.theme.ErrorRedSoft

@Composable
fun LoginNasScreen(
    onLoginSuccess: () -> Unit,
    modifier: Modifier = Modifier
) {
    val application = LocalContext.current.applicationContext as Application

    val viewModel: LoginNasViewModel = viewModel(
        factory = LoginNasViewModelFactory(application)
    )

    val state = viewModel.uiState

    val canTest =
        state.name.isNotBlank() &&
                state.host.isNotBlank() &&
                state.port.isNotBlank() &&
                state.username.isNotBlank() &&
                state.password.isNotBlank()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(
                containerColor = CardSurface
            ),
            elevation = CardDefaults.cardElevation(
                defaultElevation = 4.dp
            )
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 20.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Login NAS Studio",
                    style = MaterialTheme.typography.titleMedium
                )

                Text(
                    text = "Masukkan koneksi SFTP ke Synology (host, port, username, password).",
                    style = MaterialTheme.typography.bodySmall
                )

                OutlinedTextField(
                    value = state.name,
                    onValueChange = viewModel::onNameChange,
                    label = { Text("Nama koneksi") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = state.host,
                    onValueChange = viewModel::onHostChange,
                    label = { Text("Host / IP NAS") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = state.port,
                    onValueChange = viewModel::onPortChange,
                    label = { Text("Port SFTP") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = state.username,
                    onValueChange = viewModel::onUsernameChange,
                    label = { Text("Username NAS") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = state.password,
                    onValueChange = viewModel::onPasswordChange,
                    label = { Text("Password NAS") },
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Password
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "Protokol Koneksi",
                    style = MaterialTheme.typography.bodyMedium
                )
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    androidx.compose.foundation.layout.Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        androidx.compose.material3.RadioButton(
                            selected = state.protocol == "SFTP",
                            onClick = { viewModel.onProtocolChange("SFTP") }
                        )
                        Text("SFTP", style = MaterialTheme.typography.bodyMedium)
                    }

                    androidx.compose.foundation.layout.Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        androidx.compose.material3.RadioButton(
                            selected = state.protocol == "WEBDAV",
                            onClick = { viewModel.onProtocolChange("WEBDAV") }
                        )
                        Text("WebDAV", style = MaterialTheme.typography.bodyMedium)
                    }
                }

                if (state.protocol == "WEBDAV") {
                    androidx.compose.foundation.layout.Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        androidx.compose.material3.Checkbox(
                            checked = state.useHttps,
                            onCheckedChange = viewModel::onUseHttpsChange
                        )
                        Text("Gunakan HTTPS (Secure Connection)", style = MaterialTheme.typography.bodyMedium)
                    }
                }

                OutlinedTextField(
                    value = state.startingFolder,
                    onValueChange = viewModel::onStartingFolderChange,
                    label = { Text("Folder Awal / Starting Folder (Opsional)") },
                    placeholder = { Text("Contoh: /volume1/photo atau /photo") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                val textToShow = state.testResultMessage
                if (!textToShow.isNullOrEmpty()) {
                    val isSuccess = textToShow.startsWith("Berhasil")
                    Text(
                        text = textToShow,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isSuccess) AccentGold else ErrorRedSoft
                    )
                }

                Button(
                    onClick = {
                        viewModel.onTestConnectionClick(onLoginSuccess)
                    },
                    enabled = canTest && !state.isTesting,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    Text(
                        if (state.isTesting) {
                            "Menguji koneksi..."
                        } else {
                            "Test Connection & Lanjut"
                        }
                    )
                }

                TextButton(
                    onClick = { /* nanti bisa buka bantuan / FAQ */ },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Butuh bantuan pengaturan NAS?")
                }
            }
        }
    }
}