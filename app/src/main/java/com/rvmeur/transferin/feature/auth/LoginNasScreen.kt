package com.rvmeur.transferin.feature.auth

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
import com.rvmeur.transferin.ui.theme.AccentGold
import com.rvmeur.transferin.ui.theme.CardSurface
import com.rvmeur.transferin.ui.theme.ErrorRedSoft

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
                    text = "Connect Google Drive",
                    style = MaterialTheme.typography.titleMedium
                )

                Text(
                    text = "Masukkan Client ID, Client Secret, dan Refresh Token Anda untuk sinkronisasi ke Google Drive.",
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
                    label = { Text("Google Client ID") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = state.password,
                    onValueChange = viewModel::onPasswordChange,
                    label = { Text("Google Client Secret") },
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Password
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = state.username,
                    onValueChange = viewModel::onUsernameChange,
                    label = { Text("Google Refresh Token") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = state.startingFolder,
                    onValueChange = viewModel::onStartingFolderChange,
                    label = { Text("Target Folder Name (e.g. RVM_Photos)") },
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
            }
        }
    }
}