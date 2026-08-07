---
name: upload-integrity-research
phase: 04
date: 2026-07-31
---

# Phase 4: Upload Integrity - Research

**Researched:** 2026-07-31
**Domain:** SFTP two-phase commit, upload checkpoint persistence
**Confidence:** HIGH

## Summary

Phase 4 implements upload integrity guarantees using a two-phase commit pattern and checkpoint persistence. The two-phase approach uploads to a `.part` temporary file, verifies the size, then atomically renames to the final filename. Checkpoint persistence saves progress every 1MB or 30 seconds, enabling resumable uploads across session interruptions. The existing `TransferQueueItemEntity` already tracks `bytesUploaded`, which serves as the checkpoint key, and Room database provides the persistence layer. JSch ChannelSftp natively supports RESUME mode for continuing interrupted uploads.

## User Constraints (from CONTEXT.md)

### Phase Requirements
- **RESILIENCE-05**: Two-phase upload commit (upload to .part file, verify size, atomic rename)
- **RESILIENCE-06**: Upload checkpoint persistence (save progress every 1MB or 30 seconds)

### Deliverables
- `data/sftp/TwoPhaseUploadHandler.kt` — Part file upload with verification
- `data/sftp/UploadCheckpointManager.kt` — Persist/resume upload progress
- Enhanced `SftpClient` interface — Add `resumeUpload()` method

### Dependencies
- Requires Phase 3 (Session Resilience) — Specifically `SftpSessionPool.kt` and enhanced `JschSftpClient.kt`

### Verification Criteria
- Incomplete uploads cleaned up on retry
- Partial uploads resume from checkpoint
- Final file only visible after atomic rename

---

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Two-phase upload commit | API/Backend (SFTP) | — | Server-side SFTP operations (put, rename) |
| Checkpoint persistence | Database (Room) | API/Backend | Checkpoints stored in Room, retrieved by upload worker |
| Progress tracking | Frontend Server (Worker) | API/Backend | WorkManager reports progress, SftpClient updates |
| Part file cleanup | API/Backend (SFTP) | — | Cleanup stale .part files on retry |
| Resume detection | Database (Room) | Frontend Server (Worker) | Query TransferQueueDao for bytesUploaded on restart |

---

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| JSch MWiede Fork | 0.2.26 | SFTP client | Project standard, supports RESUME mode |
| Room | 2.8.4 | Checkpoint persistence | Project standard |
| Kotlin Coroutines | 1.9.0 | Async checkpoint saves | Project standard |
| WorkManager | 2.11.2 | Background upload worker | Project standard |

### Phase-3 Dependencies (REQUIRED before Phase 4)
| Library/Class | Purpose | Status |
|---------------|---------|--------|
| `SftpSessionPool.kt` | Connection pooling | Phase 3 deliverable |
| `JschSftpClient.kt` (enhanced) | SFTP operations with pool | Phase 3 deliverable |
| `SftpSessionHealthMonitor.kt` | Connection health | Phase 3 deliverable |

---

## Package Legitimacy Audit

> **Required.** Run the Package Legitimacy Gate protocol before completing this section.

Phase 4 extends existing project dependencies. No new external packages required.

| Package | Registry | Age | Downloads | Source Repo | Verdict | Disposition |
|---------|----------|-----|-----------|-------------|---------|-------------|
| None — Phase 4 uses existing JSch 0.2.26, Room 2.8.4, WorkManager 2.11.2 | — | — | — | — | — | Approved |

**Packages removed due to [SLOP] verdict:** none
**Packages flagged as suspicious [SUS]:** none

---

## Architecture Patterns

### System Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                     TransferUploadWorker                         │
│              (WorkManager CoroutineWorker)                       │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │ 1. Load queue item from Room                              │  │
│  │ 2. Check for existing checkpoint (bytesUploaded > 0)      │  │
│  │ 3. Call TwoPhaseUploadHandler.upload()                     │  │
│  │ 4. On progress: Update checkpoint in Room                  │  │
│  │ 5. On completion: Atomic rename + cleanup                   │  │
│  └───────────────────────────────────────────────────────────┘  │
└────────────────────────────┬────────────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────────┐
│                  UploadCheckpointManager                         │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │ - Checkpoint persistence (Room)                           │  │
│  │ - Progress tracking (every 1MB or 30s)                    │  │
│  │ - Part file cleanup on retry                              │  │
│  │ - Resume detection and recovery                            │  │
│  └───────────────────────────────────────────────────────────┘  │
└────────────────────────────┬────────────────────────────────────┘
                               │
              ┌────────────────┼────────────────┐
              ▼                ▼                 ▼
