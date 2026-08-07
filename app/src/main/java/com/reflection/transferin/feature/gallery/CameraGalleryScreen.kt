package com.reflection.transferin.feature.gallery

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.reflection.transferin.data.local.entity.CameraMediaItemEntity
import com.reflection.transferin.data.local.entity.MediaStatus
import com.reflection.transferin.ui.theme.*

/**
 * Camera Gallery Screen - Shoot Mode Style
 * White & Gold theme
 * Shows camera photos with upload status
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraGalleryScreen(
    mediaItems: List<CameraMediaItemEntity>,
    isLoading: Boolean,
    isShootModeActive: Boolean,
    baselineCount: Int,
    newCount: Int,
    uploadedCount: Int,
    onItemClick: (CameraMediaItemEntity) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Header
        GalleryHeader(
            isShootModeActive = isShootModeActive,
            baselineCount = baselineCount,
            newCount = newCount,
            uploadedCount = uploadedCount,
            onBack = onBack
        )

        // Status Bar
        if (isShootModeActive) {
            ShootModeStatusBar(newCount = newCount, uploadedCount = uploadedCount)
        }

        // Content
        if (isLoading && mediaItems.isEmpty()) {
            LoadingState()
        } else if (mediaItems.isEmpty()) {
            EmptyState()
        } else {
            GalleryGrid(
                mediaItems = mediaItems,
                onItemClick = onItemClick
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GalleryHeader(
    isShootModeActive: Boolean,
    baselineCount: Int,
    newCount: Int,
    uploadedCount: Int,
    onBack: () -> Unit
) {
    TopAppBar(
        title = {
            Column {
                Text(
                    text = "Galeri Kamera",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (isShootModeActive) "Shoot Mode Aktif" else "Mode Backup",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isShootModeActive) SuccessGreen else TextSecondary
                )
            }
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Kembali")
            }
        },
        actions = {
            // Stats
            if (isShootModeActive && newCount > 0) {
                NewBadge(count = newCount)
            }
            if (uploadedCount > 0) {
                UploadedBadge(count = uploadedCount)
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = CardSurface
        )
    )
}

@Composable
private fun NewBadge(count: Int) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = AccentGold.copy(alpha = 0.15f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = Icons.Default.FiberNew,
                contentDescription = null,
                tint = AccentGold,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = "+$count baru",
                style = MaterialTheme.typography.labelMedium,
                color = AccentGoldDark,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
    Spacer(modifier = Modifier.width(8.dp))
}

@Composable
private fun UploadedBadge(count: Int) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = SuccessGreenSoft
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = Icons.Default.CloudDone,
                contentDescription = null,
                tint = SuccessGreen,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = "$count",
                style = MaterialTheme.typography.labelMedium,
                color = SuccessGreen,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
    Spacer(modifier = Modifier.width(8.dp))
}

@Composable
private fun ShootModeStatusBar(newCount: Int, uploadedCount: Int) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = if (newCount > 0) AccentGold.copy(alpha = 0.1f) else SuccessGreenSoft
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Animated shoot indicator
                val infiniteTransition = rememberInfiniteTransition(label = "shootPulse")
                val alpha by infiniteTransition.animateFloat(
                    initialValue = 0.5f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(600),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "alpha"
                )

                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(SuccessGreen.copy(alpha = alpha))
                )

                Text(
                    text = if (newCount > 0) "Mendeteksi foto baru..." else "Menunggu jepretan...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (newCount > 0) AccentGoldDark else SuccessGreen
                )
            }

            if (uploadedCount > 0) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudUpload,
                        contentDescription = null,
                        tint = AccentGold,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "$uploadedCount uploaded",
                        style = MaterialTheme.typography.bodySmall,
                        color = AccentGoldDark
                    )
                }
            }
        }
    }
}

@Composable
private fun GalleryGrid(
    mediaItems: List<CameraMediaItemEntity>,
    onItemClick: (CameraMediaItemEntity) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(mediaItems, key = { it.id }) { item ->
            GalleryItem(
                item = item,
                onClick = { onItemClick(item) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GalleryItem(
    item: CameraMediaItemEntity,
    onClick: () -> Unit
) {
    val isUploaded = item.status == MediaStatus.UPLOADED
    val isNew = item.status == MediaStatus.DISCOVERED || item.status == MediaStatus.CACHED || item.status == MediaStatus.QUEUED

    Card(
        modifier = Modifier
            .aspectRatio(1f)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceVariant)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Thumbnail or placeholder
            if (item.thumbPath != null) {
                AsyncImage(
                    model = item.thumbPath,
                    contentDescription = item.fileName,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                // Placeholder
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(SurfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Image,
                        contentDescription = null,
                        tint = TextTertiary,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            // Status overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(4.dp)
            ) {
                // Type badge (JPG/RAW)
                if (item.isRaw) {
                    Surface(
                        modifier = Modifier.align(Alignment.TopStart),
                        shape = RoundedCornerShape(4.dp),
                        color = AccentGoldDark.copy(alpha = 0.9f)
                    ) {
                        Text(
                            text = "RAW",
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 8.sp
                        )
                    }
                }

                // Upload status
                Box(
                    modifier = Modifier.align(Alignment.BottomEnd)
                ) {
                    when (item.status) {
                        MediaStatus.UPLOADED -> {
                            Surface(
                                shape = CircleShape,
                                color = SuccessGreen
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Uploaded",
                                    tint = Color.White,
                                    modifier = Modifier
                                        .size(20.dp)
                                        .padding(4.dp)
                                )
                            }
                        }
                        MediaStatus.UPLOADING -> {
                            UploadProgressIndicator()
                        }
                        MediaStatus.QUEUED, MediaStatus.DISCOVERED, MediaStatus.CACHED -> {
                            if (isNew) {
                                Surface(
                                    shape = CircleShape,
                                    color = AccentGold
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CloudUpload,
                                        contentDescription = "Queued",
                                        tint = Color.White,
                                        modifier = Modifier
                                            .size(20.dp)
                                            .padding(4.dp)
                                    )
                                }
                            }
                        }
                        MediaStatus.FAILED -> {
                            Surface(
                                shape = CircleShape,
                                color = ErrorRedSoft
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Error,
                                    contentDescription = "Failed",
                                    tint = Color.White,
                                    modifier = Modifier
                                        .size(20.dp)
                                        .padding(4.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Filename overlay
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth(),
                color = OverlayDark.copy(alpha = 0.6f)
            ) {
                Text(
                    text = item.displayName,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun UploadProgressIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "uploadProgress")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    Surface(
        shape = CircleShape,
        color = AccentGold.copy(alpha = 0.9f)
    ) {
        Icon(
            imageVector = Icons.Default.Sync,
            contentDescription = "Uploading",
            tint = Color.White,
            modifier = Modifier
                .size(20.dp)
                .graphicsLayer { rotationZ = rotation }
                .padding(4.dp)
        )
    }
}

@Composable
private fun LoadingState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator(
                color = AccentGold,
                modifier = Modifier.size(48.dp)
            )
            Text(
                text = "Memuat galeri...",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun EmptyState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.PhotoLibrary,
                contentDescription = null,
                tint = TextTertiary,
                modifier = Modifier.size(64.dp)
            )
            Text(
                text = "Belum ada foto",
                style = MaterialTheme.typography.titleMedium,
                color = TextSecondary
            )
            Text(
                text = "Foto dari kamera akan muncul di sini",
                style = MaterialTheme.typography.bodyMedium,
                color = TextTertiary,
                textAlign = TextAlign.Center
            )
        }
    }
}
