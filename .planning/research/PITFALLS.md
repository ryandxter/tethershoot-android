# Domain Pitfalls: Canon Camera Integration and SFTP Resilience

**Project:** TransferIn - Android Camera Tethering App
**Researched:** 2026-07-31
**Confidence:** MEDIUM-HIGH (based on codebase analysis + domain knowledge)

---

## Critical Pitfalls

Mistakes that cause data loss, corruption, or complete rewrites.

### Pitfall 1: Canon .CTG Binary Format Misunderstanding

**What goes wrong:**
The current `CtgWatcher` only monitors CTG file metadata (timestamp, size) as a trigger signal - it does NOT parse the actual CTG binary content. This is a safe approach but misses the full capability of CTG files. However, if you attempt to parse CTG content, naive implementations fail.

**Root causes:**
- Canon CTG files are binary database files, NOT simple text or XML
- File format varies between Canon firmware versions (older CR2-era vs newer R5/R6/R8/R7)
- MTP-reported size may be 0 bytes due to MTP layer caching even when file has content
- CTG contains embedded pointers to file handles that may become stale

**Consequences:**
- Parsing crashes with `ArrayIndexOutOfBoundsException` or `BufferUnderflowException`
- Misinterpreting file handle references causes wrong photos to be detected
- Race between camera writing CTG and app reading causes corrupt reads

**Prevention:**
```
1. NEVER assume CTG size matches MTP-reported size
2. ALWAYS wrap CTG reads in try-catch for buffer errors
3. Validate magic bytes at known offsets before full parse
4. If parse fails, fall back to handle-based detection (current approach is safer)
```

**Current Code Status:** SAFE - the existing `CtgWatcher` avoids binary parsing by using metadata change as the signal rather than parsing CTG content.

---

### Pitfall 2: MTP Object Handle Staleness After Cable Disconnect

**What goes wrong:**
Object handles retrieved via `getAllObjectHandles()` become invalid the moment the USB cable is disconnected. Handles are session-bound identifiers, not persistent file references.

**Root causes:**
- MTP object handles are 32-bit integers scoped to the MTP session
- When `UsbDeviceConnection` closes, all handle references become dangling
- `DocumentsContract` URIs built from handles become invalid
- Any in-flight `getObjectMetadata(handle)` calls will fail silently

**Consequences:**
- `getObjectMetadata()` returns null for handles that were valid
- File stability check fails with "file_info_null" even for stable files
- Upload queue items reference handles that no longer exist
- The `lastSeenMaxHandle` tracking in `UsbSourceOrchestrator` becomes meaningless

**Prevention:**
```kotlin
// BEFORE accessing handles after potential disconnect:
suspend fun safeGetMetadata(handle: Int): CameraMedia? {
    if (!usbHelper.isSessionOpen()) {
        Log.w(TAG, "Session closed, handle $handle is stale")
        return null
    }
    return usbHelper.getObjectMetadata(handle)
}

// Store file metadata INDEPENDENTLY from handles
data class PersistentFileRef(
    val name: String,
    val size: Long,
    val modifiedTime: Long,
    val relativePath: String,  // Use path-based identification
    val handle: Int? = null     // Handle is optional/nullable
)
```

**Current Code Status:** PARTIALLY PROTECTED - `FileStabilityChecker.isFileStable()` handles null returns correctly, but the `UsbSourceOrchestrator.emitNewFiles()` assumes handles remain valid during polling.

---

### Pitfall 3: SFTP Session Per-Request Creates Connection Exhaustion

**What goes wrong:**
The current `JschSftpClient` creates a NEW Session + Channel for EVERY upload operation. With hundreds of photos, this creates connection overhead and may exhaust server connection limits.

**Root causes:**
- `withSftp()` is called for each `uploadFileFromUri()`
- Each call: new JSch session -> TCP handshake -> SSH handshake -> auth -> SFTP channel
- Server-side connection limits (often 10-20 concurrent)
- TCP keepalive not enabled by default

**Consequences:**
- Upload speeds plateau at connection establishment overhead
- Server may reject connections with "too many open files"
- Race condition: session disconnect mid-upload leaves partial file

**Prevention:**
```kotlin
// IMPLEMENT session pooling:
class SftpConnectionPool(
    private val host: String,
    private val port: Int,
    private val username: String,
    private val password: String,
    private val maxConnections: Int = 3
) {
    private val channelPool = ChannelPool<ChannelSftp>(maxConnections)
    private val session: Session  // Single session shared

    // Enable keepalive to detect dead connections
    session.setServerAliveInterval(30_000)
    session.setServerAliveCountMax(3)

    suspend fun <T> withChannel(block: (ChannelSftp) -> T): T {
        val channel = channelPool.acquire()
        try {
            if (!channel.isConnected) {
                channel.connect(10_000)
            }
            return block(channel)
        } finally {
            channelPool.release(channel)
        }
    }
}
```

