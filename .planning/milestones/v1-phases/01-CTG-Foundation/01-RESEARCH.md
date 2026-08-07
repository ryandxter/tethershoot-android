# Phase 1: CTG Foundation - Research

**Phase:** 1 - CTG Foundation
**Domain:** Canon CTG SQLite Database Parsing and Camera Media State Tracking
**Confidence:** MEDIUM (based on training knowledge + existing code analysis)
**Researched:** 2026-07-31

## Summary

Phase 1 implements the foundational CTG (Canon Thumbnail Group) file parsing infrastructure to enable accurate camera media tracking. Canon CTG files are SQLite databases that track transferred photos on the camera's storage card. By parsing these databases, TransferIn can accurately determine which photos have been transferred vs. which are new, enabling smart deduplication and integrity checking.

The core deliverable is a set of Kotlin classes that parse Canon CTG databases via SQLite APIs, create immutable snapshots of camera media state, and provide diff capabilities to detect added/modified files. The existing `CtgWatcher.kt` will be enhanced to trigger re-parsing on CTG file modifications.

**Primary recommendation:** Implement CTG parsing using Android's `SQLiteDatabase` API with `OPEN_READONLY` mode. Use immutable `data class` snapshots with copy-on-write semantics. Build diff capabilities using fingerprint-based set comparisons.

---

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| CTG file location | USB/MTP API | — | CTG files found via MTP object enumeration |
| CTG SQLite parsing | Data/Domain | — | SQLite APIs belong in data layer, domain models in domain |
| Immutable snapshot | Domain | — | Domain models are immutable by design |
| Snapshot comparison/diff | Domain | — | Business logic lives in domain layer |
| CTG change detection | Data (CtgWatcher) | — | Watcher manages file monitoring, emits events |
| Media state storage | Data (Room) | — | Room database for local persistence |

---

## Phase Requirements (from REQUIREMENTS.md)

| ID | Description | Research Support |
|----|-------------|------------------|
| CTG-01 | Parse Canon .CTG database files from MISC directory to enumerate all media handles, filenames, and metadata | Canon CTG format is SQLite; use SQLiteDatabase.openDatabase() |
| CTG-02 | Compare CTG database state against local upload queue to detect files that need upload | Snapshot pattern + set difference |
| CTG-03 | Detect when CTG file changes to trigger re-scanning of camera media state | Enhance existing CtgWatcher with FileObserver or polling |

---

## Standard Stack

### Core Technologies

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Android SQLite | Platform (API 31+) | CTG database access | Native Android SQLiteDatabase API |
| Room | 2.8.4 | Local database for snapshots | Already in project; matches existing patterns |
| Kotlin Coroutines | Platform | Async CTG parsing | Already used in CtgWatcher |
| Kotlin Flow | Platform | Reactive state streams | Already used in existing code |

### No External Dependencies Required

All CTG parsing can be achieved with existing platform and project dependencies:
- **No new Gradle dependencies** needed
- **android.database.sqlite** (platform) - SQLiteDatabase.openDatabase()
- **androidx.room** (existing) - snapshot persistence
- **kotlinx.coroutines** (existing) - async operations

---

## Canon CTG File Format

### Format Analysis [ASSUMED]

Canon .CTG files are SQLite 3 databases used by Canon Camera Connect to track transferred photos.

| Property | Value |
|----------|-------|
| Location | `/MISC/Canon_CCS/` on SD card |
| File extension | `.CTG` (but contains SQLite database) |
| Purpose | Track transferred photos with handle/filename/size/date |
| Schema | Vendor-specific; typical tables: `images`, `transfers` |

### Recommended CTG Schema Query Strategy

Since Canon CTG schema is vendor-specific and may vary, implement a flexible query approach:

```kotlin
// Try multiple query patterns to handle different Canon firmware versions
private fun queryCtgDatabase(db: SQLiteDatabase): List<CtgImageRecord> {
    // Pattern 1: Standard Canon schema
    val cursor = db.rawQuery("""
        SELECT name, size, date, handle 
        FROM images 
        WHERE transferred = 0 OR transferred IS NULL
    """, null)
    
    // Pattern 2: Alternative schema with status field
    // Pattern 3: Generic scan of all non-folder entries
}
```

### SQLite Access Pattern