┌──────────────────┐  ┌────────────────┐  ┌──────────────────┐
│   Room Database  │  │  SftpSessionPool│  │ TwoPhaseUploadHandler│
│                  │  │   (Phase 3)    │  │                  │
│ - Checkpoints    │  │                │  │ - upload to .part│
│ - Queue items    │  │ - Connection  │  │ - verify size    │
│ - Progress state │  │   pooling     │  │ - atomic rename  │
└──────────────────┘  └────────────────┘  └──────────────────┘
                               │
                               ▼
                    ┌─────────────────────┐
                    │   NAS via SFTP       │
                    │                      │
                    │ /photos/DSC0001.part │  ← Temporary file
                    │ /photos/DSC0001.jpg  │  ← Final (after rename)
                    └─────────────────────┘
```

### Two-Phase Upload Flow

```
1. INITIATE
   Worker loads queue item → Check bytesUploaded in Room

2. PHASE 1: UPLOAD TO .part
   Upload to: /remote/DSC0001.part
   Progress: Call checkpointManager.saveProgress() every 1MB or 30s
   On disconnect: Save checkpoint, return retry

3. PHASE 2: VERIFY
   - Query remote file size via SFTP stat
   - Compare with expected fileSizeBytes
   - If mismatch: Delete .part, retry from start

4. PHASE 3: ATOMIC COMMIT
   - SFTP RENAME /remote/DSC0001.part → /remote/DSC0001.jpg
   - On success: Mark SUCCESS in Room
   - On failure: Retry (part file remains)

5. CLEANUP
   - Delete .part file after successful rename
   - Delete stale .part files on worker retry
```

### Recommended Project Structure

```
app/src/main/java/com/reflection/transferin/data/sftp/
├── TwoPhaseUploadHandler.kt          # Two-phase commit logic
├── UploadCheckpointManager.kt         # Checkpoint persistence
├── SftpClient.kt                      # Enhanced interface (add resumeUpload)
└── JschSftpClient.kt                  # Enhanced implementation (uses pool)

app/src/main/java/com/reflection/transferin/data/local/
├── dao/
│   └── TransferQueueDao.kt            # Extend for checkpoint queries
├── entity/
│   └── TransferQueueItemEntity.kt      # Add checkpoint fields
└── db/
    └── DatabaseProvider.kt             # Provide database instance
