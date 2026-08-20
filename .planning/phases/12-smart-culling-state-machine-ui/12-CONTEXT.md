# Phase 12: Smart Culling State Machine, Room Migration & Quiet Luxury Studio UI — Context & Decisions

**Phase:** 12  
**Title:** Smart Culling State Machine, Room Migration & Quiet Luxury Studio UI  
**Status:** Context Gathered & Decisions Locked  
**Date:** 2026-08-20  

---

## Domain Boundary

Room Database Schema Migration v10 $\to$ v11, `CullingDecisionGate` integration with `SyncOrchestrator`, Setup Wizard Step 3 culling rule selection, `DoppelrandCard` quiet luxury badges, responsive gallery filter chips, 1-tap kinetic "Paksa Unggah" manual override, and full-screen dark modal viewer with live luminance histogram curve overlay.

---

## Implementation Decisions Locked

### 1. Database Schema & Migration Strategy
- **Migration:** Non-destructive `Migration(10, 11)` executing `ALTER TABLE` on `camera_sync_items` and `camera_media_items`.
- **New Columns Added:**
  - `thumbnailPath: TEXT` (Nullable, points to verified local thumbnail JPEG)
  - `cullingVerdict: TEXT` (Default: `'OPTIMAL'`)
  - `meanLuminance: REAL` (Default: `128.0`)
  - `entropyScore: REAL` (Default: `5.0`)
  - `isCulled: INTEGER` (Boolean 0/1, default: `0`)
  - `isManualUploadOverride: INTEGER` (Boolean 0/1, default: `0`)
- **Safety Guarantee:** Preserves all existing session records and Google Drive cloud confirmation history.

### 2. Default Session Culling Policy
- **Default Mode:** `AUTO_SKIP_UPLOAD` (Quota-First Protection).
- **Behavior:**
  - If a frame is evaluated as `UNDEREXPOSED_CRITICAL` or `OVEREXPOSED_BLOWN`, `SyncOrchestrator` marks the Room entity as `CULLED_SKIPPED`.
  - Tier 2 RAW multi-megabyte transfers over USB and cloud streaming sockets are halted immediately, saving bandwidth and Google Drive cloud quota.
  - The photo remains fully viewable in the local Studio Gallery with warning badges, and the photographer can override at any time.

### 3. Quiet Luxury Visual Badges on Media Cards
- **Component:** Subtle Monospace Pill Badge positioned in the corner of `DoppelrandCard`.
- **Styling:**
  - Underexposed / Flash Misfire: Warm Amber (`#B8860B`) background with white text.
  - Blown Highlight / Overexposed: Deep Burgundy (`#8B0000`) background with white text.
  - Typography: `RobotoMono`, 9sp, all-caps (`UNDER`, `BLOWN`).
  - Aesthetics: Understated quiet luxury conforming to `rvmeur.com` design tokens without alarmist visual noise.

### 4. 1-Tap Kinetic Force-Upload Override Interaction
- **UX Flow:** Zero-friction 1-tap kinetic press ("Paksa Unggah") on the thumbnail card or swipe gesture in the dark modal viewer.
- **Behavior:** Bypasses blocking confirmation dialogs to maintain the photographer's shooting rhythm; immediately enqueues the culled RAW file into `DirectStreamingEngine` for Google Drive upload, updating DB state to `FORCE_UPLOAD_PENDING`.

### 5. Full-Screen Dark Inspection Modal & Live Histogram
- **Overlay:** `Box` modal with semi-transparent dark background (`Color(0xEE12100E)`).
- **Features:**
  - High-fidelity preview with pinch-to-zoom and pan.
  - Real-time 256-bin luminance histogram curve drawn via Compose `Canvas`.
  - EXIF parameter HUD (ISO, Shutter Speed, Aperture, Camera Model, Film Simulation).
  - Prominent "Paksa Unggah" / Culling Status toggle button using `KineticButton`.

---

## Canonical References
- `app/src/main/java/com/rvmeur/transferin/ui/components/DoppelrandCard.kt` (Concentric card container)
- `app/src/main/java/com/rvmeur/transferin/feature/gallery/UploadedGalleryScreen.kt` (Live shoot gallery)
- `app/src/main/java/com/rvmeur/transferin/ui/screens/Step3SessionRules.kt` (Session configuration)
- `.planning/research/FEATURES.md` (Culling rules & badge specifications)
