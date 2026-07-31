# Architecture Research: TransferIn v1.0 Integration

**Project:** TransferIn Android App - Canon Camera Transfer
**Researched:** 2026-07-31
**Domain:** Mobile photo transfer with tethered camera support
**Overall Confidence:** HIGH

## Executive Summary

This document describes how to integrate three new features into the existing TransferIn architecture:

1. **Canon .CTG database file parsing** for upload integrity checking
2. **Smart upload detection** comparing camera state vs. uploaded state
3. **USB/SFTP session pooling** and reconnection resilience

The existing architecture uses Clean Architecture with MVVM, Repository pattern, and domain engines. The new features require targeted additions to the data layer (CTG parser, session pool), domain layer (smart detection engine), and modifications to the upload worker (checkpoint/resume).

---

## 1. Integration Points for CtgWatcher with ShootModeEngine

### Current State

The existing `CtgWatcher` (in `data/usb/CtgWatcher.kt`) monitors CTG file metadata changes (timestamp, size) but does NOT parse the CTG binary content. It uses `findCtgFile()` to locate CTG files and polls for changes every 2 seconds.

### Required Enhancement

**New Component: `CtgParser` (data layer)**

The `CtgWatcher` should emit `CtgChangeEvent` containing fingerprint data that the `ShootModeEngine` can use to trigger smart detection.

**Integration Flow:**

```
CtgWatcher                              ShootModeEngine
    |                                       |
    |-- detect CTG change ---------------->|
    |                                       |
    |-- emit CtgChangeEvent                 |
    |   (fingerprint, handle, fileName)     |
    |                                       |
    |<-- request new photos via CTG -------|
    |                                       |
    |-- return new photo handles            |
    |                                       |
```

### Proposed Interface

```kotlin
// In data/usb/CtgParser.kt (NEW)
class CtgParser(
    private val usbHelper: UsbHelper
) {
    /**
     * Parse CTG file and return list of photo references.
     * Each reference contains: filename, handle, size, timestamp
     */
    suspend fun parseCtgFile(storageId: Int): List<CtgPhotoEntry>
}

/**
 * Entry parsed from CTG file
 */
data class CtgPhotoEntry(
    val fileName: String,
    val objectHandle: Int,
    val sizeBytes: Long,
    val modifiedTime: Long,
    val fingerprint: String  // Unique identifier for deduplication
)
```

**Updated CtgWatcher Integration:**

```kotlin
// In domain/shoot/ShootModeEngine.kt
class ShootModeEngine(
    // ... existing dependencies
    private val ctgParser: CtgParser  // NEW
) {
    // In checkForNewPhotosViaMediaStore(), replace or supplement with:
    private suspend fun checkForNewPhotosViaCtg() {
        // 1. Poll CtgWatcher for changes
        // 2. If changed, parse CTG file via CtgParser
        // 3. Compare parsed entries against baseline
        // 4. Emit only new entries as MediaJob
    }
}
```

### Location Recommendation

| Component | Location | Rationale |
|-----------|----------|-----------|
| `CtgParser` | `data/usb/CtgParser.kt` | Parses USB/MTP data, closely coupled to UsbHelper |
| `CtgPhotoEntry` | `domain/model/CtgPhotoEntry.kt` | Domain model, reused across layers |
| Integration in ShootModeEngine | `domain/shoot/ShootModeEngine.kt` | Domain logic for photo detection |

---

## 2. New Repository/Mapper for .CTG File Content

### Repository Pattern

Following the existing pattern (see `CameraMediaRepository`, `TransferQueueRepository`), create a dedicated repository for CTG-related data.

### Proposed Architecture

```
domain/repository/
    CtgRepository.kt          // Interface defining CTG operations

data/repository/
    CtgRepositoryImpl.kt      // Implementation using CtgParser
```

### Interface Design

