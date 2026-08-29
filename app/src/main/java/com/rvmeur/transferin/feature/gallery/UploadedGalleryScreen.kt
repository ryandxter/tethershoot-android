package com.rvmeur.transferin.feature.gallery

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.rvmeur.transferin.domain.model.UploadStatus
import com.rvmeur.transferin.feature.gallery.components.CullingStatusBadge
import com.rvmeur.transferin.feature.gallery.components.ExposureInspectionModal
import com.rvmeur.transferin.ui.components.DoppelrandCard
import com.rvmeur.transferin.ui.components.RvmFooterBar
import com.rvmeur.transferin.ui.components.RvmHeaderBar
import com.rvmeur.transferin.ui.image.ThumbnailImageLoaderProvider
import com.rvmeur.transferin.ui.image.thumbnailCompoundKey
import com.rvmeur.transferin.ui.theme.AccentBurgundy
import com.rvmeur.transferin.ui.theme.AccentGreen
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
import java.io.File

@Composable
fun UploadedGalleryScreen(
    viewModel: GalleryViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedModalItem by remember { mutableStateOf<GalleryItem?>(null) }
    val haptic = LocalHapticFeedback.current

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            Column {
                RvmHeaderBar(eyebrowText = "quiet luxury galeri v1.0")
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Ground)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Kembali",
                            tint = TextPrimary
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "galeri terunggah".toBrandLowercase(),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontFamily = CormorantGaramond,
                            fontSize = 22.sp,
                            color = TextPrimary
                        )
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Box(
                        modifier = Modifier
                            .background(SurfaceVariant, CircleShape)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "${uiState.uploadedItems.size} items",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = RobotoMono,
                                color = TextMutedLight
                            )
                        )
                    }
                }
            }
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
                    .padding(horizontal = 16.dp)
            ) {
                // Responsive Filter Chips Row with Live Item Counts
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    val filterChips = listOf(
                        FilterMode.ALL to "semua (${uiState.cullingMetrics.totalCount})",
                        FilterMode.OPTIMAL to "optimal (${uiState.cullingMetrics.optimalCount})",
                        FilterMode.CULLED to "terpangkas (${uiState.cullingMetrics.culledCount})",
                        FilterMode.SKIPPED to "dilewati (${uiState.cullingMetrics.skippedCount})"
                    )

                    items(filterChips) { (mode, label) ->
                        val isSelected = uiState.filterMode == mode
                        Box(
                            modifier = Modifier
                                .background(
                                    color = if (isSelected) AccentGreen else SurfaceVariant,
                                    shape = CircleShape
                                )
                                .clickable { viewModel.setFilterMode(mode) }
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = DMSans,
                                    color = if (isSelected) Ground else TextPrimary
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Content Viewport
                when {
                    uiState.isLoading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = AccentGreen, modifier = Modifier.size(28.dp))
                        }
                    }

                    uiState.uploadedItems.isEmpty() -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "belum ada berkas dalam kategori ini.".toBrandLowercase(),
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = DMSans,
                                    color = TextMuted
                                )
                            )
                        }
                    }

                    else -> {
                        // 3-Column Responsive Grid with DoppelrandCard
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 80.dp)
                        ) {
                            items(uiState.uploadedItems, key = { it.id }) { item ->
                                DoppelrandCard(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(1f)
                                        .semantics {
                                            role = Role.Button
                                            contentDescription = "Foto ${item.fileName}, status eksposur ${item.cullingVerdict}, ketuk untuk inspeksi"
                                        }
                                        .clickable { selectedModalItem = item },
                                    outerShape = RoundedCornerShape(12.dp),
                                    innerShape = RoundedCornerShape(8.dp),
                                    outerColor = SurfaceVariant,
                                    innerColor = CardSurface,
                                    borderColor = LightBorder,
                                    gapPadding = 2.dp,
                                    contentPadding = PaddingValues(2.dp)
                                ) {
                                    Box(modifier = Modifier.fillMaxSize()) {
                                        val context = LocalContext.current
                                        val thumbDir = remember(context) { File(context.getExternalFilesDir(null), "camera_thumbnails") }
                                        val thumbStorage = remember(context) { com.rvmeur.transferin.data.thumbnail.ThumbnailStorageManager(context) }
                                        val imageFile = item.thumbnailPath?.let { File(it) }?.takeIf { it.exists() && it.length() > 0 }
                                            ?: thumbStorage.getThumbnailFile(item.id)?.takeIf { it.exists() && it.length() > 0 }
                                            ?: File(thumbDir, "thumb_${item.id}.jpg").takeIf { it.exists() && it.length() > 0 }
                                            ?: item.filePath.let { path ->
                                                val clean = if (path.startsWith("file://")) path.removePrefix("file://") else path
                                                File(clean).takeIf { it.exists() && it.length() > 0 }
                                            }
                                        AsyncImage(
                                            model = ImageRequest.Builder(context)
                                                .data(imageFile)
                                                .thumbnailCompoundKey(fingerprint = item.id)
                                                .crossfade(true)
                                                .build(),
                                            imageLoader = ThumbnailImageLoaderProvider.getImageLoader(context),
                                            contentDescription = item.fileName,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )

                                        // Monospace Culling Status Pill Badge top-left
                                        CullingStatusBadge(
                                            cullingVerdict = item.cullingVerdict,
                                            isCulled = item.isCulled,
                                            isManualUploadOverride = item.isManualUploadOverride,
                                            modifier = Modifier
                                                .align(Alignment.TopStart)
                                                .padding(4.dp)
                                        )

                                        // Status icon or Quick "Paksa Unggah" CTA top-right
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(4.dp)
                                        ) {
                                            if (item.isCulled && !item.isManualUploadOverride) {
                                                // 1-Tap Kinetic Quick Force Upload Trigger
                                                Box(
                                                    modifier = Modifier
                                                        .background(AccentGreen, CircleShape)
                                                        .clickable {
                                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                            viewModel.forceUpload(item.id)
                                                        }
                                                        .padding(4.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.CloudUpload,
                                                        contentDescription = "Paksa Unggah Foto ${item.fileName}",
                                                        tint = Ground,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                }
                                            } else if (item.status == UploadStatus.SUCCESS) {
                                                Icon(
                                                    imageVector = Icons.Default.CheckCircle,
                                                    contentDescription = "Berhasil",
                                                    tint = AccentGreen,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            } else if (item.status == UploadStatus.FAILED) {
                                                Icon(
                                                    imageVector = Icons.Default.Error,
                                                    contentDescription = "Gagal",
                                                    tint = AccentBurgundy,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Full-Screen Image Viewer Modal Overlay
            AnimatedVisibility(
                visible = selectedModalItem != null,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxSize()
            ) {
                selectedModalItem?.let { modalItem ->
                    ExposureInspectionModal(
                        fileName = modalItem.fileName,
                        filePath = modalItem.thumbnailPath ?: modalItem.filePath,
                        statusText = modalItem.status.name,
                        fileSizeText = modalItem.fileSizeFormatted,
                        cullingVerdict = modalItem.cullingVerdict,
                        meanLuminance = modalItem.meanLuminance,
                        entropyScore = modalItem.entropyScore,
                        isCulled = modalItem.isCulled,
                        isManualUploadOverride = modalItem.isManualUploadOverride,
                        onDismiss = { selectedModalItem = null },
                        onForceUpload = {
                            viewModel.forceUpload(modalItem.id)
                            selectedModalItem = null
                        }
                    )
                }
            }
        }
    }
}
