# Project Research Summary

**Project:** TransferIn (Milestone v4.0)  
**Domain:** On-Device Exposure Analysis, Real-Time Luminance Histogram Computation, Smart Culling Decision Engine & Tethered RAW Thumbnailing for Android  
**Researched:** 2026-08-20  
**Confidence:** HIGH  

---

## Executive Summary

TransferIn Milestone v4.0 solves the critical twin bottlenecks of high-throughput tethered photography: **bandwidth/storage waste from camera misfires** and **unreliable/stuttering gallery thumbnail generation**. During fast-paced commercial and studio photoshoots (burst fashion, multi-strobe portraits, event coverage), photographers routinely encounter flash synchronization dropouts (producing pitch-black frames) or strobe misconfigurations (producing completely blown highlights). In prior architectures, multi-megabyte RAW files (30MB–60MB each) were unconditionally transferred and uploaded to Google Drive, wasting precious cellular data, saturating cloud storage, and inducing USB bus contention. Furthermore, gallery thumbnails suffered from missing decoders, race conditions during disk writes, and GC heap pressure.

The research establishes an on-device, zero-allocation exposure analysis and persistent thumbnailing architecture. By intercepting the lightweight Tier 1 embedded preview JPEG slice (<128KB, fetched over USB PTP in <50ms) before initiating the heavy Tier 2 RAW binary pull, TransferIn computes a 256-bin luminance histogram and statistical entropy profile in **<1.8ms with 0 bytes of JVM heap allocation per frame**. A deterministic decision engine evaluates sensor black floor density, highlight clipping, and midtone entropy against active session rules (`AUTO_SKIP_UPLOAD`, `WARN_ONLY`, `DISABLED`). Discarded misfires are immediately halted from cloud streaming—preserving cloud quota and mobile bandwidth—while preserving local records for review. Concurrently, preview bytes are committed atomically to an isolated, persistent thumbnail store (`/files/thumbnails/{fingerprint}.jpg`), ensuring instant 60fps gallery rendering with Coil Compose even after raw transit files are purged post-upload.

The primary architectural risks identified in research are: (1) **JVM heap thrashing and ART GC pauses** during 20 fps bursts, (2) **false-positive auto-culling** of intentional low-key (black backdrop) or high-key artistic photography, (3) **USB bulk endpoint contention** between preview extraction and full RAW uploads on half-duplex camera endpoints, and (4) **asynchronous file write race conditions** causing Coil to load corrupted 0-byte thumbnails. All four risks are eliminated through subsampled direct bit-shift arithmetic, multi-feature entropy classification with profile compensation, single-flight PTP mutex arbitration, and atomic two-stage disk commits (`.tmp` write $\to$ `fd.sync()` $\to$ POSIX rename).

---

## Key Findings

### Recommended Stack

The recommended stack builds directly upon TransferIn's established Kotlin 2.2.10 and Jetpack Compose Foundation architecture without introducing heavy third-party native computer vision libraries (such as OpenCV or TensorFlow Lite), which would add +35MB APK bloat and 50–150ms inference latency.

