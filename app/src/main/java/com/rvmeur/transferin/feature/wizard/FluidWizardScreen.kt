package com.rvmeur.transferin.feature.wizard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rvmeur.transferin.domain.session.RemoteFolder
import com.rvmeur.transferin.feature.wizard.steps.Step1DriveFolder
import com.rvmeur.transferin.feature.wizard.steps.Step2CameraTether
import com.rvmeur.transferin.feature.wizard.steps.Step3SessionRules
import com.rvmeur.transferin.ui.components.FluidDotNavigationBar
import com.rvmeur.transferin.ui.components.RvmFooterBar
import com.rvmeur.transferin.ui.components.RvmHeaderBar
import com.rvmeur.transferin.ui.theme.AccentBurgundy
import com.rvmeur.transferin.ui.theme.Ground
import com.rvmeur.transferin.ui.theme.RobotoMono
import com.rvmeur.transferin.ui.theme.SurfaceVariant
import com.rvmeur.transferin.ui.theme.toBrandLowercase
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Root FluidWizardScreen — Consolidates setup into a gesture-driven HorizontalPager flow.
 */
@Composable
fun FluidWizardScreen(
    isAuthenticated: Boolean,
    onSignInClick: () -> Unit,
    onSignOutClick: () -> Unit,
    currentPath: String,
    folders: List<RemoteFolder>,
    selectedFolder: RemoteFolder?,
    onFolderSelect: (RemoteFolder) -> Unit,
    onNavigateUp: () -> Unit,
    onCreateFolder: (String) -> Unit,
    isLoadingFolder: Boolean,
    isCameraConnected: Boolean,
    cameraModelName: String,
    dcimPath: String,
    photoCount: Int,
    onSelectSourceFolder: () -> Unit,
    onLaunchSession: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pagerState = rememberPagerState(pageCount = { 3 })
    val coroutineScope = rememberCoroutineScope()
    var showWarningPill by remember { mutableStateOf(false) }

    // Step 1 validation: Requires Google Drive authentication and selected target folder
    val isStep1Valid = isAuthenticated && (selectedFolder != null || currentPath.isNotEmpty())

    // Auto-dismiss warning pill
    LaunchedEffect(showWarningPill) {
        if (showWarningPill) {
            delay(3000)
            showWarningPill = false
        }
    }

    val stepTitles = listOf("01 google drive", "02 camera tethering", "03 session rules")

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            RvmHeaderBar(
                eyebrowText = "quiet luxury tethering v1.0",
                isConnected = isCameraConnected
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
                // Floating Glass Pager Bar
                FluidDotNavigationBar(
                    pagerState = pagerState,
                    steps = stepTitles,
                    onDotClick = { index ->
                        if (index > 0 && !isStep1Valid && pagerState.currentPage == 0) {
                            showWarningPill = true
                        } else {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(index)
                            }
                        }
                    }
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Clickable Web Intent Footer
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
            HorizontalPager(
                state = pagerState,
                userScrollEnabled = if (pagerState.currentPage == 0) isStep1Valid else true,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    when (page) {
                        0 -> Step1DriveFolder(
                            isAuthenticated = isAuthenticated,
                            onSignInClick = onSignInClick,
                            onSignOutClick = onSignOutClick,
                            currentPath = currentPath,
                            folders = folders,
                            selectedFolder = selectedFolder,
                            onFolderSelect = onFolderSelect,
                            onNavigateUp = onNavigateUp,
                            onCreateFolder = onCreateFolder,
                            isLoading = isLoadingFolder
                        )

                        1 -> Step2CameraTether(
                            isCameraConnected = isCameraConnected,
                            cameraModelName = cameraModelName,
                            dcimPath = dcimPath,
                            photoCount = photoCount,
                            onSelectSourceFolder = onSelectSourceFolder
                        )

                        2 -> Step3SessionRules(
                            baselinePreview = "starting baseline: DSCF0018.JPG -> #18",
                            onLaunchSession = onLaunchSession
                        )
                    }

                    Spacer(modifier = Modifier.height(80.dp))
                }
            }

            // Floating Warning Pill when forward swipe is prevented
            AnimatedVisibility(
                visible = showWarningPill,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .background(SurfaceVariant, CircleShape)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .semantics { liveRegion = LiveRegionMode.Polite }
                ) {
                    Text(
                        text = "selesaikan otentikasi & pilih folder terlebih dahulu".toBrandLowercase(),
                        style = com.rvmeur.transferin.ui.theme.Typography.labelSmall.copy(
                            fontFamily = RobotoMono,
                            fontSize = 11.sp,
                            color = AccentBurgundy
                        )
                    )
                }
            }
        }
    }
}