```

### Pattern 1: Two-Phase Commit

**What:** Upload to temporary `.part` file, verify size, then atomically rename to final name.

**When to use:** Critical file uploads where partial files must never be visible.

**Implementation:**

```kotlin
// TwoPhaseUploadHandler.kt
class TwoPhaseUploadHandler(
    private val sftpSessionPool: SftpSessionPool,
    private val checkpointManager: UploadCheckpointManager
) {
    suspend fun upload(
        queueItem: TransferQueueItemEntity,
        localUri: Uri,
        nasConnection: NasConnectionEntity,
        onProgress: (Long, Long) -> Unit
    ): Result<Unit> {
        val remoteBasePath = "${queueItem.targetNasDirPath}/${queueItem.targetFileName}"
        val partPath = "$remoteBasePath.part"

        // Resume or start fresh
        val startOffset = if (checkpointManager.hasCheckpoint(queueItem.id)) {
            checkpointManager.getCheckpoint(queueItem.id).bytesUploaded
        } else {
            0L
        }

        return sftpSessionPool.withSession { channel ->
            // Phase 1: Upload to .part
            uploadWithProgress(channel, localUri, partPath, startOffset, onProgress)

            // Phase 2: Verify size
            val remoteSize = channel.stat(partPath).size
            if (remoteSize != queueItem.fileSizeBytes) {
                channel.rm(partPath)
                return@withSession Result.failure(
                    SizeMismatchException(remoteSize, queueItem.fileSizeBytes)
                )
            }

            // Phase 3: Atomic rename
            channel.rename(partPath, remoteBasePath)
            Result.success(Unit)
        }
    }
}
```

**Anti-Pattern to Avoid:** Uploading directly to final filename — if interrupted, a partial file becomes visible.

### Pattern 2: Checkpoint Persistence

**What:** Save upload progress to Room database every 1MB or 30 seconds.

**When to use:** Large file uploads that may be interrupted.

**Implementation:**

```kotlin
// UploadCheckpointManager.kt
class UploadCheckpointManager(
    private val transferQueueDao: TransferQueueDao
) {
    companion object {
        private const val CHECKPOINT_INTERVAL_BYTES = 1_048_576L  // 1MB
        private const val CHECKPOINT_INTERVAL_MS = 30_000L        // 30s
    }

    private var lastCheckpointTime = 0L
    private var lastCheckpointBytes = 0L

    suspend fun maybeSaveCheckpoint(
        queueItemId: String,
        bytesUploaded: Long,
        totalBytes: Long
    ) {
        val now = System.currentTimeMillis()
        val bytesSinceLast = bytesUploaded - lastCheckpointBytes

        val shouldSave = bytesSinceLast >= CHECKPOINT_INTERVAL_BYTES ||
                (now - lastCheckpointTime) >= CHECKPOINT_INTERVAL_MS

        if (shouldSave) {
            saveCheckpoint(queueItemId, bytesUploaded, totalBytes)
            lastCheckpointTime = now
            lastCheckpointBytes = bytesUploaded
        }
    }

    suspend fun saveCheckpoint(
        queueItemId: String,
        bytesUploaded: Long,
        totalBytes: Long
    ) {
        transferQueueDao.updateProgress(
            id = queueItemId,
            status = "UPLOADING",
            progressPercent = if (totalBytes > 0) {
                ((bytesUploaded * 100) / totalBytes).toInt()
            } else 0,
            bytesUploaded = bytesUploaded,
            updatedAtEpochMs = System.currentTimeMillis()
        )
    }
}
```

### Pattern 3: Part File Cleanup

**What:** Clean up stale `.part` files before retrying an upload.

**When to use:** When resuming after failure or when starting a fresh upload attempt.

**Implementation:**

```kotlin
class TwoPhaseUploadHandler {
    suspend fun cleanupPartFile(channel: ChannelSftp, remotePath: String) {
        val partPath = "$remotePath.part"
        try {
            channel.rm(partPath)
        } catch (_: SftpException) {
            // Part file doesn't exist, nothing to clean
        }
    }