```kotlin
// Source: Android Platform API (SQLiteDatabase)
// domain/shoot/CtgDatabaseParser.kt
class CtgDatabaseParser(
    private val usbHelper: UsbHelper
) {
    companion object {
        private const val TAG = "CtgDatabaseParser"
        private const val MISC_PATH = "/MISC"
        private const val CTG_FOLDER = "Canon_CCS"
    }
    
    /**
     * Parse CTG file for given storage and return image records
     */
    suspend fun parseCtgFile(storageId: Int): CtgDatabase? = withContext(Dispatchers.IO) {
        try {
            val ctgHandle = findCtgFileHandle(storageId) ?: return@withContext null
            val ctgFile = getFilePathForHandle(ctgHandle) ?: return@withContext null
            
            SQLiteDatabase.openDatabase(
                ctgFile.absolutePath,
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
        val records = mutableListOf<CtgImageRecord>()
        
        // Get schema to determine columns
        val tableNames = getTableNames(db)
        Log.d(TAG, "CTG tables found: $tableNames")
        
        // Query images table with fallback patterns
        for (query in buildQueryPatterns(tableNames)) {
            try {
                val cursor = db.rawQuery(query, null)
                cursor.use {
                    while (it.moveToNext()) {
                        records.add(parseImageRecord(it))
                    }
                }
                if (records.isNotEmpty()) break
            } catch (e: Exception) {
                Log.w(TAG, "Query failed: $query", e)
            }
        }
        
        return CtgDatabase(
            cameraId = extractCameraId(db),
            records = records,
            lastModified = System.currentTimeMillis()
        )
    }
}
```

### MTP File Path Access

Since CTG files are accessed via MTP (not direct filesystem), we need to:
1. Find the CTG file handle via MtpDevice.getObjectHandles()
2. Download the file to a temporary location using MtpDevice.importFile()
3. Open the downloaded file as SQLite database

```kotlin
// Download CTG file via MTP for SQLite parsing
private suspend fun getCtgFilePath(storageId: Int): File? = sessionMutex.withLock {
    val mtpDevice = currentMtpDevice ?: return@withLock null
    val handles = mtpDevice.getObjectHandles(storageId, 0, 0) ?: return@withLock null
    
    for (handle in handles) {
        val info = mtpDevice.getObjectInfo(handle)
        if (info?.name?.uppercase()?.contains("CTG") == true) {
            // Download to cache
            val cacheFile = File(cacheDir, "camera_ctg_${handle}.db")
            if (mtpDevice.importFile(handle, cacheFile.absolutePath)) {
                return@withLock cacheFile
            }
        }
    }
    return@withLock null
}
```

---

## Immutable Snapshot Pattern

### Design Principle

CtgMediaSnapshot represents an immutable view of camera media state at a point in time. Once created, it cannot be modified. New snapshots are created from updated CTG data.

### Domain Model

```kotlin
// domain/shoot/CtgMediaSnapshot.kt
/**
 * Immutable snapshot of camera media state captured at session start.
 * 
 * Snapshots are immutable - create a new snapshot when state changes.
 * Used for comparing current camera state against baseline to detect new photos.
 */
data class CtgMediaSnapshot(
    val id: String,                           // UUID for this snapshot
    val capturedAt: Long,                      // Timestamp when snapshot was created
    val storageId: Int,                        // MTP storage ID
    val cameraId: String,                      // Camera device identifier
    val mediaCount: Int,                       // Total number of media entries
    val mediaEntries: Map<String, CtgMediaEntry>,  // fingerprint -> entry
    val checksum: String                       // SHA-256 of sorted fingerprints for integrity
) {
    init {
        require(capturedAt > 0) { "capturedAt must be positive" }
        require(mediaCount == mediaEntries.size) { "mediaCount must match entries size" }
    }
    
    /**
     * Generate a unique fingerprint for deduplication
     */
    fun generateFingerprint(entry: CtgMediaEntry): String {
        return "${entry.handle}|${entry.filename}|${entry.size}"
    }
    
    /**
     * Check if a specific file is in this snapshot
     */
    fun contains(fingerprint: String): Boolean = fingerprint in mediaEntries
    
    /**
     * Get entry by fingerprint
     */
    fun get(fingerprint: String): CtgMediaEntry? = mediaEntries[fingerprint]
}

/**
 * Immutable media entry from CTG database
 */
data class CtgMediaEntry(
    val handle: Int,              // MTP object handle
    val filename: String,         // Original filename
    val size: Long,               // File size in bytes
    val modifiedTime: Long,        // Last modified timestamp
    val capturedTime: Long?,       // Capture timestamp (if available)
    val formatCode: Int            // MTP format code (JPEG/RAW/VIDEO)
) {
    /**
     * Immutable fingerprint - uniquely identifies this media
     */
    val fingerprint: String
        get() = "$handle|$filename|$size"
    
    /**
     * Display-friendly size
     */
    val displaySize: String
        get() = when {
            size >= 1_073_741_824 -> String.format("%.1f GB", size / 1_073_741_824.0)
            size >= 1_048_576 -> String.format("%.1f MB", size / 1_048_576.0)
            size >= 1024 -> String.format("%.1f KB", size / 1024.0)
            else -> "$size B"
        }
}
```