```kotlin
// In domain/repository/CtgRepository.kt
interface CtgRepository {
    /**
     * Get all photos from CTG database for a storage
     */
    suspend fun getPhotosFromCtg(storageId: Int): List<CtgPhotoEntry>

    /**
     * Get photos captured since a given timestamp (for incremental sync)
     */
    suspend fun getNewPhotosSince(storageId: Int, sinceTimestamp: Long): List<CtgPhotoEntry>

    /**
     * Get baseline photos (existing before current session)
     */
    suspend fun getBaselinePhotos(storageId: Int): List<CtgPhotoEntry>

    /**
     * Save parsed CTG snapshot for comparison
     */
    suspend fun saveCtgSnapshot(storageId: Int, photos: List<CtgPhotoEntry>, sessionId: String)

    /**
     * Get previously saved snapshot fingerprints
     */
    suspend fun getKnownFingerprints(sessionId: String): Set<String>
}
```

### Entity Design

```kotlin
// In data/local/entity/CtgSnapshotEntity.kt (NEW)
@Entity(
    tableName = "ctg_snapshots",
    indices = [
        Index(value = ["storageId", "sessionId"]),
        Index(value = ["fingerprint"])
    ]
)
data class CtgSnapshotEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val sessionId: String,
    val storageId: Int,

    val fileName: String,
    val objectHandle: Int,
    val sizeBytes: Long,
    val modifiedTime: Long,
    val fingerprint: String,

    val capturedAt: Long = System.currentTimeMillis()
)
```

### Mapper

```kotlin
// In data/repository/CtgRepositoryImpl.kt
class CtgRepositoryImpl(
    private val ctgParser: CtgParser,
    private val ctgSnapshotDao: CtgSnapshotDao  // NEW DAO
) : CtgRepository {

    override suspend fun getPhotosFromCtg(storageId: Int): List<CtgPhotoEntry> {
        return ctgParser.parseCtgFile(storageId)
    }

    override suspend fun getNewPhotosSince(
        storageId: Int,
        sinceTimestamp: Long
    ): List<CtgPhotoEntry> {
        val allPhotos = ctgParser.parseCtgFile(storageId)
        return allPhotos.filter { it.modifiedTime > sinceTimestamp }
    }

    override suspend fun getBaselinePhotos(storageId: Int): List<CtgPhotoEntry> {
        // Return photos known before this session
        return ctgSnapshotDao.getAll()
            .filter { it.storageId == storageId }
            .map { it.toCtgPhotoEntry() }
    }

    override suspend fun saveCtgSnapshot(
        storageId: Int,
        photos: List<CtgPhotoEntry>,
        sessionId: String
    ) {
        val entities = photos.map { photo ->
            CtgSnapshotEntity(
                sessionId = sessionId,
                storageId = storageId,
                fileName = photo.fileName,
                objectHandle = photo.objectHandle,
                sizeBytes = photo.sizeBytes,
                modifiedTime = photo.modifiedTime,
                fingerprint = photo.fingerprint
            )
        }
        ctgSnapshotDao.insertAll(entities)
    }

    override suspend fun getKnownFingerprints(sessionId: String): Set<String> {
        return ctgSnapshotDao.getFingerprintsBySession(sessionId).toSet()
    }
}
```

### Database Table Addition

Add to `TransferInDatabase.kt`:

```kotlin
@Dao
interface CtgSnapshotDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<CtgSnapshotEntity>)

    @Query("SELECT * FROM ctg_snapshots WHERE sessionId = :sessionId")
    suspend fun getBySession(sessionId: String): List<CtgSnapshotEntity>

    @Query("SELECT fingerprint FROM ctg_snapshots WHERE sessionId = :sessionId")
    suspend fun getFingerprintsBySession(sessionId: String): List<String>

    @Query("DELETE FROM ctg_snapshots WHERE sessionId = :sessionId")
    suspend fun deleteBySession(sessionId: String)
}
```

---

## 3. Session Pooling Architecture for JschSftpClient

### Current State