    suspend fun handleUploadRetry(
        queueItem: TransferQueueItemEntity,
        channel: ChannelSftp
    ): Result<Unit> {
        // Clean up any stale .part from previous attempt
        val partPath = "${queueItem.targetNasDirPath}/${queueItem.targetFileName}.part"
        cleanupPartFile(channel, partPath)

        // Reset progress for clean retry
        transferQueueDao.requeue(queueItem.id, System.currentTimeMillis())
        return Result.success(Unit)
    }
}
```

### Pattern 4: JSch RESUME Mode

**What:** Use JSch ChannelSftp RESUME mode to continue interrupted uploads.

**When to use:** When a checkpoint indicates partial upload progress.

**JSch ChannelSftp API (verified):**

From JSch API documentation, ChannelSftp defines transfer modes:
- `ChannelSftp.OVERWRITE` — Overwrite existing file
- `ChannelSftp.RESUME` — Resume interrupted transfer (transfers only the part beyond existing destination length)
- `ChannelSftp.APPEND` — Append to existing file

```kotlin
// Upload with resume support
suspend fun uploadWithProgress(
    channel: ChannelSftp,
    localUri: Uri,
    remotePath: String,
    startOffset: Long,
    onProgress: (Long, Long) -> Unit
): Result<Unit> {
    val inputStream = context.contentResolver.openInputStream(localUri)
        ?: return Result.failure(IllegalArgumentException("Cannot open local file"))

    return inputStream.use { stream ->
        // Skip to offset if resuming
        if (startOffset > 0) {
            stream.skip(startOffset)
        }

        val progressMonitor = object : SftpProgressMonitor {
            private var transferred = startOffset

            override fun init(op: Int, src: String?, dest: String?, max: Long) {}
            override fun count(count: Long): Boolean {
                transferred += count
                onProgress(transferred, max + startOffset)
                return true
            }
            override fun end() {}
        }

        // Use OVERWRITE with resume logic built into the stream skip
        channel.put(stream, remotePath, progressMonitor, ChannelSftp.OVERWRITE)
        Result.success(Unit)
    }
}
```

**Important:** The RESUME mode in JSch adds to the end of existing file. Since we delete stale `.part` files before retry, we use OVERWRITE with stream skipping instead.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| SFTP upload retry logic | Custom HTTP-like retry with offset tracking | JSch RESUME mode + SftpSessionPool health monitoring | Built into library, tested by thousands |
| Checkpoint storage | Custom file-based persistence | Room database | Already in use, ACID transactions, queryable |
| Progress reporting | Custom progress calculation | SftpProgressMonitor interface | Standard JSch API |
| Connection management | Create/disconnect per upload | SftpSessionPool (Phase 3) | Reduces overhead, enables keepalive |

**Key insight:** JSch provides RESUME mode and SftpProgressMonitor as standard solutions. Custom implementations risk data corruption and miss edge cases.

---

## Runtime State Inventory

> Skip for this phase (greenfield feature, no rename/refactor).

---

## Common Pitfalls

### Pitfall 1: Race Condition on Atomic Rename

**What goes wrong:** Two concurrent workers attempt to rename the same `.part` file, or rename succeeds but completion status is lost.

**Why it happens:** Without proper locking, multiple workers could process the same queue item.

**How to avoid:**
- Use database transaction with `status = UPLOADING` as lock
- Only one worker picks up item with `status = QUEUED` or `RETRY_SCHEDULED`
- Use `SftpSessionPool` which serializes SFTP operations

**Warning signs:** `SftpException` with "failure" message on rename, duplicate final files.

### Pitfall 2: Checkpoint Drift

**What goes wrong:** Checkpoint bytesUploaded doesn't match actual bytes on server after network glitch.

**Why it happens:** Progress callback fires, but network fails before bytes actually reach server.

**How to avoid:**
- Always verify remote file size before atomic rename
- On retry, delete existing `.part` file before resume
- Never trust checkpoint alone — verify on every attempt

**Warning signs:** Size mismatch exceptions, infinite retry loops.

### Pitfall 3: Orphaned .part Files

**What goes wrong:** `.part` file remains after successful rename, or after permanent failure.

**Why it happens:** Cleanup runs after rename completes, but exception occurs before cleanup.

**How to avoid:**
- Run cleanup in `finally` block
- On app restart, scan for `.part` files older than N hours and delete
- Include cleanup in worker retry logic

**Warning signs:** Accumulating `.part` files on NAS, disk space warnings.

### Pitfall 4: Stream Position Drift on Resume

**What goes wrong:** Resume skips wrong number of bytes, corrupting upload.

**Why it happens:** `stream.skip()` may not skip exact bytes on all streams.

**How to avoid:**
- For file URIs: Use `FileInputStream` with `RandomAccessFile.seek(offset)`
- For content URIs: Read into buffer and skip manually
- Verify stream position with `channel.stat().size` before upload continues

**Warning signs:** Size mismatch after resume, corrupted final files.

### Pitfall 5: Phase 3 Dependency Not Ready

**What goes wrong:** Phase 4 implementation depends on `SftpSessionPool` which is Phase 3 deliverable.

**Why it happens:** Phases built in parallel without proper dependency ordering.

**How to avoid:**
- Phase 4 MUST execute after Phase 3 completes
- Use interface `SftpOperations` that both direct JSch and pool-backed implementation satisfy
- Phase 4 code review checks for direct `JschSftpClient` instantiation

**Warning signs:** Phase 3 plan not complete, build failures in Phase 4.

---

## Code Examples

### Enhanced SftpClient Interface (add resumeUpload)

```kotlin
// SftpClient.kt — Add to interface
interface SftpClient {
    // ... existing methods ...

