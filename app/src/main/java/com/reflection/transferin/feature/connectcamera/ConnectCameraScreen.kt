package com.reflection.transferin.feature.connectcamera

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.reflection.transferin.data.usb.CameraSessionState
import com.reflection.transferin.ui.theme.AccentGold
import kotlinx.coroutines.delay

@Composable
fun ConnectCameraScreen(
    selectedNasFolder: String,
    selectedSourceFolder: String,
    viewModel: ConnectCameraViewModel,
    onSelectSourceFolder: () -> Unit,
    onStartSession: () -> Unit,
    onStartShootMode: (Uri?) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val session = state.cameraSession
    val scrollState = rememberScrollState()

    var tetheredActionLocked by remember { mutableStateOf(false) }
    var permissionActionLocked by remember { mutableStateOf(false) }
    var importActionLocked by remember { mutableStateOf(false) }

    LaunchedEffect(
        state.canProceedToTetheredFlow,
        state.hasPermittedUsbDevice,
        state.cameraSession.isOpen
    ) {
        if (!state.canProceedToTetheredFlow) {
            tetheredActionLocked = false
        }
        if (state.hasPermittedUsbDevice) {
            permissionActionLocked = false
        }
        if (!state.cameraSession.isOpen) {
            importActionLocked = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(scrollState)
            .padding(24.dp)
    ) {
        Text(
            text = "Koneksi Kamera",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Folder NAS: $selectedNasFolder",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(32.dp))

        UsbPermissionCard(
            hasUsbDevice = state.hasUsbDevice,
            hasPermission = state.hasPermittedUsbDevice,
            usbStatusText = state.usbStatusText,
            actionLocked = permissionActionLocked,
            onRequestPermission = {
                if (permissionActionLocked) return@UsbPermissionCard
                permissionActionLocked = true
                viewModel.requestUsbPermission()
            }
        )

        Spacer(modifier = Modifier.height(24.dp))

        CameraStatusCard(
            cameraSession = session,
            isLikelyCamera = state.isLikelyCameraConnected,
            hasPermission = state.hasPermittedUsbDevice
        )

        Spacer(modifier = Modifier.height(24.dp))

        if (state.canProceedToTetheredFlow) {
            TetheredReadyCard(
                deviceLabel = viewModel.getPrimaryDeviceLabel() ?: session.deviceLabel,
                actionLocked = tetheredActionLocked,
                onContinue = {
                    if (tetheredActionLocked) return@TetheredReadyCard
                    tetheredActionLocked = true
                    viewModel.selectTetheredMode()
                    onStartShootMode(null)
                }
            )

            Spacer(modifier = Modifier.height(16.dp))
        }

        if (state.hasPermittedUsbDevice && session.isOpen) {
            ImportModeCard(
                onSelectFolder = onSelectSourceFolder,
                onStartImport = {
                    if (importActionLocked) return@ImportModeCard
                    importActionLocked = true

                    if (selectedSourceFolder.isNotBlank()) {
                        onStartSession()
                    } else {
                        importActionLocked = false
                        onSelectSourceFolder()
                    }
                },
                selectedSourceFolder = selectedSourceFolder,
                actionLocked = importActionLocked
            )

            Spacer(modifier = Modifier.height(16.dp))
        }

        TextButton(
            onClick = onBack,
            enabled = !tetheredActionLocked && !importActionLocked,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Kembali")
        }
    }
}

@Composable
private fun UsbPermissionCard(
    hasUsbDevice: Boolean,
    hasPermission: Boolean,
    usbStatusText: String,
    actionLocked: Boolean,
    onRequestPermission: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (hasPermission) {
                Color(0xFF4CAF50).copy(alpha = 0.10f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (hasPermission) Icons.Default.CheckCircle else Icons.Default.Usb,
                    contentDescription = null,
                    tint = if (hasPermission) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (hasPermission) "Izin USB Aktif" else "Izin USB Diperlukan",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = usbStatusText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (!hasPermission) {
                Button(
                    onClick = onRequestPermission,
                    enabled = hasUsbDevice && !actionLocked,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (actionLocked) "Meminta Izin..." else "Izinkan USB"
                    )
                }
            }

            if (!hasUsbDevice) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Hubungkan kabel USB kamera ke perangkat ini terlebih dahulu.",
                    style = MaterialTheme.typography.bodySmall,
                    color = AccentGold
                )
            }
        }
    }
}

