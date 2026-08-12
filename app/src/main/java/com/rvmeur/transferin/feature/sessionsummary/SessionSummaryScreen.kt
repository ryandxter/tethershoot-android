package com.rvmeur.transferin.feature.sessionsummary

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rvmeur.transferin.ui.components.DoppelrandCard
import com.rvmeur.transferin.ui.components.KineticButton
import com.rvmeur.transferin.ui.components.RvmFooterBar
import com.rvmeur.transferin.ui.components.RvmHeaderBar
import com.rvmeur.transferin.ui.theme.AccentBurgundy
import com.rvmeur.transferin.ui.theme.AccentGreen
import com.rvmeur.transferin.ui.theme.AccentGreenSubtle
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

@Composable
fun SessionSummaryScreen(
    customerName: String?,
    nasFolderPath: String,
    totalFiles: Int,
    totalSuccess: Int,
    totalFailed: Int,
    startTimeText: String?,
    endTimeText: String?,
    onDone: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            RvmHeaderBar(eyebrowText = "quiet luxury tethering v1.0")
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Success Badge Icon
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(AccentGreenSubtle, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = AccentGreen,
                    modifier = Modifier.size(40.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "ringkasan sesi tethering".toBrandLowercase(),
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontFamily = CormorantGaramond,
                    fontSize = 24.sp,
                    color = TextPrimary
                )
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "seluruh proses transfer telah dihentikan secara aman.".toBrandLowercase(),
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = DMSans,
                    color = TextMuted
                )
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Summary Card
            DoppelrandCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SummaryRow("Target Google Drive", nasFolderPath, isMonospace = true)

                    startTimeText?.let { SummaryRow("Waktu Mulai", it) }
                    endTimeText?.let { SummaryRow("Waktu Selesai", it) }

                    HorizontalDivider(color = LightBorder, thickness = 1.dp)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        SummaryStatItem(Modifier.weight(1f), "total file", totalFiles.toString(), TextPrimary)
                        SummaryStatItem(Modifier.weight(1f), "berhasil", totalSuccess.toString(), AccentGreen)
                        SummaryStatItem(Modifier.weight(1f), "gagal", totalFailed.toString(), AccentBurgundy)
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            KineticButton(
                text = "selesai & kembali ke setup",
                onClick = onDone,
                containerColor = AccentGreen,
                contentColor = CardSurface
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String, isMonospace: Boolean = false) {
    Column {
        Text(
            text = label.toBrandLowercase(),
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = DMSans,
                color = TextMutedLight
            )
        )
        Text(
            text = value.toBrandLowercase(),
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = if (isMonospace) RobotoMono else DMSans,
                fontSize = if (isMonospace) 12.sp else 14.sp,
                color = TextPrimary
            )
        )
    }
}

@Composable
private fun SummaryStatItem(
    modifier: Modifier,
    label: String,
    value: String,
    color: Color
) {
    Column(
        modifier = modifier.semantics { contentDescription = "$label: $value" },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge.copy(
                fontFamily = RobotoMono,
                fontSize = 24.sp,
                color = color
            )
        )
        Text(
            text = label.toBrandLowercase(),
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = DMSans,
                fontSize = 11.sp,
                color = TextMutedLight
            )
        )
    }
}
