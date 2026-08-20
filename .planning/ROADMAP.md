# Roadmap: TransferIn

## Milestone v3.0: Multi-Vendor Production Engine & Zero-Leak Tether Core

**Goal:** Transform TransferIn into an agency-grade, zero-leak multi-vendor tethering platform supporting Fujifilm, Canon, Sony, and Nikon cameras with deterministic hardware lifecycles, sub-second preview extraction, direct zero-copy USB-to-cloud streaming, and zero camera firmware freezes.

---

### Execution Waves & Architecture Dependency Graph

```mermaid
graph TD
    subgraph Wave 1: Foundation
        P4["Phase 4: Zero-Leak Hardware Core & Resource Lifecycle<br><code>CORE-01..05</code>"]
    end

    subgraph Wave 2: Vendor Engine Adapters (Parallelizable)
        P5["Phase 5: Fujifilm Native Wired Tether Engine<br><code>FUJI-01..05</code>"]
        P6["Phase 6: Canon EOS Direct PTP Engine<br><code>CANON-01..04</code>"]
        P7["Phase 7: Sony Alpha & Nikon Tether Adapters<br><code>SONY-01..02, NIKON-01..02</code>"]
    end

    subgraph Wave 3: Cloud & Storage Pipeline
        P8["Phase 8: 5-Stage Camera-to-Cloud Sync & Streaming<br><code>SYNC-01..05</code>"]
    end

    subgraph Wave 4: Hardening & Audits
        P9["Phase 9: Production Hardening, Pruning & Audits<br><code>QUAL-01..04</code>"]
    end

    P4 --> P5
    P4 --> P6
    P4 --> P7
    P5 --> P8
    P6 --> P8
    P7 --> P8
    P8 --> P9
```

---

## Phases

### Phase 4: Zero-Leak Hardware Core & Resource Lifecycle

**Requirements:** `CORE-01`, `CORE-02`, `CORE-03`, `CORE-04`, `CORE-05`  
**Focus:** Establishing deterministic USB connection lifecycle, native JNI IO execution latch, direct memory buffer pooling, Android 14/15 foreground service compliance, and eliminating all off-heap/file-descriptor resource leaks.

#### Scope & Architecture
- Build `SafeUsbTransportManager` providing an atomic transfer latch and mutex protection to prevent native `SIGSEGV` in `libusbhost.so` during rapid physical cable disconnects.
- Implement deterministic `UsbHardwareSession` state machine (`Idle` $\to$ `Connected` $\to$ `Handshake` $\to$ `ActiveStreaming` $\to$ `Disconnecting` $\to$ `CleanTerminated`).
- Create `DirectByteBufferPool` offering pre-allocated off-heap direct slices (128 KB command buffers, 1 MB data streaming buffers) to completely eliminate GC pauses on 40MP–100MP RAW ingest.
- Refactor `UsbTetherService` to bind all coroutines to a centralized lifecycle `SupervisorJob` with non-cancellable application teardown barriers (3.5s watchdog timeout), fully compliant with Android 14/15 `FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE` (immediate `startForeground()` in `onCreate()`).
- Audit and encapsulate all `InputStream`, `ParcelFileDescriptor`, and `ContentResolver` operations inside strict `.use { }` resource blocks across repositories and WorkManager workers.

#### Success Criteria
1. **Zero Native SIGSEGV on Rapid Disconnect:** 100 consecutive rapid USB cable disconnect/reconnect cycles under active heavy I/O yield zero native JNI crashes or kernel panics.
2. **Zero JVM GC Thrashing:** Bulk transfer of 100 consecutive 50MB simulated RAW frames allocates zero transient byte arrays on the JVM heap, maintaining heap delta under 5MB.
3. **Deterministic Service Teardown:** Service destruction executes complete teardown (interrupt cancel $\to$ release interface $\to$ connection close) within 500ms even if parent coroutine scope is cancelled prematurely.
4. **Android 14/15 Foreground Compliance:** `UsbTetherService` activates foreground notification within 50ms of `onCreate()`, immune to the 5-second `ForegroundServiceDidNotStartInTimeException`.

#### Mandatory Quality Gates
- **AAS Code Review (`/gsd-code-review`):** Verify zero unmanaged coroutine scopes, atomic memory barriers, and strict AutoCloseable patterns on all USB handles.
- **Protocol & Memory Regression:** Automated LeakCanary 2.x assertion suite verifying zero `UsbDeviceConnection` or `ParcelFileDescriptor` leaks on hardware termination.

---

### Phase 5: Fujifilm Native Wired Tether Engine & Clean Disconnect Lifecycle