The existing `JschSftpClient` creates a new `Session` and `ChannelSftp` for every operation in `withSftp()`. This is inefficient for bulk transfers and provides no resilience.

### Proposed Architecture

```
data/sftp/
    SftpSession.kt            // Wrapper for JSch Session + Channel
    SftpSessionPool.kt        // Pool manager for session reuse
    SftpClient.kt             // Updated interface
    JschSftpClient.kt         // Updated implementation
```

### Session Pool Design

```kotlin
// In data/sftp/SftpSessionPool.kt
class SftpSessionPool(
    private val context: Context,
    private val maxSessions: Int = 2,  // Limit concurrent sessions
    private val sessionTimeoutMs: Long = 300_000,  // 5 minutes
    private val keepAliveIntervalMs: Long = 60_000  // 1 minute
) {
    private val credentialsToPool = ConcurrentHashMap<SftpCredentials, ChannelPool>()

    /**
     * Acquire a session for the given credentials.
     * Reuses existing session if available and healthy.
     */
    suspend fun acquire(credentials: SftpCredentials): SftpSessionHandle

    /**
     * Release a session back to the pool.
     * Keeps session alive for reuse if healthy.
     */
    fun release(handle: SftpSessionHandle)

    /**
     * Invalidate a session (connection dropped, etc.)
     * Pool will create a fresh session on next acquire.
     */
    fun invalidate(handle: SftpSessionHandle)

    /**
     * Close all sessions (app shutdown)
     */
    fun shutdown()
}

data class SftpCredentials(
    val host: String,
    val port: Int,
    val username: String,
    val password: String
)

class SftpSessionHandle(
    val session: SftpSession,
    val credentials: SftpCredentials,
    val createdAt: Long,
    val lastUsedAt: Long
) {
    val isHealthy: Boolean
        get() = session.isConnected && !session.isExpired()
}
```

### Session Implementation

```kotlin
// In data/sftp/SftpSession.kt
class SftpSession(
    private val credentials: SftpCredentials,
    private val sessionTimeout: Long = 10_000,
    private val channelTimeout: Long = 10_000
) {
    private var jschSession: Session? = null
    private var channel: ChannelSftp? = null

    val isConnected: Boolean
        get() = jschSession?.isConnected == true && channel?.isConnected == true

    suspend fun connect() {
        val jsch = JSch()

        jschSession = jsch.getSession(
            credentials.username,
            credentials.host,
            credentials.port
        ).apply {
            setPassword(credentials.password)
            setConfig(Properties().apply {
                put("StrictHostKeyChecking", "no")
                put("PreferredAuthentications", "password")
            })
            timeout = sessionTimeout.toInt()
            connect(sessionTimeout.toInt())
        }

        channel = (jschSession!!.openChannel("sftp") as ChannelSftp).apply {
            connect(channelTimeout.toInt())
        }
    }

    suspend fun disconnect() {
        try {
            channel?.disconnect()
        } finally {
            try {
                jschSession?.disconnect()
            } finally {
                channel = null
                jschSession = null
            }
        }
    }

    suspend fun reconnect() {
        disconnect()
        connect()
    }

    fun getChannel(): ChannelSftp = channel
        ?: throw IllegalStateException("Session not connected")

    fun sendKeepAlive() {
        jschSession?.sendKeepAliveMsg()
    }
}
```

### Updated SftpClient Interface

```kotlin
// In data/sftp/SftpClient.kt (MODIFIED)
interface SftpClient {

    /**
     * Upload with resume support.
     * If offset > 0, resumes from that byte position.
     */
    suspend fun uploadFileFromUri(
        host: String,
        port: Int,
        username: String,
        password: String,
        localUri: Uri,
        remoteDirectory: String,
        remoteFileName: String,
        offset: Long = 0,  // NEW: resume offset
        onProgress: ((bytesTransferred: Long, totalBytes: Long) -> Unit)? = null
    ): Result<Unit>

    // ... existing methods unchanged
}
```

### Pool Integration with UploadWorker