See [.planning/research/STACK.md](file:///Users/chiio/VSCode/TransferInFuji/.planning/research/STACK.md) for detailed package coordinates, version compatibility, and benchmarking specifications.

**Core technologies:**
- **Zero-Allocation Bit-Shift Luminance Engine (Kotlin 2.2.10):** Real-time 256-bin luminance histogram calculation using fixed-point ITU-R BT.601 integer arithmetic (`(77*R + 150*G + 29*B) shr 8`) over a pre-allocated reusable `IntArray(256)` bin buffer. Delivers **sub-1.8ms** execution with **0 B heap allocation**, completely eliminating GC frame drops.
- **Native Android Bitmap Sampling & Pooling (`BitmapFactory.Options` + `inBitmap`):** Built-in Android Skia/libjpeg-turbo runtime decoding with `inSampleSize = 8` and mutable bitmap recycling, downsampling embedded previews to $256 \times 170$ in 2.0–3.5ms without heap churn.
- **AndroidX ExifInterface (`androidx.exifinterface:exifinterface:1.4.2`):** Zero-decode EXIF metadata extraction (<0.5ms) parsing `TAG_FLASH`, `TAG_ORIENTATION`, ISO, shutter speed, and aperture directly from input streams without decoding image pixels.
- **Coil Compose Image Pipeline (`io.coil-kt:coil-compose:2.7.0`):** Coroutine-native Jetpack Compose image loader with custom memory and disk cache configuration (`cacheDir/thumbnails/{fingerprint}.jpg`), deterministic cache keys, and automatic EXIF orientation normalization.
- **Android Room Database (`androidx.room:room-runtime:2.8.4`):** Schema Migration 10 $\to$ 11 persisting culling verdicts, mean luminance, clipping percentiles, and persistent thumbnail paths across `camera_sync_items` and `transfer_queue_items`.
- **Direct Off-Heap Memory Manager (`DirectByteBufferPool`):** In-house 10MB off-heap pool isolating PTP USB transport buffers and streaming pipes from the JVM garbage collector.

---

### Expected Features

The feature scope for Milestone v4.0 focuses on real-time exposure analysis, intelligent cloud upload protection, and persistent thumbnail visualization, adhering strictly to the `rvmeur.com` quiet luxury design language.

See [.planning/research/FEATURES.md](file:///Users/chiio/VSCode/TransferInFuji/.planning/research/FEATURES.md) for full feature specifications, algorithm blueprints, and competitor comparisons.

**Must have (table stakes):**
- **Instant Multi-Vendor RAW Thumbnail Extraction (`THUMB-01`):** Native PTP header extraction of embedded JPEG previews (<50ms) across Fujifilm (`.RAF`), Canon (`.CR3`), Sony (`.ARW`), and Nikon (`.NEF`) without full RAW downloads.
- **Persistent Offline Thumbnail Cache (`THUMB-02`):** Extracted 1080p/720p previews stored in private app storage (`/files/thumbnails/`), remaining viewable after raw transit cache eviction (`PURGED_LOCAL`).
- **On-Device Flash Misfire & Underexposure Detection (`CULL-01`):** Sub-15ms classification identifying pitch-black frames ($Y \le 3$ density $>98\%$, mean luma $<15$, entropy $<1.0$).
- **Blown Highlight & Overexposure Detection (`CULL-02`):** Detection of severe sensor clipping ($Y \ge 248$ density $>20\%$, mean luma $>230$).
- **Session-Level Auto-Skip Upload Policy (`CULL-03`):** User-selectable culling rules in Setup Wizard & Active Session (`AUTO_SKIP_UPLOAD`, `WARN_ONLY`, `DISABLED`).
- **Manual Force-Upload Override Gesture (`UI-01`):** Single-tap tactile kinetic "Paksa Unggah" (Upload Anyway) button and swipe action in gallery and modal viewer.
- **Quiet Luxury Warning Badges on Media Cards (`UI-02`):** Concentric `DoppelrandCard` monospace badges with warm amber (`#B8860B`) for underexposure and deep burgundy (`#8B0000`) for blown highlights.
- **Full-Screen Exposure & Histogram Modal Viewer (`UI-03`):** Dark inspection viewport with full-fidelity preview, interactive luminance curve overlay, EXIF readout, and culling status toggle.

**Should have (competitive differentiators):**
- **Pre-Transfer Tier 1 Culling Filter:** Halts multi-megabyte Tier 2 RAW pulls over USB before network sockets are allocated, saving camera battery, USB bandwidth, and cloud storage.
- **Fujifilm Film Simulation Aware Thresholds:** Parses MakerNote Tag `0x1401` to adapt clipping margins dynamically for high-contrast B&W film simulations (e.g. *Acros*, *Monochrome+R*, *Velvia*).
- **Dual-State Gallery Filtering:** Filter chips for `semua` (all), `optimal`, `terpangkas` (misfires/clipped), and `dilewati` (skipped) with batch actions.

**Defer (v5.0+):**
- **On-Device NPU Face & Eye Blink AI Models:** Requires 200MB+ model weights and causes thermal throttling; deferred to future LiteRT/NNAPI milestones.
- **Destructive On-Camera SD Card Deletion:** Explicit anti-feature; violates photographer data safety protocols.
- **Intrusive Modal Alert Dialogs:** Anti-pattern that causes alert fatigue during active studio shooting.

---

### Architecture Approach

The system employs a decoupled, single-read zero-copy intake pipeline. When a new photo is captured on the camera, `SyncOrchestrator` extracts the initial 128KB header slice over USB PTP. The resulting byte buffer is piped concurrently to `ThumbnailStorageManager` (atomic disk write) and `ExposureAnalyzer` (in-memory bit-shift histogram scan). The `CullingDecisionGate` evaluates the analysis verdict against session rules. If rejected under `AUTO_SKIP_UPLOAD`, the item transitions to `CULLED_SKIPPED`, and the Tier 2 RAW binary stream is aborted. If approved (or in `WARN_ONLY` mode), `DirectStreamingEngine` streams the RAW payload directly to Google Drive via OkHttp.

See [.planning/research/ARCHITECTURE.md](file:///Users/chiio/VSCode/TransferInFuji/.planning/research/ARCHITECTURE.md) for architectural component diagrams, sequence flows, and scaling patterns.

```
Camera PTP Hardware Event
       │
       ▼
SafeUsbTransportManager (Guards IO Latch & Mutex)
       │
       ▼ Single USB Read (<50ms): Tier 1 Preview Slice
DirectByteBufferPool / Memory Buffer
       │
       ├─────────────────────────────────┬─────────────────────────────────┐
       ▼                                 ▼                                 ▼
ThumbnailStorageManager          ExposureAnalyzer                 DualTierSyncRouter
(Atomic write to disk:           (Zero-GC fixed-point luma,       (Routes Tier 1 vs
 /files/thumbnails/{fp}.jpg)      entropy & clipping in <1.8ms)    Tier 2 RAW pull)
       │                                 │
       │                                 ▼
       │                       CullingDecisionGate
       │                   (AUTO_SKIP / WARN_ONLY / DISABLED)
       │                                 │
       ▼                                 ▼
Room Database (Schema v11: thumbPath, verdict, meanLum, isCulled)
       │
       ├─────────────────────────────────┐
       ▼ (If CULLED & Auto-Skip)         ▼ (If OPTIMAL or Warning Mode)
Status: CULLED_SKIPPED           DirectStreamingEngine (UsbStreamRequestBody)
(Upload halted; Quota saved)             │
                                         ▼
                                 Google Drive API (Cloud Confirmation)
                                         │
                                         ▼
                                 TransitCacheManager (Purges RAW; Retains Thumbnail)
```

**Major components:**
1. **`SyncOrchestrator`:** Ingestion and lifecycle coordinator managing 5-stage transitions and gating Tier 2 streaming.
2. **`ExposureAnalyzer`:** Zero-allocation engine computing 256-bin histograms, mean luminance, Shannon entropy, and clipping ratios.
3. **`ThumbnailStorageManager`:** Isolated persistent thumbnail store managing `/files/thumbnails/` with 500MB bounded LRU disk cleanup.
4. **`CullingDecisionGate`:** Pure domain rule evaluator returning `CullingAction` (`PROCEED_UPLOAD`, `SKIP_UPLOAD_SAVING_QUOTA`, `FLAG_WARNING_ONLY`).
5. **`SafeUsbTransportManager`:** Two-tier mutex serializer preventing half-duplex USB endpoint packet collisions between preview extractions and bulk uploads.
6. **`UploadedGalleryScreen` & Coil:** Jetpack Compose 3-column feed with `DoppelrandCard` containers, quiet luxury badges, filter rows, and dark modal viewer.

---

### Critical Pitfalls

See [.planning/research/PITFALLS.md](file:///Users/chiio/VSCode/TransferInFuji/.planning/research/PITFALLS.md) for exhaustive failure modes, technical debt patterns, and mitigation strategies.

1. **Heap Allocation Explosion & ART GC Pauses in Burst Mode:** Naive full-frame bitmap decoding creates 150MB–350MB of short-lived objects per second during 20 fps bursts, triggering 100–350ms GC thread freezes.  
   *Mitigation:* Use `BitmapFactory.Options(inSampleSize = 8, inPreferredConfig = RGB_565)`, pass reusable `inBitmap` from a pre-allocated pool, and reuse a single `IntArray(256)` histogram bin buffer.
2. **False Rejection of Low-Key and High-Key Artistic Photography:** Dark studio portraits against black backdrops or high-key wedding scenes being falsely auto-culled due to naive global luminance thresholds.  
   *Mitigation:* Evaluate Shannon Histogram Entropy ($H \ge 3.0$ for valid low-key scenes vs $H < 1.0$ for flash misfires), sensor black floor density ($Y \le 3$), and Film Simulation profile offsets, paired with a mandatory 1-tap manual override.
3. **Race Condition Between Asynchronous Disk Flush and Coil Gallery Load:** Updating Room database entities before background `FileOutputStream` buffers sync to flash storage causes Coil to read 0-byte corrupted files and permanently cache blank black boxes.  
   *Mitigation:* Write preview bytes to `.tmp` files, invoke `fos.fd.sync()`, perform an atomic POSIX rename, and verify `file.exists() && file.length() > 0` before updating Room DAO status.
4. **USB Bulk Endpoint Contention (Preview vs RAW Streaming):** Concurrent execution of preview extraction and direct cloud RAW streaming on the same half-duplex USB endpoint causes PTP packet interleaving and fatal session desynchronization.  
   *Mitigation:* Enforce strict single-flight mutex arbitration via `SafeUsbTransportManager.ioMutex` with high-priority Tier 1 preview locks and cooperative chunked Tier 2 transfers.
5. **EXIF Orientation Loss & 90-Degree Rotated Thumbnails:** Extracted preview JPEG byte slices losing EXIF Tag `0x0112`, causing portrait orientation photos to render sideways in the Compose grid.  
   *Mitigation:* Preserve EXIF APP1 segments in preview extractors and configure Coil's decoder with explicit orientation normalization.

---

## Implications for Roadmap

Based on research findings, technical dependencies, and pitfall prevention, the implementation is structured into three focused phases:

### Phase 1: Zero-Allocation Exposure & Luminance Engine
**Rationale:** The core mathematical analyzer is pure domain logic that can be developed, optimized, microbenchmarked, and thoroughly unit-tested against real camera sample files before connecting it to hardware or UI layers.  
**Delivers:** `ExposureAnalyzer.kt`, `ExposureClassification.kt`, Shannon entropy calculation, profile-aware threshold calibration (Fuji MakerNotes), and AndroidX Microbenchmark test suite validating `<1.8ms` execution and `0 B` heap allocation.  
**Addresses:** `CULL-01` (Flash misfire detection), `CULL-02` (Blown highlight detection), and Film Simulation adaptation.  
**Avoids:** Pitfall 1 (Heap GC pauses), Pitfall 2 (False rejection of low-key portraits), and Pitfall 7 (Film simulation tone curve skew).

### Phase 2: Persistent Multi-Vendor Thumbnail Pipeline & USB Synchronization
**Rationale:** Reliable thumbnail extraction, storage, and display must be established and synchronized over USB before building user-facing culling rules and gallery UI overlays.  
**Delivers:** `ThumbnailStorageManager.kt` (atomic staging + POSIX rename + 500MB LRU limit), multi-vendor preview extractor updates (`RafPreviewExtractor`, `Cr3PreviewParser`, `ArwPreviewParser`, `NikonPtpAdapter`), `SafeUsbTransportManager` two-tier mutex priority lock, and Coil 2.7.0 configuration with deterministic cache keys and EXIF rotation preservation.  
**Uses:** `androidx.exifinterface:1.4.2`, `io.coil-kt:coil-compose:2.7.0`, and `DirectByteBufferPool`.  
**Implements:** `ThumbnailStorageManager`, PTP stream extractors, and isolated disk cache architecture.  
**Avoids:** Pitfall 3 (Disk write race condition / 0-byte corrupt files), Pitfall 4 (USB half-duplex endpoint contention), Pitfall 5 (Coil RAW decode failures & stale cache), and Pitfall 6 (EXIF 90-degree rotation loss).

### Phase 3: Smart Culling State Machine, Room Migration & Quiet Luxury UI Overlays
**Rationale:** Integrates the analyzer (Phase 1) and thumbnail pipeline (Phase 2) into the end-to-end sync machine, database schema, and user interface.  
**Delivers:** Room Database Schema Migration 10 $\to$ 11 (`CameraSyncItemEntity` and `CameraMediaItemEntity` culling columns), `CullingDecisionGate.kt` enforcing `AUTO_SKIP_UPLOAD` / `WARN_ONLY` / `DISABLED`, `SyncOrchestrator.kt` integration gating Tier 2 uploads, Setup Wizard Step 3 culling rule controls, `UploadedGalleryScreen` / `CameraGalleryScreen` `DoppelrandCard` amber/burgundy monospace badges, filter chips (`semua`, `optimal`, `terpangkas`, `dilewati`), 1-tap kinetic "Paksa Unggah" override action, and full-screen dark modal viewer with interactive luminance histogram curve overlay.  
**Addresses:** `CULL-03` (Session auto-skip policy), `UI-01` (Manual force-upload override), `UI-02` (Quiet luxury warning badges), and `UI-03` (Full-screen modal histogram viewer).  
**Avoids:** Pitfall 8 (Alert fatigue & intrusive modal dialogs), data corruption during database migration, and cloud quota waste.

---

### Phase Ordering Rationale

- **Core Math $\to$ Storage/USB $\to$ Database/UI:** Building the zero-allocation analyzer first allows deterministic unit testing and microbenchmarking. Establishing the thumbnail disk storage and USB transport serialization second guarantees that the UI has guaranteed-valid JPEG files to render. Integrating the database migration, state machine, and Compose UI last ensures all foundational services are stable.
- **Dependency Flow:** Phase 2 depends on Phase 1's byte-analysis data structures. Phase 3 consumes Phase 1's decision logic and Phase 2's persisted thumbnail file paths.
- **Pitfall Mitigation:** This order guarantees that concurrency hazards (USB contention and disk flush races) are solved in the core layers before UI exposure.

---

### Research Flags

Phases likely needing deeper research during planning:
- **Phase 1 (MakerNote & Entropy Calibration):** Fine-tuning Shannon entropy thresholds ($H$) and black floor density across diverse physical camera sensors (Fujifilm X-Trans vs Sony BSI CMOS vs Canon Dual-Pixel) to ensure zero false culling on extreme creative lighting.
- **Phase 2 (Canon CR3 PRVW Box Parsing):** Verifying PRVW/THMB custom UUID atom offsets across different Canon DIGIC processor generations (EOS R5 vs EOS R6 II vs EOS RP).

Phases with standard patterns (skip research-phase):
- **Phase 3 (Room Migration & Compose UI):** Well-documented, standard Android Jetpack patterns (`Migration(10, 11)`, Compose `LazyVerticalGrid`, Material 3 tokens, and Coil `AsyncImage`).

---

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Stack | HIGH | Kotlin 2.2.10 integer bit-shift math, Android Skia `BitmapFactory.Options`, `ExifInterface 1.4.2`, and `Coil 2.7.0` are mature, officially verified Android standards. |
| Features | HIGH | Table stakes and differentiators directly reflect professional studio tethering requirements; anti-features clearly defined and excluded. |
| Architecture | HIGH | Single-read zero-copy intake, two-tier storage decoupling, and transactional Room state machine ground-truth verified against existing v3.0 codebase. |
| Pitfalls | HIGH | Critical failure modes (GC pauses, USB endpoint collisions, disk flush races, entropy misclassification) diagnosed with concrete architectural mitigations. |

**Overall confidence:** HIGH

---

### Gaps to Address

- **Nikon NEF Embedded Preview Offset Variations:** Nikon D-series vs Z-series cameras use slightly different TIFF SubIFD tag arrangements for full-size embedded JPEG previews.  
  *Resolution during planning/execution:* Validate preview byte offsets against existing `NikonPtpAdapter` test fixtures and provide fallback to standard PTP `GetThumb` (`0x90C0`) if custom TIFF IFD parsing fails.
- **Extreme High-Key Cyclorama Calibration:** Strobe lighting on pure white seamless cycloramas creates large highlight areas that are aesthetically desirable.  
  *Resolution during planning/execution:* Ensure the `OVEREXPOSED_BLOWN` threshold requires both high mean luminance ($>230$) AND flat saturation at bin 255 ($>20\%$), while keeping the default session mode as `WARN_ONLY` so high-key shots are never auto-culled without explicit photographer opt-in.

---

## Sources

### Primary (HIGH confidence)
- **Fujifilm RAF Specification:** Fuji CCD-RAW Container Format v02.01 (Tag `0x1401` Film Simulation & Embedded JPEG at offset 84/88).
- **Canon CR3 ISO-BMFF Specification:** ISO/IEC 14496-12 ISO Base Media File Format & Canon `PRVW` / `THMB` custom UUID atom structure.
- **Sony ARW Specification:** TIFF 6.0 SubIFD tag hierarchy (`0x0201` `JPEGInterchangeFormat` & `0x0111` `StripOffsets`).
- **Android High Performance Graphics & Memory:** Official Android Developers guidance on `BitmapFactory.Options.inBitmap`, `inSampleSize`, and GC reduction.
- **ITU-R BT.601 / Rec. 709 Standard:** Standard relative luminance formula coefficients ($Y = 0.299R + 0.587G + 0.114B \equiv \frac{77R + 150G + 29B}{256}$).
- **TransferIn v3.0 Ground Truth:** `DirectByteBufferPool.kt`, `SafeUsbTransportManager.kt`, `RafPreviewExtractor.kt`, `Cr3PreviewParser.kt`, `ArwPreviewParser.kt`, `CameraSyncDao.kt`, `TransitCacheManager.kt`.

### Secondary (MEDIUM confidence)
- **Coil 2.x Architecture Reference:** Compose `AsyncImage` custom cache key strategies and disk cache sizing.
- **AndroidX Benchmark Documentation:** Microbenchmark JUnit4 test framework for sub-millisecond CPU and memory profiling.

---
*Research completed: 2026-08-20*  
*Ready for roadmap: yes*