**Requirements:** `FUJI-01`, `FUJI-02`, `FUJI-03`, `FUJI-04`, `FUJI-05`  
**Focus:** Native PTP protocol implementation for Fujifilm cameras (VID `0x04CB`), event-driven interrupt handling, clean disconnect handshake, embedded RAF preview extraction, and film simulation metadata parsing.

#### Scope & Architecture
- Implement `FujiTetherAdapter` using raw PTP bulk/interrupt commands, bypassing legacy MTP device wrappers.
- Implement explicit session initialization handshake: `0x1002 OpenSession` $\to$ `0x902B SetDevicePropValue(0xD214, 0x0001)` (Host Control) $\to$ `0x902B SetDevicePropValue(0xD216, 0x0002)` (PC + Card destination).
- Build asynchronous Interrupt IN endpoint (`0x83`) continuous drain listener to capture `0x4002` / `0xC001` `ObjectAdded` events in <15ms ($O(1)$ discovery latency), eliminating the $O(N)$ full-card metadata scan loop.
- Implement guaranteed 4-step clean teardown sequence in `NonCancellable` context: `0xD214 = 0x0000` (Host Control Release) $\to$ `0x1003 CloseSession` $\to$ `releaseInterface(usbInterface)` $\to$ `connection.close()`.
- Implement fast `.RAF` binary parser extracting embedded high-resolution JPEG previews directly from RAW file headers within the first 128KB payload.
- Extract Fujifilm Film Simulation tags (Provia/Standard, Velvia/Vivid, Astia/Soft, Classic Chrome, Classic Neg, ACROS, Nostalgic Neg) and display in the live Studio HUD.

#### Success Criteria
1. **Elimination of Camera "BUSY" / "PC TETHER" Freeze:** Disconnecting or quitting the app on Fujifilm cameras (X-T3/T4/T5, X-H2, GFX) returns the camera LCD to live shooting mode within 500ms without requiring a battery pull.
2. **Sub-15ms Event Intake:** Capture notification arrives via Interrupt IN pipe within 15ms of shutter actuation on a card with >2,000 existing photos.
3. **Instant Preview Scroller Rendering:** Embedded preview JPEG extracted and rendered in the live UI in <400ms without downloading the full 40MB+ `.RAF` binary.
4. **Accurate Film Simulation Telemetry:** Active film simulation recipe name is accurately parsed and displayed in the Live Session HUD.

#### Mandatory Quality Gates
- **AAS Code Review (`/gsd-code-review`):** Strict verification of PTP packet formatting, non-blocking interrupt polling, and non-cancellable release sequences.
- **AAS UI Review (`/gsd-ui-review`):** Audit of live film simulation badge rendering against RVM quiet luxury design tokens (`rvmeur.com`).
- **Real Protocol Regression:** Live PTP opcode sequence verification against physical/virtualized Fuji protocol stream ensuring strict `0xD214` release execution on disconnect.

---

### Phase 6: Canon EOS Direct PTP Engine & Fast Preview Extraction

**Requirements:** `CANON-01`, `CANON-02`, `CANON-03`, `CANON-04`  
**Focus:** High-speed Canon EOS PTP protocol engine (VID `0x04A9`), continuous DIGIC event queue draining, sub-500ms `.CR3` ISO-BMFF box parsing, resilient `.CTG` fallback, and multi-slot enumeration.

#### Scope & Architecture
- Implement `CanonPtpAdapter` utilizing EOS PTP Remote Mode (`0x9114 = 1`) and Event Mode (`0x9115 = 1`).
- Build dedicated event drain coroutine continuously executing `0x9116 EOS_GetEvent` / Interrupt IN polling to eliminate DIGIC circular ring buffer overflow and shutter lockups.
- Implement `.CR3` ISO-BMFF box structure parser scanning `PRVW` and `THMB` atoms within the first 128KB of the USB transfer stream.
- Provide a two-stage stability-verified fallback for legacy Canon EOS bodies using `.CTG` catalogs (`CANONMSC/CANON.CTG` binary delta diff with size/mod-time invariance check).
- Implement multi-storage descriptor enumeration mapping Slot 1 (CFexpress) and Slot 2 (SD) independently without handle collisions.

#### Success Criteria
1. **Zero DIGIC Event Overflow:** Continuous 20 fps burst shooting over 50 shots produces zero "Busy" shutter stalls or lost frames.
2. **Sub-500ms CR3 Preview Extraction:** Embedded JPEG preview extracted and pushed to UI stream from `.CR3` ISO-BMFF headers within 500ms of shutter release.
3. **Resilient CTG Fallback:** Legacy CTG mode reliably detects new frames with zero 0-byte corrupted reads across card writes.
4. **Dual-Card Handle Isolation:** Simultaneous capture to Slot 1 and Slot 2 correctly resolves separate object handles without dropping or conflating media.

