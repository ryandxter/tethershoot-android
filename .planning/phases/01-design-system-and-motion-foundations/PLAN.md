# Phase 1 Execution Plan — Design System & Motion Foundations

**Milestone:** v2 (Fluid Wizard & Agency-Grade UI/UX Overhaul)
**Phase:** 01 — Design System & Motion Foundations
**Status:** Ready for Execution
**Date:** 2026-08-12

---

## Executive Summary

Implement the **Rian-Ism Visual Memories (RVM)** quiet luxury visual design system and motion foundations in Jetpack Compose. This includes updating brand design tokens (`Color.kt`, `Type.kt`, `Theme.kt`) and creating 5 agency-grade reusable UI components (`DoppelrandCard`, `KineticButton`, `FluidDotNavigationBar`, `RvmHeaderBar`, `RvmFooterBar`).

---

## Tasks

### Task 1: Refactor Brand Color Tokens & Typography System
- **Files to Modify:**
  - `app/src/main/java/com/rvmeur/transferin/ui/theme/Color.kt`
  - `app/src/main/java/com/rvmeur/transferin/ui/theme/Type.kt`
  - `app/src/main/java/com/rvmeur/transferin/ui/theme/Theme.kt`
- **Instructions:**
  1. Define exact RVM color tokens in `Color.kt`:
     - `Ground` (`#FAF8F3`), `CardSurface` (`#F6F3EB`), `SurfaceVariant` (`#F0EBE0`)
     - `TextPrimary` (`#26170C`), `TextMuted` (`#4F453F`), `TextMutedLight` (`#6E625B`)
     - `Border` (`#D2C4BC`), `LightBorder` (`#E8E0D8`)
     - `AccentGreen` (`#2D4636`), `AccentOlive` (`#56642B`), `AccentBurgundy` (`#7A1A1A`)
  2. Configure `Type.kt` dual-font setup with `CormorantGaramond`, `DMSans`, and `RobotoMono`.
  3. Map tokens into `MaterialTheme` inside `Theme.kt`.

### Task 2: Build Double-Bezel Card & Kinetic Press Button
- **Files to Create:**
  - `app/src/main/java/com/rvmeur/transferin/ui/components/DoppelrandCard.kt`
  - `app/src/main/java/com/rvmeur/transferin/ui/components/KineticButton.kt`
- **Instructions:**
  1. `DoppelrandCard`: Build concentric double-bezel container (Outer shell `#F0EBE0` radius `24.dp`, Inner core `#F6F3EB` radius `18.dp`, gap `6.dp`, padding `20.dp`).
  2. `KineticButton`: Build tactile press button with scale down animation (`0.97f`) using Compose `animateFloatAsState(targetValue, spring(stiffness = Spring.StiffnessMediumHigh))` and trailing icon circular wrapper (`32.dp × 32.dp`). Height strictly set to `52.dp`.

### Task 3: Build Navigation & Branding Components
- **Files to Create:**
  - `app/src/main/java/com/rvmeur/transferin/ui/components/FluidDotNavigationBar.kt`
  - `app/src/main/java/com/rvmeur/transferin/ui/components/RvmHeaderBar.kt`
  - `app/src/main/java/com/rvmeur/transferin/ui/components/RvmFooterBar.kt`
- **Instructions:**
  1. `FluidDotNavigationBar`: Build floating glass pill (`detached nav`, height `52.dp`, outer radius `26.dp`). Active dot (`#2D4636`) morphs dynamically ($10.dp \rightarrow 26.dp$) linked to `PagerState` swipe offset using `spring(stiffness = Spring.StiffnessLow, dampingRatio = Spring.DampingRatioNoBouncy)`. Dot touch targets expanded to $44.dp \times 44.dp$.
  2. `RvmHeaderBar`: Top app bar (height `56.dp`) with `rvmeur` serif logo, monospace eyebrow tag (`quiet luxury tethering v1.0`), and `.dot-pulse` status indicator.
  3. `RvmFooterBar`: Fixed bottom footer (height `44.dp`) with `rian-ism visual memories • rvmeur.com ↗` text (`11.sp`, `#6E625B`). Add click listener with haptic feedback executing Android `Intent.ACTION_VIEW` targeting `https://rvmeur.com`.

### Task 4: Compilation & Quality Verification
- **Instructions:**
  1. Run `./gradlew compileDebugKotlin` to verify clean compilation without Kotlin or Compose errors.
  2. Validate WCAG contrast and touch target specifications.

---

## Verification Criteria
- [ ] `./gradlew compileDebugKotlin` passes cleanly.
- [ ] All 5 new UI component files created under `com.rvmeur.transferin.ui.components`.
- [ ] Footer click listener launches Android browser intent to `https://rvmeur.com`.
- [ ] `FluidDotNavigationBar` morphs dot size with spring animation and accepts `PagerState`.
