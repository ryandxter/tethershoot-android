package com.rvmeur.transferin.ui.components

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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rvmeur.transferin.ui.theme.AccentBurgundy
import com.rvmeur.transferin.ui.theme.AccentOlive
import com.rvmeur.transferin.ui.theme.CormorantGaramond
import com.rvmeur.transferin.ui.theme.Ground
import com.rvmeur.transferin.ui.theme.RobotoMono
import com.rvmeur.transferin.ui.theme.TextMutedLight
import com.rvmeur.transferin.ui.theme.TextPrimary

/**
 * RvmHeaderBar — Minimalist RVM Quiet Luxury Header.
 * Features Cormorant Garamond brand logo ("rvmeur"), monospace eyebrow tag, and dynamic dot-pulse.
 */
@Composable
fun RvmHeaderBar(
    modifier: Modifier = Modifier,
    eyebrowText: String = "quiet luxury tethering v1.0",
    isConnected: Boolean = false
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse_transition")
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

    val statusColor = if (isConnected) AccentOlive else AccentBurgundy

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(Ground)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(verticalArrangement = Arrangement.Center) {
            Text(
                text = "rvmeur",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontFamily = CormorantGaramond,
                    fontSize = 24.sp,
                    color = TextPrimary
                )
            )
            Text(
                text = eyebrowText.lowercase(),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = RobotoMono,
                    fontSize = 10.sp,
                    color = TextMutedLight
                )
            )
        }

        Box(
            modifier = Modifier.size(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
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
