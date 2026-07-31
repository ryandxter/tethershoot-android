# Technology Stack Additions: Shoot Mode Optimization

**Project:** TransferIn Android App
**Research Date:** 2026-07-31
**Focus:** Canon .CTG parsing, session pooling, upload checkpointing

## Executive Summary

This milestone adds three capability clusters to the existing TransferIn stack:
1. Canon .CTG database parsing for upload integrity verification
2. JSch SFTP connection pooling with reconnection resilience
3. Room-based upload checkpointing for partial transfer recovery

**No new core libraries required.** All requirements can be met by extending existing dependencies (Room, JSch) with custom wrappers following established patterns.

---

## 1. Canon .CTG Database Parsing

### Format Analysis

Canon .CTG files are **SQLite databases** used by Canon Camera Connect to track transferred photos. Key characteristics:

| Property | Value |
|----------|-------|
| Location | `/MISC/Canon_CCS/` on SD card |
| Format | SQLite 3 (`.db` extension, `.CTG` filename) |
| Purpose | Track which photos have been transferred |
| Schema | Vendor-specific; typically contains `images` table with `filename`, `size`, `date`, `transfer_status` |

### Recommended Approach

**Library:** `androidx.sqlite:sqlite-ktx:2.8.4` (already in scope via Room)

**Why SQLite over binary parsing:**
- .CTG files are SQLite databases, not custom binary format
- Binary parsing would be fragile and require reverse-engineering
- SQLite provides reliable schema extraction via standard queries

### Implementation Pattern

```kotlin
// domain/ctg/CtgDatabaseParser.kt
class CtgDatabaseParser {
    
    fun parseCtgFile(dbFile: File): CtgDatabase? {
        return try {
            SQLiteDatabase.openDatabase(
                dbFile.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY
            ).use { db ->
                extractImageRecords(db)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse CTG file", e)
            null
        }
    }
    
    private fun extractImageRecords(db: SQLiteDatabase): CtgDatabase {
        val cursor = db.rawQuery(
            "SELECT name, size, date FROM images WHERE transferred = 0",
            null
        )
        // Map to domain model
    }
}

data class CtgDatabase(
    val cameraId: String,
    val transferredImages: List<CtgImageRecord>,
    val lastModified: Long
)

data class CtgImageRecord(
    val filename: String,
    val size: Long,
    val capturedAt: Long,
    val isTransferred: Boolean
)
```

### Integration with Existing Code

| Existing Component | Integration Point |
|--------------------|-------------------|
| `PhotoWatcher` | Validate against .CTG before upload |
| `UploadQueueManager` | Skip images marked transferred in .CTG |
| `TransferQueueDao` | Store verified records in Room |

### Anti-Pattern to Avoid

**Do NOT** parse .CTG as raw binary bytes. The format is a SQLite database and requires SQLite access.

---

## 2. JSch SFTP Connection Pooling

### Current State Analysis

