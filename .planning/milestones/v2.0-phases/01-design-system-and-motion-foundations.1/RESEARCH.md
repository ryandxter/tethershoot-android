# Phase 1 Technical Research — Design System & Motion Foundations

**Milestone:** v2 (Fluid Wizard & Agency-Grade UI/UX Overhaul)
**Phase:** 01 — Design System & Motion Foundations
**Date:** 2026-08-12

---

## 1. Codebase Architecture & File Mapping

### Existing Theme Files (`app/src/main/java/com/rvmeur/transferin/ui/theme/`)
* **`Color.kt`**: Contains color definitions. Will be updated with exact RVMQuiet Luxury tokens (`Ground`, `CardSurface`, `SurfaceVariant`, `TextPrimary`, `TextMuted`, `TextMutedLight` `#6E625B`, `Border`, `LightBorder`, `AccentGreen`, `AccentOlive`, `AccentBurgundy`).
* **`Type.kt`**: Contains typography configuration mapping `CormorantGaramond` (Serif Display), `DMSans` (Body/Controls), and `RobotoMono` (Metrics/Step numbers/Footer).
* **`Theme.kt`**: Wraps `MaterialTheme` and provides `TransferInTheme`.

### Target Component Files (`app/src/main/java/com/rvmeur/transferin/ui/components/`)
1. **`DoppelrandCard.kt`**: Double-Bezel nested container composable.
2. **`KineticButton.kt`**: Tactile press physics button with button-in-button trailing icon.
3. **`FluidDotNavigationBar.kt`**: Floating glass pill navigation bar with morphing dot indicators linked to `PagerState`.
4. **`RvmHeaderBar.kt`**: Brand header bar with `rvmeur` serif logo and animated status pulse.
5. **`RvmFooterBar.kt`**: Clickable web intent footer (`rvmeur.com ↗`) with haptic feedback.

---

## 2. Technical Component Implementation Patterns

### 2.1 Double-Bezel Container (`DoppelrandCard.kt`)
```kotlin
@Composable
fun DoppelrandCard(
    modifier: Modifier = Modifier,
    outerShape: Shape = RoundedCornerShape(24.dp),
    innerShape: Shape = RoundedCornerShape(18.dp),
    outerColor: Color = SurfaceVariant,
    innerColor: Color = CardSurface,
    borderColor: Color = LightBorder,
    contentPadding: PaddingValues = PaddingValues(20.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.border(1.dp, borderColor, outerShape),
        shape = outerShape,
        color = outerColor
    ) {
        Box(modifier = Modifier.padding(6.dp)) {
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
```

### 2.2 Kinetic Button (`KineticButton.kt`)
```kotlin
@Composable
fun KineticButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = TextPrimary,
    contentColor: Color = Ground,
    trailingIcon: ImageVector? = Icons.Default.ArrowForward,
    enabled: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1.0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumHigh)
    )

    Surface(
        onClick = onClick,
        modifier = modifier
            .height(52.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        enabled = enabled,
        shape = CircleShape,
        color = containerColor,
        contentColor = contentColor,
        interactionSource = interactionSource
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = text.lowercase(),
                style = MaterialTheme.typography.labelLarge,
                fontFamily = DMSansFontFamily
            )
            if (trailingIcon != null) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(contentColor.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = trailingIcon,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = contentColor
                    )
                }
            }
        }
    }
}
```

### 2.3 Fluid Dynamic Dot Navigation Bar (`FluidDotNavigationBar.kt`)
```kotlin
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
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            steps.forEachIndexed { index, title ->
                val isSelected = pagerState.currentPage == index
                val animatedWidth by animateDpAsState(
                    targetValue = if (isSelected) 26.dp else 10.dp,
                    animationSpec = spring(stiffness = Spring.StiffnessLow, dampingRatio = Spring.DampingRatioNoBouncy)
                )

                Box(
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .size(width = animatedWidth, height = 10.dp)
                        .background(
                            color = if (isSelected) AccentGreen else Border,
                            shape = CircleShape
                        )
                        .clickable { onDotClick(index) }
                )
            }
        }
    }
}
```

### 2.4 Clickable Web Intent Footer (`RvmFooterBar.kt`)
```kotlin
@Composable
fun RvmFooterBar(
    modifier: Modifier = Modifier,
    webUrl: String = "https://rvmeur.com"
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(webUrl))
                context.startActivity(intent)
            },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(AccentGreen, CircleShape)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "rian-ism visual memories • rvmeur.com ↗",
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = RobotoMonoFontFamily,
                fontSize = 11.sp,
                color = TextMutedLight
            )
        )
    }
}
```

---

## 3. Verification & Build Constraints

1. **Compilation Check:** Code must build cleanly under `./gradlew compileDebugKotlin`.
2. **Design Tokens:** Strict adherence to color values in `Color.kt` and typography rules.
3. **Accessibility:** Touch targets $\ge 44.dp$, contrast ratios $\ge 4.5:1$ (AAA target 7:1+).