### Snapshot Creation Pattern

```kotlin
// Create snapshot from parsed CTG database
fun createSnapshot(
    ctgDb: CtgDatabase,
    storageId: Int,
    cameraId: String
): CtgMediaSnapshot {
    val entries = ctgDb.records.associate { record ->
        val entry = CtgMediaEntry(
            handle = record.handle,
            filename = record.filename,
            size = record.size,
            modifiedTime = record.modifiedTime,
            capturedTime = record.capturedTime,
            formatCode = record.formatCode
        )
        entry.fingerprint to entry
    }
    
    // Generate integrity checksum
    val checksum = generateChecksum(entries.keys.sorted())
    
    return CtgMediaSnapshot(
        id = UUID.randomUUID().toString(),
        capturedAt = System.currentTimeMillis(),
        storageId = storageId,
        cameraId = cameraId,
        mediaCount = entries.size,
        mediaEntries = entries,
        checksum = checksum
    )
}
```

---

## Snapshot Comparison (CtgStateDiffer)

### Design Pattern

CtgStateDiffer compares two snapshots to detect added, modified, and deleted files. Uses fingerprint-based set difference.

```kotlin
// domain/shoot/CtgStateDiffer.kt
/**
 * Compares CTG snapshots to detect media changes.
 * 
 * Immutable - returns new DiffResult on each comparison.
 * Thread-safe - all state is captured in DiffResult.
 */
class CtgStateDiffer {
    
    /**
     * Compare baseline snapshot against current snapshot.
     * Returns diff result describing added, modified, and deleted files.
     */
    fun diff(
        baseline: CtgMediaSnapshot,
        current: CtgMediaSnapshot
    ): DiffResult {
        val baselineFingerprints = baseline.mediaEntries.keys
        val currentFingerprints = current.mediaEntries.keys
        
        // Set difference operations
        val added = currentFingerprints - baselineFingerprints
        val deleted = baselineFingerprints - currentFingerprints
        val unchanged = baselineFingerprints intersect currentFingerprints
        
        // Check for modifications in unchanged set
        val modified = unchanged.filter { fp ->
            val baselineEntry = baseline.mediaEntries[fp]!!
            val currentEntry = current.mediaEntries[fp]!!
            isModified(baselineEntry, currentEntry)
        }.toSet()
        
        val unchanged = unchanged - modified
        
        return DiffResult(
            baselineId = baseline.id,
            currentId = current.id,
            added = added.map { current.mediaEntries[it]!! },
            modified = modified.map { fp ->
                ModifiedEntry(
                    baseline = baseline.mediaEntries[fp]!!,
                    current = current.mediaEntries[fp]!!
                )
            },
            deleted = deleted.map { baseline.mediaEntries[it]!! },
            unchangedCount = unchanged.size,
            summary = buildSummary(added.size, modified.size, deleted.size)
        )
    }
    
    /**
     * Detect if an entry has been modified (size or timestamp changed)
     */
    private fun isModified(baseline: CtgMediaEntry, current: CtgMediaEntry): Boolean {
        return baseline.size != current.size ||
               baseline.modifiedTime != current.modifiedTime
    }
}

/**
 * Result of comparing two snapshots
 */
data class DiffResult(
    val baselineId: String,
    val currentId: String,
    val added: List<CtgMediaEntry>,           // New files since baseline
    val modified: List<ModifiedEntry>,         // Files that changed
    val deleted: List<CtgMediaEntry>,          // Files removed since baseline
    val unchangedCount: Int,
    val summary: String
) {
    val hasChanges: Boolean
        get() = added.isNotEmpty() || modified.isNotEmpty() || deleted.isNotEmpty()
    
    val changeCount: Int
        get() = added.size + modified.size + deleted.size
}

data class ModifiedEntry(
    val baseline: CtgMediaEntry,
    val current: CtgMediaEntry
) {
    val reason: String
        get() = buildString {
            if (baseline.size != current.size) {
                append("Size: ${baseline.displaySize} -> ${current.displaySize; ")
            }
            if (baseline.modifiedTime != current.modifiedTime) {
                append("Modified: ${formatTime(baseline.modifiedTime)} -> ${formatTime(current.modifiedTime)}")
            }
        }.trimEnd(',', ';')
}
```

