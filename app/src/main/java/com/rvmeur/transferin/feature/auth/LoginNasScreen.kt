package com.rvmeur.transferin.feature.auth

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rvmeur.transferin.ui.components.DoppelrandCard
import com.rvmeur.transferin.ui.components.KineticButton
import com.rvmeur.transferin.ui.components.RvmFooterBar
import com.rvmeur.transferin.ui.components.RvmHeaderBar
import com.rvmeur.transferin.ui.theme.AccentGreen
import com.rvmeur.transferin.ui.theme.AccentGreenSubtle
import com.rvmeur.transferin.ui.theme.CardSurface
import com.rvmeur.transferin.ui.theme.CormorantGaramond
import com.rvmeur.transferin.ui.theme.DMSans
import com.rvmeur.transferin.ui.theme.Ground
import com.rvmeur.transferin.ui.theme.RobotoMono
import com.rvmeur.transferin.ui.theme.TextMuted
import com.rvmeur.transferin.ui.theme.TextMutedLight
import com.rvmeur.transferin.ui.theme.TextPrimary
import com.rvmeur.transferin.ui.theme.toBrandLowercase

/**
 * Dynamic OAuth Authorization Landing Screen — Quiet Luxury Styling
 * Completely removes manual credential input modals.
 */
@Composable
fun LoginNasScreen(
    onLoginSuccess: () -> Unit,
    modifier: Modifier = Modifier
) {
    val application = LocalContext.current.applicationContext as Application

    val viewModel: LoginNasViewModel = viewModel(
        factory = LoginNasViewModelFactory(application)
    )

    val state = viewModel.uiState

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
                .padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            DoppelrandCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = "otentikasi google drive (oauth)".toBrandLowercase(),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontFamily = CormorantGaramond,
                            fontSize = 22.sp,
                            color = TextPrimary
                        )
                    )

                    Text(
                        text = "otentikasi langsung untuk tim & komunitas privat. tidak memerlukan pengisian kredensial manual.".toBrandLowercase(),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = DMSans,
                            color = TextMuted
                        )
                    )

                    Box(
                        modifier = Modifier
                            .background(AccentGreenSubtle, CircleShape)
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "scope: https://www.googleapis.com/auth/drive.file",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = RobotoMono,
                                fontSize = 10.sp,
                                color = AccentGreen
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (state.isTesting) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = AccentGreen, modifier = Modifier.size(28.dp))
                        }
                    } else {
                        KineticButton(
                            text = "masuk akun google drive",
                            onClick = {
                                viewModel.loginWithDynamicOAuth(onLoginSuccess)
                            },
                            containerColor = AccentGreen,
                            contentColor = CardSurface
                        )
                    }
                }
            }
        }
    }
}