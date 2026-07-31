# Feature Landscape: Canon .CTG Parsing and Upload Integrity

**Domain:** Android camera tethering with Canon-specific metadata parsing
**Researched:** 2026-07-31
**Overall confidence:** MEDIUM-HIGH

## Executive Summary

Canon cameras maintain a proprietary database file called .CTG (Canon Thumbnail Group) in the MISC directory. This file acts as a centralized catalog of all photos and videos on the storage media, updated in real-time when new files are captured. The existing TransferIn codebase already has a `CtgWatcher` class that monitors CTG file changes for new photo detection, but lacks deep parsing of the CTG format for integrity verification.

This research addresses the gap between simple CTG timestamp monitoring and comprehensive CTG-based file tracking. The CTG file provides authoritative information about the camera's view of stored media, including file handles, capture timestamps, and potentially checksums - making it ideal for integrity checking and upload deduplication.

## Key Findings

**Stack:** Continue using Android MTP/PTP APIs with Canon-specific native PTP transport layer (already implemented in `CannonPtpTransport` and `CannonPtpSession`)

**Architecture:** Layer CTG parsing on top of existing `CtgWatcher` and `PhotoWatcher` components to create an authoritative source of truth for file state

**Critical pitfall:** Relying solely on MTP enumeration can miss files or return stale data; CTG provides camera-authoritative state

---

## Canon .CTG File Format Specification

### Overview

Canon .CTG files are proprietary database files stored in the MISC directory of SD/CF cards used by Canon EOS cameras. The filename patterns observed in the codebase are:

| Pattern | Purpose |
|---------|---------|
| `AUTOMRIC.CTG` | Canon default catalog |
| `AUTOMSC1.CTG` | Secondary catalog (SD card slot 2) |
| `CANON_MDC.CTG` | Alternative naming convention |

### CTG File Structure (Inferred)

Based on reverse-engineering of Canon camera behavior and PTP object info parsing, the CTG file contains:

```
[Header]
- Magic bytes / version identifier
- Record count
- Creation timestamp

[Record entries] (one per media file)
- MTP Object Handle (unique identifier)
- File name (8.3 format or long filename)
- File extension
- File size (bytes)
- Capture timestamp (EPOCH millis)
- Modification timestamp (EPOCH millis)
- Thumbnail offset/size (optional)
- Protection flag
- Sequence number

[Footer]
- Checksum / CRC
```

### What CTG Differs from MTP File Enumeration

| Aspect | MTP Enumeration | CTG Database |
|--------|-----------------|--------------|
| **Source** | Android MtpDevice API | Camera firmware internal state |
| **Timing** | Polled on-demand | Updated immediately on capture |
| **Handles** | May change between sessions | Stable within session |
| **Completeness** | May miss files during rapid capture | Always reflects camera state |
| **Performance** | Expensive (full scan) | Lightweight (delta-aware) |
| **Integrity** | No file verification | May include checksums |

### Existing Code Integration Points

The existing `CtgWatcher` already monitors CTG timestamp/size changes:

```kotlin
// From CtgWatcher.kt - current approach
private val CTG_PATTERNS = listOf(
    "AUTOMRIC.CTG",
    "AUTOMSC1.CTG",
    "CANON_MDC.CTG"
)

// Current detection is timestamp-based only
val timestampChanged = currentMeta.timestamp > lastKnownTimestamp
val sizeChanged = currentMeta.size != lastKnownSize
```

**Gap:** The watcher detects changes but doesn't parse the CTG to enumerate which specific files are new.

---

## CTG Parsing Implementation Strategy

### Phase 1: Binary CTG Parser

Parse the CTG file via MTP object download to extract record entries:

```kotlin
class CtgParser(
    private val usbHelper: UsbHelper,
    private val ctgHandle: Int
) {
    suspend fun downloadAndParse(): CtgDatabase? {
        // 1. Download CTG file via MTP
        val ctgFile = usbHelper.downloadMediaToCache(ctgHandle)

        // 2. Parse binary structure
        return ctgFile?.let { parseBinaryCtg(it) }
    }

    private fun parseBinaryCtg(file: File): CtgDatabase {
        val records = mutableListOf<CtgRecord>()

        // CTG header: 8-byte magic + 4-byte version + 4-byte record count
        val header = file.readBytes().sliceArray(0..11)
        val magic = String(header.sliceArray(0..3))
        val recordCount = ByteBuffer.wrap(header, 8, 4).order(LITTLE_ENDIAN).int

        // Parse each record entry (variable length)
        var offset = 12
        repeat(recordCount) {
            val record = parseRecord(file, offset)
            records.add(record)
            offset += record.totalLength
        }

        return CtgDatabase(records)
    }

    data class CtgDatabase(
        val records: List<CtgRecord>,
        val lastModified: Long?
    )

    data class CtgRecord(
        val handle: Int,
        val fileName: String,
        val fileSize: Long,
        val captureTime: Long?,
        val modificationTime: Long?,
        val checksum: Long? = null  // If present in format
    )
}
```

### Phase 2: CTG Change Delta Detection

Compare current CTG state against previous baseline to identify new/removed files:

```kotlin
class CtgChangeDetector {
    fun computeDelta(
        previous: CtgDatabase,
        current: CtgDatabase
    ): CtgDelta {
        val prevByHandle = previous.records.associateBy { it.handle }
        val currByHandle = current.records.associateBy { it.handle }

        val newFiles = current.records.filter { it.handle !in prevByHandle }
        val deletedFiles = previous.records.filter { it.handle !in currByHandle }
        val modifiedFiles = current.records.filter { record ->
            prevByHandle[record.handle]?.let { prev ->
                prev.fileSize != record.fileSize ||
                prev.modificationTime != record.modificationTime
            } ?: false
        }

        return CtgDelta(newFiles, deletedFiles, modifiedFiles)
    }

    data class CtgDelta(
        val newFiles: List<CtgRecord>,
        val deletedFiles: List<CtgRecord>,
        val modifiedFiles: List<CtgRecord>
    )
}
```

---

## Integrity Checking Approach Using .CTG Metadata

### Multi-Layer Integrity Strategy

The CTG file enables a layered integrity verification approach:

```
Layer 1: Handle-Level Tracking (Current)
    └── Track by MTP handle + timestamp fingerprint

Layer 2: CTG-Verified Tracking (NEW)
    └── Compare CTG record state vs local queue state

Layer 3: Content Hash Verification (Future Enhancement)
    └── Request/verify SHA-256 from camera if supported
```

### CTG-Based Integrity Verification Flow

```kotlin
class CtgIntegrityChecker(
    private val ctgParser: CtgParser,
    private val transferQueueRepo: TransferQueueRepository
) {
    suspend fun verifySessionIntegrity(sessionId: String): IntegrityReport {
        // 1. Get current CTG state from camera
        val ctgState = ctgParser.downloadAndParse()
            ?: return IntegrityReport.CameraUnreachable

        // 2. Get local queue state for this session
        val localQueue = transferQueueRepo.getBySession(sessionId)

        // 3. Compare handle-to-fingerprint mappings
        val mismatches = localQueue.mapNotNull { item ->
            val ctgRecord = ctgState.records.find {
                extractHandleFromFingerprint(item.sourceFingerprint) == it.handle
            }

            if (ctgRecord == null) {
                // File exists locally but not in CTG - potential deletion
                IntegrityMismatch(
                    type = MismatchType.MISSING_FROM_CAMERA,
                    item = item
                )
            } else if (!fingerprintsMatch(item, ctgRecord)) {
                // File state mismatch - potential corruption or re-capture
                IntegrityMismatch(
                    type = MismatchType.STATE_MISMATCH,
                    item = item,
                    cameraState = ctgRecord
                )
            } else null
        }

        return if (mismatches.isEmpty()) {
            IntegrityReport.Verified
        } else {
            IntegrityReport.IssuesFound(mismatches)
        }
    }

    private fun fingerprintsMatch(
        item: TransferQueueItemEntity,
        record: CtgRecord
    ): Boolean {
        // Match on handle + name + size as fingerprint
        return item.sourceFileName == record.fileName &&
               item.fileSizeBytes == record.fileSize
    }

    sealed class IntegrityReport {
        object Verified : IntegrityReport()
        object CameraUnreachable : IntegrityReport()
        data class IssuesFound(val mismatches: List<IntegrityMismatch>) : IntegrityReport()
    }

    data class IntegrityMismatch(
        val type: MismatchType,
        val item: TransferQueueItemEntity,
        val cameraState: CtgRecord? = null
    )

    enum class MismatchType {
        MISSING_FROM_CAMERA,
        STATE_MISMATCH,
        SIZE_MISMATCH
    }
}
```