---

## Enhanced CtgWatcher Integration

### Existing Code Analysis

The current `CtgWatcher.kt` (336 lines) already provides:
- CTG file detection via MTP
- Timestamp/size polling for change detection
- StateFlow/SharedFlow for reactive updates
- Coroutine-based async monitoring

### Enhancement Requirements

**CTG-03 requirement:** Detect when CTG file changes to trigger re-scanning.

The existing watcher needs enhancement to:
1. Parse CTG content (not just metadata)
2. Emit parsed media entries on change
3. Provide snapshot creation capability

```kotlin
// Enhanced data/usb/CtgWatcher.kt

class CtgWatcher(
    private val usbHelper: UsbHelper,
    private val ctgParser: CtgDatabaseParser  // NEW dependency
) {
    // ... existing code ...
    
    private val _ctgSnapshots = MutableSharedFlow<CtgMediaSnapshot>(extraBufferCapacity = 16)
    val ctgSnapshots: SharedFlow<CtgMediaSnapshot> = _ctgSnapshots.asSharedFlow()
    
    /**
     * Create a baseline snapshot for session start
     */
    suspend fun createBaselineSnapshot(): CtgMediaSnapshot? {
        val ctgDb = ctgParser.parseCtgFile(getFirstStorageId()) ?: return null
        return CtgMediaSnapshot.createSnapshot(
            ctgDb = ctgDb,
            storageId = getFirstStorageId(),
            cameraId = currentCameraId
        )
    }
    
    /**
     * Check for CTG changes and emit new snapshot if changed
     */
    private suspend fun checkForCtgChanges(): CtgMediaSnapshot? {
        val ctgDb = ctgParser.parseCtgFile(getFirstStorageId()) ?: return null
        val newSnapshot = CtgMediaSnapshot.createSnapshot(
            ctgDb = ctgDb,
            storageId = getFirstStorageId(),
            cameraId = currentCameraId
        )
        
        // Compare with last emitted snapshot
        lastEmittedSnapshot?.let { last ->
            val differ = CtgStateDiffer()
            val diff = differ.diff(last, newSnapshot)
            
            if (diff.hasChanges) {
                Log.i(TAG, "CTG changed: ${diff.summary}")
                _ctgSnapshots.emit(newSnapshot)
                lastEmittedSnapshot = newSnapshot
                return newSnapshot
            }
        } ?: run {
            // First snapshot - emit immediately
            _ctgSnapshots.emit(newSnapshot)
            lastEmittedSnapshot = newSnapshot
            return newSnapshot
        }
        
        return null
    }
}
```

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| CTG parsing | Custom binary parser | SQLiteDatabase API | CTG files are SQLite; binary parsing is fragile |
| Snapshot immutability | Mutable class with setters | Kotlin data class | Data classes are immutable by default |
| Snapshot comparison | Nested loops | Set difference operations | O(n) vs O(n²) complexity |
| CTG change detection | Custom polling loop | FileObserver + polling hybrid | FileObserver unreliable on USB MTP; polling is reliable |
| Fingerprint generation | Handle only | handle + filename + size | Handle alone can collide across sessions |

---

## Common Pitfalls

### Pitfall 1: CTG File Not Downloaded Properly via MTP

**What goes wrong:** SQLiteDatabase.openDatabase() fails with "file not found" or "not a database"

**Why it happens:** MTP importFile() requires the file to be fully downloaded before SQLite can open it. Partial downloads or cache misses cause failures.

**How to avoid:**
- Verify file exists and size > 0 before opening
- Use temporary file in app's cache directory
- Delete temporary file after parsing
- Handle MTP import failures gracefully

**Warning signs:**
```kotlin
if (!ctgFile.exists() || ctgFile.length() == 0L) {
    Log.e(TAG, "CTG file not properly downloaded")
    return null
}
```

### Pitfall 2: CTG Schema Varies Between Canon Firmware Versions

