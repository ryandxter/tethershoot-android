# Phase 2: Smart Upload Detection - Research

**Phase:** 2 - Smart Upload Detection
**Domain:** Fingerprint-based upload deduplication and CTG baseline comparison
**Confidence:** HIGH (based on existing code patterns + established techniques)
**Researched:** 2026-07-31

---

## 1. Executive Summary

Phase 2 implements fingerprint-based upload deduplication to prevent re-uploading files that were already successfully transferred. This builds on Phase 1's CTG foundation by:

1. **Storing upload fingerprints** in Room database for persistent cross-session tracking
2. **Filtering upload queue** against fingerprints before processing
3. **Comparing CTG state** against baseline snapshot to detect only new shots

**Key Decisions:**
- Use composite fingerprint (handle + filename + size) stored as single indexed string
- Room entity with unique composite primary key for fast lookups
- Repository pattern for DAO access following existing TransferIn patterns
- Lazy initialization for fingerprint index to avoid loading entire database on startup

---

## 2. Fingerprint Strategy

### 2.1 Composite Fingerprint Design

**Recommended Approach:** Composite fingerprint as single string key

```kotlin
// domain/shoot/Fingerprint.kt
/**
 * Fingerprint format: "{handle}|{normalizedFilename}|{size}"
 *
 * Components:
 * - handle: MTP object handle (Int) - unique within camera session
 * - normalizedFilename: Lowercase, trimmed filename (String)
 * - size: File size in bytes (Long)
 *
 * Separator: "|" (pipe) - not allowed in filenames, unambiguous
 */
data class MediaFingerprint(
    val handle: Int,
    val filename: String,
    val sizeBytes: Long
) {
    /**
     * Generate stable fingerprint string for database storage.
     * Normalized to ensure consistency across queries.
     */
    fun toStorageKey(): String {
        val normalized = filename.trim().lowercase()
        return "$handle|$normalized|$sizeBytes"
    }

    companion object {
        /**
         * Parse fingerprint from storage key.
         * Returns null if key format is invalid.
         */
        fun fromStorageKey(key: String): MediaFingerprint? {
            val parts = key.split("|")
            if (parts.size != 3) return null

            return try {
                MediaFingerprint(
                    handle = parts[0].toInt(),
                    filename = parts[1],
                    sizeBytes = parts[2].toLong()
                )
            } catch (e: NumberFormatException) {
                null
            }
        }
    }
}
```

### 2.2 Why Composite Over Hash?

| Approach | Pros | Cons |
|----------|------|------|
| **Composite (handle\|name\|size)** | Human-readable, debuggable, no collision risk | Longer storage key |
| **Hash-based (SHA-256)** | Fixed length, cryptographically unique | Not human-readable, hash collision theoretical risk, extra CPU |

**Decision: Composite fingerprint**

Rationale:
1. **No collision risk**: Handle+filename+size is guaranteed unique within a camera session
2. **Debuggable**: Can see exactly which file was uploaded
3. **Low storage overhead**: Typical filename < 100 chars, handle+size ~20 chars → ~120 chars
4. **Indexable**: Room indexes work well on string columns

### 2.3 Handle Name Encoding Considerations

**Unicode filenames:** Normalize to lowercase, keep original characters
```kotlin
val normalized = filename.trim().lowercase()  // Handles Unicode correctly in Kotlin
```

**Edge cases:**
- 0-byte files: `sizeBytes = 0` → fingerprint becomes `"123|filename.jpg|0"` (valid)
- Very long filenames: Room VARCHAR limit is 1000+; truncate with hash if needed
- Empty filenames: Reject or use placeholder `"_empty_"`

### 2.4 Size Edge Cases

- **0-byte files:** Valid, but verify camera produces them legitimately
- **Negative sizes:** Should never occur; validate `size >= 0`
- **Overflow:** Long.MAX_VALUE (9.2 EB) far exceeds realistic file sizes

---

## 3. Database Design

### 3.1 Room Entity Schema