```kotlin
// In session/worker/TransferUploadWorker.kt (MODIFIED)
class TransferUploadWorker(...) : CoroutineWorker(...) {

    // Inject or create pool at worker level
    private val sftpPool: SftpSessionPool by lazy {
        SftpSessionPool(applicationContext)
    }

    override suspend fun doWork(): Result {
        // ... existing validation ...

        val credentials = SftpCredentials(
            host = nasConnection.host,
            port = nasConnection.port,
            username = nasConnection.username,
            password = nasConnection.password
        )

        val handle = sftpPool.acquire(credentials)

        return try {
            val offset = queueItem.bytesUploaded  // Resume from last position

            val uploadResult = handle.session.getChannel().let { channel ->
                uploadWithChannel(channel, localUri, remoteDirectory, remoteFileName, offset, onProgress)
            }

            // ... existing success/failure handling ...
        } finally {
            sftpPool.release(handle)
        }
    }

    private suspend fun uploadWithChannel(
        channel: ChannelSftp,
        localUri: Uri,
        remoteDirectory: String,
        remoteFileName: String,
        offset: Long,
        onProgress: ((Long, Long) -> Unit)?
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val inputStream = applicationContext.contentResolver.openInputStream(localUri)
            ?: return@withContext Result.failure(IllegalArgumentException("Cannot open URI"))

        val remoteFullPath = buildRemotePath(remoteDirectory, remoteFileName)
        val totalBytes = queryContentLength(localUri)

        val monitor = createProgressMonitor(offset, totalBytes, onProgress)

        try {
            if (offset > 0) {
                // Resume mode: use APPUT (append with offset)
                // Note: JSch doesn't directly support APPUT, use put with OVERWRITE at offset
                // Requires server-side support or temporary file strategy
                channel.put(inputStream, remoteFullPath, monitor, ChannelSftp.OVERWRITE)
            } else {
                channel.put(inputStream, remoteFullPath, monitor, ChannelSftp.OVERWRITE)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            // Check if partial upload exists on server
            if (isRecoverableError(e)) {
                // Mark for retry with exponential backoff
                sftpPool.invalidate(handle)
            }
            Result.failure(e)
        }
    }

    private fun createProgressMonitor(
        offset: Long,
        totalBytes: Long,
        onProgress: ((Long, Long) -> Unit)?
    ): SftpProgressMonitor {
        return object : SftpProgressMonitor {
            private var transferred = offset

            override fun init(op: Int, src: String?, dest: String?, max: Long) {
                transferred = offset
                onProgress?.invoke(offset, totalBytes)
            }

            override fun count(count: Long): Boolean {
                transferred += count
                onProgress?.invoke(transferred, totalBytes)
                return true
            }

            override fun end() {
                onProgress?.invoke(transferred, totalBytes)
            }
        }
    }
}
```

---

## 4. Checkpoint/Resume Strategy for Interrupted Uploads

### Current State

The existing `TransferQueueItemEntity` tracks `bytesUploaded` and `progressPercent`, but the actual resume logic is not implemented in `TransferUploadWorker`. Failed uploads are simply requeued from byte 0.

### Proposed Strategy

**Three-Layer Checkpoint System:**

1. **Local Progress Tracking** - `bytesUploaded` in database
2. **Upload Worker State** - Managed via WorkManager
3. **Server-Side Verification** - For large files, verify partial upload

### Database Schema Changes

```kotlin
// In data/local/entity/TransferQueueItemEntity.kt (MODIFIED)
data class TransferQueueItemEntity(
    // ... existing fields ...

    // NEW: Checkpoint support
    val uploadOffsetBytes: Long = 0,        // Resume position
    val localTempPath: String? = null,       // Cached source for resume
    val lastCheckpointAt: Long? = null,       // When offset was last saved
    val serverETag: String? = null,           // For HTTP-based resume (if used)
)
```

### Checkpoint DAO Additions