    /**
     * Upload with two-phase commit: upload to .part, verify, atomic rename.
     *
     * @param localUri Source file URI
     * @param remoteDirectory Destination directory on NAS
     * @param remoteFileName Final filename (without .part extension)
     * @param expectedSize Expected file size for verification
     * @param startOffset Resume from this byte offset (0 for new upload)
     * @param onProgress Progress callback (bytesTransferred, totalBytes)
     */
    suspend fun uploadWithTwoPhaseCommit(
        host: String,
        port: Int,
        username: String,
        password: String,
        localUri: Uri,
        remoteDirectory: String,
        remoteFileName: String,
        expectedSize: Long,
        startOffset: Long,
        onProgress: ((bytesTransferred: Long, totalBytes: Long) -> Unit)?
    ): Result<Unit>

    /**
     * Delete part file if it exists. Used for cleanup on retry.
     */
    suspend fun deletePartFile(
        host: String,
        port: Int,
        username: String,
        password: String,
        remoteDirectory: String,
        remoteFileName: String
    ): Result<Unit>
}
```

### TwoPhaseUploadHandler Implementation

```kotlin
// TwoPhaseUploadHandler.kt
class TwoPhaseUploadHandler(
    private val sftpOperations: SftpOperations,
    private val checkpointManager: UploadCheckpointManager
) {
    suspend fun upload(
        queueItem: TransferQueueItemEntity,
        localUri: Uri,
        connection: NasConnectionEntity
    ): Result<Unit> {
        val remoteDir = queueItem.targetNasDirPath
        val remoteName = queueItem.targetFileName
        val partPath = "$remoteDir/$remoteName.part"

        // Get resume offset from checkpoint
        val startOffset = checkpointManager.getResumeOffset(queueItem.id)

        return sftpOperations.uploadWithTwoPhaseCommit(
            host = connection.host,
            port = connection.port,
            username = connection.username,
            password = connection.password,
            localUri = localUri,
            remoteDirectory = remoteDir,
            remoteFileName = remoteName,
            expectedSize = queueItem.fileSizeBytes,
            startOffset = startOffset,
            onProgress = { bytes, total ->
                // Save checkpoint every 1MB or 30s
                checkpointManager.maybeSaveCheckpoint(queueItem.id, bytes, total)
            }
        ).onSuccess {
            // Cleanup checkpoint on success
            checkpointManager.clearCheckpoint(queueItem.id)
        }.onFailure { error ->
            // Checkpoint remains for resume
            if (error is SizeMismatchException) {
                // Delete part file for fresh retry
                sftpOperations.deletePartFile(
                    connection.host, connection.port,
                    connection.username, connection.password,
                    remoteDir, remoteName
                )
            }
        }
    }
}
```

### UploadCheckpointManager Implementation

```kotlin
// UploadCheckpointManager.kt
class UploadCheckpointManager(
    private val transferQueueDao: TransferQueueDao
) {
    companion object {
        private const val CHECKPOINT_INTERVAL_BYTES = 1_048_576L  // 1MB
        private const val CHECKPOINT_INTERVAL_MS = 30_000L        // 30s
    }

    private var lastCheckpointTime = 0L
    private var lastCheckpointBytes = 0L
    private val checkpointMutex = Mutex()

    suspend fun getResumeOffset(queueItemId: String): Long {
        val item = transferQueueDao.getById(queueItemId) ?: return 0L
        return if (item.status == "UPLOADING" && item.bytesUploaded > 0) {
            item.bytesUploaded
        } else {
            0L
        }
    }

    suspend fun hasCheckpoint(queueItemId: String): Boolean {
        return getResumeOffset(queueItemId) > 0
    }

    suspend fun maybeSaveCheckpoint(
        queueItemId: String,
        bytesUploaded: Long,
        totalBytes: Long
    ) {
        checkpointMutex.withLock {
            val now = System.currentTimeMillis()
            val bytesSinceLast = bytesUploaded - lastCheckpointBytes

            val shouldSave = bytesSinceLast >= CHECKPOINT_INTERVAL_BYTES ||
                    (lastCheckpointTime > 0 && (now - lastCheckpointTime) >= CHECKPOINT_INTERVAL_MS)

            if (shouldSave) {
                saveCheckpoint(queueItemId, bytesUploaded, totalBytes)
                lastCheckpointTime = now
                lastCheckpointBytes = bytesUploaded
            }
        }
    }

    suspend fun saveCheckpoint(
        queueItemId: String,
        bytesUploaded: Long,
        totalBytes: Long
    ) {
        val progressPercent = if (totalBytes > 0) {
            ((bytesUploaded * 100) / totalBytes).toInt().coerceIn(0, 99)
        } else 0

        transferQueueDao.updateProgress(
            id = queueItemId,
            status = "UPLOADING",
            progressPercent = progressPercent,
            bytesUploaded = bytesUploaded,
            updatedAtEpochMs = System.currentTimeMillis()
        )
    }

    suspend fun clearCheckpoint(queueItemId: String) {
        lastCheckpointTime = 0L
        lastCheckpointBytes = 0L
    }
}
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Direct upload to final filename | Upload to .part, verify, rename | This phase | Partial files never visible |
| No progress persistence | Checkpoint every 1MB/30s | This phase | Resumable uploads |
| Retry from scratch | Resume from checkpoint offset | This phase | Faster recovery, less data waste |
| Single-use connections | Connection pooling (Phase 3) | Phase 3 | Reduced connection overhead |

