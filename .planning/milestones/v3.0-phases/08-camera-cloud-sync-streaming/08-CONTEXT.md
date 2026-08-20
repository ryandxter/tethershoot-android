# Phase 8 Context: 5-Stage Camera-to-Cloud Sync & Direct USB Streaming

**Milestone:** v3.0 — Multi-Vendor Production Engine & Zero-Leak Tether Core  
**Phase:** 08 — 5-Stage Camera-to-Cloud Sync & Direct USB Streaming  
**Requirements:** SYNC-01, SYNC-02, SYNC-03, SYNC-04, SYNC-05  

## Purpose & Scope
This phase replaces the monolithic, blocking upload queue with an atomic 5-stage Camera-to-Cloud state machine (`DETECTED` -> `BUFFERED_LOCAL` -> `STREAMING_CLOUD` -> `CONFIRMED_CLOUD` -> `PURGED_LOCAL`). It introduces zero-copy direct USB-to-Cloud streaming via custom OkHttp `RequestBody`, SHA-256 compound fingerprint deduplication, and safe 5GB LRU transit cache management.

## Key Architectural Decisions
1. **5-Stage State Machine (Room DB)**:
   - `CameraSyncStatus`: `DETECTED`, `BUFFERED_LOCAL`, `STREAMING_CLOUD`, `CONFIRMED_CLOUD`, `PURGED_LOCAL`, `FAILED`.
   - Atomic state transitions governed by Room `@Transaction` operations.
2. **SHA-256 Compound Fingerprinting**:
   - `CompoundFingerprintGenerator.kt`: `${vendorId}_${cameraSerial}_${objectHandle}_${byteSize}_${captureTimestampMs}` -> SHA-256 hex digest.
   - Prevents duplicate uploads across multiple camera storage cards and app restarts.
3. **Zero-Copy USB Direct Streaming**:
   - `UsbStreamRequestBody.kt`: Directly pumps `DirectByteBufferPool` slices from USB Bulk IN into OkHttp `BufferedSink` without writing full 50MB-100MB RAW files to flash memory when network throughput is sufficient.
4. **5GB LRU Transit Cache**:
   - Evicts local file caches only after `CONFIRMED_CLOUD` state is persisted with matching Google Drive / NAS MD5/SHA checksums.
5. **Real-Time UI & Telemetry**:
   - `CameraSyncTracker.kt` exposes `StateFlow<CameraSyncMetrics>` with live counts per state for UI display.