```kotlin
// data/local/entity/UploadedFingerprint.kt
package com.reflection.transferin.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Persisted record of successfully uploaded media files.
 *
 * Fingerprint uniquely identifies a file via (handle, filename, size).
 * Used for cross-session deduplication to prevent re-uploading files.
 *
 * Index design:
 * - Primary key: fingerprint (composite string) - O(1) lookup
 * - Index on sessionId + uploadedAt for cleanup queries
 */
@Entity(
    tableName = "uploaded_fingerprints",
    indices = [
        // Primary key index on fingerprint (automatic)
        Index(value = ["sessionId"]),
        Index(value = ["uploadedAt"]),
        Index(value = ["sessionId", "uploadedAt"])
    ]
)
data class UploadedFingerprintEntity(
    @PrimaryKey
    @ColumnInfo(name = "fingerprint")
    val fingerprint: String,

    @ColumnInfo(name = "session_id")
    val sessionId: String,

    @ColumnInfo(name = "source_filename")
    val sourceFilename: String,

    @ColumnInfo(name = "file_size_bytes")
    val fileSizeBytes: Long,

    @ColumnInfo(name = "nas_target_path")
    val nasTargetPath: String,

    @ColumnInfo(name = "nas_filename")
    val nasFilename: String,

    @ColumnInfo(name = "uploaded_at")
    val uploadedAt: Long,

    @ColumnInfo(name = "ctg_handle")
    val ctgHandle: Int,

    @ColumnInfo(name = "camera_device_id")
    val cameraDeviceId: String?
)
```

### 3.2 DAO Interface

```kotlin
// data/local/dao/UploadedFingerprintDao.kt
package com.reflection.transferin.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.reflection.transferin.data.local.entity.UploadedFingerprintEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for uploaded fingerprint persistence and queries.
 *
 * Key operations:
 * 1. checkUploaded(fingerprint) - O(1) existence check
 * 2. recordUpload(fingerprint, metadata) - Insert on success
 * 3. getUploadedFingerprints(sessionId) - Load for deduplication
 * 4. deleteBySession(sessionId) - Cleanup old fingerprints
 */
@Dao
interface UploadedFingerprintDao {

    /**
     * Check if a fingerprint has been uploaded (any session).
     * O(1) lookup via primary key.
     */
    @Query("""
        SELECT EXISTS(
            SELECT 1 FROM uploaded_fingerprints
            WHERE fingerprint = :fingerprint
        )
    """)
    suspend fun hasUploaded(fingerprint: String): Boolean

    /**
     * Batch check for multiple fingerprints.
     * Returns set of existing fingerprints for efficient filtering.
     */
    @Query("""
        SELECT fingerprint FROM uploaded_fingerprints
        WHERE fingerprint IN (:fingerprints)
    """)
    suspend fun filterExisting(fingerprints: List<String>): Set<String>

    /**
     * Record successful upload.
     * Uses REPLACE to handle retries (same fingerprint, different attempt).
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: UploadedFingerprintEntity)

    /**
     * Batch insert for efficiency.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<UploadedFingerprintEntity>)

    /**
     * Get all fingerprints for a session (for in-memory deduplication).
     */
    @Query("""
        SELECT fingerprint FROM uploaded_fingerprints
        WHERE sessionId = :sessionId
    """)
    suspend fun getBySession(sessionId: String): List<String>

    /**
     * Get all fingerprints (for global deduplication).
     * Warning: May be slow for large datasets. Use pagination if needed.
     */
    @Query("SELECT fingerprint FROM uploaded_fingerprints")
    suspend fun getAllFingerprints(): List<String>

    /**
     * Get entity by fingerprint.
     */
    @Query("SELECT * FROM uploaded_fingerprints WHERE fingerprint = :fingerprint")
    suspend fun getByFingerprint(fingerprint: String): UploadedFingerprintEntity?

    /**
     * Count fingerprints per session (for stats).
     */
    @Query("SELECT COUNT(*) FROM uploaded_fingerprints WHERE sessionId = :sessionId")
    suspend fun countBySession(sessionId: String): Int

    /**
     * Delete all fingerprints for a session (cleanup).
     */
    @Query("DELETE FROM uploaded_fingerprints WHERE sessionId = :sessionId")
    suspend fun deleteBySession(sessionId: String)

    /**
     * Delete fingerprints older than timestamp (cleanup old sessions).
     */
    @Query("DELETE FROM uploaded_fingerprints WHERE uploadedAt < :beforeTimestamp")
    suspend fun deleteOlderThan(beforeTimestamp: Long)

    /**
     * Observe count changes (for UI updates).
     */
    @Query("SELECT COUNT(*) FROM uploaded_fingerprints")
    fun observeTotalCount(): Flow<Int>

    /**
     * Observe count per session.
     */
    @Query("SELECT COUNT(*) FROM uploaded_fingerprints WHERE sessionId = :sessionId")
    fun observeCountBySession(sessionId: String): Flow<Int>
}
```