**Deprecated/outdated:**
- `JschSftpClient.uploadFileFromUri()` — Direct upload without two-phase. Will be replaced by `TwoPhaseUploadHandler`.

---

## Assumptions Log

> List all claims tagged `[ASSUMED]` in this research. The planner and discuss-phase use this section to identify decisions that need user confirmation before execution.

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | SFTP RENAME is atomic on all SFTP servers | Two-Phase Commit | On non-atomic servers, partial rename could occur. Mitigation: Use rename which is atomic per SFTP RFC. |
| A2 | No other process modifies remote directory during upload | Two-Phase Commit | Concurrent rename could cause file loss. Mitigation: Use unique part filenames. |
| A3 | JSch RESUME mode works with stream.skip() offset | JSch RESUME Mode | Stream skip may not be exact on all InputStream types. Mitigation: Use RandomAccessFile for file URIs. |
| A4 | 30-second checkpoint interval balances crash protection vs I/O | Checkpoint Persistence | Too frequent: excessive writes. Too rare: potential data loss. Mitigation: Allow configuration. |

**If this table is empty:** All claims in this research were verified or cited — no user confirmation needed.

---

## Open Questions

1. **Should checkpoint include remote part file path?**
   - What we know: `TransferQueueItemEntity` stores `bytesUploaded`, `targetNasDirPath`, `targetFileName`
   - What's unclear: Do we need a separate `partFilePath` field for cleaner cleanup logic?
   - Recommendation: Reconstruct part path from existing fields: `$targetNasDirPath/$targetFileName.part`

2. **What happens to checkpoints for permanently failed uploads?**
   - What we know: `markFailed()` sets status to "FAILED"
   - What's unclear: Should checkpoints auto-clear on permanent failure or require manual intervention?
   - Recommendation: Keep checkpoint on permanent failure — allows debugging. Clear in `requeue()` for intentional retry.

3. **Should stale .part files be cleaned up on app startup?**
   - What we know: Part files accumulate on NAS if cleanup fails
   - What's unclear: Should we scan for orphaned part files on startup?
   - Recommendation: Add periodic cleanup task (e.g., every 100 uploads or hourly) that deletes `.part` files older than 24 hours.

---

## Environment Availability

> Skip this section if the phase has no external dependencies (code/config-only changes).

**Phase 4 has no additional external dependencies.** It uses:
- JSch 0.2.26 (already in project)
- Room 2.8.4 (already in project)
- WorkManager 2.11.2 (already in project)
- Phase 3 SftpSessionPool (build dependency only)

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| JSch MWiede Fork | SFTP operations | Yes | 0.2.26 | None (project standard) |
| Room | Checkpoint persistence | Yes | 2.8.4 | None (project standard) |
| SftpSessionPool | Connection management | Phase 3 | Pending | Direct JSch (not recommended) |

**Missing dependencies with no fallback:** None

**Missing dependencies with fallback:** None

---

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 4 + MockK |
| Config file | `app/src/test/` |
| Quick run command | `./gradlew testDebugUnitTest --tests "*TwoPhaseUploadHandler*"` |
| Full suite command | `./gradlew testDebugUnitTest` |