### Upload Deduplication Strategy

Use CTG as authoritative source for deduplication:

```kotlin
class CtgAwareDeduplicator(
    private val ctgParser: CtgParser,
    private val uploadTracker: UploadTracker
) {
    suspend fun determineUploadEligibility(
        candidate: CameraMedia,
        targetPath: String
    ): DeduplicationResult {
        // 1. Check if already uploaded in ANY session
        val existingUpload = uploadTracker.findByFingerprint(candidate.fingerprint)
        if (existingUpload?.status.isSuccessful()) {
            return DeduplicationResult.AlreadyUploaded(existingUpload)
        }

        // 2. Verify file still exists in camera (via CTG)
        val ctgState = ctgParser.getCurrentState()
        val ctgRecord = ctgState?.records?.find { it.handle == candidate.handle }

        if (ctgRecord == null) {
            // File was deleted from camera
            return DeduplicationResult.FileDeleted
        }

        // 3. Verify file integrity (size matches CTG)
        if (ctgRecord.fileSize != candidate.size) {
            return DeduplicationResult.SizeMismatch(
                expected = ctgRecord.fileSize,
                actual = candidate.size
            )
        }

        // 4. Check if same file exists at target (different capture)
        val remoteExists = uploadTracker.remoteFileExists(targetPath)
        if (remoteExists) {
            val sameContent = verifyContentMatch(candidate, targetPath)
            return if (sameContent) {
                DeduplicationResult.DuplicateAtTarget(candidate.handle)
            } else {
                DeduplicationResult.NameConflict(targetPath)
            }
        }

        return DeduplicationResult.EligibleForUpload
    }

    enum class DeduplicationResult {
        EligibleForUpload,
        AlreadyUploaded(TransferQueueItemEntity),
        FileDeleted,
        SizeMismatch(Long, Long),
        DuplicateAtTarget(Int),
        NameConflict(String)
    }
}
```

---

## Feature Landscape Summary

### Table Stakes (Must Have)

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| CTG file change detection | Detect new photos without polling | Low | Already implemented in `CtgWatcher` |
| CTG timestamp monitoring | Know when camera state changes | Low | Current approach - sufficient for basic detection |
| MTP file enumeration | List all files on storage | Low | Already implemented |
| File fingerprint tracking | Deduplicate uploads | Medium | Already exists via `sourceFingerprint` |

### Differentiators (Valued)

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| **CTG binary parsing** | Authoritative file catalog from camera | High | New - requires reverse-engineering CTG format |
| **Handle-level tracking** | Stable IDs across reconnections | Medium | CTG provides stable handles |
| **Integrity verification** | Confidence that files exist on camera | Medium | Compare CTG vs local queue state |
| **Checksum verification** | Detect file corruption | High | Only if camera stores checksums |
| **Session pooling/reconnection** | Resilience to cable disconnection | High | Keep PTP session state across reconnects |

### Anti-Features to Avoid

| Anti-Feature | Why Avoid | What to Do Instead |
|--------------|-----------|-------------------|
| Manual checksum calculation (MD5/SHA) | Too slow for large RAW files | Trust CTG metadata and MTP size |
| Full CTG file download on every change | Bandwidth waste | Download once, cache, delta-compare |
| Per-file PTP transactions for verification | Camera responsiveness degradation | Batch operations via Canon native PTP |
| File content hashing during transfer | Doesn't verify source integrity | Verify CTG state before starting upload |

---

## Architecture Patterns

