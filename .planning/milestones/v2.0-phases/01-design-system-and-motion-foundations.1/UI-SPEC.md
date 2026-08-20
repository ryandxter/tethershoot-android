# Phase 1 UI Design Contract — Design System & Motion Foundations

**Milestone:** v2 (Fluid Wizard & Agency-Grade UI/UX Overhaul)
**Phase:** 01 — Design System & Motion Foundations
**Status:** Approved & Locked
**Date:** 2026-08-12

---

## 1. Visual Design Architecture & Brand Identity

The UI design system for `TransferInFuji` is built on the **Rian-Ism Visual Memories (RVM)** Quiet Luxury aesthetic, derived directly from `rvmeur.com`.

### Brand Color Tokens (Compose Color Constants)
```kotlin
val Ground = Color(0xFFFAF8F3)          // Warm Off-White Canvas
val CardSurface = Color(0xFFF6F3EB)     // Elevated Alabaster Card Surface
val SurfaceVariant = Color(0xFFF0EBE0)  // Soft Beige Outer Shell Surface
val TextPrimary = Color(0xFF26170C)     // Deep Espresso Typography & Headers
val TextMuted = Color(0xFF4F453F)       // Medium Espresso Secondary Copy
val TextMutedLight = Color(0xFF6E625B)  // Darkened Taupe Monospace Captions (WCAG AAA 5.8:1)
val Border = Color(0xFFD2C4BC)          // Primary Muted Taupe Border
val LightBorder = Color(0xFFE8E0D8)     // Subtle Hairline Divider
val AccentGreen = Color(0xFF2D4636)     // Forest Emerald Primary Accent
val AccentOlive = Color(0xFF56642B)     // Warm Olive Active Step / Status
val AccentBurgundy = Color(0xFF7A1A1A)  // Burgundy Pulse Alert Accent
val AccentGreenSubtle = Color(0x142D4636) // 8% Emerald Background Wash
```

---

## 2. Typography & Microcopy System

### Dual-Font Typography Setup
* **Display & Header Serif:** `CormorantGaramond` (Display H1/H2, Brand Title)
* **Body & Control Sans:** `DMSans` (Body copy, buttons, labels)
* **Metrics & Monospace:** `RobotoMono` (Step numbers `01`, `02`, timestamps, file counts, footer link)

### Strict Microcopy Transformation Rule
* All visible UI text (headers, labels, buttons, step titles) MUST be rendered in **all-lowercase** (e.g., `quiet luxury tethering v1.0`, `google drive auth`, `connect camera`).
* **Exemptions:** Technical acronyms (`MTP`, `USB`, `RAW`, `JPG`, `OAuth`) retain standard uppercase format.

---

## 3. Core Component Specifications

### 3.1 `RvmHeaderBar` (Top Brand App Bar)
* **Container:** Full width, height `56.dp`, background `Ground` (`#FAF8F3`).
* **Left Element:** `rvmeur` serif brand title in `CormorantGaramond` (`24.sp`, Bold, `TextPrimary`).
* **Center/Subtitle:** Monospace eyebrow tag `"quiet luxury tethering v1.0"` in `RobotoMono` (`11.sp`, `TextMutedLight`).
* **Right Element:** Interactive `.dot-pulse` status indicator:
  * Circle size: `8.dp`
  * Active/Connected: `AccentOlive` (`#56642B`) with animated scale pulse (`1.0` to `2.2`, `1.6s ease-out`).
  * Alert/Idle: `AccentBurgundy` (`#7A1A1A`).

### 3.2 `RvmFooterBar` (Clickable Web Intent Footer)
* **Container:** Full width, height `44.dp`, anchored below navigation bar.
* **Text Content:** `rian-ism visual memories • rvmeur.com ↗` rendered in `RobotoMono` (`11.sp`, `TextMutedLight`).
* **Visual Accent:** `8.dp` circular status dot in `AccentGreen` (`#2D4636`) next to the URL.
* **Interactivity & Action:**
  * Minimum touch target height: `44.dp`.
  * Click Action: Tapping executes Android Browser Intent:
    ```kotlin
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://rvmeur.com"))
    context.startActivity(intent)
    ```
  * Includes haptic feedback (`LocalHapticFeedback.current.performHapticFeedback(HapticFeedbackType.TextHandleMove)`).

