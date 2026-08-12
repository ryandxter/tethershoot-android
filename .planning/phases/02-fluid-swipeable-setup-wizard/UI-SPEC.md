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
- **Pill Alert Feedback:** Force-swipe attempts trigger a floating warning pill (`"selesaikan otentikasi & pilih folder terlebih dahulu"`, background `#F0EBE0`, text `#7A1A1A`, `RobotoMono` 11.sp, `LiveRegionMode.Polite`).

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
  - **Touch Target & Dynamic Height:** Card height $\ge 68.dp$ (`heightIn(min = 68.dp)` with `wrapContentHeight()` for 200% font scaling).
  - **TalkBack Semantics:** Merged semantics (`mergeDescendants = true`), `role = Role.RadioButton`, `isSelected` state description (`"terpilih"` / `"tidak terpilih"`), `contentDescription = "Folder ${folder.name}, ${folder.itemCount} item"`, `onClickLabel = "pilih folder ${folder.name}"`.
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
- **TalkBack Region:** Declares `LiveRegionMode.Polite` for camera connection announcements.

#### Card B: Fallback Manual Import Mode
- **Container:** `DoppelrandCard`.
- **Title:** `"mode impor manual"` in `CormorantGaramond` (`18.sp`, Bold).
- **Action:** Button labeled `"pilih folder impor lokal (SAF)"`.

---

### 2.3 Step 03: Session Preferences & Launch Action (`03 session rules`)

#### Card A: Tethering Rules & Baseline Extraction
- **Container:** `DoppelrandCard`.
- **Toggles & Preferences:**
  - RAW+JPG co-existence handling toggle (min touch target $48.dp$).
  - Auto-sync real-time upload toggle (min touch target $48.dp$).
  - File baseline extraction preview (`RobotoMono`, `11.sp`), e.g., `starting baseline: DSCF0018.JPG -> #18`.

#### Card B: Launch Sesi Action
- **CTA Button:** `KineticButton` labeled `"mulai sesi tethering"` with trailing arrow icon `↗` and `AccentGreen` (`#2D4636`) background.
- **Click Action:** Initializes session orchestrator and navigates to Live Session Hub.

---

## 3. Accessibility & Quality Verification Checklist (WIZARD-01..04)

### 3.1 Minimum 44dp Touch Target Contract
- [x] **Dot Bar Navigation Nodes:** Every step dot node wrapped in a minimum $44.dp \times 44.dp$ interactive touch target container.
- [x] **OAuth & Primary Buttons:** `KineticButton` elements enforce minimum height of $52.dp$ ($\ge 44.dp$).
- [x] **2-Column Folder Cards:** Grid folder cards enforce visual and touch height $\ge 68.dp$ with $8.dp$ spacing between adjacent cards.
- [x] **Breadcrumbs & Dialog Controls:** Up-1-level button, path segments, and dialog action buttons padded to minimum $44.dp \times 44.dp$.
- [x] **Preference Switches & Steppers:** RAW+JPG and auto-sync toggles wrapped in `Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)`.

### 3.2 2-Column Card Grid Scaling & Dynamic Text Contract
- [x] **Responsive Grid Layout:** `LazyVerticalGrid(columns = GridCells.Fixed(2))` on portrait mobile screens, adapting to `GridCells.Adaptive(minSize = 160.dp)` on landscape/tablet viewports ($W \ge 600.dp$).
- [x] **Dynamic Font Scaling (Up to 200% / 2.0x):** Folder card containers use `heightIn(min = 68.dp)` + `wrapContentHeight()` without hardcoded heights to prevent vertical text clipping.
- [x] **Text Ellipsis & Overflow:** Folder titles (`DMSans` $14.sp$, Bold) configured with `maxLines = 2` and `TextOverflow.Ellipsis`; item count captions (`RobotoMono` $11.sp$) configured with `maxLines = 1`.

### 3.3 RVM Microcopy & Lowercase Rule Contract
- [x] **All-Lowercase Microcopy:** 100% of visible UI strings (headers, card labels, buttons, dialog messages, status pills) use strictly all-lowercase text.
- [x] **Acronym Whitelist Enforcement:** Only technical acronyms (`OAuth`, `MTP`, `USB`, `RAW`, `JPG`, `SAF`, `DCIM`, `RVM`) retain standard uppercase casing.

### 3.4 TalkBack Screen Reader & Folder Card Semantics Contract
- [x] **Merged Card Semantics:** Each folder card uses `Modifier.semantics(mergeDescendants = true)` so TalkBack reads the card as a single focusable item rather than individual child elements.
- [x] **Selection State & Role:** Folder cards expose `role = Role.RadioButton` (or `Role.Button`) with `selected = isSelected` state and `stateDescription = if (isSelected) "terpilih" else "tidak terpilih"`.
- [x] **Informative Description Format:** Folder card `contentDescription = "Folder ${folderName}, ${itemCount} item"`.
- [x] **Custom Action Labels:** Folder cards configure `onClickLabel = "pilih folder ${folderName}"` for natural screen reader prompt *"Ketuk dua kali untuk memilih folder"*.
- [x] **Live Region Announcements:** Warning feedback pills, folder navigation level changes, and USB/MTP status changes declare `LiveRegionMode.Polite` for real-time TalkBack feedback without focus disruption.