### 3.3 Database Version and Migration

**Current version:** 8 (from TransferInDatabase.kt)
**New version:** 9

```kotlin
// data/local/db/Migration_8_9.kt
package com.reflection.transferin.data.local.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration from version 8 to 9:
 * - Add uploaded_fingerprints table for cross-session deduplication
 */
val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS uploaded_fingerprints (
                fingerprint TEXT PRIMARY KEY,
                session_id TEXT NOT NULL,
                source_filename TEXT NOT NULL,
                file_size_bytes INTEGER NOT NULL,
                nas_target_path TEXT NOT NULL,
                nas_filename TEXT NOT NULL,
                uploaded_at INTEGER NOT NULL,
                ctg_handle INTEGER NOT NULL,
                camera_device_id TEXT,
                FOREIGN KEY (session_id) REFERENCES sessions(id) ON DELETE CASCADE
            )
        """)

        // Index for session cleanup
        database.execSQL("""
            CREATE INDEX IF NOT EXISTS idx_fingerprints_session
            ON uploaded_fingerprints(session_id)
        """)

        // Index for time-based cleanup
        database.execSQL("""
            CREATE INDEX IF NOT EXISTS idx_fingerprints_uploaded_at
            ON uploaded_fingerprints(uploaded_at)
        """)

        // Composite index for session + timestamp queries
        database.execSQL("""
            CREATE INDEX IF NOT EXISTS idx_fingerprints_session_time
            ON uploaded_fingerprints(session_id, uploaded_at)
        """)
    }
}
```

### 3.4 Database Provider Update

```kotlin
// data/local/db/DatabaseProvider.kt (update)
@Provides
@Singleton
fun provideDatabase(context: Context): TransferInDatabase {
    return Room.databaseBuilder(
        context.applicationContext,
        TransferInDatabase::class.java,
        TransferInDatabase.DATABASE_NAME
    )
        .addMigrations(MIGRATION_8_9)
        .fallbackToDestructiveMigration()  // Keep for dev; restrict in prod
        .build()
}
```

### 3.5 Index Optimization

**Primary key index:** Automatic on `fingerprint`
- O(1) lookup for `hasUploaded()`
- Unique constraint prevents duplicates

**Secondary indexes:**
- `session_id` → Session cleanup queries
- `uploaded_at` → Time-based cleanup
- `(session_id, uploaded_at)` → Combined queries

**Note:** Fingerprint strings are ~100 chars. Index size is acceptable for expected dataset (< 1M rows).

---

## 4. Architecture

### 4.1 Component Responsibilities

| Component | Layer | Responsibility |
|-----------|-------|----------------|
| `UploadedFingerprintEntity` | Data | Room entity for persistence |
| `UploadedFingerprintDao` | Data | Database operations (CRUD) |
| `UploadedFingerprintRepository` | Domain | Business logic, coordinates DAO |
| `FingerprintService` | Domain | High-level fingerprint operations |
| `NewShotDetector` | Domain | CTG baseline comparison |
| `SmartUploadFilter` | Domain | Upload queue filtering |

### 4.2 Repository Pattern

