---
phase: "02"
plan: "02"
subsystem: domain
tags:
  - smart-upload
  - deduplication
  - fingerprint
  - domain-layer
dependency_graph:
  requires:
    - "02-01"
  provides:
    - "domain:shoot:Fingerprint"
    - "domain:repository:UploadedFingerprintRepository"
    - "domain:shoot:SmartUploadFilter"
    - "domain:shoot:NewShotDetector"
  affects:
    - "data:repository:UploadedFingerprintRepositoryImpl"
tech_stack:
  added:
    - Kotlin data classes
    - Coroutines Flow
  patterns:
    - Repository pattern
    - In-memory caching
    - Lazy initialization
key_files:
  created:
    - "app/src/main/java/com/reflection/transferin/domain/shoot/Fingerprint.kt"
    - "app/src/main/java/com/reflection/transferin/domain/repository/UploadedFingerprintRepository.kt"
    - "app/src/main/java/com/reflection/transferin/data/repository/UploadedFingerprintRepositoryImpl.kt"
    - "app/src/main/java/com/reflection/transferin/domain/shoot/SmartUploadFilter.kt"
    - "app/src/main/java/com/reflection/transferin/domain/shoot/NewShotDetector.kt"
decisions:
  - id: "02-02-D1"
    decision: "Use pipe separator '|' in fingerprint format"
    rationale: "Unambiguous - prohibited in all major OS filenames"
  - id: "02-02-D2"
    decision: "Lazy initialization for SmartUploadFilter"
    rationale: "Defer DB load until first use, improving startup time"
  - id: "02-02-D3"
    decision: "FingerprintSource interface for filterable items"
    rationale: "Decouples filter logic from specific entity types"
metrics:
  duration: "~15 minutes"
  tasks: 4
  commits: 4
  files: 5
status: "complete"
actuals:
  tokens: 12000
  tasks: 4
  commits: 4
---

# Phase 2 Plan 02: Smart Upload Detection Domain Layer Summary

## One-liner

Domain layer for fingerprint-based upload deduplication with in-memory caching and baseline comparison.

## Completed Tasks

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Create Fingerprint.kt | 7b6271e | domain/shoot/Fingerprint.kt |
| 2 | Create UploadedFingerprintRepository | 7f836b7 | domain/repository/UploadedFingerprintRepository.kt, data/repository/UploadedFingerprintRepositoryImpl.kt |
| 3 | Create SmartUploadFilter | e072111 | domain/shoot/SmartUploadFilter.kt |
| 4 | Create NewShotDetector | 923b386 | domain/shoot/NewShotDetector.kt |

## Artifacts Created

### Fingerprint.kt
- `MediaFingerprint` data class with handle, filename, sizeBytes
- `toStorageKey()` returning "{handle}|{normalized}|{size}"
- `fromStorageKey()` companion to parse storage key back
- KDoc explaining fingerprint format and separator rationale

### UploadedFingerprintRepository
- Interface in `domain/repository/` with business methods
- Implementation in `data/repository/` delegating to DAO
- Methods: hasUploaded, filterNewFingerprints, recordUpload, getSessionFingerprints, getAllFingerprints, cleanupOldFingerprints, observeTotalCount, observeSessionCount

### SmartUploadFilter
- Lazy initialization via `initialize(sessionId?)` or `initializeWith(fingerprints)`
- `filter(items)` partitioning into new vs skipped
- `isNew(fingerprint)` for O(1) checking
- `recordUpload(entity)` syncing memory with persistence
- `FingerprintSource` interface for filterable items
- `FilterResult` data class for filter output

### NewShotDetector
- `detectNewShots(baseline, current)` returning items not in baseline
- `buildFingerprint(media)` generating fingerprint from CameraMedia
- `extractFingerprints(mediaList)` bulk extraction
- `isNewShot()` single-item check
- `parseFingerprint()` and `isValidFingerprint()` utilities
- `CtgChangeEvent` data class

## Integration Points

| Key Link | Description |
|----------|-------------|
| SmartUploadFilter.filter() | Called before upload processing to skip duplicates |
| NewShotDetector.detectNewShots() | Uses CameraMedia fingerprints to find new shots |
| Fingerprint.toStorageKey() | Called when recording uploads to entity |

## Success Criteria Status

- [x] Fingerprint.kt has MediaFingerprint with toStorageKey()/fromStorageKey()
- [x] UploadedFingerprintRepository wraps DAO with business logic
- [x] SmartUploadFilter.initialize() loads fingerprints from repository
- [x] SmartUploadFilter.filter() separates new and skipped items
- [x] NewShotDetector.detectNewShots() returns items not in baseline
- [x] CameraMedia fingerprints are correctly generated

## Deviations from Plan

None - plan executed exactly as written.

## Threat Surface Scan

| Flag | File | Description |
|------|------|-------------|
| N/A | All | No new security surface introduced; fingerprint strings are opaque identifiers |

## Self-Check

- [x] All files created at correct paths
- [x] All required methods present
- [x] All 4 tasks committed individually
- [x] No stubs or placeholder code

## Self-Check: PASSED

---

**Plan:** 02-02
**Tasks:** 4/4
**Commits:** 4
**Duration:** ~15 minutes
