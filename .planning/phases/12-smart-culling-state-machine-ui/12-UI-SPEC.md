# UI Design Contract: Phase 12 — Smart Culling State Machine, Room Migration & Quiet Luxury Studio UI

**Phase:** 12  
**Design System:** `rvmeur.com` Quiet Luxury Design Language  
**Target Screens:** `Step3SessionRules.kt`, `UploadedGalleryScreen.kt`, `CameraGalleryScreen.kt`, `DoppelrandCard.kt`, `ExposureInspectionModal.kt`  
**Status:** Approved & Locked  
**Date:** 2026-08-20  

---

## 1. Visual Token Architecture

| Token | Hex / Value | Semantic Role |
| :--- | :--- | :--- |
| **`Ground`** | `#FAF8F3` | Primary warm cream canvas & scaffold background |
| **`CardSurface`** | `#F6F3EB` | Inner surface for concentric `DoppelrandCard` |
| **`SurfaceVariant`** | `#EDE8DC` | Outer bezel & filter chip container background |
| **`TextPrimary`** | `#26170C` | High-contrast dark espresso typography |
| **`TextMuted`** | `#6E6259` | Secondary captions & body typography |
| **`TextMutedLight`** | `#A89F91` | EXIF metadata & subtle labels |
| **`AccentGreen`** | `#2D4636` | Primary brand accent & positive states (`OPTIMAL`) |
| **`AccentAmber`** | `#B8860B` | Warning badge for flash misfires (`UNDEREXPOSED`) |
| **`AccentBurgundy`** | `#8B0000` | Warning badge for blown highlights (`OVEREXPOSED`) |
| **`ModalScrim`** | `Color(0xEE12100E)` | Semi-transparent deep dark inspection backdrop |

---

## 2. Typography Hierarchy

- **Title / Display:** `CormorantGaramond` (22sp–28sp, Medium/Bold, lowercase brand styling).
- **Body / Interface Labels:** `DMSans` (13sp–15sp, Normal/Medium).
- **HUD, Telemetry & Status Badges:** `RobotoMono` (9sp–11sp, uppercase letter-spacing +0.5sp).

---

## 3. Component Design Specifications

### A. Concentric `DoppelrandCard` Status Badges (`UI-01`)
- **Geometry:** 2dp outer bezel, 2dp gap padding, 8dp inner corner radius.
- **Badge Position:** `Alignment.TopStart` on thumbnail cards.
- **Pill Dimensions:** Horizontal padding `5.dp`, vertical padding `2.dp`, corner radius `4.dp`.
- **Badge Content:**
  - Underexposed: `UNDER` badge in `#B8860B` with white text.
  - Overexposed: `BLOWN` badge in `#8B0000` with white text.
  - Culled/Skipped: Subtle `#6E6259` border with strikethrough icon or `LEWAT` badge.

### B. Responsive Gallery Filter Row (`UI-04`)
- **Pills:** Horizontal scrollable `LazyRow` with rounded pill chips (`CircleShape`).
- **Modes:**
  - `semua (X)`: Default showing all media items.
  - `optimal (Y)`: Showing approved photos.
  - `terpangkas (Z)`: Filter showing photos flagged with exposure warnings.
  - `dilewati (W)`: Filter showing culled photos skipped from Google Drive upload.
- **Active State:** Background `AccentGreen`, Text `Ground`.
- **Inactive State:** Background `SurfaceVariant`, Text `TextPrimary`.

### C. 1-Tap Kinetic Force-Upload Override Button (`UI-02`)
- **Action:** Single-tap kinetic button on media card or swipe gesture.
- **Physics:** Scale down to 0.96x on press with 120ms spring damping curve.
- **Feedback:** Tactile haptic feedback (`HapticFeedbackType.LongPress`) on trigger.
- **State Transition:** Card badge updates immediately from `UNDER`/`BLOWN` to `MEMPROSES UNGGAH` (Processing Upload).

### D. Full-Screen Dark Modal Inspection Viewer (`UI-03`)
- **Background:** Dark frosted glass overlay (`Color(0xEE12100E)`).
- **Center Image:** High-fidelity preview with pinch-to-zoom and double-tap zoom reset.
- **Bottom HUD Overlay:**
  - Real-time 256-bin luminance histogram curve drawn via Compose `Canvas` with smooth Bézier interpolation.
  - 4-Stat EXIF telemetry strip: `ISO`, `Shutter Speed`, `Aperture`, `Camera Profile`.
  - Prominent `KineticButton` labeled `"paksa unggah ke google drive"` with `AccentGreen` container.

---

## 4. Accessibility & Microinteractions
- Touch target size: minimum 48x48 dp for all interactive buttons and filter chips.
- Semantic descriptions: `semantics { contentDescription = "Foto $fileName, status eksposur $verdict, ketuk untuk inspeksi" }`.
- Zero frame drops: All histogram path computations memoized or calculated off-RenderThread.