The project uses `com.github.mwiede:jsch:0.2.26` (MWiede's maintained fork). Current usage in `JschSftpClient.kt` creates a new session per transfer.

### Connection Pooling Requirements

JSch sessions have these characteristics:

| Property | Behavior |
|----------|----------|
| Thread Safety | NOT thread-safe; one session per thread |
| Channel Creation | Each SFTP operation needs a new `ChannelSftp` |
| Session Lifecycle | Expensive to create; cheap to reuse |
| Keepalive | Built-in via `Session.setServerAliveInterval()` |
| Reconnection | Manual; no automatic retry |

### Recommended Pattern: Session Pool with Channel Borrowing

```
SessionPool (1..N Sessions)
    ├── Session A (for connection 1)
    │   ├── ChannelSftp (borrowed for upload 1)
    │   └── ChannelSftp (borrowed for upload 2)
    └── Session B (for connection 2)
        └── ChannelSftp (borrowed for upload 3)
```

### Implementation Pattern

```kotlin
// session/pool/SftpSessionPool.kt
class SftpSessionPool(
    private val connectionConfig: NasConnectionConfig,
    private val maxSessions: Int = 2
) {
    private val sessions = ConcurrentLinkedQueue<Session>()
    private val activeCount = AtomicInteger(0)
    
    suspend fun <T> withChannel(block: suspend (ChannelSftp) -> T): T {
        val session = acquireSession()
        val channel = session.openSftpChannel()
        return try {
            block(channel)
        } finally {
            channel.close()
            releaseSession(session)
        }
    }
    
    private fun acquireSession(): Session {
        // Reuse existing or create new
        sessions.poll()?.takeIf { it.isConnected }?.let { return it }
        return createAndConnectSession()
    }
    
    private fun createAndConnectSession(): Session {
        val jsch = JSch()
        val session = jsch.getSession(
            connectionConfig.username,
            connectionConfig.host,
            connectionConfig.port
        )
        session.setPassword(connectionConfig.password)
        session.setConfig("StrictHostKeyChecking", "no")
        session.setServerAliveInterval(30000)  // 30s keepalive
        session.setServerAliveCountMax(3)      // 3 misses before disconnect
        session.connect()
        return session
    }
    
    private fun releaseSession(session: Session) {
        if (session.isConnected && activeCount.get() < maxSessions) {
            sessions.offer(session)
        } else {
            session.disconnect()
        }
    }
}
```

### Reconnection Resilience Pattern

```kotlin
// session/pool/ResilientSftpOperation.kt
class ResilientSftpOperation(
    private val sessionPool: SftpSessionPool,
    private val maxRetries: Int = 3
) {
    suspend fun uploadFile(
        localFile: File,
        remotePath: String,
        progress: (Long) -> Unit
    ): Result<Unit> {
        var lastException: Exception? = null
        
        repeat(maxRetries) { attempt ->
            try {
                sessionPool.withChannel { channel ->
                    channel.put(localFile.inputStream(), remotePath) { uploaded, _, _ ->
                        progress(uploaded)
                    }
                }
                return Result.success(Unit)
            } catch (e: Exception) {
                lastException = e
                delay(calculateBackoff(attempt))  // Exponential backoff
            }
        }
        return Result.failure(lastException ?: IOException("Unknown error"))
    }
    
    private fun calculateBackoff(attempt: Int): Long {
        return (1000L * (1 shl attempt)).coerceAtMost(30000L)  // 1s, 2s, 4s, max 30s
    }
}
```

### Key JSch Configuration for Resilience

| Config Key | Value | Purpose |
|------------|-------|---------|
| `StrictHostKeyChecking` | `no` | Avoid host key prompts |
| `ServerAliveInterval` | `30000` | Detect dead connections |
| `ServerAliveCountMax` | `3` | Max keepalive failures before reconnect |
| `PreferredAuthentications` | `password` | Simplified auth |

---

## 3. Upload Checkpointing with Room

### Checkpoint Requirements

Resume capability for interrupted uploads requires:

| State to Persist | When | Purpose |
|------------------|------|---------|
| `localPath` | Before upload | Source file reference |
| `remotePath` | Before upload | Destination on NAS |
| `bytesUploaded` | During upload | Resume point |
| `totalBytes` | Before upload | Total file size |
| `status` | Always | PENDING, IN_PROGRESS, COMPLETED, FAILED |
| `lastAttempt` | After attempt | For retry logic |
| `retryCount` | After failure | Max retry tracking |

### Extended Entity Design

```kotlin
// data/local/entity/UploadCheckpointEntity.kt
@Entity(tableName = "upload_checkpoints")
data class UploadCheckpointEntity(
    @PrimaryKey
    val jobId: String,                    // Links to MediaJob
    val localPath: String,
    val remotePath: String,
    val totalBytes: Long,
    val bytesUploaded: Long = 0,
    val status: CheckpointStatus = CheckpointStatus.PENDING,
    val createdAt: Long = System.currentTimeMillis(),
    val lastAttemptAt: Long? = null,
    val retryCount: Int = 0,
    val errorMessage: String? = null
)

enum class CheckpointStatus {
    PENDING,      // Not started
    IN_PROGRESS,  // Currently uploading
    COMPLETED,    // Successfully transferred
    FAILED,       // Exceeded retries
    CANCELLED     // User cancelled
}
```

### DAO Additions

```kotlin
// data/local/dao/UploadCheckpointDao.kt
@Dao
interface UploadCheckpointDao {
    
    @Query("SELECT * FROM upload_checkpoints WHERE status = :status")
    suspend fun getByStatus(status: CheckpointStatus): List<UploadCheckpointEntity>
    
    @Query("SELECT * FROM upload_checkpoints WHERE jobId = :jobId")
    suspend fun getByJobId(jobId: String): UploadCheckpointEntity?
    
    @Query("SELECT * FROM upload_checkpoints WHERE status = 'IN_PROGRESS'")
    suspend fun getInProgress(): List<UploadCheckpointEntity>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(checkpoint: UploadCheckpointEntity)
    
    @Query("UPDATE upload_checkpoints SET bytesUploaded = :bytes WHERE jobId = :jobId")
    suspend fun updateProgress(jobId: String, bytes: Long)
    
    @Query("UPDATE upload_checkpoints SET status = :status WHERE jobId = :jobId")
    suspend fun updateStatus(jobId: String, status: CheckpointStatus)
    
    @Query("UPDATE upload_checkpoints SET status = 'FAILED', errorMessage = :error, retryCount = retryCount + 1 WHERE jobId = :jobId")
    suspend fun markFailed(jobId: String, error: String)
    
    @Transaction
    suspend fun resumeOrCreate(job: MediaJob): UploadCheckpointEntity {
        val existing = getByJobId(job.id)
        if (existing != null && existing.status != CheckpointStatus.COMPLETED) {
            return existing
        }
        val checkpoint = UploadCheckpointEntity(
            jobId = job.id,
            localPath = job.localPath,
            remotePath = job.remotePath,
            totalBytes = job.totalBytes,
            status = CheckpointStatus.PENDING
        )
        insert(checkpoint)
        return checkpoint
    }
}
```

### Integration with WorkManager

```kotlin
// data/worker/CheckpointingUploadWorker.kt
class CheckpointingUploadWorker(
    ctx: Context,
    params: WorkerParameters
) : CoroutineWorker(ctx, params) {
    
    private val checkpointDao: UploadCheckpointDao = ...
    private val sftpPool: SftpSessionPool = ...
    
    override suspend fun doWork(): Result {
        val jobId = inputData.getString(KEY_JOB_ID) ?: return Result.failure()
        
        // Get or create checkpoint
        val checkpoint = checkpointDao.resumeOrCreate(loadJob(jobId))
        
        if (checkpoint.status == CheckpointStatus.COMPLETED) {
            return Result.success()  // Already done
        }
        
        // Update status to in-progress
        checkpointDao.updateStatus(jobId, CheckpointStatus.IN_PROGRESS)
        
        val file = File(checkpoint.localPath)
        return try {
            resilientSftp.uploadFile(
                localFile = file,
                remotePath = checkpoint.remotePath,
                resumeFrom = checkpoint.bytesUploaded
            ) { bytesWritten ->
                // Periodic checkpoint save (every 1MB)
                if (bytesWritten % (1024 * 1024) == 0L) {
                    checkpointDao.updateProgress(jobId, checkpoint.bytesUploaded + bytesWritten)
                    setProgress(workDataOf(KEY_PROGRESS to bytesWritten))
                }
            }
            checkpointDao.updateStatus(jobId, CheckpointStatus.COMPLETED)
            Result.success()
        } catch (e: Exception) {
            checkpointDao.markFailed(jobId, e.message ?: "Unknown error")
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}
```

---

## 4. State Persistence for Smart Upload Detection

### Strategy: Compare Camera State vs Uploaded State

```
Camera State (from .CTG)    vs    Uploaded State (from Room)
        │                              │
        ▼                              ▼
Set<ImageRecord>              Set<ImageRecord> (by filename)
        │                              │
        └────────── Set Difference ────┘
                      │
                      ▼
            Images NOT in Room DB
                  (candidates)
```

### Integration DAO

```kotlin
// data/local/dao/CameraStateDao.kt
@Dao
interface CameraStateDao {
    
    @Query("""
        SELECT localPath FROM media_jobs 
        WHERE sessionId = :sessionId AND status = 'COMPLETED'
    """)
    suspend fun getUploadedFilenames(sessionId: String): List<String>
    
    @Query("""
        SELECT * FROM media_jobs 
        WHERE sessionId = :sessionId AND localPath NOT IN (:filenames)
    """)
    suspend fun getPendingUploads(sessionId: String, filenames: List<String>): List<MediaJobEntity>
}
```

---

## 5. Dependency Summary

### Existing Dependencies (No Change)

| Library | Version | Purpose |
|---------|---------|---------|
| JSch (mwiede fork) | 0.2.26 | SFTP client |
| Room | 2.8.4 | Local database |
| WorkManager | 2.11.2 | Background processing |

### Additions Required

| Library | Version | Purpose |
|---------|---------|---------|
| None | - | All achievable with existing deps |

### Code Structure Additions

```
app/src/main/java/com/reflection/transferin/
├── domain/ctg/
│   ├── CtgDatabaseParser.kt         # Parse Canon .CTG files
│   └── CtgModels.kt                 # Domain models
├── session/pool/
│   ├── SftpSessionPool.kt           # Session pooling
│   └── ResilientSftpOperation.kt    # Retry logic
├── data/local/dao/
│   ├── UploadCheckpointDao.kt       # Checkpoint persistence
│   └── CameraStateDao.kt            # Upload state tracking
└── data/worker/
    └── CheckpointingUploadWorker.kt # Resumable upload worker
```

---

## 6. Verification Checklist

Before implementation, verify:

- [ ] .CTG file exists at expected path on Canon cameras (`/MISC/Canon_CCS/`)
- [ ] .CTG opens as SQLite database (use `sqlite3` CLI to inspect schema)
- [ ] Existing `JschSftpClient` interface allows pooling replacement
- [ ] Room schema migration plan exists for new entities
- [ ] WorkManager `ExistingPeriodicWorkPolicy.KEEP` for resumption

---

## Sources

- [JSch MWiede Fork](https://github.com/mwiede/jsch) - Maintained SFTP library
- [Room Database Documentation](https://developer.android.com/training/data-storage/room) - Android persistence
- [WorkManager Documentation](https://developer.android.com/topic/libraries/architecture/workmanager) - Background processing