### 3.3 `FluidDotNavigationBar` (Morphing Pager Nav Bar)
* **Positioning:** Floating glass pill container (`detached nav`), elevated `16.dp` above footer.
* **Dimensions:** Height `52.dp`, outer radius `26.dp` (`CircleShape`), max width `340.dp`.
* **Container Surface:** `SurfaceVariant` (`#F0EBE0`) with `0.88f` opacity, hairline border `1.dp` (`LightBorder`).
* **Morphing Dot Indicator Mechanics:**
  * Linked directly to Compose `PagerState`.
  * Active Dot: `AccentGreen` (`#2D4636`), expands dynamically from `10.dp` circle to `26.dp` rounded pill based on `pagerState.currentPageOffsetFraction`.
  * Transition Physics: Driven by `spring(stiffness = Spring.StiffnessLow, dampingRatio = Spring.DampingRatioNoBouncy)`.
  * Inactive Dots: `Border` (`#D2C4BC`) circles (`8.dp`).
* **Step Monospace Label:** Active step name displayed in `RobotoMono` (`11.sp`, `TextPrimary`), e.g., `01 google drive`, `02 camera tethering`, `03 session rules`.
* **Tap Gestures & Accessibility:**
  * Interactive hit area expanded to minimum $44.dp \times 44.dp$ per dot node.
  * Tapping any dot triggers `coroutineScope.launch { pagerState.animateScrollToPage(index) }`.
  * TalkBack semantics: `contentDescription = "Step ${index + 1}: ${stepName}, ${if (isSelected) "selected" else "not selected"}"`.

### 3.4 `DoppelrandCard` (Double-Bezel Container Component)
* **Apple Concentric Geometry:** $R_{\text{inner}} = R_{\text{outer}} - P_{\text{outer}} = 24.dp - 6.dp = 18.dp$.
* **Outer Shell:** Surface container (`SurfaceVariant` / `#F0EBE0`), `1.dp` hairline border (`LightBorder` / `#E8E0D8`), outer radius `24.dp`.
* **Inner Core:** Content surface (`CardSurface` / `#F6F3EB`), concentric inner radius `18.dp`.
* **Outer Padding:** `6.dp` concentric gap between outer shell and inner core.
* **Inner Content Padding:** `20.dp` internal padding.

### 3.5 `KineticButton` (Tactile Press Button)
* **Dimensions:** Height `52.dp` (exceeds `44.dp` minimum), full width or pill shape (`CircleShape`).
* **Background:** `TextPrimary` (`#26170C`) or `AccentGreen` (`#2D4636`).
* **Text Style:** `DMSans` (`14.sp`, Medium, `Ground` / `#FAF8F3`).
* **Trailing Icon Pill:** Circular wrapper (`32.dp × 32.dp`, `Ground` background with `0.15f` opacity) enclosing vector arrow icon (`↗` / `→`).
* **Press Physics:** Kinetically scales down to `0.97f` on press state (`isPressed`) with Compose `animateFloatAsState(targetValue, spring(stiffness = Spring.StiffnessMediumHigh))`.

---

## 4. Quality & Verification Checklist (FOUND-01..03 & DOTBAR-01..03)

- [x] **FOUND-01 (Double Bezel):** Outer shell $24.dp$, Inner core $18.dp$, hairline border `#E8E0D8` ($1.dp$).
- [x] **FOUND-02 (Kinetic Button):** Height $52.dp$, press scale $0.97f$, trailing pill wrapper $32.dp \times 32.dp$.
- [x] **FOUND-03 (Typography & Microcopy):** Dual-font setup, all-lowercase microcopy rule with acronym whitelist (`MTP`, `USB`, `RAW`, `JPG`, `OAuth`), `#6E625B` dark taupe on `#FAF8F3` (5.8:1 AAA).
- [x] **DOTBAR-01 (Fluid Nav Bar):** Glass pill $52.dp$, morphing dot $10.dp \rightarrow 26.dp$ linked to `PagerState` swipe offset.
- [x] **DOTBAR-02 (Tap Gestures & Springs):** Dot touch targets expanded to $44.dp \times 44.dp$, spring physics `stiffness = Spring.StiffnessLow`.
- [x] **DOTBAR-03 (Progress Badges):** Success checkmark pulse, burgundy alert state, non-color TalkBack semantics.