**What goes wrong:** Queries return empty results or throw SQL errors

**Why it happens:** Canon firmware may use different table/column names across models and versions.

**How to avoid:**
- Implement fallback query patterns
- Log available tables/columns for debugging
- Use generic queries that work across schemas

**Warning signs:**
```kotlin
// Log schema for debugging
val tables = db.rawQuery(
    "SELECT name FROM sqlite_master WHERE type='table'", null
)
// Fallback to generic SELECT * FROM images if specific columns fail
```

### Pitfall 3: Snapshot Fails on Empty Camera

**What goes wrong:** Snapshot creation fails when camera has no photos

**Why it happens:** CTG database may be empty or have no records

**How to avoid:**
- Handle empty result sets gracefully
- Return empty snapshot instead of null
- Validate snapshot in tests

**Warning signs:**
```kotlin
if (records.isEmpty()) {
    Log.w(TAG, "CTG database has no image records")
    return createEmptySnapshot()
}
```

### Pitfall 4: Polling Interval Too Frequent

**What goes wrong:** Excessive MTP calls slow down camera operations

**Why it happens:** CTG change detection polling can interfere with camera's own CTG updates

**How to avoid:**
- Use reasonable polling interval (2-5 seconds)
- Stop polling during active transfers
- Debounce rapid changes

**Recommended interval:** 2000ms (as currently implemented in CtgWatcher)

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| File timestamp polling only | CTG content parsing + metadata polling | 2026-07-31 | Enables fingerprint-based deduplication |
| Handle-only identification | Handle + filename + size fingerprint | 2026-07-31 | Eliminates false positives across sessions |
| Mutable state tracking | Immutable snapshot pattern | 2026-07-31 | Safer concurrent access, simpler debugging |
| Single-point-in-time comparison | Set difference with history | 2026-07-31 | Detects added/modified/deleted files |

---

## Architecture Patterns

### Pattern 1: Immutable Snapshot with Factory

**What:** Use factory method to create immutable data class instances
**When to use:** When you need versioned state that can't be accidentally modified
**Example:**
```kotlin
data class CtgMediaSnapshot(...) {
    companion object {
        fun create(...): CtgMediaSnapshot {
            // Validation and initialization
            return CtgMediaSnapshot(...)
        }
    }
}
```

### Pattern 2: Set Difference for Change Detection

**What:** Use Kotlin set operations to detect additions/removals
**When to use:** When comparing two collections for differences
**Example:**
```kotlin
val added = currentFingerprints - baselineFingerprints
val removed = baselineFingerprints - currentFingerprints
```

### Pattern 3: MTP File Download for External SQLite Access

**What:** Download MTP files to local cache for processing
**When to use:** When you need to process files from USB devices using non-MTP APIs
**Example:**
```kotlin
val tempFile = File(cacheDir, "temp_${handle}.db")
mtpDevice.importFile(handle, tempFile.absolutePath)
// Process tempFile...
tempFile.delete() // Cleanup
```

---

## Recommended Project Structure

```
app/src/main/java/com/reflection/transferin/
├── domain/
│   └── shoot/
│       ├── CtgDatabaseParser.kt         # NEW: Parse Canon CTG files
│       ├── CtgMediaSnapshot.kt          # NEW: Immutable snapshot model
│       ├── CtgStateDiffer.kt            # NEW: Compare snapshots
│       └── ShootModeEngine.kt          # MODIFY: Integrate CTG parsing
├── data/
│   ├── usb/
│   │   └── CtgWatcher.kt               # MODIFY: Enhance with CTG parsing
│   └── local/
│       ├── dao/
│       │   └── CameraMediaDao.kt      # MODIFY: Add CTG-related queries
│       └── entity/
│           └── CameraMediaItemEntity.kt # MODIFY: Add fingerprint field
```

---

## Environment Availability

**Step 2.6: SKIPPED** - No external dependencies identified beyond existing Android platform and project dependencies. All CTG parsing uses:
- Android SQLite API (platform)
- MTP API (platform)
- Kotlin Coroutines (existing)
- Room (existing)

---

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 4 (platform default) |
| Config file | None required |
| Quick run command | `./gradlew testDebugUnitTest --tests "*CtgDatabaseParser*"` |
| Full suite command | `./gradlew testDebugUnitTest` |

