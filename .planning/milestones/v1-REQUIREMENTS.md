# Requirements Archive: v1 v1

**Archived:** 2026-08-07
**Status:** SHIPPED

For current requirements, see `.planning/REQUIREMENTS.md`.

---

# Requirements

**Analysis Date:** 2026-08-07

## Milestone v1 Active Requirements

### 1. SFTP Connection Resilience (SFTP)

- [x] **SFTP-01**: The system must reuse active SSH channels via `SftpSessionPool` to avoid connection overhead.
- [x] **SFTP-02**: The system must monitor channel health using keepalive packets every 30 seconds and auto-evict dead connections.
- [x] **SFTP-03**: The system must implement exponential backoff (1s, 2s, 4s, 8s, up to 30s) and a circuit breaker (tripping after 5 failures) for reconnection.

### 2. USB Device Stability (USB)

- [x] **USB-01**: The system must debounce USB attach/detach events with a 2-second stabilization delay to avoid race conditions.
- [x] **USB-02**: The system must fallback to a generic MTP directory-listing poll on non-Canon cameras.

### 3. Service Compliance (SVC)

- [x] **SVC-01**: The system must run `UsbTetherService` conforming to Android 14+ `FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE` restrictions.

### 4. Large File Resumability (FILE)

- [x] **FILE-01**: The system must upload files using a temporary `.tmp` suffix, renaming them only upon successful completion.
- [x] **FILE-02**: The system must query and persist checkpoints via `UploadCheckpointManager` to resume partial uploads.
- [x] **FILE-03**: The system must clean up dangling `.tmp` files older than 24 hours on the remote server during session initialization.

### 5. Room Migration Verification (DB)

- [x] **DB-01**: The system must validate Room database migration schemas (e.g. version 8 to 9) using Room's `MigrationTestHelper` in automated instrumented tests.

## Future Requirements

- [ ] **SFTP-04**: High-availability SFTP failover to secondary backup server.
- [ ] **USB-03**: Direct camera trigger control via PTP bulk commands.

## Out of Scope

- Direct local media file editing or metadata manipulation within the app.

## Traceability Matrix

- **SFTP-01**: Phase 1 (SFTP Pooling & Reconnection Resilience)
- **SFTP-02**: Phase 1 (SFTP Pooling & Reconnection Resilience)
- **SFTP-03**: Phase 1 (SFTP Pooling & Reconnection Resilience)
- **USB-01**: Phase 2 (USB Debounce & Service Compliance)
- **USB-02**: Phase 3 (Multi-Vendor MTP Fallback)
- **SVC-01**: Phase 2 (USB Debounce & Service Compliance)
- **FILE-01**: Phase 4 (Resumable Transfers & Server Cleanup)
- **FILE-02**: Phase 4 (Resumable Transfers & Server Cleanup)
- **FILE-03**: Phase 4 (Resumable Transfers & Server Cleanup)
- **DB-01**: Phase 5 (Room Migration Validation Suite)
