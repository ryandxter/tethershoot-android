# Project Research Summary

## Key Findings

We conducted an analysis of the active concerns and technical debt documented in `CONCERNS.md` to establish a safe, resilient, and compliant integration roadmap.

### 1. Connection & SFTP Resilience
- **Pooling & Health:** Integrate keepalive parameters (`serverAliveInterval=30000`, `serverAliveCountMax=3`) in JSch.
- **Circuit Breaker:** Prevent constant reconnections by opening the circuit on 5 consecutive failures, with a 60-second recovery timeout.
- **Exponential Backoff:** Escalate reconnection delays from 1s up to 30s.

### 2. Multi-Vendor Camera & USB Support
- **MTP Fallback:** Support generic MTP folders for non-Canon cameras, verifying files using dates and size comparison instead of relying entirely on proprietary CTG databases.
- **Robust Handsakes:** Wrap PTP handshakes in connection state guards to prevent firmware corruption.

### 3. Foreground Service Lifecycle
- **Android 14 Compliance:** Enforce `FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE` in both Manifest and service runtime configurations.
- **Debouncer:** Introduce a 2-second delay to debounce hardware connection fluctuations.

### 4. Large Media transfers & Checkpoints
- **Two-Phase Resuming:** Use temporary file suffixes (`.tmp`) and query local checkpoints via `UploadCheckpointManager` to safely resume uploads.
- **Temp file Garbage Collection:** Execute cleanups of orphaned `.tmp` files older than 24 hours on NAS initialization.

### 5. Migration Safety
- **Schema Validation:** Implement Room-testing (`MigrationTestHelper`) to verify version 8 to 9 migration scripts without risking user data loss.

## Implications for Roadmap

The concerns will be decomposed into 5 distinct phases:
1. **SFTP Pooling & Reconnection Resilience** (Addressing Pool leaks and network handovers)
2. **USB Debounce & Foreground Service Compliance** (Addressing stabilization delay and Android 14+ regulations)
3. **Multi-Vendor MTP Fallback Support** (Addressing non-Canon camera discovery and listing)
4. **Large Media Upload Checkpoints & Temp File Cleanup** (Addressing chunking, resuming, and server-side garbage collection)
5. **Database Migration Validation Test Suite** (Addressing Room schema migration safety)

## Sources
- Android Developer Documentation (Foreground Service Types)
- Room Database Migration Guide (Android Developers)
- JSch SSH Specification and Keepalive Configs

---

*Synthesis completed: 2026-08-07*
