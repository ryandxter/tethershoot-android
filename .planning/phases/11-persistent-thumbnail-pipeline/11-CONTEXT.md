# Phase 11: Persistent Multi-Vendor Thumbnail Pipeline & USB Synchronization — Context & Decisions

**Phase:** 11  
**Title:** Persistent Multi-Vendor Thumbnail Pipeline & USB Synchronization  
**Status:** Context Gathered & Decisions Locked  
**Date:** 2026-08-20  

---

## Domain Boundary

Multi-vendor PTP embedded JPEG preview extraction, atomic two-stage disk persistence (`cacheDir/thumbnails/`), 3-day/logout lifecycle pruning, USB transport mutex serialization, and Coil Compose deterministic caching with EXIF orientation normalization.

---

## Implementation Decisions Locked

### 1. Storage Location & Lifecycle Policy
- **Path:** `context.cacheDir/thumbnails/{fingerprint}.jpg`.
- **Lifecycle Management:** Aggressive automatic cleanup on user logout / session termination, plus an automatic 3-day time-to-live (TTL) eviction policy for stale thumbnails.
- **Safety Invariant:** Thumbnails remain accessible during active shooting sessions even when local multi-megabyte RAW transit files are purged post Google Drive upload (`PURGED_LOCAL`).

### 2. Atomic Disk Commit & Zero-Byte Prevention
- **Write Mechanism:** Two-stage atomic file transaction:
  1. Write JPEG preview bytes to `thumb_{fingerprint}.tmp`.
  2. Call `fos.fd.sync()` to force filesystem buffer flush.
  3. Atomic POSIX `renameTo(finalFile)`.
  4. Verify `finalFile.exists() && finalFile.length() > 0` before updating Room DAO status.
- **Rationale:** Prevents Coil from prematurely reading 0-byte corrupt files and caching blank placeholders.

### 3. USB Transport Mutex Priority Arbitration
- **Mechanism:** Two-Tier Priority Lock on `SafeUsbTransportManager.ioMutex`.
- **Behavior:** Tier 1 embedded preview extraction (<50ms) claims immediate priority execution over the half-duplex USB endpoint, temporarily yielding chunked Tier 2 RAW background streaming.
- **Rationale:** Eliminates PTP packet interleaving and fatal transaction desynchronization while ensuring sub-second photographer preview feedback.

### 4. Coil Compose ImageLoader Architecture
- **Cache Configuration:**
  - Dedicated `ImageLoader` instance configured with 25% JVM heap memory cache and 250MB LRU disk cache.
  - Deterministic compound cache key: `"thumb_${item.deviceId}_${item.objectHandle}_${item.fingerprint}"`.
- **Rationale:** Eliminates gallery tile flickering, stale image reuse, and blank loading states.

### 5. EXIF Orientation Normalization
- **Method:** Preserve EXIF APP1 header segments containing Tag `0x0112` (Orientation) during preview slicing across all extractors (`RafPreviewExtractor`, `Cr3PreviewParser`, `ArwPreviewParser`, `NikonPtpAdapter`).
- **Rendering:** Coil automatically parses EXIF Tag `0x0112` and rotates the decoded bitmap to 0°, 90°, 180°, or 270° without manual Canvas Matrix hackery.

---

## Canonical References
- `app/src/main/java/com/rvmeur/transferin/data/usb/SafeUsbTransportManager.kt` (USB mutex & IO latch)
- `app/src/main/java/com/rvmeur/transferin/data/usb/extractors/` (Vendor preview extractors)
- `.planning/research/ARCHITECTURE.md` (Thumbnail pipeline architecture)
- `.planning/research/PITFALLS.md` (Disk flush race condition & USB contention)