```kotlin
// domain/repository/UploadedFingerprintRepository.kt
package com.reflection.transferin.domain.repository

import com.reflection.transferin.data.local.dao.UploadedFingerprintDao
import com.reflection.transferin.data.local.entity.UploadedFingerprintEntity
import kotlinx.coroutines.flow.Flow

/**
 * Repository for uploaded fingerprint tracking.
 *
 * Responsibilities:
 * 1. Record successful uploads
 * 2. Check if fingerprint exists (deduplication)
 * 3. Batch check fingerprints
 * 4. Cleanup old fingerprints
 */
class UploadedFingerprintRepository(
    private val dao: UploadedFingerprintDao
) {
    /**
     * Check if a fingerprint has been uploaded.
     */
    suspend fun hasUploaded(fingerprint: String): Boolean {
        return dao.hasUploaded(fingerprint)
    }

    /**
     * Filter list of fingerprints to only include new ones.
     * Returns set of fingerprints that have NOT been uploaded.
     */
    suspend fun filterNewFingerprints(fingerprints: List<String>): Set<String> {
        if (fingerprints.isEmpty()) return emptySet()
        val existing = dao.filterExisting(fingerprints)
        return fingerprints.toSet() - existing
    }

    /**
     * Record a successful upload.
     */
    suspend fun recordUpload(entity: UploadedFingerprintEntity) {
        dao.insert(entity)
    }

    /**
     * Get all fingerprints for a session (for in-memory deduplication).
     */
    suspend fun getSessionFingerprints(sessionId: String): Set<String> {
        return dao.getBySession(sessionId).toSet()
    }

    /**
     * Get ALL fingerprints (for global deduplication).
     * Warning: May be slow. Consider pagination for large datasets.
     */
    suspend fun getAllFingerprints(): Set<String> {
        return dao.getAllFingerprints().toSet()
    }

    /**
     * Cleanup fingerprints for old sessions.
     */
    suspend fun cleanupOldFingerprints(keepDays: Int = 30) {
        val cutoff = System.currentTimeMillis() - (keepDays * 24 * 60 * 60 * 1000L)
        dao.deleteOlderThan(cutoff)
    }

    /**
     * Observe total fingerprint count.
     */
    fun observeTotalCount(): Flow<Int> = dao.observeTotalCount()

    /**
     * Observe session fingerprint count.
     */
    fun observeSessionCount(sessionId: String): Flow<Int> = dao.observeCountBySession(sessionId)
}
```

### 4.3 Smart Upload Filter

```kotlin
// domain/shoot/SmartUploadFilter.kt
package com.reflection.transferin.domain.shoot

/**
 * Filters upload queue items against uploaded fingerprints.
 *
 * Usage:
 * 1. Initialize with fingerprints from repository
 * 2. Call filter() to deduplicate a list of jobs
 * 3. Call record() after successful upload
 */
class SmartUploadFilter(
    private val fingerprintRepository: UploadedFingerprintRepository
) {
    // In-memory cache of uploaded fingerprints
    private var uploadedFingerprints: Set<String> = emptySet()
    private var isInitialized = false

    /**
     * Initialize filter with fingerprints from repository.
     * Call at session start or when resuming.
     */
    suspend fun initialize(sessionId: String? = null) {
        uploadedFingerprints = if (sessionId != null) {
            fingerprintRepository.getSessionFingerprints(sessionId)
        } else {
            fingerprintRepository.getAllFingerprints()
        }
        isInitialized = true
    }

    /**
     * Initialize with provided fingerprints (e.g., from CTG baseline).
     */
    fun initializeWith(fingerprints: Set<String>) {
        uploadedFingerprints = fingerprints
        isInitialized = true
    }

    /**
     * Filter items, returning only those not yet uploaded.
     * Items that were already uploaded are returned separately.
     */
    fun filter(
        items: List<FingerprintSource>
    ): FilterResult {
        require(isInitialized) { "Filter not initialized" }

        val newItems = mutableListOf<FingerprintSource>()
        val skippedItems = mutableListOf<FingerprintSource>()

        for (item in items) {
            if (item.fingerprint in uploadedFingerprints) {
                skippedItems.add(item)
            } else {
                newItems.add(item)
            }
        }

        return FilterResult(
            newItems = newItems,
            skippedItems = skippedItems,
            skippedCount = skippedItems.size
        )
    }

    /**
     * Filter single item.
     */
    fun isNew(fingerprint: String): Boolean = fingerprint !in uploadedFingerprints

    /**
     * Record successful upload (update in-memory cache).
     * Should also persist via repository.
     */
    suspend fun recordUpload(entity: UploadedFingerprintEntity) {
        uploadedFingerprints = uploadedFingerprints + entity.fingerprint
        fingerprintRepository.recordUpload(entity)
    }

    /**
     * Add fingerprint to cache without persisting.
     * Use for batch processing efficiency.
     */
    fun addToCache(fingerprint: String) {
        uploadedFingerprints = uploadedFingerprints + fingerprint
    }

    /**
     * Clear cache and reinitialize.
     */
    suspend fun refresh(sessionId: String? = null) {
        initialize(sessionId)
    }

    data class FilterResult(
        val newItems: List<FingerprintSource>,
        val skippedItems: List<FingerprintSource>,
        val skippedCount: Int
    )

    interface FingerprintSource {
        val fingerprint: String
    }
}
```

