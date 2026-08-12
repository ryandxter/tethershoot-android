package com.rvmeur.transferin.feature.folder

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.rvmeur.transferin.ui.theme.CardSurface

@Composable
fun SelectSourceFolderScreen(
    currentSourceTreeUri: String,
    onSourceFolderSelected: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val openTreeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            val contentResolver = context.contentResolver
            val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION

            try {
                contentResolver.takePersistableUriPermission(uri, takeFlags)
            } catch (_: SecurityException) {
                // Beberapa provider tidak memberi seluruh grant persistable.
            }

            onSourceFolderSelected(uri.toString())
        }
    }

    val hasSelectedSource = currentSourceTreeUri.isNotBlank()

    val selectedLabel = remember(currentSourceTreeUri) {
        currentSourceTreeUri.ifBlank { "Belum ada folder source dipilih." }
    }

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
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Pilih Folder Sumber",
                    style = MaterialTheme.typography.titleMedium
                )

                Text(
                    text = "Pilih folder kamera, kartu memori, atau USB yang akan dipindai untuk proses transfer ke Google Drive.",
                    style = MaterialTheme.typography.bodySmall
                )

                Text(
                    text = "Source aktif",
                    style = MaterialTheme.typography.labelMedium
                )

                Text(
                    text = selectedLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (hasSelectedSource) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.error
                    }
                )

                Text(
                    text = "Akses folder akan disimpan agar bisa dipakai lagi pada sesi berikutnya, selama provider penyimpanan masih mengizinkannya.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Button(
                    onClick = { openTreeLauncher.launch(null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    Text(
                        text = if (hasSelectedSource) {
                            "Ganti folder source"
                        } else {
                            "Pilih folder source"
                        }
                    )
                }

                OutlinedButton(
                    onClick = {
                        if (currentSourceTreeUri.isNotBlank()) {
                            onSourceFolderSelected("")
                        }
                    },
                    enabled = currentSourceTreeUri.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Kosongkan pilihan source")
                }

                TextButton(
                    onClick = onBack,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Kembali")
                }
            }
        }
    }
}