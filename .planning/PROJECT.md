---
name: transferin-project
description: Android app for tethered camera shooting with NAS upload
metadata:
  type: project
---

# TransferIn

**Project Type:** Android Mobile Application (Kotlin)
**Analysis Date:** 2026-07-31

## Core Value

Enable professional photographers to shoot tethered with Canon cameras and automatically upload to NAS via SFTP — eliminating manual card management and ensuring no shot is lost.

## Context

### Problem
- Photographers need reliable, automated photo transfer during shoots
- Cable disconnects should not interrupt the session
- Only new shots should be uploaded (not re-upload entire card)
- Canon cameras store database files (.CTG) that track all media

### Users
- Professional photographers shooting tethered
- Studios requiring real-time backup to NAS

## Technology

- **Language:** Kotlin 2.2.10
- **UI:** Jetpack Compose with Material 3
- **Database:** Room 2.8.4
- **Background:** WorkManager 2.11.2
- **Transfer:** JSch SFTP 0.2.26
- **Architecture:** Clean Architecture + MVVM

## Existing Capabilities

1. **Tethered Shooting Mode** — Live photo capture detection via PhotoWatcher
2. **Import Fallback Mode** — Manual media ingest from MTP storage
3. **SFTP Upload** — JschSftpClient transfers to NAS
4. **Session Management** — ProjectSessionManager tracks upload state
5. **Upload Queue** — UploadQueueManager orchestrates transfer jobs

## Evolution

This document evolves at phase transitions and milestone boundaries.

**After each phase transition** (via `/gsd-transition`):
1. Requirements invalidated? → Move to Out of Scope with reason
2. Requirements validated? → Move to Validated with phase reference
3. New requirements emerged? → Add to Active
4. Decisions to log? → Add to Key Decisions
5. "What This Is" still accurate? → Update if drifted

**After each milestone** (via `/gsd-complete-milestone`):
1. Full review of all sections
2. Core Value check — still the right priority?
3. Audit Out of Scope — reasons still valid?
4. Update Context with current state

## Current Milestone: v2 Camera File Numbering & Traversal Alignment

**Goal:** Correctly align photo-watching and file-syncing boundaries to respect actual camera memory structures (nested DCIM directories) and filename numbering formats.

**Target features:**
- Implement starting numbering baseline extraction from existing card files.
- Enable camera DCIM subfolders recursive traversal.
- Ensure flawless RAW+JPG concurrent file emission and synchronization.

---

*Project created: 2026-07-31*
