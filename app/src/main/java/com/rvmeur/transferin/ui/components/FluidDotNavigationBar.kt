package com.rvmeur.transferin.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rvmeur.transferin.ui.theme.AccentGreen
import com.rvmeur.transferin.ui.theme.Border
import com.rvmeur.transferin.ui.theme.LightBorder
import com.rvmeur.transferin.ui.theme.RobotoMono
import com.rvmeur.transferin.ui.theme.SurfaceVariant
import com.rvmeur.transferin.ui.theme.TextPrimary
import com.rvmeur.transferin.ui.theme.toBrandLowercase

/**
 * FluidDotNavigationBar — Floating glass pill navigation bar with morphing active dot.
 * Linked to PagerState with spring physics and step titles.
 */
@Composable
fun FluidDotNavigationBar(
    pagerState: PagerState,
    steps: List<String>,
    onDotClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .height(52.dp)
            .widthIn(max = 340.dp)
            .border(1.dp, LightBorder, CircleShape),
        shape = CircleShape,
        color = SurfaceVariant.copy(alpha = 0.88f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                steps.forEachIndexed { index, stepName ->
                    val isSelected = pagerState.currentPage == index
                    val animatedWidth by animateDpAsState(
                        targetValue = if (isSelected) 26.dp else 10.dp,
                        animationSpec = spring(
                            stiffness = Spring.StiffnessLow,
                            dampingRatio = Spring.DampingRatioNoBouncy
                        ),
                        label = "dot_width_animation"
                    )

                    val interactionSource = remember { MutableInteractionSource() }

                    Box(
                        modifier = Modifier
                            .size(width = 44.dp, height = 44.dp)
                            .semantics {
                                contentDescription = "Step ${index + 1}: ${stepName.toBrandLowercase()}, ${if (isSelected) "selected" else "not selected"}"
                            }
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null
                            ) { onDotClick(index) },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(width = animatedWidth, height = 10.dp)
                                .background(
                                    color = if (isSelected) AccentGreen else Border,
                                    shape = CircleShape
                                )
                        )
                    }
                }
            }

            if (steps.isNotEmpty() && pagerState.currentPage < steps.size) {
                val activeStepName = steps[pagerState.currentPage]
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = activeStepName.toBrandLowercase(),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = RobotoMono,
                        fontSize = 11.sp,
                        color = TextPrimary
                    )
                )
            }
        }
    }
}
