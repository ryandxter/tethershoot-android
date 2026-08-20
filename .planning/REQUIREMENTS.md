# Requirements: TransferIn (Milestone v4.0)

**Defined:** 2026-08-20  
**Milestone:** v4.0 — On-Device AI Exposure Analysis, Smart Culling & Reliable Thumbnailing Engine  
**Core Value:** Enable professional photographers to shoot tethered with Fujifilm, Canon, Sony, and Nikon cameras and automatically stream high-resolution photos to Google Drive with sub-second preview feedback, zero memory leaks, smart auto-culling of misfired/blown shots, and reliable gallery thumbnails, delivered through a fluid, quiet luxury UI experience.

---

## v1 Requirements

### Exposure & Culling Engine (CULL)

- [ ] **CULL-01**: The system computes a 256-bin luminance histogram in $<1.8\text{ms}$ with zero JVM heap allocations using fixed-point ITU-R BT.601 integer bit-shift math on a pre-allocated reusable buffer.
- [ ] **CULL-02**: The system automatically classifies captured frames as `UNDEREXPOSED_CRITICAL` when shadow density ($Y \le 3$) exceeds $98\%$, mean luminance is $<15$, and Shannon entropy is $<1.0$.
- [ ] **CULL-03**: The system automatically classifies captured frames as `OVEREXPOSED_BLOWN` when highlight saturation ($Y \ge 248$) exceeds $20\%$ and mean luminance is $>230$.
- [ ] **CULL-04**: The system parses camera MakerNote / Picture Profiles (e.g. Fujifilm *Acros*, *Velvia*) to apply dynamic clipping offsets, preventing false culling on high-contrast B&W film simulations.
- [ ] **CULL-05**: The user can configure session culling rules (`AUTO_SKIP_UPLOAD`, `WARN_ONLY`, `DISABLED`), with `AUTO_SKIP_UPLOAD` halting Tier 2 RAW USB transfer and saving cloud quota.

### Tethered Thumbnail Pipeline & Storage (THUMB)

- [ ] **THUMB-01**: The system extracts embedded JPEG previews in $<50\text{ms}$ from container headers across Fujifilm (`.RAF`), Canon (`.CR3`), Sony (`.ARW`), and Nikon (`.NEF`) without transferring full RAW files over USB.
- [ ] **THUMB-02**: The system atomically persists extracted previews to private app storage (`/files/thumbnails/{fingerprint}.jpg`) using two-stage commits (`.tmp` write $\to$ `fd.sync()` $\to$ rename) with a 500MB bounded LRU cache that survives post-upload RAW file purging.
- [ ] **THUMB-03**: The system serializes USB endpoint access via `SafeUsbTransportManager.ioMutex` with high-priority locks for Tier 1 preview extraction to eliminate PTP packet interleaving during concurrent cloud streaming.
- [ ] **THUMB-04**: The system loads persistent JPEG thumbnails in Coil Compose with deterministic cache keys and EXIF orientation normalization (Tag `0x0112`), eliminating blank tiles and 90° portrait rotation bugs.

### Quiet Luxury Studio UI & Manual Override (UI)

- [ ] **UI-01**: The media gallery displays quiet luxury status badges on `DoppelrandCard` containers (warm amber `#B8860B` for underexposure, deep burgundy `#8B0000` for blown highlights) with subtle monospace typography.
- [ ] **UI-02**: The user can perform a 1-tap kinetic "Paksa Unggah" (Force Upload Anyway) override action or swipe gesture to manually upload any culled or skipped photo to Google Drive.
- [ ] **UI-03**: The user can inspect any captured photo in a full-screen dark modal viewer (`Color(0xEE12100E)`) featuring high-fidelity preview zoom, real-time 256-bin luminance histogram curve overlay, EXIF metadata readout, and culling status toggle.
- [ ] **UI-04**: The media gallery provides responsive filter chips for `semua`, `optimal`, `terpangkas`, and `dilewati` with live item counts and batch actions.

### Quality, Performance & Verification Gates (QUAL)

- [ ] **QUAL-01**: The test suite validates the exposure analyzer via Android Microbenchmarks demonstrating $<1.8\text{ms}$ execution, 0 B heap allocation, and 100% classification accuracy on synthetic edge case gradients.
- [ ] **QUAL-02**: The codebase completes mandatory Code Review (`/gsd-code-review`) achieving Grade A+ ($\ge 95\%$ rating) with zero memory leaks and safe concurrency.
- [ ] **QUAL-03**: The UI components complete mandatory UI Contract (`/gsd-ui-phase`) and UI Review (`/gsd-ui-review`) achieving Grade A+ verifying `rvmeur.com` quiet luxury aesthetic compliance.

---

## v2 Requirements (Deferred)

- **AI-01**: On-device NPU face and blink detection scoring via LiteRT/NNAPI.
- **LUT-01**: Real-time 3D LUT `.cube` color grading shader preview on RAW files.
- **WIFI-01**: Wireless tethering over Wi-Fi Direct and PTP/IP.
- **REMOTE-01**: In-app live view streaming and remote shutter trigger from phone.

---

## Out of Scope

| Feature | Reason |
|---------|--------|
| Cloud-Based Exposure AI | 500ms–3000ms latency, cellular data waste, offline failure risk, and API costs |
| Destructive Camera SD Card Deletion | Violates photographer data safety protocols; SD card remains untouched master copy |
| Full 50MP RAW Demosaicing for Thumbnails | 2–5s decoding latency and OOM risks; pre-rendered embedded previews extracted instead in $<30\text{ms}$ |
| Intrusive Modal Alert Popups | Disrupts photographer rhythm and induces alert fatigue; subtle badges used instead |

---

## Traceability

| Requirement | Phase | Status |
|-------------|-------|--------|
| CULL-01 | Phase 10 | Pending |
| CULL-02 | Phase 10 | Pending |
| CULL-03 | Phase 10 | Pending |
| CULL-04 | Phase 10 | Pending |
| THUMB-01 | Phase 11 | Pending |
| THUMB-02 | Phase 11 | Pending |
| THUMB-03 | Phase 11 | Pending |
| THUMB-04 | Phase 11 | Pending |
| CULL-05 | Phase 12 | Pending |
| UI-01 | Phase 12 | Pending |
| UI-02 | Phase 12 | Pending |
| UI-03 | Phase 12 | Pending |
| UI-04 | Phase 12 | Pending |
| QUAL-01 | Phase 10 | Pending |
| QUAL-02 | Phase 13 | Pending |
| QUAL-03 | Phase 13 | Pending |

**Coverage:**
- v1 requirements: 16 total
- Mapped to phases: 16
- Unmapped: 0 ✓

---
*Requirements defined: 2026-08-20*  
*Last updated: 2026-08-20 for Milestone v4.0*
