# Roadmap

## Milestone v1 Phases

### Phase 1: SFTP Pooling & Reconnection Resilience
- **Goal:** Establish a leak-free pool of SFTP sessions with keepalives, exponential backoff, and circuit breakers.
- **Requirements:** `SFTP-01`, `SFTP-02`, `SFTP-03`
- **Success Criteria:**
  1. SFTP connections are successfully reused from `SftpSessionPool` during active transfers.
  2. Unresponsive connections are auto-evicted within 30s of missing keepalive telemetry.
  3. Reconnections use escalating delays (1s to 30s) and trip the circuit breaker to OPEN state after 5 failed retries.

### Phase 2: USB Debounce & Service Compliance
- **Goal:** Debounce physical connection drops and enforce Android 14+ foreground service constraints.
- **Requirements:** `USB-01`, `SVC-01`
- **Success Criteria:**
  1. USB connect/disconnect events within a 2-second stabilization window are merged to avoid duplicate sessions.
  2. `UsbTetherService` launches and runs conforming to `FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE` constraints.

### Phase 3: Multi-Vendor MTP Fallback
- **Goal:** Support folder listing and sync capabilities for generic MTP cameras.
- **Requirements:** `USB-02`
- **Success Criteria:**
  1. Generic MTP cameras (non-Canon) trigger folder scanning instead of failing.
  2. Photos are synchronized based on size/date diff algorithms.

### Phase 4: Resumable Transfers & Server Cleanup
- **Goal:** Support offset resumption for large files and garbage collection of temporary files.
- **Requirements:** `FILE-01`, `FILE-02`, `FILE-03`
- **Success Criteria:**
  1. Interrupted transfers query `UploadCheckpointManager` and resume at the correct byte offset.
  2. Ongoing files are uploaded with a `.tmp` suffix, renamed only when completion succeeds.
  3. Orphaned server-side `.tmp` files older than 24 hours are pruned automatically upon initialization.

### Phase 5: Room Migration Validation Suite
- **Goal:** Create automated migration test suites to guarantee Room schema update integrity.
- **Requirements:** `DB-01`
- **Success Criteria:**
  1. Instrumented test runner runs `MigrationTestHelper` migrations from v8 to v9.
  2. Migration test suites verify database schema and tables consistency.