**Current Code Status:** VULNERABLE - `withSftp()` disconnects after every operation. For 500 photos, this creates 500 separate SSH sessions.

---

## Moderate Pitfalls

### Pitfall 4: MTP Cache Invalidation Race with Camera Writing

**What goes wrong:**
Android's DocumentsContract query returns cached results even after `resolver.notifyChange()` is called. The camera may still be writing the file when the cache refreshes.

**Root causes:**
- `QUERY_ARG_RELOAD = 1` is a hint, not a guarantee
- Camera write completes in firmware, then MTP layer updates, then Android cache updates
- These three stages have variable latency (100ms to 5s)
- Stale file metadata (especially size) causes incorrect stability decisions

**Consequences:**
- `FileStabilityChecker` sees file size as final when camera still writing
- RAW files (CR3/NEF) appear stable at partial size (megabytes wrong)
- Stability check passes on file still being written
- Upload attempts read truncated file from MTP

**Prevention:**
```kotlin
// ADDITION to FileStabilityChecker:
suspend fun isFileStableWithRetry(
    uri: Uri,
    minStableChecks: Int = 2,  // INCREASE from 1
    stabilityWindowMs: Long = 3000  // ADD time-based stability
): Boolean {
    val stabilityWindow = measureTimeMillis {
        repeat(minStableChecks) { /* existing logic */ }
    }
    // REJECT if entire stability window took too long (camera busy)
    if (stabilityWindow > stabilityWindowMs * minStableChecks * 2) {
        Log.w(TAG, "Stability check took too long, camera may be busy")
        return false
    }
    return isStable
}
```

**Current Code Status:** PARTIALLY PROTECTED - `FileStabilityChecker` already does multiple reads, but the window is fixed time, not adaptive to camera write speed.

---

### Pitfall 5: USB Disconnect During Active Upload

**What goes wrong:**
If cable is disconnected while `channel.put()` is in progress, JSch throws `JSchException: "channel is not opened."` or SSH-level errors.

**Root causes:**
- USB disconnect is asynchronous to app execution
- JSch SFTP channel has no awareness of USB state
- Progress callback continues until TCP write fails
- Exception propagates to `TransferQueueDao` state update

**Consequences:**
- Upload marked as failed in DB, but file may have partially transferred
- Next session will re-upload the partial file (if size-based dedup fails)
- Remote file exists but is corrupted

**Prevention:**
```kotlin
// IMPLEMENT upload with verification:
suspend fun uploadWithVerification(
    localUri: Uri,
    remotePath: String,
    expectedSize: Long
): Result<Unit> {
    // 1. Upload to temporary location
    val tempPath = "$remotePath.part"
    uploadToPath(localUri, tempPath)

    // 2. Verify remote size matches expected
    val remoteSize = getRemoteFileSize(tempPath)
    if (remoteSize != expectedSize) {
        deleteRemoteFile(tempPath)
        return Result.failure(UploadException("Size mismatch: $remoteSize vs $expectedSize"))
    }

    // 3. Atomic rename
    renameRemote(tempPath, remotePath)

    return Result.success(Unit)
}
```

**Current Code Status:** VULNERABLE - `JschSftpClient.uploadFileFromUri()` has no verification step and no temporary file pattern.

---

### Pitfall 6: Database State vs SFTP State Divergence

**What goes wrong:**
`TransferQueueDao` marks items as SUCCESS, but the remote SFTP file may not exist or be corrupted. The database becomes the source of truth but is wrong.

**Root causes:**
- `markSuccess()` updates DB immediately after SFTP `put()` returns
- SFTP success only means data written to kernel buffer, not disk
- Network interruption after DB update but before disk write
- Server crash after DB update

**Consequences:**
- DB says "SUCCESS", remote file missing
- Subsequent session skips the file (fingerprint already emitted)
- Data loss is silent

**Prevention:**
```kotlin
// IMPLEMENT two-phase commit:
suspend fun completeUpload(item: TransferQueueItemEntity): Boolean {
    // Phase 1: Verify remote exists
    val remoteExists = sftpClient.remoteFileExists(item.targetPath)
    if (!remoteExists) {
        dao.markFailed(item.id, "Remote file not found after upload")
        return false
    }

    // Phase 2: Verify size
    val remoteSize = sftpClient.getRemoteFileSize(item.targetPath)
    if (remoteSize != item.fileSizeBytes) {
        sftpClient.deleteRemoteFile(item.targetPath)
        dao.markFailed(item.id, "Size mismatch after upload: $remoteSize vs ${item.fileSizeBytes}")
        return false
    }

    // Phase 3: Mark success
    dao.markSuccess(item.id, System.currentTimeMillis())
    return true
}
```

**Current Code Status:** VULNERABLE - `markSuccess()` is called in `SessionViewModel` immediately after `channel.put()` returns, with no verification.

---

## Minor Pitfalls

### Pitfall 7: Handle-Based vs Path-Based Deduplication Mismatch

