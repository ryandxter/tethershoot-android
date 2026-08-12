# Phase 2 Research: UI Style Refactoring (rvmeur.com Design)

## Context & Objective
The goal is to align the visual identity of the app with the extracted styling guidelines and tokens of `rvmeur.com`.

## Extracted Design Tokens
Based on the research of `rvmeur.com`:
- **Background (`CreamBackground`):** `#FAF8F3` (warm light sand)
- **Primary / Typography (`TextPrimary`):** `#26170C` (deep warm dark brown)
- **Surface / Card (`CardSurface`):** `#F6F3EB` (soft warm card panel)
- **Surface Variant (`SurfaceVariant`):** `#F0EBE0` (warm sidebar background)
- **Border / Divider:**
  - Soft: `#E8E0D8`
  - Medium: `#D2C4BC`
- **Accent Green:** `#2D4636` (Deep forest green, used for primary action buttons, success states, and key callouts)
- **Accent Olive:** `#56642B` (Secondary accents)
- **Typography pairing:**
  - Serif headers: Mapped to Compose `FontFamily.Serif` (representing *Cormorant Garamond*)
  - Sans body/labels: Mapped to Compose `FontFamily.SansSerif` (representing *DM Sans*)

## Implementation Plan

1. **Update `Color.kt`:**
   - Define exact hexadecimal color mappings for all new design system tokens.

2. **Update `Theme.kt`:**
   - Map new tokens to `MaterialTheme`'s `ColorScheme` properties (e.g., `primary`, `background`, `surface`, `outline`).

3. **Update `Type.kt`:**
   - Map `display`, `headline`, and `title` text styles to `FontFamily.Serif` with custom font weights.
   - Map `body` and `label` text styles to `FontFamily.SansSerif` / `FontFamily.Default`.
