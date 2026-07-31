---
name: transferin-roadmap
description: Roadmap for TransferIn v1.0 Shoot Mode Optimization
metadata:
  type: roadmap
  milestone: v1.0
  created: 2026-07-31
---

# TransferIn v1.0 Roadmap

**Milestone:** Shoot Mode Optimization
**Goal:** Optimize shoot mode with better integrity checking, smarter caching, and resilient connectivity

---

## Phase Overview

| Phase | Name | Focus | Requirements | Status |
|-------|------|-------|--------------|--------|
| 1 | CTG Foundation | Parse Canon database files | CTG-01, CTG-02, CTG-03 | Ready to execute |
| 2 | Smart Upload Detection | Deduplication & baseline tracking | UPLOAD-01, UPLOAD-02, UPLOAD-03 | 2 plans ready |
| 3 | Session Resilience | Connection pooling & reconnection | RESILIENCE-01, 02, 03, 04 | Not Started |
| 4 | Upload Integrity | Two-phase commit & checkpointing | RESILIENCE-05, 06 | Could Have |

### Phase 1 Plans

| Plan | Type | Focus | Status |
|------|------|-------|--------|
| 01-01 | Tracer | End-to-end CTG parsing (Parser + Entry + Snapshot) | Ready to execute |
| 01-02 | Standard | State Differ + CtgWatcher enhancement | Ready to execute |

**Implementation Order:**
1. Plan 01-01: Create domain models and CTG parser
2. Plan 01-02: Create differ and enhance watcher

---

## Phase 1: CTG Foundation

**Objective:** Implement SQLite-based CTG file parsing to enumerate all camera media and track changes.

### Requirements

| ID | Requirement | Key Implementation | Plan |
|----|-------------|-------------------|------|
| CTG-01 | Parse Canon .CTG database files from MISC directory to enumerate all media handles, filenames, and metadata | Use SQLiteDatabase.openDatabase() with fallback queries | 01-01 |
| CTG-02 | Compare CTG database state against local upload queue to detect files that need upload | Build immutable snapshots, diff with CtgStateDiffer | 01-02 |
| CTG-03 | Detect when CTG file changes to trigger re-scanning of camera media state | Enhance CtgWatcher with parsed snapshot emission | 01-02 |

### Deliverables

| File | Type | Description | Plan |
|------|------|-------------|------|
| `domain/shoot/CtgMediaEntry.kt` | New | Immutable media entry with fingerprint | 01-01 |
| `domain/shoot/CtgDatabaseParser.kt` | New | SQLite parsing for Canon CTG format | 01-01 |
| `domain/shoot/CtgMediaSnapshot.kt` | New | Immutable snapshot of CTG state | 01-01 |
| `domain/shoot/CtgStateDiffer.kt` | New | Compare snapshots to detect changes | 01-02 |
| `data/usb/CtgWatcher.kt` | Modify | Enhanced with snapshot emission | 01-02 |

### Dependencies
- Requires: Existing `CtgWatcher.kt`, `UsbHelper.kt` (already exist)
- Required by: Phase 2, Phase 3

### Verification
- [x] Plan 01-01: Can parse CTG file and enumerate all media handles
- [x] Plan 01-01: Snapshot captures handle, filename, size, timestamp
- [x] Plan 01-02: CTG change detection triggers re-parse
- [x] Plan 01-02: Differ correctly identifies new files vs existing

---

## Phase 2: Smart Upload Detection

**Objective:** Prevent re-uploading already transferred files by tracking uploads via CTG fingerprints.

### Requirements

| ID | Requirement | Key Implementation |
|----|-------------|-------------------|
| UPLOAD-01 | Track which files have been successfully uploaded via CTG fingerprint (handle + name + size) | Room entity with composite fingerprint. Store on upload completion. |
| UPLOAD-02 | Skip already-uploaded files during upload queue processing (deduplication) | Pre-filter upload queue against fingerprint index before processing. |
| UPLOAD-03 | Detect only new shots since session started (using CTG baseline) | Compare current CTG state against `CtgMediaSnapshot` from Phase 1. |

### Deliverables
- `data/local/entity/UploadedFingerprint.kt` — Room entity for fingerprint tracking
- `data/local/dao/UploadedFingerprintDao.kt` — DAO for fingerprint CRUD
- `domain/shoot/NewShotDetector.kt` — Compares CTG state to baseline snapshot
- `domain/shoot/SmartUploadFilter.kt` — Filters upload queue against fingerprints

### Dependencies
- Requires: Phase 1 (CTG Foundation)
- Required by: Phase 3

### Verification
- [ ] Fingerprints stored on successful upload
- [ ] Duplicate files skipped in queue processing
- [ ] Only new shots since session start are detected
- [ ] Rescan after CTG change picks up new files

### Phase 2 Plans

| Plan | Type | Focus | Status |
|------|------|-------|--------|
| 02-01 | Tracer | Database foundation (Entity + DAO + Migration) | Ready to execute |
| 02-02 | Standard | Domain layer (Repository + SmartUploadFilter + NewShotDetector) | Ready to execute |

