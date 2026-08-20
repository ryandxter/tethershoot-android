package com.rvmeur.transferin.feature.gallery.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rvmeur.transferin.ui.theme.AccentGreen
import com.rvmeur.transferin.ui.theme.BadgeDeepBurgundy
import com.rvmeur.transferin.ui.theme.BadgeWarmAmber
import com.rvmeur.transferin.ui.theme.RobotoMono
import com.rvmeur.transferin.ui.theme.TextMutedLight

/**
 * Quiet Luxury Monospace status badge for exposure verdicts and culling states.
 */
@Composable
fun CullingStatusBadge(
    cullingVerdict: String,
    isCulled: Boolean = false,
    isManualUploadOverride: Boolean = false,
    modifier: Modifier = Modifier
) {
    val (backgroundColor, labelText, textColor) = when {
        isManualUploadOverride -> {
            Triple(AccentGreen, "UNGGAH", Color.White)
        }
        cullingVerdict == "UNDEREXPOSED_CRITICAL" || cullingVerdict.contains("UNDER", ignoreCase = true) -> {
            Triple(BadgeWarmAmber, "UNDER", Color.White)
        }
        cullingVerdict == "OVEREXPOSED_BLOWN" || cullingVerdict.contains("BLOWN", ignoreCase = true) || cullingVerdict.contains("OVER", ignoreCase = true) -> {
            Triple(BadgeDeepBurgundy, "BLOWN", Color.White)
        }
        isCulled -> {
            Triple(TextMutedLight, "LEWAT", Color.White)
        }
        else -> return
    }

    Box(
        modifier = modifier
            .background(backgroundColor, RoundedCornerShape(4.dp))
            .padding(horizontal = 5.dp, vertical = 2.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (isManualUploadOverride) {
                Icon(
                    imageVector = Icons.Default.CloudUpload,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(10.dp)
                )
                Spacer(modifier = Modifier.width(3.dp))
            }
            Text(
                text = labelText,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = RobotoMono,
                    fontSize = 9.sp,
                    letterSpacing = 0.5.sp,
                    color = textColor
                )
            )
        }
    }
}
