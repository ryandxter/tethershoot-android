package com.rvmeur.transferin.feature.folder

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rvmeur.transferin.domain.session.RemoteFolder
import com.rvmeur.transferin.ui.theme.CardSurface

@Composable
fun SelectFolderScreen(
    state: FolderUiState,
    onOpenFolder: (RemoteFolder) -> Unit,
    onSelectCurrentFolder: () -> Unit,
    onCreateFolder: (String) -> Unit,
    onGoUp: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showDialog by remember { mutableStateOf(false) }
    var folderName by remember { mutableStateOf("") }

    val hasValidPath = state.currentPath.isNotBlank()
    val actionEnabled = !state.isLoading && !state.isTransferringFiles
    val canSelectCurrentFolder = actionEnabled && hasValidPath

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.Top,
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
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Pilih Folder Google Drive",
                    style = MaterialTheme.typography.titleMedium
                )

                Text(
                    text = "Masuk ke folder tujuan upload, lalu tekan tombol pilih folder. Setelah itu aplikasi akan lanjut ke Connect Camera untuk izin USB dan koneksi source.",
                    style = MaterialTheme.typography.bodySmall
                )

                Text(
                    text = "Path aktif:",
                    style = MaterialTheme.typography.labelMedium
                )

                Text(
                    text = state.currentPath.ifBlank { "-" },
                    style = MaterialTheme.typography.bodySmall
                )

                if (state.isTransferringFiles) {
                    Text(
                        text = "Transfer sedang berjalan. Navigasi folder dinonaktifkan sementara.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onGoUp,
                        modifier = Modifier.weight(1f),
                        enabled = actionEnabled && hasValidPath
                    ) {
                        Text("Naik 1 Folder")
                    }

                    OutlinedButton(
                        onClick = { showDialog = true },
                        modifier = Modifier.weight(1f),
                        enabled = actionEnabled
                    ) {
                        Text("Buat Folder Baru")
                    }
                }

                HorizontalDivider()

                when {
                    state.isLoading -> {
                        Text(
                            text = "Memuat folder...",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    state.errorMessage != null -> {
                        Text(
                            text = state.errorMessage,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    state.folders.isEmpty() -> {
                        Text(
                            text = "Tidak ada subfolder di lokasi ini.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    else -> {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 360.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(state.folders) { folder ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(enabled = actionEnabled) {
                                            onOpenFolder(folder)
                                        },
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surface
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier.padding(16.dp)
                                    ) {
                                        Text(
                                            text = folder.name,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Text(
                                            text = folder.fullPath,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Button(
                    onClick = onSelectCurrentFolder,
                    enabled = canSelectCurrentFolder,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                ) {
                    Text("Pilih folder ini")
                }

                if (!hasValidPath) {
                    Text(
                        text = "Pilih atau buka folder tujuan Google Drive terlebih dulu.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                TextButton(
                    onClick = onBack,
                    enabled = !state.isTransferringFiles,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Kembali")
                }
            }
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = {
                showDialog = false
                folderName = ""
            },
            title = {
                Text(text = "Buat Folder Baru")
            },
            text = {
                OutlinedTextField(
                    value = folderName,
                    onValueChange = { folderName = it },
                    label = { Text("Nama Folder") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (folderName.isNotBlank()) {
                            onCreateFolder(folderName)
                            showDialog = false
                            folderName = ""
                        }
                    },
                    enabled = folderName.isNotBlank()
                ) {
                    Text("Buat")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDialog = false
                        folderName = ""
                    }
                ) {
                    Text("Batal")
                }
            }
        )
    }
}