### 4.4 New Shot Detector (CTG Comparison)

```kotlin
// domain/shoot/NewShotDetector.kt
package com.reflection.transferin.domain.shoot

import com.reflection.transferin.domain.model.CameraMedia

/**
 * Detects new shots by comparing current CTG state against baseline snapshot.
 *
 * Integrates with Phase 1's CtgMediaSnapshot and CtgStateDiffer.
 * Used at session start to establish baseline, then for each CTG change event.
 *
 * Note: This assumes Phase 1 deliverables (CtgMediaSnapshot, CtgStateDiffer)
 * are implemented. If not yet available, use fallback approach.
 */
class NewShotDetector {

    /**
     * Compare current media list against baseline.
     * Returns list of new media items not in baseline.
     */
    fun detectNewShots(
        baseline: Set<String>,  // Fingerprints from baseline snapshot
        current: List<CameraMedia>
    ): List<CameraMedia> {
        return current.filter { media ->
            val fingerprint = buildFingerprint(media)
            fingerprint !in baseline
        }
    }

    /**
     * Build fingerprint from CameraMedia.
     */
    fun buildFingerprint(media: CameraMedia): String {
        val normalized = media.name.trim().lowercase()
        return "${media.handle}|$normalized|${media.size}"
    }

    /**
     * Extract fingerprints from CameraMedia list.
     */
    fun extractFingerprints(mediaList: List<CameraMedia>): Set<String> {
        return mediaList.mapTo(mutableSetOf()) { buildFingerprint(it) }
    }

    /**
     * Detect new shots from CTG change event.
     */
    fun detectFromChangeEvent(
        baseline: Set<String>,
        changeEvent: CtgChangeEvent
    ): Boolean {
        val fingerprint = "${changeEvent.handle}|${changeEvent.fileName.lowercase()}|${changeEvent.fileSize}"
        return fingerprint !in baseline
    }

    /**
     * Build fingerprint from UploadQueueItemEntity sourceFingerprint field.
     * Assumes sourceFingerprint already follows our format.
     */
    fun parseFingerprint(fingerprint: String): MediaFingerprint? {
        return MediaFingerprint.fromStorageKey(fingerprint)
    }

    /**
     * Validate fingerprint format.
     */
    fun isValidFingerprint(fingerprint: String): Boolean {
        return MediaFingerprint.fromStorageKey(fingerprint) != null
    }

    data class CtgChangeEvent(
        val handle: Int,
        val fileName: String,
        val fileSize: Long,
        val timestamp: Long
    )
}
```

---

## 5. Upload Queue Integration

### 5.1 Integration Points

The upload pipeline consists of:
1. **Discovery** → Media found on camera
2. **Queue** → Added to TransferQueueItemEntity
3. **Filter** → SmartUploadFilter checks duplicates
4. **Upload** → Worker processes queue item
5. **Record** → Fingerprint persisted on success

