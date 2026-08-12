package com.rvmeur.transferin.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rvmeur.transferin.ui.theme.CardSurface
import com.rvmeur.transferin.ui.theme.LightBorder
import com.rvmeur.transferin.ui.theme.SurfaceVariant

/**
 * DoppelrandCard — Apple-tier Double-Bezel Container Composable.
 * Features concentric radii (Outer 24dp, Inner 18dp with 6dp padding gap)
 * and RVM quiet luxury styling.
 */
@Composable
fun DoppelrandCard(
    modifier: Modifier = Modifier,
    outerShape: Shape = RoundedCornerShape(24.dp),
    innerShape: Shape = RoundedCornerShape(18.dp),
    outerColor: Color = SurfaceVariant,
    innerColor: Color = CardSurface,
    borderColor: Color = LightBorder,
    borderWidth: Dp = 1.dp,
    gapPadding: Dp = 6.dp,
    contentPadding: PaddingValues = PaddingValues(20.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.border(borderWidth, borderColor, outerShape),
        shape = outerShape,
        color = outerColor
    ) {
        Box(modifier = Modifier.padding(gapPadding)) {
            Surface(
                shape = innerShape,
                color = innerColor
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(contentPadding),
                    content = content
                )
            }
        }
    }
}
