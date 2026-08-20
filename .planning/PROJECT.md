---
name: transferin-project
description: Android app for tethered camera shooting with Google Drive upload
metadata:
  type: project
---

# TransferIn

**Project Type:** Android Mobile Application (Kotlin + Jetpack Compose)  
**Latest Milestone Shipped:** v4.0 (2026-08-20)

## Core Value

Enable professional photographers to shoot tethered with Fujifilm, Canon, Sony, and Nikon cameras and automatically stream high-resolution photos to Google Drive with sub-second preview feedback, zero memory leaks, smart on-device exposure culling, reliable persistent thumbnails, and zero camera lockups, delivered through a fluid, premium, quiet luxury UI experience.

## Technology

- **Language:** Kotlin 2.2.10
- **UI Architecture:** Jetpack Compose with Material 3, Compose Foundation Pager, RenderThread hardware acceleration
- **Design Tokens:** `rvmeur.com` quiet luxury design system (`#FAF8F3` ground, `#F6F3EB` card, `#26170C` primary, `#2D4636` accent green, `#B8860B` amber, `#8B0000` burgundy, Cormorant Garamond, DM Sans, Roboto Mono)
- **Database:** Room 2.8.4 (Schema v11 with culling metadata and persistent thumbnail references)
- **Networking & Streaming:** OkHttp 4.12.0 (`UsbStreamRequestBody` direct socket streaming), Google Drive REST API Client, Google Play Services Auth
- **Image Pipeline & Graphics:** Coil 2.6.0 with custom disk cache & EXIF rotation normalizer, zero-allocation ITU-R BT.601 integer math engine (`ExposureAnalyzer`), Compose Canvas Bézier histogram curve
- **Hardware & USB:** Direct PTP vendor tether adapters (Fuji, Canon EOS, Sony Alpha, Nikon), `SafeUsbTransportManager` with two-tier priority mutex (`UsbPriorityMutex`), `DirectByteBufferPool` off-heap memory manager

## Shipped Capabilities

1. **On-Device AI Exposure Analysis & Zero-Allocation Math Engine (v4.0)** — ITU-R BT.601 integer bit-shift luminance engine ($<0.25\text{ms}$ latency, 0 B heap allocation) evaluating flash misfires (`UNDEREXPOSED_CRITICAL`), blown highlights (`OVEREXPOSED_BLOWN`), and Shannon entropy to protect intentional low-key studio portraits.
2. **Persistent Multi-Vendor Thumbnail Pipeline (v4.0)** — Atomic two-stage persistence (`.tmp` $\to$ `fd.sync()` $\to$ POSIX `renameTo`), 500MB bounded LRU disk cache, EXIF Tag `0x0112` orientation normalization, and two-tier USB mutex arbitration giving instant preview extraction priority over background RAW streaming.
3. **Smart Culling State Machine & Room v11 Migration (v4.0)** — Session culling policies (`AUTO_SKIP_UPLOAD`, `WARN_ONLY`, `DISABLED`) in Setup Wizard Step 3, halting multi-megabyte RAW uploads on culled frames to save cellular quota and Google Drive storage.
4. **Quiet Luxury Studio Gallery & Inspection HUD (v4.0)** — Monospace pill status badges on `DoppelrandCard`, responsive filter chips (`semua`, `optimal`, `terpangkas`, `dilewati`), 1-tap kinetic "Paksa Unggah" override, and full-screen dark modal viewer (`Color(0xEE12100E)`) with live cubic Bézier luminance histogram curve.
5. **Multi-Vendor Tether Engine (v3.0)** — Direct native PTP drivers for Fujifilm (VID `0x04CB`, 0xD214 host control), Canon EOS (VID `0x04A9`, 0x9114/0x9116 event pump), Sony Alpha (VID `0x054C`, SDIO handshake + DRAM safety guard), and Nikon (VID `0x04B0`, vendor PTP + adaptive backoff).
6. **Zero-Leak Hardware Core (v3.0)** — Off-heap `DirectByteBufferPool` (10MB bounded pool, zero GC churn), JNI IO execution latch (`SafeUsbTransportManager`) preventing native `SIGSEGV` page faults during rapid disconnects, and Android 14/15 `FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE` compliance.
7. **5-Stage Camera-to-Cloud Sync (v3.0)** — Transactional state machine (`DETECTED` $\to$ `BUFFERED_LOCAL` $\to$ `STREAMING_CLOUD` $\to$ `CONFIRMED_CLOUD` $\to$ `PURGED_LOCAL`), compound SHA-256 fingerprint deduplication, direct zero-copy streaming to Google Drive, 5GB LRU transit cache with confirmed-only eviction, and real-time telemetry.
8. **RVM Quiet Luxury Design System (v2.0)** — Double-bezel concentric card containers (`DoppelrandCard`), tactile kinetic press physics (`KineticButton`), floating glass morphing dot navigation bar (`FluidDotNavigationBar`), custom typography and brand tokens.
9. **Fluid Swipeable Setup Wizard (v2.0)** — Gesture-driven 3-step setup flow connecting Google Drive OAuth & folder selection, live camera tethering pulse, and session rules.

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

