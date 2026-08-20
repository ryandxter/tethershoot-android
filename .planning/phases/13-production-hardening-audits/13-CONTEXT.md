# Phase 13: Production Hardening, Code Review & Quiet Luxury UI Verification — Context & Decisions

**Phase:** 13  
**Title:** Production Hardening, Code Review & Quiet Luxury UI Verification  
**Status:** Context Gathered & Decisions Locked  
**Date:** 2026-08-20  

---

## Domain Boundary

Multi-vendor end-to-end stress test harness (100-shot burst per camera vendor), LeakCanary memory allocation profiling, AAS Code Review certification Grade A+ ($\ge 95\%$), AAS UI Contract & Review certification Grade A+ ($\ge 95\%$), and full Milestone v4.0 release tagging.

---

## Implementation Decisions Locked

### 1. Multi-Vendor Stress Testing Standards
- **Harness:** 100-Shot Burst Stress Test simulating rapid 20 fps capture across all 4 camera formats:
  - Fujifilm: `.RAF` container parsing + embedded preview extraction.
  - Canon EOS: `.CR3` ISO-BMFF box parsing + DIGIC event pump.
  - Sony Alpha: `.ARW` TIFF SubIFD parsing + SDIO sync.
  - Nikon: `.NEF` vendor PTP buffer ingestion.
- **Pass Invariant:** Zero dropped frames, zero deadlocks on `SafeUsbTransportManager.ioMutex`, 100% accurate culling decisions, and zero unclosed file descriptors.

### 2. Memory Leak Profiling & Allocation Budget
- **Verification:** Strict LeakCanary monitoring and Android Profiler heap dump analysis.
- **Budget:**
  - Hot mathematical loop (`ExposureAnalyzer`): $0\text{ B}$ heap allocation.
  - Image preview extraction: strictly bounded within `DirectByteBufferPool` (10MB maximum off-heap).
  - Gallery lifecycle: zero retained `Bitmap` references after screen destruction.

### 3. Code Review Quality Gate (`QUAL-02`)
- **Reviewer:** Specialized AAS Code Reviewer (`/gsd-code-review`).
- **Standard:** Grade A+ ($\ge 95\%$ rating).
- **Mandatory Checks:** Zero Critical/High/Medium severity issues, verified thread-safety on USB mutex locks, transactional Room migration integrity, and clean Kotlin architecture.

### 4. UI Contract & Design Review Gate (`QUAL-03`)
- **Reviewer:** Specialized AAS UI Auditor (`/gsd-ui-phase` + `/gsd-ui-review`).
- **Standard:** Grade A+ ($\ge 95\%$ rating).
- **Mandatory Checks:**
  - Design tokens: Ground `#FAF8F3`, Card `#F6F3EB`, Primary `#26170C`, Accent Green `#2D4636`, Amber `#B8860B`, Burgundy `#8B0000`.
  - Double-bezel concentric geometry (`DoppelrandCard`) with crisp contrast ratios.
  - Tactile kinetic press physics (`KineticButton`) and smooth 60fps animations.
  - Typography: Cormorant Garamond (headings), DM Sans (body), Roboto Mono (metadata & badges).

### 5. Final Release Protocol
- **Artifacts:** Tag `v4.0` in git, archive milestone achievements into `MILESTONES.md`, create `.planning/HANDOFF.json`, and push commits and tags to `origin/main`.

---

## Canonical References
- `.planning/PROJECT.md` (Project core values & architecture)
- `.planning/REQUIREMENTS.md` (Milestone v4.0 requirements)
- `.planning/ROADMAP.md` (Phase execution dependencies)
- `.planning/research/SUMMARY.md` (Synthesized research foundation)