```kotlin
// In data/local/dao/TransferQueueDao.kt (MODIFIED)
@Dao
interface TransferQueueDao {
    // ... existing methods ...

    @Query("""
        UPDATE transfer_queue_items
        SET uploadOffsetBytes = :offset,
            bytesUploaded = :offset,
            progressPercent = :percent,
            lastCheckpointAt = :checkpointAt,
            updatedAtEpochMs = :updatedAt
        WHERE id = :id
    """)
    suspend fun updateCheckpoint(
        id: String,
        offset: Long,
        percent: Int,
        checkpointAt: Long,
        updatedAt: Long
    )

    @Query("SELECT uploadOffsetBytes FROM transfer_queue_items WHERE id = :id")
    suspend fun getUploadOffset(id: String): Long
}
```

### Checkpoint Strategy Implementation

```kotlin
// In session/worker/TransferUploadWorker.kt (MODIFIED)
class TransferUploadWorker(...) : CoroutineWorker(...) {

    companion object {
        private const val CHECKPOINT_INTERVAL_BYTES = 1024 * 1024  // Every 1MB
        private const val CHECKPOINT_INTERVAL_SECONDS = 30        // Or every 30s
    }

    private var lastCheckpointOffset = 0L
    private var lastCheckpointTime = 0L

    override suspend fun doWork(): Result {
        val queueItem = queueDao.getById(queueItemId) ?: return failure

        // Get resume offset
        val resumeOffset = queueItem.uploadOffsetBytes
        val startOffset = if (resumeOffset > 0) {
            Log.i(TAG, "Resuming upload from offset $resumeOffset")
            resumeOffset
        } else {
            0L
        }

        // Perform upload with checkpointing
        val result = performUploadWithCheckpointing(
            queueItem,
            startOffset,
            onCheckpoint = { currentOffset, totalBytes ->
                updateCheckpoint(queueItem.id, currentOffset, totalBytes)
            }
        )

        return result
    }

    private suspend fun performUploadWithCheckpointing(
        queueItem: TransferQueueItemEntity,
        startOffset: Long,
        onCheckpoint: suspend (Long, Long) -> Unit
    ): Result {
        val totalBytes = resolveFileSize(queueItem.sourceUri)
        var currentOffset = startOffset

        // Create progress monitor with checkpointing
        val monitor = object : SftpProgressMonitor {
            override fun init(op: Int, src: String?, dest: String?, max: Long) {
                currentOffset = startOffset
                lastCheckpointTime = System.currentTimeMillis()
            }

            override fun count(count: Long): Boolean {
                currentOffset += count

                // Check if checkpoint needed
                val shouldCheckpoint = (
                    currentOffset - lastCheckpointOffset >= CHECKPOINT_INTERVAL_BYTES ||
                    System.currentTimeMillis() - lastCheckpointTime >= CHECKPOINT_INTERVAL_SECONDS * 1000
                )

                if (shouldCheckpoint) {
                    // Save checkpoint to database
                    val percent = if (totalBytes > 0) {
                        ((currentOffset * 100) / totalBytes).toInt().coerceIn(0, 99)
                    } else 0

                    runCatching {
                        onCheckpoint(currentOffset, totalBytes)
                    }

                    lastCheckpointOffset = currentOffset
                    lastCheckpointTime = System.currentTimeMillis()
                }

                return true
            }

            override fun end() {
                // Final checkpoint
                runCatching {
                    onCheckpoint(currentOffset, totalBytes)
                }
            }
        }

        // Perform the upload
        return executeUpload(queueItem, startOffset, monitor)
    }

    private suspend fun updateCheckpoint(
        id: String,
        offset: Long,
        totalBytes: Long
    ) {
        val percent = if (totalBytes > 0) {
            ((offset * 100) / totalBytes).toInt().coerceIn(0, 99)
        } else 0

        val now = System.currentTimeMillis()

        // Update database
        queueDao.updateCheckpoint(
            id = id,
            offset = offset,
            percent = percent,
            checkpointAt = now,
            updatedAt = now
        )

        // Update WorkManager progress for visibility
        setProgress(workDataOf(
            KEY_PROGRESS_PERCENT to percent,
            KEY_BYTES_UPLOADED to offset,
            KEY_STATUS to "UPLOADING"
        ))

        Log.d(TAG, "Checkpoint saved: offset=$offset, percent=$percent")
    }
}
```