@Composable
private fun CameraStatusCard(
    cameraSession: CameraSessionState,
    isLikelyCamera: Boolean,
    hasPermission: Boolean
) {
    val isReady = cameraSession.isOpen
    val isOpening = cameraSession.isOpening
    val hasError = cameraSession.errorMessage != null

    val title = when {
        isReady -> "Kamera Tersambung"
        isOpening -> "Membuka Sesi Kamera..."
        hasPermission && isLikelyCamera -> "Kamera Siap untuk Tethered"
        isLikelyCamera -> "Kamera Terdeteksi"
        else -> "Menunggu Kamera"
    }

    val subtitle = when {
        hasError -> cameraSession.errorMessage ?: "Terjadi kesalahan pada kamera."
        isReady -> "${cameraSession.deviceLabel ?: "Kamera"} • ${cameraSession.storageCount} storage"
        hasPermission && isLikelyCamera -> "Izin sudah aktif. Lanjutkan untuk masuk ke mode ready to shoot dan upload."
        isLikelyCamera -> "Perangkat kamera terdeteksi. Berikan izin USB untuk melanjutkan."
        else -> "Sambungkan kamera melalui USB untuk memulai."
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isReady -> Color(0xFF4CAF50).copy(alpha = 0.10f)
                isOpening -> Color(0xFF2196F3).copy(alpha = 0.10f)
                hasError -> Color(0xFFF44336).copy(alpha = 0.10f)
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = when {
                    isReady -> Icons.Default.CameraAlt
                    isOpening -> Icons.Default.Sync
                    else -> Icons.Default.CameraAlt
                },
                contentDescription = null,
                tint = when {
                    isReady -> Color(0xFF4CAF50)
                    isOpening -> Color(0xFF2196F3)
                    hasError -> Color(0xFFF44336)
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(48.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = if (hasError) Color(0xFFF44336) else MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (cameraSession.storageIds.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Storage: ${cameraSession.storageIds.joinToString()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun TetheredReadyCard(
    deviceLabel: String?,
    actionLocked: Boolean,
    onContinue: () -> Unit
) {
    val resolvedDeviceLabel = deviceLabel ?: "Kamera siap digunakan"

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF2196F3).copy(alpha = 0.10f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.PhotoCamera,
                    contentDescription = null,
                    tint = Color(0xFF1565C0),
                    modifier = Modifier.size(36.dp)
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = "Ready to Shoot dan Upload",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1565C0)
                    )
                    Text(
                        text = resolvedDeviceLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text(
                text = "Lanjutkan untuk unlock kamera, scan file existing, lalu masuk ke gallery session.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Button(
                onClick = onContinue,
                enabled = !actionLocked,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    if (actionLocked) "Memproses..." else "Lanjut ke Mode Tethered"
                )
            }
        }
    }
}

@Composable
private fun ImportModeCard(
    onSelectFolder: () -> Unit,
    onStartImport: () -> Unit,
    selectedSourceFolder: String,
    actionLocked: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Mode Import (Fallback)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Gunakan hanya jika memang ingin import folder tertentu.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (selectedSourceFolder.isNotBlank()) {
                Text(
                    text = "Folder source: $selectedSourceFolder",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = onStartImport,
                    enabled = !actionLocked,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.CloudUpload, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (actionLocked) "Memproses..." else "Mulai Import")
                }
            } else {
                OutlinedButton(
                    onClick = onSelectFolder,
                    enabled = !actionLocked,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Pilih Folder Source")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Mode ini berisiko mengakses storage kamera seperti flow lama.",
                style = MaterialTheme.typography.labelSmall,
                color = AccentGold
            )
        }
    }
}