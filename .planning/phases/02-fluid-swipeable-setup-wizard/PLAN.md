# Phase 2 Execution Plan — Fluid Swipeable Setup Wizard

**Milestone:** v2 (Fluid Wizard & Agency-Grade UI/UX Overhaul)
**Phase:** 02 — Fluid Swipeable Setup Wizard
**Status:** Ready for Execution
**Date:** 2026-08-12

---

## Executive Summary

Consolidate legacy standalone setup routes into a unified, gesture-driven `FluidWizardScreen` powered by `HorizontalPager`. Features forward swipe gesture locks, Google Drive auth with a 2-column card grid folder browser (Step 1), real-time camera tethering status aura card with DCIM scan preview (Step 2), session preference rules, and a kinetic launch action (Step 3).

---

## Tasks

### Task 1: Build Step 1 Components (OAuth Card & Card Grid Folder Browser)
- **File to Create:**
  - `app/src/main/java/com/rvmeur/transferin/feature/wizard/steps/Step1DriveFolder.kt`
- **Instructions:**
  1. Build Card A (Google Drive OAuth Status): Displays connection state badge pill and `KineticButton` sign-in CTA.
  2. Build Card B (Folder Browser Grid): Displays directories in a 2-column card grid (`LazyVerticalGrid(columns = GridCells.Fixed(2))`) using `DoppelrandCard` folder items (featuring folder icon, name, item count, and `#2D4636` active selection border). Include path breadcrumbs (`/root/Shoots/...`) and a `"buat folder baru"` action dialog.

### Task 2: Build Step 2 & Step 3 Wizard Screens
- **Files to Create:**
  - `app/src/main/java/com/rvmeur/transferin/feature/wizard/steps/Step2CameraTether.kt`
  - `app/src/main/java/com/rvmeur/transferin/feature/wizard/steps/Step3SessionRules.kt`
- **Instructions:**
  1. `Step2CameraTether`: Build live USB/MTP status aura card (`.dot-pulse` scaling aura, `#56642B` / `#7A1A1A`), auto-detected camera model title (`Canon EOS R5` / `FUJIFILM X-T5`), DCIM scan preview (`/DCIM/100FUJI/`), and fallback manual SAF import mode card.
  2. `Step3SessionRules`: Build tethering preference toggles (RAW+JPG co-existence, auto-sync rules, baseline extraction preview) and `"mulai sesi tethering"` `KineticButton` launch CTA.

### Task 3: Build Root `FluidWizardScreen` Container & Navigation Wiring
- **File to Create:**
  - `app/src/main/java/com/rvmeur/transferin/feature/wizard/FluidWizardScreen.kt`
- **Files to Modify:**
  - `app/src/main/java/com/rvmeur/transferin/MainActivity.kt`
- **Instructions:**
  1. Create `FluidWizardScreen.kt`: Host `HorizontalPager` with 3 steps (`01 google drive`, `02 camera tethering`, `03 session rules`). Frame between `RvmHeaderBar` (top) and `FluidDotNavigationBar` + `RvmFooterBar` (bottom).
  2. Implement forward swipe lock (`userScrollEnabled = isStep1Valid`) with floating warning pill (`"selesaikan otentikasi & pilih folder terlebih dahulu"`).
  3. Update `MainActivity.kt` root `NavHost` to make `FluidWizardScreen` the initial setup route.

### Task 4: Compilation & Quality Verification
- **Instructions:**
  1. Run `./gradlew compileDebugKotlin` to verify clean build without Kotlin or Compose errors.
  2. Validate 2-column card grid layout, touch target dimensions ($\ge 44.dp$), and TalkBack accessibility.

---

## Verification Criteria
- [ ] `./gradlew compileDebugKotlin` passes cleanly.
- [ ] `FluidWizardScreen` hosts `HorizontalPager` with 3 steps.
- [ ] Step 1 folder browser renders in a 2-column card grid.
- [ ] Step 2 displays live camera status aura card with camera model title.
- [ ] Forward swipe is locked until Step 1 validation passes.
