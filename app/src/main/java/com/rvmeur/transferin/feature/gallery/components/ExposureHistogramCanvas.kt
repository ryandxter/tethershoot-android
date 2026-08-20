package com.rvmeur.transferin.feature.gallery.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rvmeur.transferin.ui.theme.AccentGreen
import com.rvmeur.transferin.ui.theme.BadgeDeepBurgundy
import com.rvmeur.transferin.ui.theme.BadgeWarmAmber
import com.rvmeur.transferin.ui.theme.CardSurface
import com.rvmeur.transferin.ui.theme.RobotoMono
import com.rvmeur.transferin.ui.theme.TextMuted
import com.rvmeur.transferin.ui.theme.TextMutedLight
import java.util.Locale

/**
 * High-performance Compose Canvas rendering a 256-bin luminance histogram curve with cubic Bézier smoothing
 * and shadow/highlight clipping boundary overlays.
 */
@Composable
fun ExposureHistogramCanvas(
    histogramBins: IntArray?,
    meanLuminance: Double,
    entropyScore: Double,
    modifier: Modifier = Modifier,
    shadowClippingPercent: Float = 0f,
    highlightClippingPercent: Float = 0f
) {
    val bins = remember(histogramBins) {
        if (histogramBins != null && histogramBins.size >= 256) {
            histogramBins
        } else {
            // Fallback synthetic bell curve if bins not pre-calculated
            IntArray(256) { i ->
                val diff = (i - meanLuminance).toDouble()
                (1000 * Math.exp(-(diff * diff) / (2.0 * 40.0 * 40.0))).toInt().coerceAtLeast(1)
            }
        }
    }

    val maxBinCount = remember(bins) {
        (bins.maxOrNull() ?: 1).coerceAtLeast(1)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF1C1917), RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        // Telemetry Row Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "HISTOGRAM LUMINANSI 256-BIN",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = RobotoMono,
                    fontSize = 9.sp,
                    letterSpacing = 0.5.sp,
                    color = TextMutedLight
                )
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = String.format(Locale.US, "Ȳ: %.1f", meanLuminance),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = RobotoMono,
                        fontSize = 10.sp,
                        color = Color.White
                    )
                )
                Text(
                    text = String.format(Locale.US, "H: %.2f", entropyScore),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = RobotoMono,
                        fontSize = 10.sp,
                        color = AccentGreen
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Canvas Curve
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
        ) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val width = size.width
                val height = size.height

                if (bins.isEmpty() || width <= 0f || height <= 0f) return@Canvas

                val stepX = width / 255f
                val fillPath = Path()
                val strokePath = Path()

                val firstNorm = (bins[0].toFloat() / maxBinCount).coerceIn(0f, 1f)
                val firstY = height - (firstNorm * (height - 4f))

                fillPath.moveTo(0f, height)
                fillPath.lineTo(0f, firstY)
                strokePath.moveTo(0f, firstY)

                var prevX = 0f
                var prevY = firstY

                for (i in 1..255) {
                    val currentX = i * stepX
                    val normY = (bins[i].toFloat() / maxBinCount).coerceIn(0f, 1f)
                    val currentY = height - (normY * (height - 4f))

                    // Cubic Bézier control points
                    val midX = (prevX + currentX) / 2f
                    strokePath.cubicTo(midX, prevY, midX, currentY, currentX, currentY)
                    fillPath.cubicTo(midX, prevY, midX, currentY, currentX, currentY)

                    prevX = currentX
                    prevY = currentY
                }

                fillPath.lineTo(width, height)
                fillPath.close()

                // Gradient Fill Under Curve
                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            AccentGreen.copy(alpha = 0.45f),
                            AccentGreen.copy(alpha = 0.05f)
                        ),
                        startY = 0f,
                        endY = height
                    )
                )

                // Smooth Stroke Line
                drawPath(
                    path = strokePath,
                    color = AccentGreen,
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                )

                // Shadow Clipping Line (Y <= 3) at ~ 3/255 of width
                val shadowClipX = 3 * stepX
                drawLine(
                    color = BadgeWarmAmber.copy(alpha = 0.7f),
                    start = Offset(shadowClipX, 0f),
                    end = Offset(shadowClipX, height),
                    strokeWidth = 1.dp.toPx()
                )

                // Highlight Blowout Line (Y >= 248) at ~ 248/255 of width
                val highlightClipX = 248 * stepX
                drawLine(
                    color = BadgeDeepBurgundy.copy(alpha = 0.7f),
                    start = Offset(highlightClipX, 0f),
                    end = Offset(highlightClipX, height),
                    strokeWidth = 1.dp.toPx()
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Bottom Boundary Labels
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "0 (BAYANGAN)",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = RobotoMono,
                    fontSize = 8.sp,
                    color = BadgeWarmAmber
                )
            )
            Text(
                text = "128 (TENGAH)",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = RobotoMono,
                    fontSize = 8.sp,
                    color = TextMuted
                )
            )
            Text(
                text = "255 (SOROTAN)",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = RobotoMono,
                    fontSize = 8.sp,
                    color = BadgeDeepBurgundy
                )
            )
        }
    }
}
