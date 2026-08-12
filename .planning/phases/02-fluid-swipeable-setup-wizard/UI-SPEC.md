# Phase 2 UI Design Contract — Fluid Swipeable Setup Wizard

**Milestone:** v2 (Fluid Wizard & Agency-Grade UI/UX Overhaul)
**Phase:** 02 — Fluid Swipeable Setup Wizard
**Status:** Approved & Locked
**Date:** 2026-08-12

---

## 1. Screen Architecture (`FluidWizardScreen`)

Consolidates standalone setup screens into a unified, gesture-driven `HorizontalPager` wizard framed between brand chrome headers and footers.

### Layout Hierarchy
```
RvmHeaderBar (Top App Bar, 56.dp)
└── FluidWizardScreen (HorizontalPager Container)
    ├── Step 01: Google Drive Auth & Target Folder Grid
    ├── Step 02: Camera Tethering & Live Pulse Aura
    └── Step 03: Session Preferences & Launch CTA
FluidDotNavigationBar (Floating Glass Pill, 52.dp)
RvmFooterBar (Clickable Web Intent Footer, 44.dp)
```

### Swiping & Gesture Lock Mechanics
- **Forward Swipe Lock:** Forward swiping to Step 2/3 is disabled (`userScrollEnabled = false`) until Step 1 validation passes (Google Drive OAuth signed in & target folder selected).
- **Backward Swiping:** Backward swiping is always enabled (`userScrollEnabled = true`).
- **Pill Alert Feedback:** Force-swipe attempts trigger a floating warning pill (`"selesaikan otentikasi & pilih folder terlebih dahulu"`, background `#F0EBE0`, text `#7A1A1A`, `RobotoMono` 11.sp).

---

## 2. Step Specifications

### 2.1 Step 01: Google Drive Auth & Target Folder Card Grid (`01 google drive`)

#### Card A: OAuth Connection Status
- **Container:** `DoppelrandCard` (Outer `#F0EBE0` 24.dp, Inner `#F6F3EB` 18.dp).
- **Header:** `"otentikasi google drive"` in `CormorantGaramond` (`20.sp`, Bold, `TextPrimary`).
- **Status Badge:** Pill badge displaying connection state (`"connected"` in `AccentGreen` `#2D4636`, or `"sign in required"` in `AccentBurgundy` `#7A1A1A`).
- **Action:** `KineticButton` labeled `"masuk akun google drive"` with trailing arrow icon `↗`.

#### Card B: Folder Browser (2-Column Card Grid Layout)
- **Container:** `DoppelrandCard` hosting folder browser.
- **Breadcrumb Header:** Current directory path display (`/root/Shoots/...`) rendered in `RobotoMono` (`11.sp`, `TextMutedLight`).
- **2-Column Card Grid:** `LazyVerticalGrid(columns = GridCells.Fixed(2))` displaying directory items as folder cards:
  - **Folder Card Style:** `DoppelrandCard` with outer radius `16.dp`, inner radius `12.dp`, outer gap `4.dp`.
  - **Visual Elements:** Folder vector icon, folder name (`DMSans`, `14.sp`, Bold, `TextPrimary`), item count caption (`RobotoMono`, `11.sp`, `TextMutedLight`).
  - **Selected State:** Border stroke `2.dp` in `AccentGreen` (`#2D4636`), background wash `AccentGreenSubtle`.
  - **Touch Target:** Card height $\ge 68.dp$ (exceeds $44.dp$ minimum).
  - **TalkBack Semantics:** `contentDescription = "Folder ${folderName}, ${itemCount} items, ${if (isSelected) "selected" else "not selected"}"`.
- **Breadcrumb Navigation & New Folder Action:**
  - Up 1 level button (`"← naik 1 folder"`).
  - Create folder CTA (`"buat folder baru"`) triggering custom RVM `AlertDialog`.

---

### 2.2 Step 02: Camera Tethering & Live Pulse Aura (`02 camera tethering`)

#### Card A: Live USB/MTP Status Aura Card
- **Container:** `DoppelrandCard`.
- **Status Aura:** `.dot-pulse` indicator ($8.dp$ circle with scale pulse $1.0 \rightarrow 2.2$ over $1.6s$).
  - Connected: `AccentOlive` (`#56642B`).
  - Searching / Disconnected: `AccentBurgundy` (`#7A1A1A`).
- **Camera Model Title:** Auto-detected camera model name in `CormorantGaramond` (`22.sp`, Bold, `TextPrimary`), e.g., `Canon EOS R5` or `FUJIFILM X-T5`.
- **DCIM Path Scan Preview:** Monospace media preview line (`RobotoMono`, `11.sp`, `TextMutedLight`), e.g., `media path: /DCIM/100FUJI/ (148 photos found)`.

#### Card B: Fallback Manual Import Mode
- **Container:** `DoppelrandCard`.
- **Title:** `"mode impor manual"` in `CormorantGaramond` (`18.sp`, Bold).
- **Action:** Button labeled `"pilih folder impor lokal (SAF)"`.

---

### 2.3 Step 03: Session Preferences & Launch Action (`03 session rules`)

#### Card A: Tethering Rules & Baseline Extraction
- **Container:** `DoppelrandCard`.
- **Toggles & Preferences:**
  - RAW+JPG co-existence handling toggle.
  - Auto-sync real-time upload toggle.
  - File baseline extraction preview (`RobotoMono`, `11.sp`), e.g., `starting baseline: DSCF0018.JPG -> #18`.

#### Card B: Launch Sesi Action
- **CTA Button:** `KineticButton` labeled `"mulai sesi tethering"` with trailing arrow icon `↗` and `AccentGreen` (`#2D4636`) background.
- **Click Action:** Initializes session orchestrator and navigates to Live Session Hub.

---

## 3. Accessibility & Quality Checklist (WIZARD-01..04)

- [x] **WIZARD-01 (Pager Container):** Single `HorizontalPager` with 3 steps, forward swipe lock when Step 1 is invalid.
- [x] **WIZARD-02 (Drive Auth & Folder Grid):** OAuth status badge, 2-column folder card grid layout ($\ge 44.dp$ touch targets), new folder creation dialog.
- [x] **WIZARD-03 (Camera Tethering Card):** Live pulse aura (`#56642B` / `#7A1A1A`), auto-detected camera model title, DCIM subfolder scan preview.
- [x] **WIZARD-04 (Preferences & Kinetic CTA):** RAW+JPG toggle, baseline preview, `KineticButton` launch CTA.
- [x] **Accessibility & Microcopy:** All UI strings forced to lowercase (acronym whitelist `MTP`, `USB`, `RAW`, `JPG`, `OAuth`), WCAG AAA contrast ratio ($\ge 7:1$), TalkBack semantics for folder cards.
