package com.rvmeur.transferin.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rvmeur.transferin.ui.theme.AccentGreen
import com.rvmeur.transferin.ui.theme.RobotoMono
import com.rvmeur.transferin.ui.theme.TextMutedLight

/**
 * RvmFooterBar — Clickable Web Intent Footer.
 * Anchored below navigation bar. Launches Android browser intent targeting https://rvmeur.com.
 */
@Composable
fun RvmFooterBar(
    modifier: Modifier = Modifier,
    webUrl: String = "https://rvmeur.com"
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .semantics { role = Role.Button }
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(webUrl))
                context.startActivity(intent)
            },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(AccentGreen, CircleShape)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "rian-ism visual memories • rvmeur.com ↗",
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = RobotoMono,
                fontSize = 11.sp,
                color = TextMutedLight
            )
        )
    }
}