#### Mandatory Quality Gates
- **AAS Code Review (`/gsd-code-review`):** Validate ISO-BMFF byte offset safety, bounds checking, and dual-slot handle partitioning.
- **Real Protocol Regression:** Verification of Canon EOS PTP event packet sequence and DIGIC event pump resilience under simulated high burst rates.

---

### Phase 7: Sony Alpha & Nikon Multi-Vendor Tether Adapters

**Requirements:** `SONY-01`, `SONY-02`, `NIKON-01`, `NIKON-02`  
**Focus:** Sony Alpha SDIO PTP adapter (VID `0x054C`), "PC+Camera" destination safety guard, multi-packet defragmentation, Nikon vendor PTP adapter (VID `0x04B0`), and EXPEED `0x2019 DeviceBusy` exponential backoff.

#### Scope & Architecture
- Implement `SonyPtpAdapter` handling `0x9280 SDIOConnect` handshake and `0x9281 GetSDIOExtDeviceInfo`.
- Implement automated verification of Sony Save Destination property, ensuring `PC + Camera` is configured to prevent volatile camera DRAM data loss.
- Implement multi-packet PTP payload defragmentation handling Zero-Length Packets (ZLP) and short packet boundaries; extract embedded preview TIFF SubIFD from `.ARW` containers.
- Implement `NikonPtpAdapter` with Nikon vendor extensions (`0x90C0 CheckEvent`, `0x90C1 SetEventMode`) and dual-slot storage ID enumeration (`0x00010001` Slot 1, `0x00020001` Slot 2).
- Build adaptive exponential backoff retry loop (base 25ms, multiplier 1.5, max 8 retries) for Nikon `0x2019 PTP_RC_DeviceBusy` responses during in-camera EXPEED card flushes.
- Unify Fuji, Canon, Sony, and Nikon adapters behind `CameraTetherEngine` factory with automatic USB VID/PID detection.

#### Success Criteria
1. **Sony DRAM Safety Guard:** Connecting a Sony camera configured to "PC Only" immediately triggers safety notification and switches/alerts to enforce `PC + Camera` destination.
2. **Sony ARW Preview Extraction:** Embedded JPEG preview extracted from `.ARW` TIFF SubIFD in <500ms.
3. **Nikon EXPEED Busy Tolerance:** `0x2019 DeviceBusy` during continuous Nikon shooting retries cleanly with zero dropped sessions or crashes.
4. **Unified Multi-Vendor Auto-Detection:** Plugging in any Fuji, Canon, Sony, or Nikon body instantly instantiates the correct vendor adapter within 200ms of USB permission grant.

#### Mandatory Quality Gates
- **AAS Code Review (`/gsd-code-review`):** Verify multi-vendor factory polymorphism, retry backoff jitter safety, and TIFF/Exif header parsing bounds checks.
- **Real Protocol Regression:** Protocol simulation test suite for Sony SDIO packet defragmentation and Nikon busy response backoff curves.

---

### Phase 8: 5-Stage Camera-to-Cloud Sync & Direct USB Streaming

**Requirements:** `SYNC-01`, `SYNC-02`, `SYNC-03`, `SYNC-04`, `SYNC-05`  
**Focus:** Transactional 5-stage media state machine (`DISCOVERED` $\to$ `CACHED` $\to$ `QUEUED` $\to$ `UPLOADING` $\to$ `CONFIRMED`), composite SHA-256 fingerprinting, zero-copy `UsbStreamRequestBody` OkHttp streaming, and dual-tier sync mode.

#### Scope & Architecture
- Standardize Room SQLite persistence with `CameraMediaItemEntity` and `TransferQueueItemEntity` implementing the strict 5-stage transactional state machine.
- Implement composite SHA-256 media fingerprinting (`SHA-256(CameraSerial + StorageID + Filename + SizeBytes + Timestamp)`) to ensure 100% idempotent deduplication across USB disconnect/reconnect cycles.
- Build zero-copy `UsbStreamRequestBody` piping binary streams directly from USB Bulk IN endpoints to Google Drive Resumable Upload HTTP endpoints via OkHttp `BufferedSink` / `FileChannel`.
- Implement local transit cache storage manager with an LRU 5GB boundary and automatic cache TTL eviction.
- Implement Dual-Tier Sync Router: Priority 1 instant upload of 3-second JPEG previews to cloud gallery, Priority 2 background streaming of full RAW files for archival.