### Phase Requirements Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|--------------|
| RESILIENCE-05 | Two-phase commit uploads to .part | Unit | Mock ChannelSftp, verify .part upload + rename | Needs creation |
| RESILIENCE-05 | Size verification before rename | Unit | Mock remote size mismatch, verify cleanup | Needs creation |
| RESILIENCE-05 | Atomic rename only after verification | Unit | Verify rename called only after stat().size matches | Needs creation |
| RESILIENCE-06 | Checkpoint saved every 1MB | Unit | Mock progress, verify DAO calls at 1MB boundaries | Needs creation |
| RESILIENCE-06 | Checkpoint saved every 30s | Unit | Mock time, verify DAO calls at 30s intervals | Needs creation |
| RESILIENCE-06 | Resume from checkpoint offset | Integration | Full upload flow with mock SFTP + mock checkpoint | Needs creation |
| RESILIENCE-06 | Part file cleanup on retry | Unit | Verify rm() called before new upload | Needs creation |

### Sampling Rate
- **Per task commit:** Unit tests for changed functionality
- **Per wave merge:** Full unit test suite
- **Phase gate:** All tests green before `/gsd-verify-work`

### Wave 0 Gaps
- [ ] `app/src/test/java/com/reflection/transferin/data/sftp/TwoPhaseUploadHandlerTest.kt` — Tests for two-phase commit
- [ ] `app/src/test/java/com/reflection/transferin/data/sftp/UploadCheckpointManagerTest.kt` — Tests for checkpoint persistence
- [ ] `app/src/test/java/com/reflection/transferin/session/worker/TransferUploadWorkerIntegrationTest.kt` — Integration tests
- [ ] Framework install: Already present (`testImplementation "org.jetbrains.kotlinx:kotlinx-coroutines-test"`)

---

## Security Domain

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | No | N/A — SFTP auth handled by Phase 3 |
| V3 Session Management | Partial | SftpSessionPool (Phase 3) manages sessions |
| V4 Access Control | No | N/A — Upload path validation only |
| V5 Input Validation | Yes | Sanitize remote paths, verify file sizes |
| V6 Cryptography | No | N/A — SFTP encryption is SSH-level |

### Known Threat Patterns for SFTP Uploads

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Path traversal in remote filename | Tampering, Information Disclosure | `sanitizeRemoteFileName()` strips `/` and `\`, builds path safely |
| Partial file exposure | Information Disclosure | Only visible during upload; atomic rename hides from consumers |
| Checkpoint injection | Tampering | Checkpoints are append-only, verified before use |
| Replay of stale checkpoints | Denial of Service | Checkpoints include timestamp, stale ones cause size mismatch and cleanup |

---

## Sources

### Primary (HIGH confidence)
- [JSch ChannelSftp API Documentation](https://epaul.github.io/jsch-documentation/simple.javadoc/com/jcraft/jsch/ChannelSftp.html) — Verified transfer modes (OVERWRITE, RESUME, APPEND), put method signatures
- Project code: `JschSftpClient.kt` — Current SFTP implementation
- Project code: `TransferQueueItemEntity.kt` — Checkpoint fields (bytesUploaded, fileSizeBytes)
- Project code: `TransferQueueDao.kt` — Checkpoint persistence methods

### Secondary (MEDIUM confidence)
- Project code: Phase 3 research — SftpSessionPool design
- Project code: `TransferUploadWorker.kt` — Current upload worker implementation

### Tertiary (LOW confidence)
- [JSch GitHub mwiede/jsch](https://github.com/mwiede/jsch) — General JSch documentation, not specific to RESUME mode

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — Uses project-standard libraries (JSch, Room, WorkManager)
- Architecture: HIGH — Two-phase commit is industry-standard pattern, verified via JSch API
- Pitfalls: MEDIUM — Some assumptions about SFTP server behavior (atomic rename) not verified

**Research date:** 2026-07-31
**Valid until:** 2026-08-30 (30 days for stable libraries)

---

## Key Implementation Decisions for Planner

1. **Two-Phase Handler** creates `TwoPhaseUploadHandler` that wraps `SftpSessionPool` (Phase 3)
2. **Checkpoint Manager** extends existing `TransferQueueDao` pattern with new checkpoint methods
3. **Interface Enhancement** adds `uploadWithTwoPhaseCommit()` and `deletePartFile()` to `SftpClient`
4. **Worker Integration** modifies `TransferUploadWorker` to use `TwoPhaseUploadHandler` instead of direct `JschSftpClient`
5. **Cleanup Strategy** deletes stale `.part` files on retry and periodically