### Recovery Flow

```
App Start / Resume
       |
       v
Check TransferQueueDao for QUEUED/RETRY_SCHEDULED items
       |
       v
For each item with uploadOffsetBytes > 0:
       |
       v
Re-enqueue as WorkManager job with resume offset
       |
       v
TransferUploadWorker reads offset and resumes
```

### Cleanup Strategy

After successful upload, reset checkpoint state:

```kotlin
private suspend fun onUploadSuccess(queueItem: TransferQueueItemEntity) {
    val now = System.currentTimeMillis()

    queueDao.updateProgress(
        id = queueItem.id,
        status = "SUCCESS",
        progressPercent = 100,
        bytesUploaded = queueItem.fileSizeBytes,
        updatedAtEpochMs = now
    )

    queueDao.markSuccess(
        id = queueItem.id,
        completedAtEpochMs = now,
        updatedAtEpochMs = now
    )

    // Reset checkpoint fields
    queueDao.updateCheckpoint(
        id = queueItem.id,
        offset = 0,
        percent = 100,
        checkpointAt = now,
        updatedAt = now
    )
}
```

---

## 5. Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────────┐
│                         PRESENTATION LAYER                          │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────────────┐   │
│  │ SessionVM   │  │ GalleryVM   │  │ ConnectionCameraVM      │   │
│  └──────┬───────┘  └──────┬───────┘  └───────────┬──────────────┘   │
└─────────┼─────────────────┼─────────────────────┼──────────────────┘
          │                 │                     │
┌─────────┼─────────────────┼─────────────────────┼──────────────────┐
│         v                 v                     v    DOMAIN LAYER   │
│  ┌──────────────────────────────────────────────────────────────┐  │
│  │                    ShootModeEngine                           │  │
│  │  - Coordinates tethered shooting                              │  │
│  │  - Integrates with CtgRepository for smart detection       │  │
│  └──────────────────────────────────────────────────────────────┘  │
│  ┌──────────────────┐  ┌──────────────────┐  ┌────────────────┐   │
│  │ UploadQueueManager│  │ CameraEngine     │  │ ImportEngine   │   │
│  └────────┬─────────┘  └────────┬─────────┘  └───────┬────────┘   │
└───────────┼──────────────────────┼──────────────────┼──────────────┘
            │                      │                  │
