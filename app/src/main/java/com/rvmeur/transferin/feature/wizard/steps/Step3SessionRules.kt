package com.rvmeur.transferin.feature.wizard.steps

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rvmeur.transferin.domain.culling.CullingPolicy
import com.rvmeur.transferin.ui.components.DoppelrandCard
import com.rvmeur.transferin.ui.components.KineticButton
import com.rvmeur.transferin.ui.theme.AccentGreen
import com.rvmeur.transferin.ui.theme.AccentGreenSubtle
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

/**
 * Step 3: Session Preferences, Smart Culling Rules & Kinetic Launch CTA
 */
@Composable
fun Step3SessionRules(
    baselinePreview: String,
    onLaunchSession: () -> Unit,
    modifier: Modifier = Modifier,
    initialCullingPolicy: CullingPolicy = CullingPolicy.AUTO_SKIP_UPLOAD,
    onCullingPolicyChange: (CullingPolicy) -> Unit = {}
) {
    var rawJpgCoexistence by remember { mutableStateOf(true) }
    var autoSyncEnabled by remember { mutableStateOf(true) }
    var selectedPolicy by remember { mutableStateOf(initialCullingPolicy) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Card A: Preferences & Tethering Rules
        DoppelrandCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "aturan sesi & sinkronisasi".toBrandLowercase(),
                style = MaterialTheme.typography.titleLarge.copy(
                    fontFamily = CormorantGaramond,
                    fontSize = 20.sp,
                    color = TextPrimary
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            // RAW+JPG Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "koeksistensi RAW+JPG".toBrandLowercase(),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = DMSans,
                            color = TextPrimary
                        )
                    )
                    Text(
                        text = "unggah kedua format file secara bersamaan tanpa saling memblokir.".toBrandLowercase(),
                        style = MaterialTheme.typography.bodySmall.copy(color = TextMuted)
                    )
                }
                Switch(
                    checked = rawJpgCoexistence,
                    onCheckedChange = { rawJpgCoexistence = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Ground,
                        checkedTrackColor = AccentGreen,
                        uncheckedThumbColor = TextMutedLight,
                        uncheckedTrackColor = LightBorder
                    )
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Auto-Sync Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "sinkronisasi langsung".toBrandLowercase(),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = DMSans,
                            color = TextPrimary
                        )
                    )
                    Text(
                        text = "otomatis mengunggah foto baru begitu terdeteksi dari kamera.".toBrandLowercase(),
                        style = MaterialTheme.typography.bodySmall.copy(color = TextMuted)
                    )
                }
                Switch(
                    checked = autoSyncEnabled,
                    onCheckedChange = { autoSyncEnabled = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Ground,
                        checkedTrackColor = AccentGreen,
                        uncheckedThumbColor = TextMutedLight,
                        uncheckedTrackColor = LightBorder
                    )
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Baseline Extraction Preview
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Text(
                    text = "ekstraksi baseline penomoran kamera:".toBrandLowercase(),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = DMSans,
                        color = TextMuted
                    )
                )
                Text(
                    text = baselinePreview.toBrandLowercase(),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = RobotoMono,
                        fontSize = 11.sp,
                        color = AccentGreen
                    )
                )
            }
        }

        // Card B: Smart Culling & Quota Control
        DoppelrandCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "kontrol smart culling & kuota".toBrandLowercase(),
                style = MaterialTheme.typography.titleLarge.copy(
                    fontFamily = CormorantGaramond,
                    fontSize = 20.sp,
                    color = TextPrimary
                )
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "otomatis mendeteksi flash misfire dan highlight bocor untuk menghemat kuota cloud.".toBrandLowercase(),
                style = MaterialTheme.typography.bodySmall.copy(color = TextMuted)
            )

            Spacer(modifier = Modifier.height(16.dp))

            val policies = listOf(
                Triple(
                    CullingPolicy.AUTO_SKIP_UPLOAD,
                    "lewati otomatis (hemat kuota)",
                    "hentikan transfer raw untuk frame gelap gulita atau overexposure parah."
                ),
                Triple(
                    CullingPolicy.WARN_ONLY,
                    "peringatan saja (tetap unggah)",
                    "tampilkan lencana status di galeri, namun tetap unggah semua file."
                ),
                Triple(
                    CullingPolicy.DISABLED,
                    "nonaktif",
                    "matikan evaluasi eksposur & smart culling secara total."
                )
            )

            policies.forEach { (policy, title, desc) ->
                val isSelected = selectedPolicy == policy
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSelected) AccentGreenSubtle else CardSurface)
                        .border(
                            width = if (isSelected) 1.5.dp else 1.dp,
                            color = if (isSelected) AccentGreen else LightBorder,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .clickable {
                            selectedPolicy = policy
                            onCullingPolicyChange(policy)
                        }
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Radio circle indicator
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) AccentGreen else Color.Transparent)
                                .border(
                                    width = if (isSelected) 0.dp else 1.5.dp,
                                    color = if (isSelected) Color.Transparent else TextMutedLight,
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Ground,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = title.toBrandLowercase(),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontFamily = DMSans,
                                    fontSize = 14.sp,
                                    color = if (isSelected) AccentGreen else TextPrimary
                                )
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = desc.toBrandLowercase(),
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = DMSans,
                                    fontSize = 12.sp,
                                    color = TextMuted
                                )
                            )
                        }
                    }
                }
            }
        }

        // Card C: Launch Action CTA
        DoppelrandCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "siap memulai sesi".toBrandLowercase(),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = CormorantGaramond,
                    fontSize = 18.sp,
                    color = TextPrimary
                )
            )
            Text(
                text = "semua konfigurasi terverifikasi. ketuk tombol di bawah untuk memulai tethering live.".toBrandLowercase(),
                style = MaterialTheme.typography.bodySmall.copy(color = TextMuted)
            )
            Spacer(modifier = Modifier.height(16.dp))
            KineticButton(
                text = "mulai sesi tethering",
                onClick = onLaunchSession,
                containerColor = AccentGreen,
                contentColor = CardSurface
            )
        }
    }
}
