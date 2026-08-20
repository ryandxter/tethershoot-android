# Phase 3 Visual UI Specification Contract — Live Shoot Dashboard & Gallery Polish

**Project:** TransferInFuji  
**Milestone:** v2 (Fluid Wizard & Agency-Grade UI/UX Overhaul)  
**Phase:** 03 — Live Shoot Dashboard & Gallery Polish  
**Date:** 2026-08-12  
**Status:** Approved & Locked  

---

## 1. Executive Summary

Phase 3 overhauls the active live tethering dashboard (`SessionActiveScreen`), uploaded media gallery (`UploadedGalleryScreen`), and session summary screen into agency-grade, quiet-luxury interfaces aligned with the `rvmeur.com` visual identity. 

It replaces standard Material buttons, square cards, and plain lists with double-bezel containers (`DoppelrandCard`), real-time status aura indicators (`.dot-pulse`), monospace metrics, responsive multi-column thumbnail grids with aspect ratio preservation, and a full-screen image viewer modal.

---

## 2. Component & Screen Architecture

### 2.1 Live Session Hub (`SessionActiveScreen`)

Framed by `RvmHeaderBar` (top chrome) and `RvmFooterBar` (bottom chrome).

```
RvmHeaderBar ("quiet luxury tethering v1.0" + live .dot-pulse status)
└── Live Session Hub Container
    ├── Card 1: Camera Status & Connection Mode Header
    │   ├── Live .dot-pulse indicator (#56642B connected / #7A1A1A searching)
    │   ├── Active device title (CormorantGaramond, 22.sp, Bold)
    │   └── Status text & mode badge (RobotoMono, 11.sp, TextMutedLight)
    ├── Card 2: 4-Stat Metric Grid (2-Column x 2-Row DoppelrandCard Grid)
    │   ├── "pending" count pill (#6E625B)
    │   ├── "uploading" count pill (#56642B)
    │   ├── "success" count pill (#2D4636)
    │   └── "failed" count pill (#7A1A1A)
    ├── Card 3: Live Transfer Stream & Progress Card
    │   ├── Active file transfer title & progress bar
    │   └── Monospace transfer speed & byte counter
    └── Card 4: Quick Action Bar
        ├── "buka galeri terunggah" (KineticButton, TextPrimary container)
        └── "selesaikan sesi tethering" (KineticButton, AccentBurgundy container)
RvmFooterBar (Clickable Web Intent -> https://rvmeur.com)
```

#### 4-Stat Metric Grid Design
- **Layout:** `LazyVerticalGrid(columns = GridCells.Fixed(2))` inside a `DoppelrandCard` container.
- **Card Sub-Items:** Each metric is rendered inside an inner `DoppelrandCard` (Outer radius `16.dp`, Inner radius `12.dp`, Outer gap `4.dp`).
- **Typography:**
  - Metric Value: `RobotoMono`, `28.sp`, Bold, `TextPrimary` (`#26170C`).
  - Metric Label: `DMSans`, `12.sp`, `TextMutedLight` (`#6E625B`) in brand lowercase (`"pending"`, `"uploading"`, `"success"`, `"failed"`).

---

### 2.2 Uploaded Gallery & Preview (`UploadedGalleryScreen`)

Replaces raw grid views with a responsive media gallery and full-screen viewer.

#### Gallery Screen Architecture
- **Header:** Branded title `"galeri terunggah"` (`CormorantGaramond`, `22.sp`) + total media count pill (`RobotoMono`, `11.sp`).
- **Filter Chips Row:** `LazyRow` filter pills (`"semua"`, `"foto"`, `"video"`, `"berhasil"`, `"gagal"`) styled in RVM quiet luxury tokens (`SurfaceVariant` `#F0EBE0` unselected, `AccentGreen` `#2D4636` selected).
- **Responsive Media Grid:** `LazyVerticalGrid(columns = GridCells.Fixed(3))` displaying thumbnails:
  - **Thumbnail Container:** `DoppelrandCard` (Outer radius `12.dp`, Inner radius `8.dp`, aspect ratio `1:1`).
  - **Overlays:** Monospace file extension badge (`JPG`, `RAW`) in top-left, transfer status icon in top-right.
  - **TalkBack Semantics:** `contentDescription = "Gambar ${fileName}, status ${statusText}, ketuk untuk pratinjau"`.

#### Full-Screen Image Viewer Modal
- **Overlay Container:** Darkened backdrop (`Color(0xEE12100E)`).
- **Header Bar:** Monospace file name (`RobotoMono`, `13.sp`, `#FAF8F3`), file size, timestamp, and close button `✕`.
- **Image Preview Area:** High-resolution image viewport with gesture pinch-zoom support.
- **Bottom Action Bar:** `"unduh file"`, `"buka di google drive"`, `"hapus"`.

---

### 2.3 Session Summary Screen (`SessionSummaryScreen`)

Clean end-of-session summary view replacing standard dialogs.

- **Header:** `"ringkasan sesi tethering"` (`CormorantGaramond`, `24.sp`, Bold).
- **Session Stats Card:** `DoppelrandCard` displaying total files processed, total upload time, average transfer rate, and target Google Drive folder path.
- **Action CTA:** `KineticButton` labeled `"selesai & kembali ke setup"` with trailing arrow `↗` (`AccentGreen` `#2D4636` container).

---

## 3. RVM Design System Tokens

### Color Palette
- `Ground`: `#FAF8F3` (Canvas background)
- `CardSurface`: `#F6F3EB` (Elevated card surface)
- `SurfaceVariant`: `#F0EBE0` (Soft beige shell)
- `TextPrimary`: `#26170C` (Deep espresso typography)
- `TextMuted`: `#4F453F` (Dark taupe body copy)
- `TextMutedLight`: `#6E625B` (Darkened taupe captions, WCAG AAA 5.8:1)
- `AccentGreen`: `#2D4636` (Emerald primary accent)
- `AccentOlive`: `#56642B` (Olive active status)
- `AccentBurgundy`: `#7A1A1A` (Burgundy alert/failed state)
- `AccentGreenSubtle`: `#142D4636` (Subtle emerald wash)

### Typography Triad
- **Display Header:** `CormorantGaramond` (Serif, 18.sp - 28.sp, Bold)
- **Body & Controls:** `DMSans` (Sans-serif, 12.sp - 14.sp)
- **Metrics & Captions:** `RobotoMono` (Monospace, 11.sp - 28.sp)

### Microcopy Governance
- All UI labels **MUST** enforce the RVM **all-lowercase** rule via `.toBrandLowercase()`, with whitelist exemptions for technical acronyms: `MTP`, `USB`, `RAW`, `JPG`, `OAuth`, `SAF`, `DCIM`, `RVM`, `NAS`.

---

## 4. Verification Gate Checklist (DASHBOARD-01..02)

- [ ] **DASHBOARD-01 (Live Session Hub):** Real-time status aura card, 4-stat metric grid (`DoppelrandCard`), live queue stream card, and `KineticButton` action bar.
- [ ] **DASHBOARD-02 (Uploaded Gallery & Modal):** 3-column responsive thumbnail grid (`DoppelrandCard`), filter chip row, monospace metadata badges, and full-screen image viewer modal.
- [ ] **Accessibility & Semantics:** TalkBack descriptions for metric items and gallery thumbnails, touch targets $\ge 44.dp$, contrast ratio $\ge 7:1$.
- [ ] **Compilation Gate:** Clean build via `./gradlew compileDebugKotlin` with 0 errors.
