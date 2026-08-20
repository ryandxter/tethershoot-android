# Executive Research Synthesis: Multi-Vendor Tethering & Zero-Leak Production Engine

**Document Version:** 3.0.0  
**Target Milestone:** v3.0 Multi-Vendor Production Engine & Zero-Leak Tether Core  
**Date:** 2026-08-20  
**Status:** Validated Architecture, Protocol Specifications & Implementation Blueprint  
**Input Artifacts:** `STACK.md`, `FEATURES.md`, `ARCHITECTURE.md`, `PITFALLS.md`, `TECH-DEBT-AUDIT.md`  
**Target Platform:** Android 12 to 15 (API 31–35, Target SDK 35/37)  
**Supported Vendors:** Fujifilm (`0x04CB`), Canon EOS (`0x04A9`), Sony Alpha (`0x054C`), Nikon (`0x04B0`)

---

## 1. Executive Summary & Core Mission for Milestone v3.0

TransferIn is transitioning from an experimental, single-vendor Android MTP/CTG file scanner burdened by concurrency deadlocks and memory churn into an agency-grade, deterministic **Direct USB Multi-Vendor Tethering & Cloud Synchronization Engine**.

Professional studio and on-location photography demands:
1. **Sub-Second Frame Ingestion:** Immediate thumbnail/preview extraction (<500ms) and live HUD display upon shutter release during high-speed bursts (up to 20 fps).
2. **Deterministic Hardware Lifecycle & Zero Freezes:** 100% elimination of the infamous Fujifilm / Canon "BUSY" and "PC TETHER" firmware locks that require physical camera battery pulls.
3. **Zero-Memory-Leak & Zero-FD Bleed Core:** Complete resource hygiene across 12-hour continuous shoots (>5,000 RAW shots, >250 GB), eliminating JVM Garbage Collection pauses, off-heap native leaks, and Android OS Low Memory Killer (LMK) terminations.
4. **Android 14/15 Foreground Service Compliance:** Full conformance with `FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE`, immune to Android 15's 6-hour daily background cutoff while hardware remains connected.
5. **Two-Tier Resumable Cloud Streaming:** Direct USB-to-Cloud zero-copy streaming pipeline to Google Drive with automatic offline caching, fingerprint deduplication, and LRU transit cache management.