### 5.2 Where to Inject Deduplication

**Option A: Pre-queue (at discovery)**
```
Discovery → Check fingerprint → Already uploaded? → Skip / Queue
```
- Pros: Saves queue space, no processing wasted
- Cons: Requires repository access at discovery time

**Option B: At queue processing (worker)**
```
Queue → Get next item → Check fingerprint → Already uploaded? → Mark skip / Process
```
- Pros: Centralized, easier to test
- Cons: Worker still gets item, just skips it

**Option C: Hybrid (recommended)**
```
Discovery → Queue (always)
Worker → Check fingerprint → Already uploaded? → Mark SKIPPED → Continue
```
- Pros: Tracks skipped items, enables retry logic
- Cons: Slightly more queue entries

### 5.3 Fingerprint Recording on Success

```kotlin
// In upload worker (pseudo-code)
suspend fun processUpload(item: TransferQueueItemEntity) {
    val result = sftpClient.uploadFile(...)

    if (result.isSuccess) {
        // Record fingerprint
        val entity = UploadedFingerprintEntity(
            fingerprint = item.sourceFingerprint,
            sessionId = item.sessionId,
            sourceFilename = item.sourceFileName,
            fileSizeBytes = item.fileSizeBytes,
            nasTargetPath = item.targetNasDirPath,
            nasFilename = item.targetFileName,
            uploadedAt = System.currentTimeMillis(),
            ctgHandle = extractHandle(item.sourceFingerprint),
            cameraDeviceId = null  // TODO: Extract from job context
        )

        fingerprintRepository.recordUpload(entity)
        transferQueueRepository.markSuccess(item.id, ...)

        // Update in-memory filter cache
        smartUploadFilter.addToCache(item.sourceFingerprint)
    } else {
        // Handle failure
        transferQueueRepository.markFailed(item.id, ...)
    }
}
```

### 5.4 Error Handling

**Fingerprint recording failure:**
```kotlin
suspend fun processUpload(item: TransferQueueItemEntity) {
    val result = sftpClient.uploadFile(...)

    if (result.isSuccess) {
        try {
            fingerprintRepository.recordUpload(entity)
        } catch (e: Exception) {
            // Log but don't fail the upload
            // Fingerprint can be reconstructed from transfer_queue_items
            Log.w(TAG, "Failed to record fingerprint", e)
        }

        transferQueueRepository.markSuccess(item.id, ...)
    }
}
```

**Recovery strategy:** TransferQueueItemEntity already has `sourceFingerprint`. If fingerprint recording fails, can rebuild from queue on next session start.

---

## 6. Implementation Risks

### 6.1 Risk Analysis

| Risk | Probability | Impact | Mitigation |
|------|-------------|--------|------------|
| **Fingerprint collision** | Very Low | High | Composite key (handle+name+size) has no theoretical collision |
| **Database migration failure** | Low | High | Add migration with IF NOT EXISTS; fallback to destructive |
| **Large fingerprint table** | Medium | Low | Pagination, cleanup old sessions, lazy loading |
| **Inconsistent fingerprint format** | Medium | High | Validate at insert, reject malformed entries |
| **Memory pressure from cache** | Low | Medium | Use Set<String>, limit to recent session fingerprints |

### 6.2 Database Performance

**Concern:** Loading all fingerprints on startup
**Solution:** Lazy initialization + pagination
```kotlin
// Don't load at startup
val smartUploadFilter = SmartUploadFilter(repository)

// Load when needed (first queue access)
suspend fun ensureInitialized() {
    if (!smartUploadFilter.isInitialized) {
        smartUploadFilter.initialize(sessionId)
    }
}
```

### 6.3 Migration Safety

```kotlin
// Use IF NOT EXISTS for all DDL
database.execSQL("""
    CREATE TABLE IF NOT EXISTS uploaded_fingerprints (...)
""")

// Use IF NOT EXISTS for indexes
database.execSQL("""
    CREATE INDEX IF NOT EXISTS idx_fingerprints_session (...)
""")
```

---

## 7. Testing Strategy

### 7.1 Unit Tests

