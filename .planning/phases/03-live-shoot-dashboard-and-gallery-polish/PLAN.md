# Phase 3 Execution Plan — Live Shoot Dashboard & Gallery Polish

**Milestone:** v2 (Fluid Wizard & Agency-Grade UI/UX Overhaul)
**Phase:** 03 — Live Shoot Dashboard & Gallery Polish
**Status:** Ready for Execution
**Date:** 2026-08-12

---

## Executive Summary

Overhaul the live tethering dashboard (`SessionActiveScreen`), uploaded media gallery (`UploadedGalleryScreen`), full-screen photo viewer modal, and session summary screen (`SessionSummaryScreen`). Apply RVM Quiet Luxury design tokens, double-bezel framing (`DoppelrandCard`), real-time status aura indicators (`.dot-pulse`), tactile buttons (`KineticButton`), monospace metadata badges (`RobotoMono`), and full TalkBack screen reader accessibility.

---

## Tasks

### Task 1: Refactor `SessionActiveScreen.kt` (Live Shoot Dashboard)
- **File to Modify:**
  - `app/src/main/java/com/rvmeur/transferin/feature/sessionactive/SessionActiveScreen.kt`
- **Instructions:**
  1. Frame top app bar with `RvmHeaderBar` and bottom app bar with `RvmFooterBar`.
  2. Refactor Camera Status Header into a `DoppelrandCard` featuring a real-time status aura indicator (`.dot-pulse`, `#56642B` / `#7A1A1A`), camera device title (`CormorantGaramond`, `22.sp`), and monospace status text (`RobotoMono`, `11.sp`).
  3. Implement 4-Stat Metric Grid using a 2-column x 2-row `DoppelrandCard` layout (`antrean`, `mengunggah`, `berhasil`, `gagal`) with bold monospace counts (`RobotoMono`, `28.sp`, `#26170C`).
  4. Implement live transfer queue stream card with animated progress indicator and transfer speed stats.
  5. Refactor action bar to use `KineticButton` instances (`"buka galeri terunggah"`, `"selesaikan sesi tethering"`).

### Task 2: Refactor `UploadedGalleryScreen.kt` & Full-Screen Image Viewer Modal
- **File to Modify:**
  - `app/src/main/java/com/rvmeur/transferin/feature/gallery/UploadedGalleryScreen.kt`
- **Instructions:**
  1. Apply RVM quiet luxury tokens (`Ground` `#FAF8F3`, `CardSurface` `#F6F3EB`, `SurfaceVariant` `#F0EBE0`).
  2. Refactor filter chips (`"semua"`, `"foto"`, `"video"`, `"berhasil"`, `"gagal"`).
  3. Refactor thumbnail grid into a 3-column `DoppelrandCard` grid (1:1 aspect ratio) with monospace extension badges (`RAW`, `JPG`), status overlay icons, and TalkBack semantics (`contentDescription`).
  4. Build full-screen image viewer modal overlay with quiet-luxury dark backdrop (`Color(0xEE12100E)`), monospace file metadata header, touch gesture viewport, and bottom action bar (`"unduh file"`, `"buka di google drive"`, `"hapus"`).

### Task 3: Refactor `SessionSummaryScreen.kt`
- **File to Modify:**
  - `app/src/main/java/com/rvmeur/transferin/feature/sessionsummary/SessionSummaryScreen.kt`
- **Instructions:**
  1. Wrap end-of-session details (target Google Drive folder path, session duration, total files, success count, failed count) inside `DoppelrandCard`.
  2. Use `CormorantGaramond` for display titles, `RobotoMono` for path and metric values, and `KineticButton` labeled `"selesai & kembali ke setup"` with trailing arrow `↗`.

### Task 4: Compilation Verification
- **Instructions:**
  1. Run `./gradlew compileDebugKotlin` to verify error-free build.

---

## Verification Criteria
- [ ] `./gradlew compileDebugKotlin` passes cleanly.
- [ ] `SessionActiveScreen` uses `DoppelrandCard` 4-stat metric grid and live status aura.
- [ ] `UploadedGalleryScreen` uses 3-column `DoppelrandCard` grid and full-screen image viewer modal.
- [ ] `SessionSummaryScreen` uses `DoppelrandCard` summary card and `KineticButton` finish CTA.
