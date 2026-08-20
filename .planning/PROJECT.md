---
name: transferin-project
description: Android app for tethered camera shooting with Google Drive upload
metadata:
  type: project
---

# TransferIn

**Project Type:** Android Mobile Application (Kotlin + Jetpack Compose)
**Analysis Date:** 2026-08-12

## Core Value

Enable professional photographers to shoot tethered with Canon/Fuji cameras and automatically stream high-resolution photos to Google Drive, delivered through a fluid, premium, agency-grade UI experience.

## Context

### Problem
- Existing app UI is rigid, static, and functional-only, lacking visual polish and smooth navigation.
- Photographers need an intuitive, tactile experience that feels natural during fast-paced studio and field shoots.
- Setting up Google Drive storage, camera tethering, and session rules should be a seamless, swipeable wizard flow rather than disconnected form screens.

### Users
- Professional photographers shooting tethered in studio or field environments.
- Creative teams requiring instant, beautifully presented cloud delivery.

## Technology

- **Language:** Kotlin 2.2.10
- **UI Architecture:** Jetpack Compose with Material 3, Accompanist / Compose Foundation Pager (`HorizontalPager`)
- **Design Tokens:** `rvmeur.com` design system (`#FAF8F3` ground, `#F6F3EB` card, `#26170C` primary, `#2D4636` accent green, Cormorant Garamond, DM Sans, Roboto Mono)
- **Database:** Room 2.8.4
- **Background & Sync:** WorkManager 2.11.2, Google Drive REST API Client, Google Play Services Auth

## Shipped Capabilities

1. **RVM Quiet Luxury Design System (v2.0)** — Double-bezel concentric card containers (`DoppelrandCard`), tactile kinetic press physics (`KineticButton`), floating glass morphing dot navigation bar (`FluidDotNavigationBar`), custom typography and brand tokens.
2. **Fluid Swipeable Setup Wizard (v2.0)** — Gesture-driven 3-step setup flow connecting Google Drive OAuth & folder selection, live camera tethering pulse, and session rules.
3. **Studio Live Dashboard & Gallery (v2.0)** — Active session monitor with 4-stat metric grid, live queue stream, 3-column media thumbnail grid with full-screen dark modal viewer, and session summary.
4. **Google Drive Cloud Storage Engine (v1.0)** — Direct Google Drive REST API integration with OAuth2 authentication, dynamic folder resolution, and multipart file upload.
5. **Camera File Traversal Core (v2 Legacy)** — MTP subfolder scanning, Canon/Fuji baseline extraction, and RAW+JPG concurrent file emission.

## Current Milestone: v3.0 Multi-Vendor Production Engine & Zero-Leak Tether Core

**Goal:** Transform TransferIn into a bulletproof, production-ready multi-vendor tethering platform with zero memory leaks, deterministic hardware lifecycle management, and full support for Fujifilm, Canon, Sony, and Nikon.

**Target features:**
- **Zero Memory Leaks & Resource Hygiene:** Systematic elimination of coroutine leaks, USB file descriptor leaks, foreground service lifecycle races, and unmanaged memory retention.
- **Fuji Native Tether Clean Lifecycle:** Fix invisible recursive ping-pong communication, implement clean tether shutdown/disconnect handshake, and precise camera-to-cloud synchronization state.
- **Multi-Vendor Architecture (Fuji, Canon, Sony, Nikon):** Vendor-specific protocol adapters (Fuji native wired tether, Canon PTP/CTG, Sony remote PTP/MTP, Nikon vendor PTP) behind a unified, robust tether abstraction.
- **Production Rigor & Quality Gates:** Mandatory deep code reviews, UI audits, security audits, and real hardware/protocol regression testing (no pseudo-testing).

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

*Last updated: 2026-08-20 for Milestone v3.0 initialization*
