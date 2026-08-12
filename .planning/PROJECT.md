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
- **Background & Sync:** WorkManager 2.11.2, Google Drive REST API Client

## Existing Capabilities

1. **Package Namespace & Brand Token Integration** — Rebranded to `com.rvmeur.transferin` with local fonts and brand colors.
2. **Google Drive Storage Engine** — OAuth2 sign-in, dynamic folder creation/resolution, and multipart REST uploads.
3. **Camera File Traversal & Tethering Core** — MTP subfolder scanning, Canon/Fuji baseline extraction, RAW+JPG concurrent file emission.

## Current Milestone: v2 — Fluid Wizard & Agency-Grade UI/UX Overhaul

**Goal:** Overhaul the entire application UI from a rigid functional layout into a fluid, gesture-driven wizard with a custom dynamic dot navigation bar, double-bezel card enclosures, and agency-grade micro-interactions matching `rvmeur.com` brand identity.

**Target Features:**
1. **Design System & Motion Foundations** — Double-bezel card containers (`Doppelrand`), kinetic button physics, custom brand tokens, and lowercase typography rules.
2. **Fluid Swipeable Wizard Flow** — Gesture-driven `HorizontalPager` connecting Google Drive Auth, Folder Selection, and Camera Tethering into a single smooth flow.
3. **Dynamic Dot Bar Navigation Bar** — Custom Compose indicator bar with animated dot sizing, morphing pill containers, and step validation feedback.
4. **Live Shoot Dashboard Polish** — Refactored active session view with real-time transfer counters, pulse status indicators, and haptic feedback.

---

*Project updated: 2026-08-12*
