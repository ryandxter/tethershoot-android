---
name: transferin-project
description: Android app for tethered camera shooting with Google Drive upload
metadata:
  type: project
---

# TransferIn

**Project Type:** Android Mobile Application (Kotlin + Jetpack Compose)  
**Latest Milestone Shipped:** v3.0 (2026-08-20)

## Core Value

Enable professional photographers to shoot tethered with Fujifilm, Canon, Sony, and Nikon cameras and automatically stream high-resolution photos to Google Drive with sub-second preview feedback, zero memory leaks, and zero camera lockups, delivered through a fluid, premium, quiet luxury UI experience.

## Technology

- **Language:** Kotlin 2.2.10
- **UI Architecture:** Jetpack Compose with Material 3, Compose Foundation Pager, RenderThread hardware acceleration
- **Design Tokens:** `rvmeur.com` quiet luxury design system (`#FAF8F3` ground, `#F6F3EB` card, `#26170C` primary, `#2D4636` accent green, Cormorant Garamond, DM Sans, Roboto Mono)
- **Database:** Room 2.8.4 (Schema v10 with transactional 5-stage sync tables)
- **Networking & Streaming:** OkHttp 4.12.0 (`UsbStreamRequestBody` direct socket streaming), Google Drive REST API Client, Google Play Services Auth
- **Hardware & USB:** Direct PTP vendor tether adapters (Fuji, Canon EOS, Sony Alpha, Nikon), `SafeUsbTransportManager`, `DirectByteBufferPool` off-heap memory manager

## Shipped Capabilities

1. **Multi-Vendor Tether Engine (v3.0)** — Direct native PTP drivers for Fujifilm (VID `0x04CB`, 0xD214 host control), Canon EOS (VID `0x04A9`, 0x9114/0x9116 event pump), Sony Alpha (VID `0x054C`, SDIO handshake + DRAM safety guard), and Nikon (VID `0x04B0`, vendor PTP + adaptive backoff).
2. **Zero-Leak Hardware Core (v3.0)** — Off-heap `DirectByteBufferPool` (10MB bounded pool, zero GC churn), JNI IO execution latch (`SafeUsbTransportManager`) preventing native `SIGSEGV` page faults during rapid disconnects, and Android 14/15 `FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE` compliance.
3. **Sub-Second Preview Extractors (v3.0)** — High-speed embedded preview parsers for Fujifilm `.RAF` (<120ms), Canon `.CR3` (<80ms), and Sony `.ARW` (<5ms) without downloading full multi-megabyte RAW files over USB.
4. **5-Stage Camera-to-Cloud Sync (v3.0)** — Transactional state machine (`DETECTED` $\to$ `BUFFERED_LOCAL` $\to$ `STREAMING_CLOUD` $\to$ `CONFIRMED_CLOUD` $\to$ `PURGED_LOCAL`), compound SHA-256 fingerprint deduplication, direct zero-copy streaming to Google Drive, 5GB LRU transit cache with confirmed-only eviction, and real-time telemetry.
5. **RVM Quiet Luxury Design System (v2.0)** — Double-bezel concentric card containers (`DoppelrandCard`), tactile kinetic press physics (`KineticButton`), floating glass morphing dot navigation bar (`FluidDotNavigationBar`), custom typography and brand tokens.
6. **Fluid Swipeable Setup Wizard (v2.0)** — Gesture-driven 3-step setup flow connecting Google Drive OAuth & folder selection, live camera tethering pulse, and session rules.
7. **Studio Live Dashboard & Gallery (v2.0)** — Active session monitor with 4-stat metric grid, live queue stream, 3-column media thumbnail grid with full-screen dark modal viewer, and session summary.

## Current Milestone: v4.0 — On-Device AI Exposure Analysis, Smart Culling & Reliable Thumbnailing Engine

**Goal:** Equip TransferIn with zero-allocation on-device exposure analysis (<15ms per frame) to detect severely underexposed (flash misfire) and overexposed (blown highlight) shots, provide smart automated culling rules to avoid wasting Google Drive cloud quota and cellular bandwidth, and definitively fix the tethered thumbnail rendering/transfer pipeline across all camera vendors.

### Target Features:
1. **On-Device Exposure & Luminance Clipping Analyzer (`CULL-01`):** Fast histogram analysis on raw preview byte buffers, classifying frames into `OPTIMAL`, `UNDEREXPOSED_CRITICAL`, or `OVEREXPOSED_BLOWN`.
2. **End-to-End Tethered Thumbnail Pipeline Fix (`THUMB-01`):** Resolve camera-to-app thumbnail transfer/caching failures, ensuring every captured photo reliably renders high-fidelity thumbnails in the Live Studio Gallery and dark modal viewer upon upload/capture.
3. **Session Culling Rules & Quiet-Luxury Warning Overlays (`UI-01`):** Configurable culling modes in Setup Wizard (Auto-Skip Upload, Warning Badge Only, Normal Upload), non-intrusive quiet luxury warning badges on `DoppelrandCard` gallery items, and manual upload override gestures.
4. **Mandatory Quality & Design Verification:** High-end UI contracts via `/gsd-ui-phase`, zero-GC performance audit via `/gsd-code-review` (Grade A+), and visual design compliance via `/gsd-ui-review` (Grade A+).

## Key Decisions

| Decision | Rationale | Status |
| :--- | :--- | :--- |
| **Native PTP Host Control over MTP Polling** | Eliminates camera firmware lockups and keeps physical camera controls and shutter operational during tethering | ✓ Good |
| **Direct Off-Heap Memory Buffering (`DirectByteBufferPool`)** | Prevents JVM Garbage Collection pauses and OOMs during multi-gigabyte burst shooting | ✓ Good |
| **Confirmed-Only Transit Cache Eviction** | Ensures zero photo loss even under extreme local storage pressure | ✓ Good |
| **Adversarial USB Packet Fuzzing Test Suite** | Validates native crash resilience against malformed packets and sudden cable disconnects | ✓ Good |
| **On-Device Preview Histogram Analysis over Cloud AI** | Sub-15ms client-side luminance evaluation avoids latency and unnecessary cloud upload bandwidth | ✓ Proposed |

## Evolution

This document evolves at phase transitions and milestone boundaries.

**After each phase transition** (via `/gsd-transition`):
1. Requirements invalidated? → Move to Out of Scope with reason
2. Requirements validated? → Move to Validated with phase reference
3. New requirements emerged? → Add to Active
4. Decisions to log? → Add to Key Decisions
5. "What This Is" still accurate? → Update if drifted

**After each milestone** (via `/gsd:complete-milestone`):
1. Full review of all sections
2. Core Value check — still the right priority?
3. Audit Out of Scope — reasons still valid?
4. Update Context with current state

---

*Last updated: 2026-08-20 for Milestone v4.0 planning*