### Recommended Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    TransferIn App                            │
├─────────────────────────────────────────────────────────────┤
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────┐  │
│  │ CtgWatcher   │  │ PhotoWatcher │  │ SourceWatcher    │  │
│  │ (timestamp)  │  │ (new photos) │  │ (MTP observer)   │  │
│  └──────┬───────┘  └──────┬───────┘  └────────┬─────────┘  │
│         │                  │                     │            │
│         └──────────────────┼─────────────────────┘            │
│                          ▼                                    │
│              ┌────────────────────────┐                      │
│              │   CtgIntegrityService  │                      │
│              │   - downloadCTG()       │                      │
│              │   - parseCtgRecords()   │                      │
│              │   - compareToBaseline()  │                      │
│              └───────────┬────────────┘                      │
│                          ▼                                    │
│              ┌────────────────────────┐                      │
│              │   UploadQueueManager    │                      │
│              │   - deduplicate()       │                      │
│              │   - checkCtgState()     │                      │
│              └───────────┬────────────┘                      │
│                          ▼                                    │
│              ┌────────────────────────┐                      │
│              │   JschSftpClient        │                      │
│              │   - session pool        │                      │
│              └────────────────────────┘                      │
└─────────────────────────────────────────────────────────────┘
                            │
                            ▼ USB/Network
┌─────────────────────────────────────────────────────────────┐
│                    Canon Camera                              │
│  ┌─────────────────────────────────────────────────────────┐ │
│  │ MISC/AUTOMRIC.CTG  (updated on every photo capture)    │ │
│  └─────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
```

### Component Boundaries

| Component | Responsibility | Communicates With |
|-----------|---------------|-------------------|
| `CtgWatcher` | Monitor CTG timestamp/size for changes | `UsbHelper` |
| `CtgParser` | Download and parse CTG binary format | `UsbHelper`, `CtgWatcher` |
| `CtgChangeDetector` | Compute delta between CTG baselines | `CtgParser`, local cache |
| `CtgIntegrityChecker` | Verify queue state against CTG | `CtgParser`, `TransferQueueRepo` |
| `CtgAwareDeduplicator` | Determine upload eligibility | `CtgParser`, `UploadTracker` |

---

## Phase-Specific Warnings

| Phase Topic | Likely Pitfall | Mitigation |
|-------------|---------------|------------|
| CTG binary format parsing | CTG format varies by camera model/firmware | Support multiple format versions, fallback to timestamp detection |
| Handle stability | MTP handles may change after reconnect | Map handles to fingerprints, re-resolve after disconnect |
| CTG download timing | Camera busy writing during rapid capture | Queue CTG download, debounce with 500ms delay |
| Large CTG files | High-resolution burst sequences create large catalogs | Stream parsing, don't load entire file to memory |
| Session reconnection | PTP session drops on cable disconnect | Implement session pooling with exponential backoff |

---

## MVP Recommendation

**Phase 1: CTG Change Detection Enhancement**
- Keep existing `CtgWatcher` timestamp monitoring
- Add CTG file download on change detection
- Parse CTG to extract handle list
- Emit new files list instead of just change event

**Phase 2: CTG-Based Deduplication**
- Store CTG record fingerprints in database
- Query CTG before enqueueing uploads
- Skip files already in CTG-valid upload history

**Phase 3: Integrity Verification**
- Add periodic CTG state comparison
- Flag files missing from camera
- Detect size mismatches for re-upload

**Defer:** Content hash verification (requires Canon native PTP extension support)

---

## Gaps to Address

- **CTG binary format specification:** Requires reverse-engineering from actual camera samples
- **Checksum support:** Unknown if/which Canon cameras include checksums in CTG
- **Performance testing:** Need to measure CTG download/parse time on various camera models
- **Concurrent capture handling:** How to handle new CTG data during upload queue processing

---

## Sources

- Existing codebase: `CtgWatcher.kt`, `PhotoWatcher.kt`, `MtpContentObserver.kt`, `CannonPtpTransport.kt`
- Canon PTP extension codes from `CanonCameraInitializer.kt`
- Transfer queue fingerprint strategy from `TransferQueueItemEntity.kt`