#### Success Criteria
1. **Zero-Copy Direct Streaming:** RAW files stream directly from USB Bulk IN to Google Drive upload socket with zero intermediate 50MB byte array heap allocations.
2. **100% Deduplication Across Reconnects:** Reconnecting a camera with 500 already-transferred photos queues exactly 0 duplicate uploads.
3. **Sub-3s Dual-Tier Cloud Preview:** Captured frame thumbnail/preview is confirmed in Google Drive within 3 seconds of shutter release on standard LTE/Wi-Fi.
4. **LRU 5GB Boundary Enforcement:** Local transit cache stays strictly within the 5GB limit, evicting oldest confirmed media without deleting un-uploaded items.

#### Mandatory Quality Gates
- **AAS Code Review (`/gsd-code-review`):** Validate database transaction boundaries, OkHttp custom RequestBody thread safety, and LRU eviction safety invariants.
- **AAS UI Review (`/gsd-ui-review`):** Audit of real-time upload progress indicators, speed telemetry, and 5-stage state transitions in `SessionActiveScreen.kt`.
- **Real Protocol & Network Regression:** Simulated upload throttling, connection dropouts, and resumption validation without data corruption.

---

### Phase 9: Production Hardening, Codebase Pruning & Comprehensive Audits

**Requirements:** `QUAL-01`, `QUAL-02`, `QUAL-03`, `QUAL-04`  
**Focus:** Tech debt eradication, removal of dead code (`UsbDisconnectHandler.kt`, `SessionOrchestrator.kt`, fake `MediaStore` queries), USB fuzzing & protocol test harnesses, AAS Code & UI audits, and security/privacy verification.

#### Scope & Architecture
- Purge all obsolete/dead code files: delete `UsbDisconnectHandler.kt` (361 dead lines), remove stub `SessionOrchestrator.kt`, eliminate fake `MediaStore.Images` fallback queries in `ShootModeEngine.kt`.
- Consolidate split session implementations (`ProjectSessionManagerImpl` vs `TransferSessionManagerImpl`) into a single unified `SessionRepository` backed by Room reactive flows.
- Fix `UsbPermissionManager.kt` `PendingIntent` flags to enforce `FLAG_MUTABLE` on API 31+ and `0` on older Android versions.
- Build comprehensive USB packet fuzzing and protocol state machine test harness (verifying malformed PTP headers, truncated packets, and sudden termination recovery without mocks).
- Execute comprehensive AAS Code Review and UI Review audits enforcing strict memory safety, non-blocking UI threads, and RVM quiet luxury design token fidelity.
- Perform end-to-end Security & Privacy audit: verify OAuth token storage in Android `EncryptedSharedPreferences` / Jetpack Security Crypto and verify zero transit data leakage.

#### Success Criteria
1. **Zero Dead Code & Unified Session Architecture:** 100% deletion of identified legacy files with zero broken dependencies and single-source-of-truth session orchestration.
2. **Android 12–15 USB Compatibility:** Zero `IllegalArgumentException` on `PendingIntent` creation across API 31 through 35 devices.
3. **Fuzz & Protocol Robustness:** Protocol state machine survives 10,000 randomized malformed PTP packet injections without unhandled exceptions or state deadlock.
4. **Zero Security & Memory Violations:** Passed mandatory AAS Code Review, UI Review, and Security Audits with zero critical or high-severity findings.

#### Mandatory Quality Gates
- **AAS Code Review (`/gsd-code-review`):** Deep audit of all Kotlin classes, coroutine scopes, and architecture layers.
- **AAS UI Review (`/gsd-ui-review`):** Full design token audit against `rvmeur.com` quiet luxury guidelines.
- **AAS Security Audit (`/gsd-secure-phase`):** Cryptographic verification of credential storage and data boundary integrity.

---

## Requirements Traceability Matrix

