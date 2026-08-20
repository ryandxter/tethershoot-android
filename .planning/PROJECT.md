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

## Key Decisions

| Decision | Rationale | Status |
| :--- | :--- | :--- |
| **Native PTP Host Control over MTP Polling** | Eliminates camera firmware lockups and keeps physical camera controls and shutter operational during tethering | ✓ Good |
| **Direct Off-Heap Memory Buffering (`DirectByteBufferPool`)** | Prevents JVM Garbage Collection pauses and OOMs during multi-gigabyte burst shooting | ✓ Good |
| **Confirmed-Only Transit Cache Eviction** | Ensures zero photo loss even under extreme local storage pressure | ✓ Good |
| **Adversarial USB Packet Fuzzing Test Suite** | Validates native crash resilience against malformed packets and sudden cable disconnects | ✓ Good |

---

*Last updated: 2026-08-20 after v3.0 milestone completion*
