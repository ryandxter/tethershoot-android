package com.rvmeur.transferin.feature.sessionactive

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rvmeur.transferin.domain.model.CameraRuntimeState
import com.rvmeur.transferin.domain.model.ConnectionMode
import com.rvmeur.transferin.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SessionActiveScreen(
    cameraState: CameraRuntimeState,
    pendingCount: Int,
    uploadingCount: Int,
    successCount: Int,
    failedCount: Int,
    onStartImport: () -> Unit,
    onStartLiveHybrid: () -> Unit,
    onStartShootMode: () -> Unit,
    onStopShootMode: () -> Unit,
    onFinish: () -> Unit,
    onOpenGallery: () -> Unit,
    onBack: () -> Unit,
    shootModeState: ShootModeState? = null,
    tetherState: TetherState? = null,
    modifier: Modifier = Modifier
) {
    val totalProcessed = successCount + failedCount
    val isEngineBusy = uploadingCount > 0 || cameraState.isReceiving
    val isShootModeActive = shootModeState?.isActive == true
    val isTetheredMode = cameraState.activeMode == ConnectionMode.TETHERED

    val tetherStatus = tetherState?.statusText.orEmpty()
    val isTetherUnlocked = tetherState?.isUnlocked == true
    val isTetherWatching = tetherState?.isWatching == true

    val isTetherConnecting = tetherStatus.contains("membuka sesi", ignoreCase = true) ||
            tetherStatus.contains("handshake", ignoreCase = true) ||
            tetherStatus.contains("menyiapkan", ignoreCase = true) ||
            tetherStatus.contains("menyambung", ignoreCase = true)

    val canStartShootMode = when {
        isTetheredMode -> isTetherUnlocked &&
                isTetherWatching &&
                !isEngineBusy &&
                !isTetherConnecting
        else -> !isEngineBusy
    }

    val canRunSecondaryActions = when {
        isTetheredMode -> isTetherUnlocked &&
                !isEngineBusy &&
                !isShootModeActive &&
                !isTetherConnecting
        else -> cameraState.isConnected && !isEngineBusy && !isShootModeActive
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isTetheredMode && tetherState != null) {
                TetherStatusBanner(tetherState)
                Spacer(modifier = Modifier.height(12.dp))
            }

            if (isShootModeActive && shootModeState != null && !isTetheredMode) {
                ShootModeStatusBanner(shootModeState)
                Spacer(modifier = Modifier.height(12.dp))
            }

            CameraStatusHeader(
                state = cameraState,
                tetherState = tetherState,
                isShootModeActive = isShootModeActive
            )

            Spacer(modifier = Modifier.height(20.dp))

            QuickStatsCard(
                pendingCount = pendingCount,
                uploadingCount = uploadingCount,
                successCount = successCount,
                failedCount = failedCount,
                isEngineBusy = isEngineBusy,
                onOpenGallery = onOpenGallery
            )

            Spacer(modifier = Modifier.height(16.dp))

            StatusDetailsCard(
                pendingCount = pendingCount,
                uploadingCount = uploadingCount,
                successCount = successCount,
                failedCount = failedCount,
                isEngineBusy = isEngineBusy,
                cameraState = cameraState,
                tetherState = tetherState
            )

            Spacer(modifier = Modifier.weight(1f))

            ActionButtonsArea(
                isConnected = cameraState.isConnected,
                isEngineBusy = isEngineBusy,
                isShootModeActive = isShootModeActive,
                isTetheredMode = isTetheredMode,
                tetherState = tetherState,
                canStartShootMode = canStartShootMode,
                canRunSecondaryActions = canRunSecondaryActions,
                onStartImport = onStartImport,
                onStartLiveHybrid = onStartLiveHybrid,
                onStartShootMode = onStartShootMode,
                onStopShootMode = onStopShootMode,
                onFinish = onFinish,
                onOpenGallery = onOpenGallery,
                onBack = onBack,
                canFinish = totalProcessed > 0 || successCount > 0
            )
        }

        val reconnectText = shootModeState?.reconnectionStatusText
        if (reconnectText != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f))
                    .clickable(enabled = true, onClick = {}),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier.padding(32.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Text(
                            text = reconnectText,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ShootModeStatusBanner(state: ShootModeState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (state.isBaselineReady) {
                Color(0xFF4CAF50).copy(alpha = 0.15f)
            } else {
                AccentGold.copy(alpha = 0.15f)
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                rememberInfiniteTransition(label = "shootPulse").animateFloat(
                    initialValue = 0.5f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(800),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "alpha"
                )

                Icon(
                    imageVector = if (state.isBaselineReady) Icons.Default.CameraAlt else Icons.Default.Sync,
                    contentDescription = null,
                    tint = if (state.isBaselineReady) Color(0xFF4CAF50) else AccentGold,
                    modifier = Modifier.size(24.dp)
                )

                Column {
                    Text(
                        text = when {
                            state.isScanning -> "Scanning Camera..."
                            state.isBaselineReady -> "Shoot Mode Aktif"
                            else -> "Memulai Shoot Mode..."
                        },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (state.isBaselineReady) Color(0xFF4CAF50) else AccentGoldDark
                    )
                    Text(
                        text = when {
                            state.isScanning -> "${state.baselineFileCount} files found"
                            state.isBaselineReady -> "Baseline: ${state.baselineFileCount} files"
                            else -> "Memindai storage..."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }

            if (state.uploadedCount > 0) {
                Surface(
                    shape = CircleShape,
                    color = Color(0xFF4CAF50)
                ) {
                    Text(
                        text = "+${state.uploadedCount}",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun TetherStatusBanner(state: TetherState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (state.isUnlocked) {
                Color(0xFF4CAF50).copy(alpha = 0.15f)
            } else {
                AccentGold.copy(alpha = 0.15f)
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                rememberInfiniteTransition(label = "tetherPulse").animateFloat(
                    initialValue = 0.5f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(800),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "alpha"
                )

                Icon(
                    imageVector = if (state.isUnlocked) Icons.Default.CameraAlt else Icons.Default.Sync,
                    contentDescription = null,
                    tint = if (state.isUnlocked) Color(0xFF4CAF50) else AccentGold,
                    modifier = Modifier.size(24.dp)
                )

                Column {
                    Text(
                        text = when {
                            state.isUnlocked && state.isWatching -> "🔓 TETHERED SIAP"
                            state.isUnlocked -> "🔓 TETHERED TERHUBUNG"
                            else -> "⏳ Menyambung..."
                        },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (state.isUnlocked) Color(0xFF4CAF50) else AccentGoldDark
                    )
                    Text(
                        text = state.statusText,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }

            if (state.detectedCount > 0) {
                Surface(
                    shape = CircleShape,
                    color = Color(0xFF4CAF50)
                ) {
                    Text(
                        text = "+${state.detectedCount}",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CameraStatusHeader(
    state: CameraRuntimeState,
    tetherState: TetherState? = null,
    isShootModeActive: Boolean = false
) {
    val tetherStatus = tetherState?.statusText.orEmpty()
    val isConnecting = tetherStatus.contains("membuka sesi", ignoreCase = true) ||
            tetherStatus.contains("handshake", ignoreCase = true) ||
            tetherStatus.contains("menyiapkan", ignoreCase = true) ||
            tetherStatus.contains("menyambung", ignoreCase = true)

    val isReady = tetherState?.isUnlocked == true ||
            state.statusText.contains("siap", ignoreCase = true) ||
            state.statusText.contains("aktif", ignoreCase = true) ||
            state.statusText.contains("tersambung", ignoreCase = true)

    val statusColor by animateColorAsState(
        targetValue = when {
            isShootModeActive -> Color(0xFF4CAF50)
            isConnecting -> AccentGold
            isReady -> Color(0xFF4CAF50)
            !state.isConnected && tetherState == null -> ErrorRedSoft
            else -> AccentGold
        },
        label = "statusColor"
    )

    val headerTitle = when {
        isShootModeActive -> "Shoot Mode Aktif"
        isConnecting -> "Menghubungkan Kamera..."
        isReady -> "Siap Transfer"
        !state.isConnected && tetherState == null -> "Kamera Belum Tersambung"
        else -> "Kamera Terhubung"
    }

    val headerIcon = when {
        isConnecting -> Icons.Default.Sync
        !state.isConnected && tetherState == null -> Icons.Default.UsbOff
        else -> Icons.Default.CameraAlt
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (isReady && !isConnecting) {
                    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                    val pulseScale by infiniteTransition.animateFloat(
                        initialValue = 1f,
                        targetValue = 1.3f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1000, easing = LinearEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "pulseScale"
                    )
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .graphicsLayer {
                                scaleX = pulseScale
                                scaleY = pulseScale
                            }
                            .clip(CircleShape)
                            .background(statusColor.copy(alpha = 0.2f))
                    )
                }

                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(statusColor),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = headerIcon,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = headerTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = statusColor
                    )
                    if (isReady && !isConnecting) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = tetherState?.statusText ?: state.statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickStatsCard(
    pendingCount: Int,
    uploadingCount: Int,
    successCount: Int,
    failedCount: Int,
    isEngineBusy: Boolean,
    onOpenGallery: () -> Unit
) {
    Card(
        onClick = onOpenGallery,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = AccentGold.copy(alpha = 0.08f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "File Terupload",
                    style = MaterialTheme.typography.labelMedium,
                    color = AccentGoldDark
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "$successCount",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = AccentGoldDark
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MiniStatPill(
                    icon = Icons.Default.CloudUpload,
                    value = uploadingCount.toString(),
                    color = AccentGold
                )
                MiniStatPill(
                    icon = Icons.Default.Check,
                    value = successCount.toString(),
                    color = Color(0xFF4CAF50)
                )
                MiniStatPill(
                    icon = Icons.Default.Error,
                    value = failedCount.toString(),
                    color = ErrorRedSoft
                )
            }

            Icon(
                Icons.Default.ChevronRight,
                contentDescription = "Buka Galeri",
                tint = AccentGold
            )
        }
    }
}

@Composable
private fun MiniStatPill(
    icon: ImageVector,
    value: String,
    color: Color
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = color.copy(alpha = 0.15f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = color
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}

@Composable
private fun StatusDetailsCard(
    pendingCount: Int,
    uploadingCount: Int,
    successCount: Int,
    failedCount: Int,
    isEngineBusy: Boolean,
    cameraState: CameraRuntimeState,
    tetherState: TetherState? = null
) {
    val isTetherUnlocked = tetherState?.isUnlocked == true
    val isTetherWatching = tetherState?.isWatching == true

    val detailText = when {
        isTetherWatching && pendingCount + uploadingCount == 0 ->
            "Watching foto baru dari kamera..."
        isTetherUnlocked && !isTetherWatching ->
            "Kamera tersambung, watcher sedang disiapkan..."
        !isEngineBusy && pendingCount + uploadingCount == 0 ->
            "Menunggu file masuk..."
        else ->
            "Sesi berjalan normal."
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Status Transfer",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Schedule,
                    label = "Antrean",
                    value = pendingCount.toString(),
                    color = MaterialTheme.colorScheme.primary
                )
                StatCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.CloudUpload,
                    label = "Mengupload",
                    value = uploadingCount.toString(),
                    color = AccentGold
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.CheckCircle,
                    label = "Berhasil",
                    value = successCount.toString(),
                    color = Color(0xFF4CAF50)
                )
                StatCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Error,
                    label = "Gagal",
                    value = failedCount.toString(),
                    color = ErrorRedSoft
                )
            }

            HorizontalDivider(thickness = 0.5.dp, color = BorderSoft)

            AnimatedVisibility(
                visible = isEngineBusy,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                ActiveIndicator(
                    text = if (uploadingCount > 0) {
                        "Sedang mengirim ke Google Drive..."
                    } else {
                        "Menerima file dari kamera..."
                    }
                )
            }

            if (!isEngineBusy) {
                Text(
                    text = detailText,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        }
    }
}

@Composable
private fun StatCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    value: String,
    color: Color
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.08f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun ActiveIndicator(text: String) {
    val infiniteTransition = rememberInfiniteTransition(label = "loading")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(AccentGold.copy(alpha = 0.1f))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Sync,
            contentDescription = null,
            modifier = Modifier
                .size(20.dp)
                .graphicsLayer { rotationZ = rotation },
            tint = AccentGold
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = AccentGoldDark,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun ActionButtonsArea(
    isConnected: Boolean,
    isEngineBusy: Boolean,
    isShootModeActive: Boolean,
    isTetheredMode: Boolean,
    tetherState: TetherState?,
    canStartShootMode: Boolean,
    canRunSecondaryActions: Boolean,
    onStartImport: () -> Unit,
    onStartLiveHybrid: () -> Unit,
    onStartShootMode: () -> Unit,
    onStopShootMode: () -> Unit,
    onFinish: () -> Unit,
    onOpenGallery: () -> Unit,
    onBack: () -> Unit,
    canFinish: Boolean
) {
    var actionLocked by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun runLockedAction(block: () -> Unit) {
        if (actionLocked) return
        actionLocked = true
        block()
        scope.launch {
            delay(800)
            actionLocked = false
        }
    }

    val isTetherUnlocked = tetherState?.isUnlocked == true
    val isTetherWatching = tetherState?.isWatching == true

    val shootButtonEnabled = !actionLocked && canStartShootMode
    val secondaryButtonsEnabled = !actionLocked && canRunSecondaryActions
    val stopButtonEnabled = !actionLocked
    val finishEnabled = !actionLocked && canFinish

    val shootSubtitle = when {
        isShootModeActive -> null
        isTetheredMode && !isTetherUnlocked -> "Tunggu kamera selesai handshake"
        isTetheredMode && isTetherUnlocked && !isTetherWatching -> "Watcher tether sedang disiapkan"
        else -> "Auto-detect camera • Upload foto baru saja"
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (isShootModeActive) {
            Button(
                onClick = { runLockedAction(onStopShootMode) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
                enabled = stopButtonEnabled,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ErrorRedSoft,
                    contentColor = Color.White
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Stop,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "Stop Shoot Mode",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        } else {
            Button(
                onClick = { runLockedAction(onStartShootMode) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
                enabled = shootButtonEnabled,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF4CAF50),
                    contentColor = Color.White,
                    disabledContainerColor = Color(0xFF4CAF50).copy(alpha = 0.3f)
                )
            ) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = null,
                    modifier = Modifier.size(26.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = if (isTetheredMode && !isTetherUnlocked) {
                        "MENUNGGU KAMERA..."
                    } else {
                        "SHOOT & UPLOAD"
                    },
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }

            shootSubtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = { runLockedAction(onStartLiveHybrid) },
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
                enabled = secondaryButtonsEnabled,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentGold,
                    contentColor = Color.White
                )
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Live Sync",
                    fontWeight = FontWeight.SemiBold
                )
            }

            OutlinedButton(
                onClick = { runLockedAction(onStartImport) },
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
                enabled = secondaryButtonsEnabled,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = AccentGoldDark
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Backup,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Backup",
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = onOpenGallery,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = TextPrimary
                )
            ) {
                Icon(
                    imageVector = Icons.Outlined.Folder,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text("Galeri")
            }

            OutlinedButton(
                onClick = onBack,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                enabled = !actionLocked,
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text("Kembali")
            }

            Button(
                onClick = { runLockedAction(onFinish) },
                enabled = finishEnabled,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF4CAF50)
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Done,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text("Selesai")
            }
        }
    }
}