| Requirement ID | Requirement Description | Mapped Phase | Status |
| :--- | :--- | :--- | :--- |
| **CORE-01** | Deterministic USB hardware session lifecycle with JNI IO execution latch | Phase 4 | Planned |
| **CORE-02** | Zero unmanaged coroutine leaks and zero Android `usbfs` file descriptor leaks | Phase 4 | Planned |
| **CORE-03** | Direct native `DirectByteBufferPool` (128KB/1MB slices) for high-throughput streaming | Phase 4 | Planned |
| **CORE-04** | Android 14+ `FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE` lifecycle compliance | Phase 4 | Planned |
| **CORE-05** | Eliminate leaked `ContentResolver` file descriptors and duplicate thumbnail decoding | Phase 4 | Planned |
| **FUJI-01** | Native PTP `FujiTetherAdapter` with explicit `0xD214` / `0xD216` property handshakes | Phase 5 | Planned |
| **FUJI-02** | Asynchronous Interrupt IN (`0x83`) drain capturing `0x4002`/`0xC001` in <15ms ($O(1)$) | Phase 5 | Planned |
| **FUJI-03** | 4-step clean teardown sequence preventing camera LCD "BUSY" / "PC TETHER" firmware freezes | Phase 5 | Planned |
| **FUJI-04** | Direct `.RAF` embedded JPEG preview header extraction without full RAW download | Phase 5 | Planned |
| **FUJI-05** | Fujifilm Film Simulation metadata extraction and display in live session HUD | Phase 5 | Planned |
| **CANON-01** | `CanonPtpAdapter` with EOS Remote/Event modes and continuous `0x9116` event drain | Phase 6 | Planned |
| **CANON-02** | Sub-500ms `.CR3` ISO-BMFF box structure (`PRVW`/`THMB`) preview parser | Phase 6 | Planned |
| **CANON-03** | Resilient two-stage stability-verified fallback for legacy `.CTG` catalog structures | Phase 6 | Planned |
| **CANON-04** | Simultaneous multi-card slot enumeration (CFexpress + SD) without handle collision | Phase 6 | Planned |
| **SONY-01** | `SonyPtpAdapter` with `0x9280 SDIOConnect` and `PC + Camera` destination safety guard | Phase 7 | Planned |
| **SONY-02** | Sony multi-packet PTP payload defragmentation and `.ARW` SubIFD preview extraction | Phase 7 | Planned |
| **NIKON-01** | `NikonPtpAdapter` supporting vendor commands (`0x90C0`/`0x90C1`) & dual-slot storage | Phase 7 | Planned |
| **NIKON-02** | Adaptive exponential backoff retry loop for Nikon `0x2019 PTP_RC_DeviceBusy` responses | Phase 7 | Planned |
| **SYNC-01** | Transactional 5-stage media state machine backed by Room DB | Phase 8 | Planned |
| **SYNC-02** | Composite SHA-256 media fingerprints for idempotent deduplication | Phase 8 | Planned |
| **SYNC-03** | Direct zero-copy streaming from USB Bulk IN to Google Drive via `UsbStreamRequestBody` | Phase 8 | Planned |
| **SYNC-04** | Local transit storage manager with LRU 5GB boundary and automatic cache TTL eviction | Phase 8 | Planned |
| **SYNC-05** | Dual-tier sync mode (3-second JPEG preview delivery + background RAW archival) | Phase 8 | Planned |
| **QUAL-01** | Purge dead legacy code (`UsbDisconnectHandler.kt`, `SessionOrchestrator.kt`, etc.) | Phase 9 | Planned |
| **QUAL-02** | Real USB packet fuzzing and protocol state machine regression test harnesses | Phase 9 | Planned |
| **QUAL-03** | Mandatory AAS Code Review and UI Review audits (memory safety, quiet luxury) | Phase 9 | Planned |
| **QUAL-04** | Comprehensive Security & Privacy audits (EncryptedSharedPreferences, zero leakage) | Phase 9 | Planned |

---

## Previous Milestones (Archived)

### Milestone v2.0: Fluid Wizard & Agency-Grade UI/UX Overhaul (Shipped: 2026-08-12)
- [x] Phase 1: Design System & Motion Foundations
- [x] Phase 2: Fluid Swipeable Setup Wizard
- [x] Phase 3: Live Shoot Dashboard & Gallery Polish
- Archive: [v2.0-fluid-wizard-ROADMAP.md](file:///Users/chiio/VSCode/TransferInFuji/.planning/milestones/v2.0-fluid-wizard-ROADMAP.md)

### Milestone v1.0: Rebranding & Google Drive Migration (Shipped: 2026-08-12)
- [x] Phase 1: Rebranding & Package Rename
- [x] Phase 2: UI Style Refactoring (rvmeur.com Design)
- [x] Phase 3: Google Drive Integration & NAS Replacement
- Archive: [v1.0-rebranding-ROADMAP.md](file:///Users/chiio/VSCode/TransferInFuji/.planning/milestones/v1.0-rebranding-ROADMAP.md)

### Milestone v2 (Legacy): Camera File Numbering & Traversal Alignment (Shipped: 2026-08-07)
- [x] Phase 6: Camera File Number Baseline Extraction
- [x] Phase 7: Robust RAW+JPG Co-existence & Verification
- [x] Phase 8: Canon and Fuji Connection Polish

---

*Roadmap generated: 2026-08-20 for Milestone v3.0*