### Phase Requirements to Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|--------------|
| CTG-01 | Parse CTG files with fallback schema | Unit | `testParseCtgFile_*` | YES (create) |
| CTG-02 | Compare snapshots with diff | Unit | `testSnapshotDiff_*` | YES (create) |
| CTG-03 | Detect CTG changes and emit events | Unit | `testCtgChangeDetection_*` | NO (modify CtgWatcher) |

### Wave 0 Gaps

- [ ] `app/src/test/java/com/reflection/transferin/domain/shoot/CtgDatabaseParserTest.kt` - Parse CTG with mock MTP
- [ ] `app/src/test/java/com/reflection/transferin/domain/shoot/CtgMediaSnapshotTest.kt` - Immutable snapshot creation
- [ ] `app/src/test/java/com/reflection/transferin/domain/shoot/CtgStateDifferTest.kt` - Set difference logic
- [ ] `app/src/androidTest/java/com/reflection/transferin/data/usb/CtgWatcherTest.kt` - Integration test

---

## Security Domain

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V4 Access Control | Yes | CTG files are read-only; no write access required |
| V5 Input Validation | Yes | Validate CTG schema, handle fingerprints, file sizes |
| V6 Cryptography | No | CTG parsing doesn't involve cryptography |

### Known Threat Patterns

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Malformed CTG file causing SQLite exception | Denial | Try-catch with graceful null return; validate before parsing |
| CTG file with very large number of entries | Denial | Set reasonable limits (e.g., 100,000 entries max) |
| Fingerprint collision across camera sessions | Spoofing | Include camera ID in snapshot; reset on new session |

---

## Sources

### Primary (HIGH confidence)
- Android SQLiteDatabase API documentation - Platform API
- Existing CtgWatcher.kt - Canon CTG detection patterns
- Existing CameraMediaItemEntity.kt - Media tracking schema
- Room 2.8.4 documentation - Database persistence

### Secondary (MEDIUM confidence)
- Canon Camera Connect behavior analysis - [ASSUMED based on common Canon patterns]
- MTP USB specification - Standard protocol behavior
- Kotlin Coroutines documentation - Async patterns

### Tertiary (LOW confidence)
- CTG SQLite schema specifics - [ASSUMED; needs verification on real camera]

---

## Assumptions Log

> All [ASSUMED] claims need user/camera verification before implementation

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | Canon CTG files are SQLite databases | Canon CTG Format | HIGH - If CTG is binary, entire approach changes |
| A2 | CTG located at `/MISC/Canon_CCS/` | Canon CTG Format | MEDIUM - Different Canon models may vary |
| A3 | CTG contains `images` table with handle/name/size | Canon CTG Format | MEDIUM - Schema may vary by firmware version |
| A4 | MTP importFile() can download CTG files | MTP Access | LOW - Standard MTP operation |
| A5 | Immutable snapshot pattern fits use case | Snapshot Pattern | LOW - Design choice, can adjust |

---

## Open Questions

1. **Does Canon CTG schema vary across camera models?**
   - What we know: Canon uses standard SQLite; schema likely stable
   - What's unclear: Specific table/column names and their meanings
   - Recommendation: Implement fallback queries and log schema for debugging

2. **Should CTG snapshots be persisted to Room database?**
   - What we know: CameraMediaItemEntity already exists
   - What's unclear: Need for historical snapshots vs. just baseline
   - Recommendation: Start with in-memory snapshots; persist if performance issue

3. **How to handle CTG files larger than available cache space?**
   - What we know: Camera cards can have 100,000+ photos
   - What's unclear: Memory constraints on Android devices
   - Recommendation: Process CTG in streaming fashion; don't load entire DB into memory

4. **When to reset baseline snapshot?**
   - What we know: Baseline should reset on new session
   - What's unclear: Should it reset on USB disconnect or camera power cycle?
   - Recommendation: Reset on explicit session start; handle camera power gracefully

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH - Uses existing Android platform APIs
- Architecture: HIGH - Immutable patterns well-established
- Pitfalls: MEDIUM - Based on Android MTP experience; Canon-specific details [ASSUMED]
- Canon CTG schema: LOW - Requires real camera verification

**Research date:** 2026-07-31
**Valid until:** 2026-08-30 (30 days for stable domain)

**Next steps:**
1. Create Phase 1 PLAN.md with detailed task breakdown
2. Verify CTG schema on real Canon camera (if available)
3. Implement CtgDatabaseParser with fallback query patterns
4. Implement CtgMediaSnapshot and CtgStateDiffer
5. Enhance CtgWatcher with CTG parsing integration