```
┌──────────────────────────────────────────────────────────────────────────────────────────────────┐
│                                TRANSFERIN V3.0 UNIFIED ARCHITECTURE                              │
├──────────────────────────────────────────────────────────────────────────────────────────────────┤
│  PRESENTATION LAYER (Jetpack Compose, Live Studio HUD, Exposure Metrics, Preview Scroller)      │
└────────────────────────────────────────────────┬─────────────────────────────────────────────────┘
                                                 │ StateFlow / SharedFlow
┌────────────────────────────────────────────────▼─────────────────────────────────────────────────┐
│  SESSION & DOMAIN LAYER                                                                          │
│  SessionOrchestrator • SmartUploadFilter • DualTierSyncRouter • FingerprintDeduplicator          │
└────────────────────────────────────────────────┬─────────────────────────────────────────────────┘
                                                 │ Direct Adapter Contract
┌────────────────────────────────────────────────▼─────────────────────────────────────────────────┐
│  UNIFIED TETHER ABSTRACTION (`CameraTetherEngine`)                                               │
│  ┌──────────────────────┬──────────────────────┬──────────────────────┬──────────────────────┐   │
│  │  FujiTetherAdapter   │   CanonPtpAdapter    │    SonyPtpAdapter    │    NikonPtpAdapter   │   │
│  │   (VID: 0x04CB)      │    (VID: 0x04A9)     │    (VID: 0x054C)     │    (VID: 0x04B0)     │   │
│  │  • 0xD214 Handshake  │  • EOS Remote 0x9114 │  • SDIO Connect      │  • Direct Capture    │   │
│  │  • Interrupt Drain   │  • EOS Event 0x9116  │  • PC+Card Enforced  │  • Busy (0x2019) BO  │   │
│  │  • Clean Teardown    │  • CTG Fast-Diff     │  • Packet Defrag     │  • Dual-Slot Mapping │   │
│  └──────────┬───────────┴──────────┬───────────┴──────────┬───────────┴──────────┬───────────┘   │
└─────────────┼──────────────────────┼──────────────────────┼──────────────────────┼───────────────┘
              │                      │                      │                      │
┌─────────────▼──────────────────────▼──────────────────────▼──────────────────────▼───────────────┐
│  HARDWARE GOVERNANCE & MEMORY SAFETY LAYER                                                       │
│  • UsbHardwareSession (Deterministic AutoCloseable)  • SafeUsbTransportManager (SIGSEGV Latch)   │
│  • DirectByteBufferPool (128KB/1MB Direct Slices)    • ServiceScope Lifecycle (NonCancellable)   │
└────────────────────────────────────────────────┬─────────────────────────────────────────────────┘
                                                 │ Direct NIO Stream
┌────────────────────────────────────────────────▼─────────────────────────────────────────────────┐
│  PERSISTENCE & CLOUD PIPELINE                                                                    │
│  • Room DB (Transactional Fingerprints)  • Google Drive REST v3 (Resumable Chunked OkHttp Stream)│
└──────────────────────────────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Key Technical Findings & Forensic Resolutions

### 2.1 The Fujifilm "Infinite Recursive Loop" & Camera Freeze Resolution

#### Forensic Root Cause
When connected to Fujifilm cameras (X-T3, X-T4, X-T5, X-H2, GFX series) set to **`USB TETHER SHOOTING AUTO/FIXED`**:
1. **Missing CTG File Fallback Disaster:** `CtgWatcher` looks for Canon-specific `.CTG` files. Finding none, it executes `findLatestFile()`, running `getAllObjectHandles()` and looping through all $N$ handles (up to 3,000 photos) calling `getObjectMetadata()` over USB bulk endpoints.
2. **Concurrency Storm:** Both `CtgWatcher` and `PhotoWatcher` execute this $O(N)$ blocking loop every 2,000ms simultaneously without synchronization.
3. **Interrupt EP Buffer Starvation:** Fuji's internal X-Processor firmware halts internal buffer flushing until the host drains the **Interrupt IN endpoint (`0x83`)**. Polling Bulk OUT (`0x1008`) while the Interrupt pipe is full returns `PTP_RC_DEVICE_BUSY` (`0x2019`), triggering more retries and deadlocking the USB transceiver.
4. **Unclean Disconnect (Dirty Drop):** When Android closes the USB file descriptor without sending Fuji's host-control release sequence (`0xD214 = 0x0000`), the camera remains locked in PC-tether state with dials disabled and LED flashing, forcing a physical battery pull.

#### Deterministic Architectural Resolution
- **Direct PTP Adapter (`FujiTetherAdapter`):** Bypass `android.mtp.MtpDevice` completely. Take direct control of Bulk OUT, Bulk IN, and Interrupt IN endpoints.
- **Handshake Sequence:** On session open:
  1. `OpenSession` (`0x1002`, `SessionID = 1`).
  2. `SetDevicePropValue` (`0x902B`) $\to$ Property `0xD214` (Host Control) = `0x0001` (Active).
  3. `SetDevicePropValue` (`0x902B`) $\to$ Property `0xD216` (Destination) = `0x0002` (PC + Card).
- **Asynchronous Interrupt Listener:** A dedicated coroutine continuously drains the Interrupt IN endpoint (`0x83`). When event `0x4002` or `0xC001` (`ObjectAdded`) arrives, metadata and binary streams are fetched on demand ($O(1)$ latency <15ms).
- **Mandatory Clean Teardown Protocol:**
  ```kotlin
  // Guaranteed execution in NonCancellable block:
  setFujiProperty(PROP_FUJI_HOST_CONTROL, HOST_CONTROL_RELEASE, txId = 999) // 0xD214 = 0x0000
  sendPtpCommand(OC_CLOSE_SESSION, txId = 1000, params = intArrayOf(1))     // 0x1003
  connection.releaseInterface(usbInterface)
  connection.close()
  ```

---

### 2.2 Canon EOS PTP Direct Events vs. CTG Fallback

#### Forensic Analysis
- **Legacy CTG Flaws:** Polling `CANONMSC/CANON.CTG` has file-write race conditions where the FAT catalog updates *before* RAW payloads finish flushing from DIGIC buffer memory to flash, resulting in 0-byte or corrupted `.CR3` reads. Moreover, EOS R-series mirrorless cameras bypass `.CTG` updates in certain tether modes.
- **Event Buffer Overflow:** When `EOS_SetRemoteMode(1)` (`0x9114`) and `EOS_SetEventMode(1)` (`0x9115`) are enabled, Canon cameras divert all hardware events into an internal 256-entry ring buffer. Failing to drain this via `EOS_GetEvent` (`0x9116`) or Interrupt EP causes the camera to flash `"Busy"` on the top LCD and block the physical shutter.

#### Deterministic Architectural Resolution
- **Fast Path:** Listen on Interrupt IN or drain `0x9116 EOS_GetEvent` to receive `0xC101 EOS_ObjectAddedEx` instantly.
- **Resilient Fallback:** Two-stage file stability verification (confirming size/mod-time invariance across two intervals) before importing via CTG delta diffs.
- **CR3 Box Structure Parsing:** Extract `PRVW` and `THMB` ISO-BMFF boxes from `.CR3` files within the first 128KB of the stream, displaying instant previews without downloading the full 50MB mosaic payload.

---

### 2.3 Sony Alpha & Nikon PTP Protocol Engineering

#### Sony Alpha Architecture
- **USB Mode Requirement:** Camera must be configured in `PC Remote` mode.
- **Handshake:** `OpenSession (0x1002)` $\to$ `Sony_SDIOConnect (0x9280)` $\to$ `Sony_GetSDIOExtDeviceInfo (0x9281)`.
- **"PC Only" Volatile Memory Hazard:** If configured to "PC Only", images exist only in camera volatile DRAM. TransferIn v3.0 detects this setting, warns the user, and defaults to `PC + Camera` to prevent data loss on cable disconnects.
- **Packet Fragmentation:** Handles multi-packet chunking terminated by Zero-Length Packets (ZLP) or short packets.

#### Nikon PTP Architecture
- **Handshake & Capture:** Uses Nikon vendor extension `0x0000000A` with opcodes `0x90C0` (`CheckEvent`) and `0x90C1` (`SetEventMode`).
- **`PTP_RC_DeviceBusy (0x2019)` Backoff:** Nikon EXPEED processors lock USB bulk endpoints during card flushes. The engine implements exponential backoff (base 25ms, multiplier 1.5, max 8 retries) rather than crashing or terminating the session.
- **Dual-Slot Storage ID Partitioning:** Separate enumeration of Slot 1 (`0x00010001`) and Slot 2 (`0x00020001`) to prevent buffer truncation on large cards (>10,000 objects).

---

### 2.4 Zero-Leak Hardware Lifecycle & Memory Safety

#### Root Cause Analysis of Tech Debt
1. **Service Teardown Race Condition:** `UsbTetherService.onDestroy()` launched cleanup on `serviceScope` and immediately called `serviceScope.cancel()`, cancelling the cleanup coroutine before `closeCameraSession()` could execute.
2. **Kernel `usbfs` File Descriptor Leaks:** Calling `connection.close()` before `connection.releaseInterface(intf)` or releasing during native blocking reads leaked kernel file descriptors, preventing subsequent `openDevice()` calls until OS reboot.
3. **Unclosed Thumbnail Streams:** `UploadQueueManager.createThumbnail()` opened `InputStream` and `ParcelFileDescriptor` instances without `.use { }` blocks, causing `EMFILE (Too many open files)` crashes on RAW decodes.
4. **Duplicate Thumbnail Work:** Thumbnails were generated twice per photo (once on discovery, once on upload completion), doubling CPU/memory load.

#### Zero-Leak Architectural Enforcements
- **`UsbHardwareSession` Wrapper:** Guarantees deterministic, sequential release (`Interrupt Job Cancel` $\to$ `Vendor Release` $\to$ `PTP CloseSession` $\to$ `releaseInterface` $\to$ `connection.close`).
- **Application Cleanup Scope:** Service cleanup is dispatched on an application-level `SupervisorJob` using `withContext(NonCancellable)` with a 3,500ms watchdog barrier.
- **Safe USB Transport Latch:** `SafeUsbTransportManager` uses an atomic active transfer counter and state mutex to prevent `SIGSEGV` in `libusbhost.so` when the USB cable is physically unplugged during an active `bulkTransfer`.
- **Single-Pass Thumbnail Pipeline:** Thumbnails generated once at intake via `ExifInterface` / embedded preview parser with strict `.use { }` blocks; path persisted in Room DB.
- **LeakCanary 2.x Integration:** Automated CI assertions and `AppWatcher.objectWatcher` validation on hardware session destruction.

---

### 2.5 High-Throughput Direct Streaming & Buffer Pooling

- **Heap Thrashing Elimination:** Traditional `ByteArray(50 * 1024 * 1024)` allocations for RAW images cause massive GC pauses (100–400ms), dropping USB packets.
- **Direct ByteBuffer Pool (`DirectByteBufferPool`):** Pre-allocated pool of 128 KB (command/stream) and 1 MB (data) direct buffers (`ByteBuffer.allocateDirect`).
- **Zero-Copy OkHttp Streaming:** `UsbStreamRequestBody` reads directly from USB Bulk IN endpoints into OkHttp `BufferedSink` / `FileChannel`, piping binary payloads directly to Google Drive or local transit cache without intermediate heap allocation.

---

## 3. Multi-Vendor Protocol Specifications Matrix

| Protocol Dimension | Fujifilm Native Tether | Canon EOS Tether | Sony Alpha Remote | Nikon Vendor PTP |
| :--- | :--- | :--- | :--- | :--- |
| **USB Vendor ID (VID)** | `0x04CB` | `0x04A9` | `0x054C` / `0x054D` | `0x04B0` |
| **Primary USB Mode** | `USB Tether Shooting Auto/Fixed` | `EOS Remote Mode` | `PC Remote (PC+Camera)` | `PTP Remote / Still Image` |
| **Vendor Extension ID** | `0x0000000E` / `0x00000010` | `0x0000000B` / `0x00000001` | `0x00000011` / `0x00000012` | `0x0000000A` |
| **USB Class / Subclass** | `0x06`/`0x01` or `0xFF`/`0x01` | `0x06` / `0x01` / `0x01` | `0x06` / `0x01` / `0x01` | `0x06` / `0x01` / `0x01` |
| **Handshake OpCodes** | `0x1002` OpenSession<br>`0x902B` SetProp `0xD214=1`<br>`0x902B` SetProp `0xD216=2` | `0x1002` OpenSession<br>`0x9114` SetRemoteMode(1)<br>`0x9115` SetEventMode(1) | `0x1002` OpenSession<br>`0x9280` SDIOConnect<br>`0x9281` GetSDIOExtInfo | `0x1002` OpenSession<br>`0x90C1` SetEventMode(1)<br>`0x90C0` GetVendorProps |
| **Capture Notification** | Interrupt `0x83`: `0x4002` / `0xC001` | Interrupt `0x83`: `0xC101` / `0x4002` or `0x9116` | Interrupt `0x83`: `0x4002` / `0xC201` or `0x9284` | Interrupt `0x83`: `0x4002` or `0x90C0 CheckEvent` |
| **Fallback Discovery** | Incremental Handle Diff ($O(k)$) | `CANON_MDC.CTG` Binary Diff | Storage Descriptor Polling | Per-Slot Handle Polling |
| **Clean Teardown** | `0x902B` SetProp `0xD214=0`<br>`0x1003` CloseSession | `0x9114` SetRemoteMode(0)<br>`0x1003` CloseSession | `0x1003` CloseSession | `0x1003` CloseSession |
| **RAW Format** | `.RAF` (X-Trans / Bayer) | `.CR2` / `.CR3` (ISO-BMFF) | `.ARW` (TIFF Structure) | `.NEF` (TIFF Structure) |
| **Fast Preview Extraction** | Extract embedded JPEG from RAF header | Extract `PRVW`/`THMB` boxes from CR3 | Extract JPEG from SubIFD0 | Extract JPEG from SubIFD preview |
| **Known Hardware Quirk** | Camera UI freezes if `0xD214=0` omitted on exit | Circular event queue overflow locks shutter ("Busy") | "PC Only" setting drops photos on disconnect | `0x2019 DeviceBusy` during card flush |

---

## 4. 5-Stage Camera-to-Cloud Sync State Engine

Every discovered image transitions through a deterministic, transactional 5-stage state machine backed by Room SQLite (`CameraMediaItemEntity` and `TransferQueueItemEntity`):

```
┌──────────────────────────────────────────────────────────────────────────────────────────────────┐
│                                 5-STAGE CLOUD SYNC STATE MACHINE                                 │
├──────────────────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                                  │
│  [ STAGE 1: DISCOVERED ]                                                                         │
│  • PTP ObjectAdded or CTG event detected. Handle resolved in <15ms.                             │
│  • Compound Fingerprint: SHA-256(CameraSerial + StorageID + Filename + SizeBytes + Timestamp)    │
│  • Persisted in Room DB with Status = DISCOVERED.                                                │
│                                              │                                                   │
│                                              ▼                                                   │
│  [ STAGE 2: CACHED (IN-TRANSIT) ]                                                                │
│  • Streamed over USB Bulk IN into local transit cache (`/cache/transit/`) via ByteBufferPool.    │
│  • Magic number validation (JPEG, RAF, CR3, ARW, NEF). Embedded preview extracted for UI HUD.   │
│  • Persisted in Room DB with Status = CACHED.                                                    │
│                                              │                                                   │
│                                              ▼                                                   │
│  [ STAGE 3: QUEUED ]                                                                             │
│  • Evaluated against Session Rules (Smart Dual-Tier: Preview JPEGs Priority 1, RAWs Priority 2). │
│  • Persisted in Room DB with Status = QUEUED.                                                    │
│                                              │                                                   │
│                                              ▼                                                   │
│  [ STAGE 4: UPLOADING ]                                                                          │
│  • Dispatched to Google Drive Resumable Upload pipeline via shared OkHttpClient singleton.       │
│  • Real-time progress metrics emitted (Bytes transferred, speed MB/s, ETA).                      │
│  • Persisted in Room DB with Status = UPLOADING.                                                 │
│                                              │                                                   │
│                                              ▼                                                   │
│  [ STAGE 5: CONFIRMED (UPLOADED) ]                                                               │
│  • Google Drive returns HTTP 200/201 with `fileId`, `md5Checksum`, `webViewLink`.                │
│  • Recorded in `UploadedFingerprintEntity` to permanently block duplicate re-uploads.            │
│  • Local transit cache cleared or managed by LRU quota manager (5GB default boundary).           │
│  • Persisted in Room DB with Status = UPLOADED.                                                  │
│                                                                                                  │
└──────────────────────────────────────────────────────────────────────────────────────────────────┘
```

---

## 5. Architectural Strategy & Tech Debt Pruning

### 5.1 Tech Debt Pruning & Removal Inventory

| Component | Status | Action Required in Milestone v3.0 |
| :--- | :--- | :--- |
| `UsbDisconnectHandler.kt` | Dead Code | **Delete** (361 lines of unreferenced, orphaned logic). |
| `SessionOrchestrator.kt` | Dead Code | **Replace** with real Domain Session Orchestrator or delete stub. |
| `ShootModeEngine.kt` MediaStore Fallback | Defect | **Remove** fake `MediaStore.Images` local phone query that re-emits screenshots. |
| `ProjectSessionManagerImpl` vs `TransferSessionManagerImpl` | Architecture Split | **Consolidate** into unified `SessionRepository` backed by Room reactive flows. |
| `UsbPermissionManager.kt` `FLAG_NO_CREATE` | Bug | **Fix** to use `FLAG_MUTABLE` on API 31+ and `0` on older versions (eliminates Android 12 failure). |
| Independent `OkHttpClient` Allocations | Resource Leak | **Replace** with singleton `OkHttpClient` container injected across app and workers. |
| Unmanaged `CoroutineScope` Allocations | Memory Leak | **Refactor** all watchers and engines to accept lifecycle-bound scopes or implement `AutoCloseable`. |

### 5.2 Android 14 & 15 OS Compliance Strategy

1. **Foreground Service Declaration:**
   ```xml
   <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
   <uses-permission android:name="android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE" />
   <service
       android:name=".service.UsbTetherService"
       android:exported="false"
       android:foregroundServiceType="connectedDevice" />
   ```
2. **Strict 5-Second Deadline Guard:** `UsbTetherService.onCreate()` invokes `startForeground()` immediately with a placeholder notification before performing any USB enumeration, database setup, or network operations.
3. **Android 15 Exemption Safety:** `connectedDevice` services are exempt from Android 15's 6-hour daily background runtime quota while the camera remains physically attached. Immediate `stopForeground(STOP_FOREGROUND_REMOVE)` and `stopSelf()` are invoked on physical detachment.
4. **Power & WakeLock Management:** Acquire `PowerManager.PARTIAL_WAKE_LOCK` with tag `TransferIn::UsbTetherWakeLock` on connection; release deterministically in `onDestroy()`.

---

## 6. Pitfalls & Anti-Patterns to Avoid

| Category | Severe Pitfall / Anti-Pattern | Why It Fails | Mandatory Architecture Pattern |
| :--- | :--- | :--- | :--- |
| **Fujifilm** | Naive MTP polling loop over all handles. | Saturates USB bulk endpoints; camera DSP freezes and drops shots. | Use dedicated Interrupt IN event listener + incremental handle diffs ($O(k)$). |
| **Fujifilm** | Omitting `0xD214 = 0` and `0x1003` on shutdown. | Transceiver remains locked in tether mode; dials/shutter frozen until battery pull. | Mandatory clean teardown handshake in `NonCancellable` block. |
| **Canon EOS** | Polling `.CTG` while camera flushes DRAM buffer. | Reads truncated 0-byte RAW files (`.CR3`); corrupts photoshoot. | Fast PTP event path + 2-stage stability verification for fallback. |
| **Canon EOS** | Not draining `0x9116` / Interrupt endpoint. | Circular event FIFO overflows; camera LCD flashes "Busy" and blocks shutter. | Dedicated continuous event drain coroutine. |
| **Sony Alpha** | Allowing "PC Only" storage mode. | Images stored in volatile DRAM; lost permanently if cable disconnects. | Verify vendor properties; prompt user for `PC + Camera` dual destination. |
| **Nikon** | Crashing or aborting on `0x2019 DeviceBusy`. | EXPEED processor temporarily locks USB during card write; abort corrupts session. | Implement exponential backoff retry loop (base 25ms, max 8 retries). |
| **Android OS** | Calling `connection.close()` during active JNI transfer. | Native use-after-free crash (`SIGSEGV` in `libusbhost.so`) bypassing Kotlin try-catch. | Guard all transfers with `SafeUsbTransportManager` IO latch. |
| **Android OS** | Launching cleanup on a scope cancelled on next line. | Cleanup coroutine is cancelled before starting; native handles and sockets leak. | Execute teardown on application-level lifecycle scope with watchdog timeout. |
| **Memory** | Allocating `ByteArray(50MB)` on JVM heap per shot. | Severe ART GC pauses (100–400ms), heap fragmentation, and USB timeout stalls. | Stream via `DirectByteBufferPool` (128KB/1MB) directly to disk/socket. |
| **Storage** | Deleting files from camera memory card. | Risk of accidental data loss. | TransferIn is strictly **read-only**; never issue PTP `DeleteObject`. |

---

## 7. Actionable Implementation Roadmap for Milestone v3.0

The development of Milestone v3.0 is partitioned into 5 sequential, deterministic execution phases:

```
┌──────────────────────────────────────────────────────────────────────────────────────────────────┐
│                                 MILESTONE V3.0 EXECUTION PHASES                                  │
├──────────────────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                                  │
│  PHASE 1: Zero-Leak Hardware Core & Buffer Pooling                                               │
│  • Implement `DirectByteBufferPool` (128KB / 1MB direct slices).                                 │
│  • Build `SafeUsbTransportManager` (IO execution latch protecting against unplug SIGSEGV).       │
│  • Fix `UsbTetherService.onDestroy()` lifecycle cancellation race using `NonCancellable`.         │
│  • Prune dead code (`UsbDisconnectHandler.kt`, stub files, fake MediaStore queries).             │
│  • Fix `UsbPermissionManager` PendingIntent flags for Android 12+ compatibility.                 │
│                                                                                                  │
│  PHASE 2: Fujifilm Native Wired Tether Engine (PTP Direct)                                       │
│  • Implement `FujiTetherAdapter` with `0xD214` host control and `0xD216` PC+Card destination.    │
│  • Build dedicated Interrupt IN event listener for `0x4002` / `0xC001` ObjectAdded events.       │
│  • Implement clean teardown sequence (`0xD214=0` $\to$ `0x1003` $\to$ release $\to$ close).      │
│  • Implement fast `.RAF` embedded preview extractor.                                             │
│                                                                                                  │
│  PHASE 3: Canon EOS Dual-Path Engine & Stability Verification                                    │
│  • Implement `CanonPtpAdapter` with `0x9114` RemoteMode and `0x9115` EventMode handshakes.       │
│  • Build continuous `0x9116` / Interrupt event drain loop to eliminate "Busy" shutter freezes.   │
│  • Implement `.CR3` ISO-BMFF box parser for sub-second preview extraction.                       │
│  • Implement 2-stage file stability verifier for resilient CTG fallback.                         │
│                                                                                                  │
│  PHASE 4: Sony Alpha & Nikon Tethering Adapters                                                  │
│  • Implement `SonyPtpAdapter` (SDIO `0x9280` handshake, "PC+Camera" guard, packet defrag).       │
│  • Implement `NikonPtpAdapter` (Direct capture, `0x2019 DeviceBusy` exponential backoff).        │
│  • Integrate multi-vendor detection into unified `CameraTetherEngine` factory.                   │
│                                                                                                  │
│  PHASE 5: 5-Stage Camera-to-Cloud Sync & Storage Management                                      │
│  • Standardize `CameraMediaItemEntity` and `TransferQueueItemEntity` state machines in Room.     │
│  • Implement Smart Dual-Tier Sync (Instant JPEG review + Background RAW archival).               │
│  • Build zero-copy `UsbStreamRequestBody` for OkHttp Google Drive resumable streaming.           │
│  • Implement transit cache LRU manager (5GB boundary) with automated stale file eviction.        │
│  • Execute automated LeakCanary 2.x regression suites on live connected camera bodies.           │
│                                                                                                  │
└──────────────────────────────────────────────────────────────────────────────────────────────────┘
```

---

## 8. Verification & Quality Gates

Before completing Milestone v3.0, every component must pass the following automated and physical test gates:

### 8.1 Automated Quality Gates
1. **Handshake Verification:** Unit tests asserting exact PTP opcode transmission sequences for Fuji (`0x1002` $\to$ `0xD214` $\to$ `0xD216`), Canon (`0x1002` $\to$ `0x9114` $\to$ `0x9115`), Sony (`0x1002` $\to$ `0x9280`), and Nikon (`0x1002` $\to$ `0x90C1`).
2. **Teardown Verification:** Tests confirming that teardown routines execute `0xD214=0` / `0x9114=0` and `0x1003 CloseSession` within 500ms even when parent coroutines are cancelled.
3. **Memory Safety Stress Test:** 20 concurrent simulated transfer threads transferring 1,000 mock 50MB files through `DirectByteBufferPool` with **zero heap expansion** and zero leaked file descriptors.
4. **Room Idempotency:** Rapid insertion of duplicate compound fingerprints during simulated USB disconnect/reconnect cycles produces exactly 1 database queue entry.

### 8.2 Real Hardware Validation Gates
- [ ] **Fujifilm (X-T3/X-T4/X-T5/X-H2):** Connect in Tether Auto mode $\to$ Active in <1.5s $\to$ 10-shot burst appears in HUD in <500ms/frame $\to$ Disconnect cable $\to$ UI recovers $\to$ Reconnect $\to$ Resumes $\to$ Exit app $\to$ Camera LCD turns on and returns to manual shooting without battery pull.
- [ ] **Canon EOS (R5/R6/5D IV):** Connect in Remote Mode $\to$ Continuous shutter actuation does not trigger "Busy" lock $\to$ Cloud upload stream starts within 1s.
- [ ] **Sony Alpha (A7 IV/A7R V):** Connect in PC Remote $\to$ Enforce PC+Camera $\to$ RAW files stream directly to Drive without data loss.
- [ ] **Nikon (Z8/Z9):** Shoot burst during active card write $\to$ `0x2019 DeviceBusy` backoff retries succeed without session termination.

---

*Synthesis completed and verified for TransferIn Milestone v3.0 on 2026-08-20.*
