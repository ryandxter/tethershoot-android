package com.rvmeur.transferin.feature.gallery.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.rvmeur.transferin.ui.components.KineticButton
import com.rvmeur.transferin.ui.theme.AccentGreen
import com.rvmeur.transferin.ui.theme.CardSurface
import com.rvmeur.transferin.ui.theme.Ground
import com.rvmeur.transferin.ui.theme.ModalDarkBackground
import com.rvmeur.transferin.ui.theme.RobotoMono
import com.rvmeur.transferin.ui.theme.TextMuted
import com.rvmeur.transferin.ui.theme.TextMutedLight
import com.rvmeur.transferin.ui.theme.toBrandLowercase
import java.io.File

/**
 * Full-screen Dark Modal Inspection Viewer featuring pinch-to-zoom,
 * live 256-bin luminance histogram, EXIF HUD, and 1-tap kinetic force-upload override.
 */
@Composable
fun ExposureInspectionModal(
    fileName: String,
    filePath: String,
    statusText: String,
    fileSizeText: String,
    cullingVerdict: String,
    meanLuminance: Double,
    entropyScore: Double,
    isCulled: Boolean,
    isManualUploadOverride: Boolean,
    onDismiss: () -> Unit,
    onForceUpload: () -> Unit,
    modifier: Modifier = Modifier,
    iso: String = "400",
    shutterSpeed: String = "1/250s",
    aperture: String = "f/2.8",
    cameraModel: String = "FUJIFILM X-T5",
    filmSimulation: String = "CLASSIC CHROME",
    histogramBins: IntArray? = null
) {
    val haptic = LocalHapticFeedback.current

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ModalDarkBackground)
            .semantics { isTraversalGroup = true }
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = fileName.toBrandLowercase(),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = RobotoMono,
                                fontSize = 13.sp,
                                color = Ground
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        CullingStatusBadge(
                            cullingVerdict = cullingVerdict,
                            isCulled = isCulled,
                            isManualUploadOverride = isManualUploadOverride
                        )
                    }
                    Text(
                        text = "status: $statusText • $fileSizeText".toBrandLowercase(),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = RobotoMono,
                            fontSize = 11.sp,
                            color = TextMutedLight
                        )
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Tutup pratinjau foto",
                        tint = Ground
                    )
                }
            }

            // Center Zoomable & Pannable Image Viewport
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 4.5f)
                            offset = if (scale > 1f) {
                                Offset(offset.x + pan.x, offset.y + pan.y)
                            } else {
                                Offset.Zero
                            }
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = {
                                if (scale > 1f) {
                                    scale = 1f
                                    offset = Offset.Zero
                                } else {
                                    scale = 2.5f
                                }
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                val context = LocalContext.current
                val thumbStorage = remember(context) { com.rvmeur.transferin.data.thumbnail.ThumbnailStorageManager(context) }
                val resolvedFile = filePath.let { path ->
                    val clean = if (path.startsWith("file://")) path.removePrefix("file://") else path
                    File(clean).takeIf { it.exists() && it.length() > 0 }
                } ?: thumbStorage.getThumbnailFile(fileName)?.takeIf { it.exists() && it.length() > 0 }

                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(resolvedFile)
                        .crossfade(true)
                        .build(),
                    contentDescription = "Foto $fileName, status eksposur $cullingVerdict, ketuk dua kali untuk zoom",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offset.x
                            translationY = offset.y
                        }
                )
            }

            // EXIF Parameter Readout Strip
            Surface(
                color = Color(0xFF181513),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ExifTag(label = "ISO", value = iso)
                    ExifTag(label = "SS", value = shutterSpeed)
                    ExifTag(label = "APERTURE", value = aperture)
                    ExifTag(label = "PROFIL", value = filmSimulation)
                }
            }

            // Live 256-Bin Luminance Histogram Overlay
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                ExposureHistogramCanvas(
                    histogramBins = histogramBins,
                    meanLuminance = meanLuminance,
                    entropyScore = entropyScore
                )
            }

            // Bottom Action Bar: Paksa Unggah / Tutup
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                if (isCulled || !isManualUploadOverride) {
                    KineticButton(
                        text = if (isManualUploadOverride) "dalam antrean unggah" else "paksa unggah ke cloud",
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onForceUpload()
                        },
                        containerColor = AccentGreen,
                        contentColor = CardSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                KineticButton(
                    text = "tutup pratinjau",
                    onClick = onDismiss,
                    containerColor = Color(0xFF2E2722),
                    contentColor = Ground
                )
            }
        }
    }
}

@Composable
private fun ExifTag(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = RobotoMono,
                fontSize = 8.sp,
                color = TextMutedLight
            )
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = RobotoMono,
                fontSize = 11.sp,
                color = Color.White
            )
        )
    }
}