**What goes wrong:**
`UsbSourceOrchestrator` uses fingerprints (`name|size|modTime`) for deduplication, but camera firmware may reuse names (e.g., "IMG_0001.CR3" overwritten after card format).

**Root causes:**
- Fingerprint uses `lastModified` which camera may reset on format
- Same name + same size = same fingerprint even if different photo
- `TransferQueueDao` has `OnConflictStrategy.IGNORE` for this

**Prevention:**
```kotlin
// Augment fingerprint with handle if available:
val fingerprint = buildString {
    append(name.lowercase())
    append("|")
    append(size)
    append("|")
    append(modTime)
    append("|")
    append(handle)  // Add handle for uniqueness
}
```

**Current Code Status:** PARTIAL - code captures handle but doesn't include in fingerprint.

---

### Pitfall 8: SFTP Timeout Config Ignores Mobile Networks

**What goes wrong:**
`session.timeout = 10_000` (10 seconds) and `session.connect(10_000)` are too aggressive for mobile networks where latency spikes are common.

**Root causes:**
- Mobile data on cellular: latency can spike to 3-5 seconds
- WiFi handoff: 5-10 second interruption normal
- NAS responding slowly under load

**Consequences:**
- Valid connections timeout and reconnect
- Reconnection storm when many uploads retry simultaneously
- "Connection refused" after timeout even when server is fine

**Prevention:**
```kotlin
// IMPLEMENT adaptive timeouts:
val baseTimeout = when {
    isOnMobileNetwork() -> 60_000  // 60s for cellular
    isOnWifi() -> 30_000          // 30s for WiFi
    else -> 20_000                // 20s default
}

session.timeout = baseTimeout
session.connect(baseTimeout)

// ADD retry with backoff:
suspend fun uploadWithRetry(
    item: TransferQueueItemEntity,
    maxRetries: Int = 3
): Result<Unit> {
    var lastError: Throwable? = null
    repeat(maxRetries) { attempt ->
        if (attempt > 0) {
            delay(attempt * 2000L)  // 2s, 4s, 6s backoff
        }
        val result = runCatching { uploadFile(item) }
        if (result.isSuccess) return result
        lastError = result.exceptionOrNull()
    }
    return Result.failure(lastError ?: UnknownError())
}
```

**Current Code Status:** VULNERABLE - hardcoded 10s timeout with no retry logic for transient failures.

---

### Pitfall 9: Canon PTP Session State Leakage

**What goes wrong:**
`CanonCameraInitializer` and `CanonPtpEnumerator` maintain PTP session state, but `closeCameraSessionInternalNoLock()` may not properly close the PTP session, leading to zombie sessions.

**Root causes:**
- Canon PTP native session is separate from MTP session
- `closeCanonNativeSessionIfNeeded()` checks `hasActiveSessionFor()` which may return stale state
- If camera powers down without clean disconnect, session state persists in app

**Prevention:**
```kotlin
// IMPLEMENT session cleanup with verification:
private suspend fun closeCanonNativeSessionIfNeeded() {
    val device = currentSessionUsbDevice() ?: return
    val connection = currentSessionConnection ?: return
    val canonInitializer = getCanonInitializer() ?: return

    if (!canonInitializer.hasActiveSessionFor(device.deviceName)) {
        return
    }

    // Verify session is actually usable before closing
    runCatching {
        val session = canonInitializer.getActivePtpSession()
        if (session != null && session.deviceName == device.deviceName) {
            canonInitializer.closeSession(device, connection)
            Log.d(TAG, "Canon native session closed cleanly")
        }
    }.onFailure { e ->
        Log.e(TAG, "Failed to close Canon native session", e)
        // Force forget session state
        canonInitializer.forgetSession(device.deviceName)
    }
}
```

**Current Code Status:** PARTIAL - cleanup exists but lacks verification step.

---

## Phase-Specific Warnings

| Phase Topic | Likely Pitfall | Mitigation |
|-------------|---------------|------------|
| CTG Parsing Implementation | Binary format version mismatch | Version detection first, graceful fallback |
| MTP Handle Caching | Stale handles after disconnect | Always validate session state before handle access |
| SFTP Pool Implementation | Channel reuse with dead connection | Implement health check on acquire |
| Upload Verification | Time-of-check vs time-of-use race | Atomic rename pattern |
| Disconnect Recovery | Partial state from interrupted session | Session state machine with explicit states |

---

## Sources

- Android MTP Documentation (source hierarchy: official)
- JSch API Documentation (source hierarchy: official)
- Canon SDK Documentation for CTG file format (internal knowledge)
- Android DocumentsContract source code analysis (codebase analysis)
- TransferIn codebase analysis: `JschSftpClient.kt`, `UsbHelper.kt`, `FileStabilityChecker.kt`, `UsbSourceOrchestrator.kt`, `CtgWatcher.kt`, `TransferQueueDao.kt`
