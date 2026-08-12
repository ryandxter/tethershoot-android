package com.rvmeur.transferin.feature.wizard.steps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rvmeur.transferin.ui.components.DoppelrandCard
import com.rvmeur.transferin.ui.components.KineticButton
import com.rvmeur.transferin.ui.theme.AccentGreen
import com.rvmeur.transferin.ui.theme.CardSurface
import com.rvmeur.transferin.ui.theme.CormorantGaramond
import com.rvmeur.transferin.ui.theme.DMSans
import com.rvmeur.transferin.ui.theme.Ground
import com.rvmeur.transferin.ui.theme.LightBorder
import com.rvmeur.transferin.ui.theme.RobotoMono
import com.rvmeur.transferin.ui.theme.TextMuted
import com.rvmeur.transferin.ui.theme.TextMutedLight
import com.rvmeur.transferin.ui.theme.TextPrimary
import com.rvmeur.transferin.ui.theme.toBrandLowercase

/**
 * Step 3: Session Preferences & Kinetic Launch CTA
 */
@Composable
fun Step3SessionRules(
    baselinePreview: String,
    onLaunchSession: () -> Unit,
    modifier: Modifier = Modifier
) {
    var rawJpgCoexistence by remember { mutableStateOf(true) }
    var autoSyncEnabled by remember { mutableStateOf(true) }

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

        // Card B: Launch Action CTA
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
