---
id: SEED-001
status: planted
planted: 2026-08-20
planted_during: Milestone v3.0 -> v4.0 transition
trigger_when: when starting Milestone v4.0 planning (/gsd-new-milestone)
scope: Medium
---

# SEED-001: On-Device AI Exposure & Automated Culling Engine

## Why This Matters

During fast-paced professional photography sessions (burst shooting, studio shoots, events), photographers often produce misfired shots (severely underexposed due to flash misfire, or blown-out/overexposed due to incorrect manual settings). Streaming these multi-megabyte raw photos to Google Drive wastes cellular/Wi-Fi bandwidth, consumes cloud storage quota, and clutters the client delivery folder.

By performing zero-latency, on-device exposure analysis directly on the extracted embedded JPEG preview (or raw luminance histogram):
1. The app can instantly classify photos as `OPTIMAL`, `UNDEREXPOSED_CRITICAL`, or `OVEREXPOSED_BLOWN`.
2. Photographers can configure auto-culling rules (e.g., "Skip uploading rejected/misfired exposures", "Tag for review only", or "Upload with warning badge").
3. Sub-second feedback appears in the Studio Live Dashboard with clear visual badges.

## When to Surface

**Trigger:** During `/gsd-new-milestone` when planning Milestone v4.0 (AI, In-App Culling & Studio Optimization).

## Scope Estimate

**Medium** (1-2 Phases):
- **Phase A (Engine & Algorithm):** Fast luminance histogram & percentile clipping analyzer on `DirectByteBufferPool` / extracted preview (<15ms per frame), Room DB schema update for culling metadata (`culling_status`, `exposure_score`, `histogram_clipped_pct`).
- **Phase B (UI & Quiet Luxury Controls):** Culling threshold settings in Setup Wizard / Active Session, live warning badges on `DoppelrandCard` media gallery, and manual override gesture.

## Quality & Review Gates

- **Mandatory Code Review (`/gsd-code-review`):** Verify zero memory allocations during histogram computation, ensure thread-safety on preview worker pool, and enforce test coverage on histogram edge cases (pure black, pure white, gradient).
- **Mandatory UI Review (`/gsd-ui-review`):** Verify quiet luxury styling (`#26170C`, `#2D4636`, `#FAF8F3`), subtle badge indicators without alarmist visual noise, and smooth swipe/dismiss physics.

## Breadcrumbs

- `app/src/main/java/com/rvmeur/transferin/data/usb/extractors/` (Embedded preview parsers for Fuji `.RAF`, Canon `.CR3`, Sony `.ARW`)
- `app/src/main/java/com/rvmeur/transferin/data/usb/DirectByteBufferPool.kt` (Off-heap memory management)
- `app/src/main/java/com/rvmeur/transferin/data/sync/SyncOrchestrator.kt` (5-stage cloud streaming state machine)
- `app/src/main/java/com/rvmeur/transferin/ui/screens/SessionActiveScreen.kt` & `UploadedGalleryScreen.kt` (Live shoot dashboard & gallery)
