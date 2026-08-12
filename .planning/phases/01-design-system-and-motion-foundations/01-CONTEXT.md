# Phase 1 Context & Decisions — Design System & Motion Foundations

**Milestone:** v2 (Fluid Wizard & Agency-Grade UI/UX Overhaul)
**Phase:** 01 — Design System & Motion Foundations
**Date:** 2026-08-12

## Locked Design Decisions

### 1. Header Brand Identity (`RvmHeaderBar`)
- **Layout:** Minimalist top app bar container with `#FAF8F3` ground background.
- **Brand Logo:** `rvmeur` serif logo on top-left rendered in `Cormorant Garamond` (`#26170C`).
- **Eyebrow Tag:** Lowercase tag `"quiet luxury tethering v1.0"` in `Roboto Mono` (`11dp`, `#81756E`).
- **Status Pulse:** Top-right `.dot-pulse` indicator (Olive `#56642B` for active connection, Burgundy `#7A1A1A` for alert).

### 2. Clickable Elegant Footer (`RvmFooterBar`)
- **Layout:** Fixed bottom footer anchored below the `FluidDotNavigationBar`.
- **Credits Text:** Monospace credit text in `Roboto Mono` (`11dp`, `#81756E`): `rian-ism visual memories • rvmeur.com ↗`.
- **Interactive Web Intent:** Tapping the footer triggers an Android `Intent.ACTION_VIEW` targeting `https://rvmeur.com` to launch the live website in the user's browser.
- **Visual Accent:** Integrated `#2D4636` status dot next to the website URL.

### 3. Fluid Dynamic Dot Navigation Bar (`FluidDotNavigationBar`)
- **Container Style:** Floating glass pill (`detached nav`) elevated above the screen bottom and footer.
- **Morphing Dot Mechanics:** Animated active dot (`#2D4636` Emerald) that morphs and stretches based on `PagerState` swipe offsets, driven by Compose `spring(stiffness = Spring.StiffnessLow, dampingRatio = Spring.DampingRatioNoBouncy)`.
- **Step Label Format:** Monospace step indicator text (e.g. `01 google drive`, `02 camera tethering`, `03 session rules`).
- **Gestures:** Supports direct dot tap gestures for smooth page jumping.

### 4. Double-Bezel Container (`DoppelrandCard`)
- **Outer Shell:** Surface container (`#F0EBE0`) framed with a `1dp` hairline border (`#E8E0D8`) and `24.dp` outer radius.
- **Inner Core:** Content surface (`#F6F3EB`) with concentric `18.dp` inner radius.

### 5. Kinetic Button Physics (`KineticButton`)
- **Press Motion:** Kinetically scales down to `0.97f` on press state.
- **Button-in-Button Icon:** Trailing icon enclosed inside a circular pill wrapper (`w-8 h-8 rounded-full bg-black/5 flex items-center justify-center`).

---

## Technical Constraints & Guidelines
- All UI text strings follow the strict **all-lowercase** brand rule (except technical acronyms `MTP`, `USB`, `RAW`, `JPG`, `OAuth`).
- Color tokens match `rvmeur.com` specification:
  - Ground: `#FAF8F3`
  - Card: `#F6F3EB`
  - Primary: `#26170C`
  - Muted: `#4F453F`
  - Border: `#D2C4BC`
  - Accent Green: `#2D4636`
  - Accent Olive: `#56642B`
  - Accent Burgundy: `#7A1A1A`
