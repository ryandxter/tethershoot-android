# Phase 2 Context & Decisions — Fluid Swipeable Setup Wizard

**Milestone:** v2 (Fluid Wizard & Agency-Grade UI/UX Overhaul)
**Phase:** 02 — Fluid Swipeable Setup Wizard
**Date:** 2026-08-12

## Locked Design Decisions

### 1. Wizard Layout & Navigation Architecture (`FluidWizardScreen`)
- **Structure:** Single root `FluidWizardScreen` composable hosting `HorizontalPager` with 3 steps:
  - `Step 1`: Google Drive Auth & Target Folder (`01 google drive`)
  - `Step 2`: Camera Tethering & DCIM Detection (`02 camera tethering`)
  - `Step 3`: Session Preferences & Launch Rules (`03 session rules`)
- **Integration:** Framed between `RvmHeaderBar` at the top and `FluidDotNavigationBar` + `RvmFooterBar` at the bottom.

### 2. Step Validation & Gesture Rules
- **Forward Swipe Lock:** Forward swiping to Step 2/3 is disabled (`userScrollEnabled = false`) until Step 1 is valid (Google Drive signed in and target folder selected).
- **Backward Swiping:** Backward swiping to prior completed steps is always allowed (`userScrollEnabled = true`).
- **Pill Alert Feedback:** Attempting to force-swipe forward prematurely displays a subtle taupe/burgundy warning pill (`"selesaikan otentikasi & pilih folder terlebih dahulu"`).

### 3. Step 1: Google Drive Auth & Folder Card Grid Browser
- **OAuth Status Card:** OAuth2 credentials indicator and Google Drive connection status pill.
- **Folder Browser (Card Grid Layout):** Directories are displayed as a **2-column Card Grid** using `DoppelrandCard` folder items (featuring folder icon, directory name, item count, and active selection border), replacing traditional vertical lists.
- **Breadcrumb & Creation:** Includes parent directory navigation breadcrumb (`/root/...`) and a "buat folder baru" action button.

### 4. Step 2: Camera Tethering & Real-Time Discovery Card
- **Live Status Aura Card:** Real-time pulse aura (`#56642B` green when connected, `#7A1A1A` burgundy searching), auto-detected camera model name (`Canon EOS R5`, `FUJIFILM X-T5`), storage media count, and DCIM subfolder scan preview (`/DCIM/100FUJI/`).
- **Fallback Import Mode:** Compact card toggle to switch to manual local SAF folder import.

### 5. Step 3: Session Preferences & Kinetic Launch Action
- **Session Rules:** Toggles for RAW+JPG co-existence handling, auto-sync rules, and starting file number baseline extraction preview (`DSCF0018.JPG` -> `18`).
- **Launch CTA:** `KineticButton` labeled `"mulai sesi tethering"` with trailing arrow icon `↗`. Tapping initializes session orchestrator and transitions to the Live Session Hub.

---

## Technical Constraints & Guidelines
- All UI text strings enforce the RVM **all-lowercase** rule (except acronyms `MTP`, `USB`, `RAW`, `JPG`, `OAuth`).
- Folder cards in Step 1 use `DoppelrandCard` with `CardSurface` background (`#F6F3EB`) and `#2D4636` accent border when selected.