**UploadedFingerprintEntityTest**
```kotlin
@Test
fun `fingerprint generation is stable`() {
    val fp = MediaFingerprint(handle = 123, filename = "IMG_0001.JPG", sizeBytes = 1234567)
    assertEquals("123|img_0001.jpg|1234567", fp.toStorageKey())
}

@Test
fun `parse valid fingerprint`() {
    val parsed = MediaFingerprint.fromStorageKey("123|img_0001.jpg|1234567")
    assertNotNull(parsed)
    assertEquals(123, parsed?.handle)
    assertEquals("img_0001.jpg", parsed?.filename)
    assertEquals(1234567L, parsed?.sizeBytes)
}

@Test
fun `reject invalid fingerprint format`() {
    assertNull(MediaFingerprint.fromStorageKey("invalid"))
    assertNull(MediaFingerprint.fromStorageKey("123|456"))
    assertNull(MediaFingerprint.fromStorageKey(""))
}
```

**UploadedFingerprintDaoTest (with Room test helper)**
```kotlin
@Test
fun `hasUploaded returns true for existing fingerprint`() {
    dao.insert(testEntity(fingerprint = "123|test.jpg|1000"))
    runBlocking {
        assertTrue(dao.hasUploaded("123|test.jpg|1000"))
    }
}

@Test
fun `filterExisting returns intersection`() {
    dao.insert(testEntity(fingerprint = "1|a|100"))
    dao.insert(testEntity(fingerprint = "2|b|200"))

    runBlocking {
        val result = dao.filterExisting(listOf("1|a|100", "2|b|200", "3|c|300"))
        assertEquals(setOf("1|a|100", "2|b|200"), result)
    }
}
```

**SmartUploadFilterTest**
```kotlin
@Test
fun `filter separates new and existing`() {
    filter.initializeWith(setOf("1|a|100"))

    val items = listOf(
        fingerprintSource("1|a|100"),  // Exists
        fingerprintSource("2|b|200"),  // New
        fingerprintSource("3|c|300")   // New
    )

    val result = filter.filter(items)

    assertEquals(2, result.newItems.size)
    assertEquals(1, result.skippedItems.size)
    assertEquals(1, result.skippedCount)
}
```

**NewShotDetectorTest**
```kotlin
@Test
fun `detectNewShots returns only new items`() {
    val baseline = setOf("1|a|100", "2|b|200")
    val current = listOf(
        cameraMedia(handle = 1, name = "a", size = 100),
        cameraMedia(handle = 2, name = "b", size = 200),
        cameraMedia(handle = 3, name = "c", size = 300)  // New
    )

    val new = detector.detectNewShots(baseline, current)

    assertEquals(1, new.size)
    assertEquals(3, new[0].handle)
}
```

### 7.2 Integration Tests

**DatabaseMigrationTest**
```kotlin
@Test
fun `migration 8 to 9 creates table`() {
    val db = MigrationTestHelper.create(version = 8)
    // Run migration
    // Verify table exists
    // Verify indexes exist
}
```

### 7.3 Test Commands

```bash
# Unit tests
./gradlew testDebugUnitTest --tests "*UploadedFingerprint*"
./gradlew testDebugUnitTest --tests "*SmartUploadFilter*"
./gradlew testDebugUnitTest --tests "*NewShotDetector*"

# Integration tests (requires instrumented runner)
./gradlew connectedAndroidTest --tests "*Migration*"
```

---

## 8. Project Structure

```
app/src/main/java/com/reflection/transferin/
├── data/
│   └── local/
│       ├── entity/
│       │   └── UploadedFingerprintEntity.kt  # NEW
│       ├── dao/
│       │   └── UploadedFingerprintDao.kt     # NEW
│       └── db/
│           ├── TransferInDatabase.kt         # MODIFY: Add entity + DAO
│           ├── Migration_8_9.kt              # NEW: Add migration
│           └── DatabaseProvider.kt           # MODIFY: Add migration
├── domain/
│   ├── repository/
│   │   └── UploadedFingerprintRepository.kt # NEW
│   └── shoot/
│       ├── Fingerprint.kt                    # NEW: Fingerprint model
│       ├── NewShotDetector.kt                # NEW: CTG comparison
│       └── SmartUploadFilter.kt              # NEW: Upload filtering
└── data/
    └── worker/
        └── TransferUploadWorker.kt           # MODIFY: Record fingerprints
```