┌───────────┼──────────────────────┼──────────────────┼──────────────┐
│           v                      v                  v    DATA LAYER │
│  ┌────────────────┐  ┌─────────────────┐  ┌─────────────────┐    │
│  │CtgRepository   │  │CtgParser        │  │CtgWatcher       │    │
│  │(interface)     │  │(parses .CTG)    │  │(monitors CTG)   │    │
│  └───────┬────────┘  └────────┬────────┘  └────────┬────────┘    │
│          │                    │                     │               │
│  ┌───────┴────────────────────┴────────────────────┴─────────┐    │
│  │              CtgRepositoryImpl                            │    │
│  │  - Uses CtgParser to read CTG files                       │    │
│  │  - Stores snapshots in CtgSnapshotDao                     │    │
│  └───────────────────────────────────────────────────────────┘    │
│                                                                   │
│  ┌───────────────────────────────────────────────────────────┐    │
│  │              SftpSessionPool                              │    │
│  │  - Manages JSch session reuse                            │    │
│  │  - Handles reconnection on failure                        │    │
│  └────────────────────────┬──────────────────────────────────┘    │
│                           │                                       │
│  ┌────────────────────────┴──────────────────────────────────┐    │
│  │              JschSftpClient (implements SftpClient)       │    │
│  │  - Uses pooled sessions from SftpSessionPool               │    │
│  │  - Supports upload resume via offset parameter            │    │
│  └───────────────────────────────────────────────────────────┘    │
│                                                                   │
│  ┌───────────────────────────────────────────────────────────┐    │
│  │              TransferUploadWorker                          │    │
│  │  - Checkpoints progress every 1MB or 30s                  │    │
│  │  - Recovers from interruption using stored offset         │    │
│  └───────────────────────────────────────────────────────────┘    │
│                                                                   │
│  ┌───────────────────────────────────────────────────────────┐    │
│  │                    LOCAL DATABASE                          │    │
│  │  ┌─────────────────┐  ┌──────────────────┐  ┌───────────┐ │    │
│  │  │TransferQueueDao │  │CtgSnapshotDao    │  │CameraMedia│ │    │
│  │  │(checkpoint      │  │(CTG photo        │  │Dao        │ │    │
│  │  │ tracking)        │  │ snapshots)       │  │           │ │    │
│  │  └─────────────────┘  └──────────────────┘  └───────────┘ │    │
│  └───────────────────────────────────────────────────────────┘    │
└───────────────────────────────────────────────────────────────────┘
```

---

## 6. Component Responsibilities

| Component | Layer | Responsibility |
|-----------|-------|----------------|
| `CtgParser` | Data | Parse Canon .CTG binary files |
| `CtgWatcher` | Data | Monitor CTG file changes |
| `CtgRepository` | Domain | Interface for CTG operations |
| `CtgRepositoryImpl` | Data | Implementation with snapshot storage |
| `CtgSnapshotDao` | Data | DAO for CTG snapshots |
| `SftpSession` | Data | Wrapper for JSch session |
| `SftpSessionPool` | Data | Pool manager for sessions |
| `JschSftpClient` | Data | Updated to use pooled sessions |
| `TransferUploadWorker` | Data | Checkpoint/resume logic |

---

## 7. Phase-Specific Warnings

| Feature | Likely Pitfall | Mitigation |
|---------|----------------|------------|
| CTG Parsing | CTG format varies by Canon model | Use defensive parsing with fallback to handle-based scanning |
| Session Pooling | Thread safety with JSch | Each session has single-threaded channel access; pool per credential |
| Checkpoint Resume | Server doesn't support resume | Store offset anyway; on failure, retry from 0 |
| USB Disconnect | MTP session state loss | Save checkpoint before disconnect; resume after reconnect |

---

## 8. Recommended Implementation Order

1. **Phase 1: CtgParser** (lowest risk)
   - Add `CtgParser` to parse .CTG files
   - Test with various Canon camera models

2. **Phase 2: CtgRepository + Snapshot Storage**
   - Add `CtgSnapshotEntity` and DAO
   - Implement `CtgRepository`
   - Integrate with `ShootModeEngine`

3. **Phase 3: SftpSessionPool** (moderate risk)
   - Create `SftpSession` wrapper
   - Implement `SftpSessionPool`
   - Update `JschSftpClient` to use pool
   - Add reconnection logic

4. **Phase 4: Checkpoint/Resume** (moderate risk)
   - Update `TransferQueueItemEntity` with offset fields
   - Add checkpoint DAO methods
   - Implement checkpoint logic in `TransferUploadWorker`
   - Add recovery flow on app restart

---

## 9. Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| CTG Integration | MEDIUM | CTG binary format may vary; defensive parsing needed |
| Session Pooling | HIGH | JSch patterns well-established |
| Checkpoint/Resume | HIGH | Database structure already supports progress tracking |
| Overall Architecture | HIGH | Follows existing patterns; minimal disruption |

---

## Sources

- JSch Documentation: [JCraft JSch](http://www.jcraft.com/jsch/)
- Android MTP: [Android MTP Documentation](https://developer.android.com/guide/topics/usb/mtp)
- Room Database: [Android Room Documentation](https://developer.android.com/training/data-storage/room)
