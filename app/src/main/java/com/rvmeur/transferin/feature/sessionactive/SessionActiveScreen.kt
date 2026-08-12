package com.rvmeur.transferin.feature.sessionactive

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rvmeur.transferin.domain.model.CameraRuntimeState
import com.rvmeur.transferin.domain.model.ConnectionMode
import com.rvmeur.transferin.ui.components.DoppelrandCard
import com.rvmeur.transferin.ui.components.KineticButton
import com.rvmeur.transferin.ui.components.RvmFooterBar
import com.rvmeur.transferin.ui.components.RvmHeaderBar
import com.rvmeur.transferin.ui.theme.AccentBurgundy
import com.rvmeur.transferin.ui.theme.AccentGreen
import com.rvmeur.transferin.ui.theme.AccentOlive
import com.rvmeur.transferin.ui.theme.CardSurface
import com.rvmeur.transferin.ui.theme.CormorantGaramond
import com.rvmeur.transferin.ui.theme.DMSans
import com.rvmeur.transferin.ui.theme.Ground
import com.rvmeur.transferin.ui.theme.LightBorder
import com.rvmeur.transferin.ui.theme.RobotoMono
import com.rvmeur.transferin.ui.theme.SurfaceVariant
import com.rvmeur.transferin.ui.theme.TextMuted
import com.rvmeur.transferin.ui.theme.TextMutedLight
import com.rvmeur.transferin.ui.theme.TextPrimary
import com.rvmeur.transferin.ui.theme.toBrandLowercase

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
    val isEngineBusy = uploadingCount > 0 || cameraState.isReceiving
    val isTetheredMode = cameraState.activeMode == ConnectionMode.TETHERED
    val statusColor = if (cameraState.isConnected) AccentOlive else AccentBurgundy

    // Infinite pulse animation for camera status
    val infiniteTransition = rememberInfiniteTransition(label = "session_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 2.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = EaseOut),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse_scale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = EaseOut),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse_alpha"
    )

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            RvmHeaderBar(
                eyebrowText = "quiet luxury tethering v1.0",
                isConnected = cameraState.isConnected
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Ground)
                    .padding(bottom = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                RvmFooterBar()
            }
        },
        containerColor = Ground
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Card 1: Camera Live Status Aura Header
                DoppelrandCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .semantics { liveRegion = LiveRegionMode.Polite }
                        ) {
                            Text(
                                text = (if (cameraState.isConnected) "kamera terhubung" else "mencari kamera...").toBrandLowercase(),
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontFamily = CormorantGaramond,
                                    fontSize = 22.sp,
                                    color = TextPrimary
                                )
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = (if (isTetheredMode) "mode: tethered usb live" else "mode: penjelajah media mtp").toBrandLowercase(),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = RobotoMono,
                                    fontSize = 11.sp,
                                    color = TextMutedLight
                                )
                            )
                        }

                        // Live .dot-pulse status aura
                        Box(
                            modifier = Modifier.size(28.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .graphicsLayer {
                                        scaleX = pulseScale
                                        scaleY = pulseScale
                                        alpha = pulseAlpha
                                    }
                                    .background(statusColor, CircleShape)
                            )
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(statusColor, CircleShape)
                            )
                        }
                    }
                }

                // Card 2: 4-Stat Metric Grid
                DoppelrandCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "metrik transfer sesi".toBrandLowercase(),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = CormorantGaramond,
                            fontSize = 18.sp,
                            color = TextPrimary
                        )
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    val metrics = listOf(
                        StatMetric("antrean", pendingCount, TextMutedLight),
                        StatMetric("mengunggah", uploadingCount, AccentOlive),
                        StatMetric("berhasil", successCount, AccentGreen),
                        StatMetric("gagal", failedCount, AccentBurgundy)
                    )

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 180.dp),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        items(metrics.size) { index ->
                            val metric = metrics[index]
                            DoppelrandCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 72.dp)
                                    .semantics {
                                        contentDescription = "Status ${metric.label}: ${metric.count}"
                                    },
                                outerShape = RoundedCornerShape(16.dp),
                                innerShape = RoundedCornerShape(12.dp),
                                outerColor = SurfaceVariant,
                                innerColor = CardSurface,
                                borderColor = LightBorder,
                                gapPadding = 4.dp,
                                contentPadding = PaddingValues(12.dp)
                            ) {
                                Column {
                                    Text(
                                        text = metric.count.toString(),
                                        style = MaterialTheme.typography.headlineMedium.copy(
                                            fontFamily = RobotoMono,
                                            fontSize = 26.sp,
                                            color = metric.color
                                        )
                                    )
                                    Text(
                                        text = metric.label.toBrandLowercase(),
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontFamily = DMSans,
                                            fontSize = 11.sp,
                                            color = TextMutedLight
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                // Card 3: Live Queue Stream Progress Card
                AnimatedVisibility(
                    visible = isEngineBusy,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    DoppelrandCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "mengirim berkas...".toBrandLowercase(),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontFamily = CormorantGaramond,
                                    fontSize = 16.sp,
                                    color = TextPrimary
                                )
                            )
                            CircularProgressIndicator(
                                color = AccentGreen,
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            color = AccentGreen,
                            trackColor = LightBorder,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // Card 4: Action Bar
                DoppelrandCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "tindakan sesi".toBrandLowercase(),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = CormorantGaramond,
                            fontSize = 18.sp,
                            color = TextPrimary
                        )
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    KineticButton(
                        text = "buka galeri terunggah",
                        onClick = onOpenGallery,
                        containerColor = TextPrimary,
                        contentColor = Ground
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    KineticButton(
                        text = "selesaikan sesi tethering",
                        onClick = onFinish,
                        containerColor = AccentBurgundy,
                        contentColor = Ground
                    )
                }

                Spacer(modifier = Modifier.height(60.dp))
            }
        }
    }
}

private data class StatMetric(
    val label: String,
    val count: Int,
    val color: androidx.compose.ui.graphics.Color
)