---

## 9. Dependencies

### 9.1 Phase Dependencies

| Phase | Deliverable | Required For |
|-------|-------------|--------------|
| Phase 1 | `CtgMediaSnapshot` | NewShotDetector (baseline comparison) |
| Phase 1 | `CtgStateDiffer` | Optional - can implement inline |

**Note:** If Phase 1 deliverables are not yet implemented, NewShotDetector can work with basic set comparison using the `buildFingerprint()` method.

### 9.2 Required Modifications to Existing Files

| File | Change |
|------|--------|
| `TransferInDatabase.kt` | Add `UploadedFingerprintEntity`, add DAO getter |
| `DatabaseProvider.kt` | Add migration |
| `TransferUploadWorker.kt` | Call `fingerprintRepository.recordUpload()` on success |

---

## 10. Assumptions Log

| # | Assumption | Risk if Wrong | Mitigation |
|---|------------|---------------|------------|
| A1 | Fingerprint format is stable | MEDIUM | Validate format at insert, reject malformed |
| A2 | MTP handles are stable within session | LOW | Handle is part of fingerprint, unique per camera |
| A3 | Filename encoding is UTF-8 | LOW | Kotlin strings are UTF-16, normalized to lowercase handles Unicode |
| A4 | File size is accurate from MTP | LOW | MTP provides size; verify against upload progress |
| A5 | Session cleanup is acceptable | LOW | Configurable retention period |

---

## 11. Open Questions

1. **Should fingerprints be scoped per-session or global?**
   - Current design: Global (persists across sessions)
   - Alternative: Per-session (deleted on session end)
   - Recommendation: Global for true deduplication, with cleanup

2. **How to handle camera disconnect during upload?**
   - Scenario: Upload succeeds but fingerprint recording fails
   - Recovery: Rebuild from TransferQueueItemEntity on reconnect

3. **Should skipped items be tracked separately?**
   - Option A: Insert with SKIPPED status (extra rows)
   - Option B: Log only, don't insert (potential audit gap)
   - Recommendation: Option B with periodic summary

4. **Cleanup strategy for old fingerprints?**
   - Option A: Delete after N days
   - Option B: Delete on session end
   - Option C: Keep forever (limited growth)
   - Recommendation: Option A with 30-day default, configurable

---

## 12. References

- [Room 2.8.4 Documentation](https://developer.android.com/jetpack/androidx/releases/room)
- [Android SQLiteDatabase API](https://developer.android.com/reference/android/database/sqlite/SQLiteDatabase)
- [Existing TransferIn patterns](file:///Users/chiio/VSCode/TransferIn/app/src/main/java/com/reflection/transferin/data/local/entity/CameraMediaItemEntity.kt)
- [Phase 1 Research](file:///Users/chiio/VSCode/TransferIn/.planning/phases/01-CTG-Foundation/01-RESEARCH.md)

---

## Metadata

**Confidence breakdown:**
- Database design: HIGH - Based on established Room patterns
- Fingerprint strategy: HIGH - Composite key has no collision risk
- Integration points: HIGH - Clear separation of concerns
- Testing strategy: HIGH - Standard unit/integration test patterns

**Research date:** 2026-07-31
**Valid until:** 2026-08-30 (30 days for stable domain)

**Next steps:**
1. Create Phase 2 PLAN.md with implementation tasks
2. Add `UploadedFingerprintEntity` and `UploadedFingerprintDao`
3. Add database migration from v8 to v9
4. Implement `UploadedFingerprintRepository`
5. Implement `SmartUploadFilter`
6. Implement `NewShotDetector`
7. Integrate fingerprint recording into upload worker
8. Add unit tests
9. Add integration tests for migration

---

## RESEARCH COMPLETE