**Implementation Order:**
1. Plan 02-01: Create Room entity, DAO, and database migration
2. Plan 02-02: Create domain layer and deduplication logic

---

## Phase 3: Session Resilience

**Objective:** Implement persistent SFTP sessions with heartbeat and automatic reconnection for uninterrupted uploads.

### Requirements

| ID | Requirement | Key Implementation |
|----|-------------|-------------------|
| RESILIENCE-01 | Implement SFTP session pooling (1-2 persistent sessions) to reduce connection overhead | Session pool with configurable size. Reuse channels across uploads. |
| RESILIENCE-02 | Add keepalive heartbeat (30s interval) to detect dead connections | JSch `Session.setKeepAliveInterval()`. Monitor for dead sessions. |
| RESILIENCE-03 | Implement automatic reconnection with exponential backoff on session failure | Retry logic with backoff: 1s, 2s, 4s, 8s, max 30s. |
| RESILIENCE-04 | Survive USB cable disconnects during shoot mode and resume seamlessly | USB disconnect detection via UsbManager broadcasts. Reinitialize on reconnect. |

### Deliverables
- `data/sftp/SftpSessionPool.kt` — Session pooling with 1-2 persistent connections
- `data/sftp/SftpSessionHealthMonitor.kt` — Keepalive and dead connection detection
- `data/sftp/SftpSessionReconnector.kt` — Exponential backoff reconnection logic
- `service/UsbDisconnectHandler.kt` — USB cable disconnect/reconnect handling
- Enhanced `JschSftpClient.kt` — Uses session pool instead of per-operation sessions

### Dependencies
- Requires: Existing `JschSftpClient.kt` (already exists)
- Required by: Phase 4

### Verification
- [ ] Session pool maintains 1-2 persistent connections
- [ ] Keepalive detects dead connections within 60s
- [ ] Reconnection succeeds after NAS temporary unavailability
- [ ] USB disconnect triggers graceful pause and resume
- [ ] Upload queue resumes without data loss after reconnect

---

## Phase 4: Upload Integrity (Could Have)

**Objective:** Ensure upload integrity with atomic commits and checkpoint persistence.

### Requirements

| ID | Requirement | Key Implementation |
|----|-------------|-------------------|
| RESILIENCE-05 | Two-phase upload commit (upload to .part file, verify size, atomic rename) | Upload to `filename.part`, verify size matches, `rename()` to final name. |
| RESILIENCE-06 | Upload checkpoint persistence (save progress every 1MB or 30 seconds) | Track byte offset. Resume from checkpoint on failure. |

### Deliverables
- `data/sftp/TwoPhaseUploadHandler.kt` — Part file upload with verification
- `data/sftp/UploadCheckpointManager.kt` — Persist/resume upload progress
- Enhanced `SftpClient` interface — Add `resumeUpload()` method

### Dependencies
- Requires: Phase 3 (Session Resilience)

### Verification
- [ ] Incomplete uploads cleaned up on retry
- [ ] Partial uploads resume from checkpoint
- [ ] Final file only visible after atomic rename

---

## Implementation Order

```
Phase 1 (CTG Foundation)
       │
       ├── Phase 2 (Smart Upload Detection)
       │                │
       │                │
       └────────────────┴── Phase 3 (Session Resilience)
                                        │
                                        │
                                        └── Phase 4 (Upload Integrity)
```

### Why This Order?

1. **Phase 1 first:** CTG parsing is foundational — all other phases depend on accurate media enumeration
2. **Phase 2 next:** Smart upload detection builds on CTG snapshots to prevent duplicate uploads
3. **Phase 3 follows:** Session resilience applies the deduplication layer in production with persistent connections
4. **Phase 4 last:** Two-phase commit and checkpointing are advanced features; optional for v1.0

---

## Key Files Reference

### Existing (to be enhanced)
- `app/src/main/java/com/reflection/transferin/data/usb/CtgWatcher.kt` — Existing CTG watcher
- `app/src/main/java/com/reflection/transferin/data/sftp/JschSftpClient.kt` — SFTP client needing pooling

### New (to be created)
```
app/src/main/java/com/reflection/transferin/
├── domain/shoot/
│   ├── CtgDatabaseParser.kt
│   ├── CtgMediaSnapshot.kt
│   ├── CtgStateDiffer.kt
│   ├── NewShotDetector.kt
│   └── SmartUploadFilter.kt
├── data/local/
│   ├── entity/UploadedFingerprint.kt
│   └── dao/UploadedFingerprintDao.kt
├── data/sftp/
│   ├── SftpSessionPool.kt
│   ├── SftpSessionHealthMonitor.kt
│   ├── SftpSessionReconnector.kt
│   ├── TwoPhaseUploadHandler.kt
│   └── UploadCheckpointManager.kt
└── service/
    └── UsbDisconnectHandler.kt
```

---

## Success Criteria

For milestone v1.0 to be complete:

1. **CTG Parsing:** Camera media enumerated from SQLite CTG database
2. **Smart Detection:** Only new shots uploaded, duplicates skipped
3. **Resilience:** Sessions survive disconnects and reconnect automatically
4. **No Data Loss:** Upload queue state persists across app restarts

---

*Roadmap created: 2026-07-31*
