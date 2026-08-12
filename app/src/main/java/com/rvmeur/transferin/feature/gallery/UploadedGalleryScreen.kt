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
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.rvmeur.transferin.domain.model.UploadStatus
import com.rvmeur.transferin.ui.components.DoppelrandCard
import com.rvmeur.transferin.ui.components.KineticButton
import com.rvmeur.transferin.ui.components.RvmFooterBar
import com.rvmeur.transferin.ui.components.RvmHeaderBar
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
                // Filter Chips Row
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    val filterModes = listOf(
                        FilterMode.ALL to "semua",
                        FilterMode.PHOTOS to "foto",
                        FilterMode.VIDEOS to "video",
                        FilterMode.UPLOADED to "berhasil",
                        FilterMode.FAILED to "gagal"
                    )

                    items(filterModes) { (mode, label) ->
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
                                text = "belum ada berkas terunggah dalam sesi ini.".toBrandLowercase(),
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
                                            contentDescription = "Gambar ${item.fileName}, ketuk untuk pratinjau"
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
                                        AsyncImage(
                                            model = ImageRequest.Builder(LocalContext.current)
                                                .data(File(item.filePath))
                                                .crossfade(true)
                                                .build(),
                                            contentDescription = item.fileName,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )

                                        // Monospace format badge (RAW/JPG) top-left
                                        val ext = item.fileName.substringAfterLast('.', "JPG").uppercase()
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.TopStart)
                                                .padding(4.dp)
                                                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = ext,
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontFamily = RobotoMono,
                                                    fontSize = 9.sp,
                                                    color = Color.White
                                                )
                                            )
                                        }

                                        // Status icon top-right
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(4.dp)
                                        ) {
                                            if (item.status == UploadStatus.SUCCESS) {
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
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xEE12100E))
                            .semantics { isTraversalGroup = true }
                    ) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            // Top Bar Header
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = modalItem.fileName.toBrandLowercase(),
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontFamily = RobotoMono,
                                            fontSize = 14.sp,
                                            color = Ground
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "status: ${modalItem.status.name} • ${modalItem.fileSizeFormatted}".toBrandLowercase(),
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontFamily = RobotoMono,
                                            fontSize = 11.sp,
                                            color = TextMutedLight
                                        )
                                    )
                                }

                                IconButton(onClick = { selectedModalItem = null }) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Tutup pratinjau foto",
                                        tint = Ground
                                    )
                                }
                            }

                            // Center Image Viewport
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(File(modalItem.filePath))
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = modalItem.fileName,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            // Bottom Action Bar
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp)
                            ) {
                                KineticButton(
                                    text = "tutup pratinjau",
                                    onClick = { selectedModalItem = null },
                                    containerColor = AccentGreen,
                                    contentColor = CardSurface
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
