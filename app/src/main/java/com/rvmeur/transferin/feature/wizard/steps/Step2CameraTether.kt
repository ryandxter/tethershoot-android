package com.rvmeur.transferin.feature.wizard.steps

import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rvmeur.transferin.ui.components.DoppelrandCard
import com.rvmeur.transferin.ui.components.KineticButton
import com.rvmeur.transferin.ui.theme.AccentBurgundy
import com.rvmeur.transferin.ui.theme.AccentOlive
import com.rvmeur.transferin.ui.theme.CormorantGaramond
import com.rvmeur.transferin.ui.theme.RobotoMono
import com.rvmeur.transferin.ui.theme.TextMuted
import com.rvmeur.transferin.ui.theme.TextMutedLight
import com.rvmeur.transferin.ui.theme.TextPrimary
import com.rvmeur.transferin.ui.theme.toBrandLowercase

/**
 * Step 2: Camera Tethering & Live Pulse Aura
 */
@Composable
fun Step2CameraTether(
    isCameraConnected: Boolean,
    cameraModelName: String,
    dcimPath: String,
    photoCount: Int,
    onSelectSourceFolder: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "step2_pulse_transition")
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

    val statusColor = if (isCameraConnected) AccentOlive else AccentBurgundy

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Card A: Live USB/MTP Status Aura Card
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
                        text = (if (isCameraConnected) cameraModelName else "mencari kamera...").toBrandLowercase(),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontFamily = CormorantGaramond,
                            fontSize = 22.sp,
                            color = TextPrimary
                        )
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = (if (isCameraConnected) "media path: $dcimPath ($photoCount foto)" else "hubungkan kamera via kabel USB/MTP").toBrandLowercase(),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = RobotoMono,
                            fontSize = 11.sp,
                            color = TextMutedLight
                        )
                    )
                }

                // Live Pulse Aura Dot
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

        // Card B: Fallback Manual Import Mode (SAF)
        DoppelrandCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "mode impor manual".toBrandLowercase(),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = CormorantGaramond,
                    fontSize = 18.sp,
                    color = TextPrimary
                )
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "opsional: gunakan direktori penyimpanan lokal jika tidak memakai tethering USB langsung.".toBrandLowercase(),
                style = MaterialTheme.typography.bodySmall.copy(
                    color = TextMuted
                )
            )
            Spacer(modifier = Modifier.height(16.dp))
            KineticButton(
                text = "pilih folder impor lokal (SAF)",
                onClick = onSelectSourceFolder,
                containerColor = TextPrimary,
                contentColor = MaterialTheme.colorScheme.background
            )
        }
    }
}
