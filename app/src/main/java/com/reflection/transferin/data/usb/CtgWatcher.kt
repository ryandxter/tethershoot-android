package com.reflection.transferin.data.usb

import android.util.Log
import com.reflection.transferin.domain.shoot.CtgDatabase
import com.reflection.transferin.domain.shoot.CtgDatabaseParser
import com.reflection.transferin.domain.shoot.CtgMediaSnapshot
import com.reflection.transferin.domain.shoot.CtgStateDiffer
import com.reflection.transferin.domain.shoot.DiffResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Info dari CTG file yang berubah
 */
data class CtgChangeEvent(
    val timestamp: Long,
    val fingerprint: String,
    val handle: Int,
    val fileName: String,
    val fileSize: Long
)

/**
 * State dari CTG watcher
 */
data class CtgWatcherState(
    val isWatching: Boolean = false,
    val lastKnownTimestamp: Long = 0,
    val lastKnownSize: Long = 0,
    val changeCount: Int = 0,
    val lastChangeAt: Long? = null,
    val statusText: String = "CTG Watcher siap"
)

/**
 * Monitor CTG (Canon Thumbnail Group) metadata untuk deteksi foto baru.
 *
 * CTG file adalah file khusus Canon yang di-update setiap kali foto baru diambil.
 * Dengan memantau perubahan timestamp/size CTG, kita bisa tahu kapan ada foto baru
 * tanpa harus lock kamera.
 *
 * Flow:
 * 1. initialize() - scan CTG baseline
 * 2. startWatching() - mulai polling
 * 3. onCtgChanged() - emit event saat CTG berubah
 * 4. stopWatching() - stop polling
 */
class CtgWatcher(
    private val usbHelper: UsbHelper,
    private val ctgParser: CtgDatabaseParser? = null  // Optional for backward compatibility
) {

    companion object {
        private const val TAG = "CtgWatcher"

        // CTG filename pattern untuk Canon
        private val CTG_PATTERNS = listOf(
            "AUTOMRIC.CTG",  // Canon default
            "AUTOMSC1.CTG",
            "CANON_MDC.CTG"
        )
    }

    private val scope = CoroutineScope(Dispatchers.IO + Job())

    private val _state = MutableStateFlow(CtgWatcherState())
    val state: StateFlow<CtgWatcherState> = _state.asStateFlow()

    // Emit saat CTG berubah (indicates new photo)
    private val _ctgChanges = MutableSharedFlow<CtgChangeEvent>(extraBufferCapacity = 64)
    val ctgChanges: SharedFlow<CtgChangeEvent> = _ctgChanges.asSharedFlow()

    private var watchJob: Job? = null
    private var cachedCtgHandle: Int? = null
    private var isInitialized = false

    // Baseline values
    private var baselineTimestamp: Long = 0
    private var baselineSize: Long = 0
    private var baselineHandle: Int = 0
    private var baselineFileName: String = ""

    // Latest values for comparison
    private var lastKnownTimestamp: Long = 0
    private var lastKnownSize: Long = 0

    // Baseline snapshot for session start
    private var baselineSnapshot: CtgMediaSnapshot? = null

    // Last emitted snapshot for change detection
    private var lastEmittedSnapshot: CtgMediaSnapshot? = null

    // Latest parsed snapshot
    private var currentSnapshot: CtgMediaSnapshot? = null

    // Emit parsed snapshots on CTG change
    private val _ctgSnapshots = MutableSharedFlow<CtgMediaSnapshot>(extraBufferCapacity = 16)
    val ctgSnapshots: SharedFlow<CtgMediaSnapshot> = _ctgSnapshots.asSharedFlow()

    // Emit diff results
    private val _ctgDiffs = MutableSharedFlow<DiffResult>(extraBufferCapacity = 16)
    val ctgDiffs: SharedFlow<DiffResult> = _ctgDiffs.asSharedFlow()

    /**
     * Initialize dengan scan baseline CTG file
     */
    suspend fun initialize() {
        if (isInitialized) return

        Log.d(TAG, ">>> CtgWatcher initializing...")

        val storageId = usbHelper.getFirstStorageId()
        if (storageId == 0) {
            Log.w(TAG, "No storage available, CTG watcher cannot initialize")
            _state.value = _state.value.copy(statusText = "CTG: Storage tidak tersedia")
            return
        }

        // Scan untuk CTG file
        val ctgMeta = findCtgFile(storageId)

        if (ctgMeta != null) {
            baselineTimestamp = ctgMeta.timestamp
            baselineSize = ctgMeta.size
            baselineHandle = ctgMeta.handle
            baselineFileName = ctgMeta.name

            lastKnownTimestamp = baselineTimestamp
            lastKnownSize = baselineSize

            isInitialized = true

            Log.d(TAG, ">>> CtgWatcher initialized: CTG=${ctgMeta.name}, ts=${ctgMeta.timestamp}, size=${ctgMeta.size}, handle=${ctgMeta.handle}")
            _state.value = _state.value.copy(
                lastKnownTimestamp = baselineTimestamp,
                lastKnownSize = baselineSize,
                statusText = "CTG: Baseline siap - ${ctgMeta.name}"
            )
        } else {
            // No CTG found - try to find latest file as anchor
            Log.w(TAG, "No CTG file found, using latest file as anchor")
            val latestMeta = findLatestFile(storageId)

            if (latestMeta != null) {
                baselineTimestamp = latestMeta.timestamp
                baselineSize = latestMeta.size

                lastKnownTimestamp = baselineTimestamp
                lastKnownSize = baselineSize

                isInitialized = true

                Log.d(TAG, ">>> CtgWatcher initialized with latest file: ${latestMeta.name}, ts=${latestMeta.timestamp}")
                _state.value = _state.value.copy(
                    lastKnownTimestamp = baselineTimestamp,
                    lastKnownSize = baselineSize,
                    statusText = "CTG: Anchor=${latestMeta.name}"
                )
            } else {
                Log.w(TAG, "No files found on storage")
                _state.value = _state.value.copy(statusText = "CTG: Tidak ada file")
            }
        }
    }

    /**
     * Start watching for CTG changes
     */
    fun startWatching(pollIntervalMs: Long = 2000L) {
        if (watchJob?.isActive == true) {
            Log.d(TAG, "Already watching")
            return
        }

        if (!isInitialized) {
            Log.w(TAG, "CtgWatcher not initialized, call initialize() first")
            return
        }

        Log.d(TAG, ">>> CtgWatcher started watching (interval=${pollIntervalMs}ms)")
        _state.value = _state.value.copy(isWatching = true, statusText = "CTG: Watching...")

        watchJob = scope.launch {
            while (isActive) {
                checkForChanges()
                delay(pollIntervalMs)
            }
        }
    }

    /**
     * Stop watching
     */
    fun stopWatching() {
        watchJob?.cancel()
        watchJob = null
        _state.value = _state.value.copy(isWatching = false, statusText = "CTG: Stopped")
        Log.d(TAG, "CtgWatcher stopped")
    }

    /**
     * Check for CTG changes
     */
    private suspend fun checkForChanges() {
        val storageId = usbHelper.getFirstStorageId()
        if (storageId == 0) return

        val currentMeta = findCtgFile(storageId)

        if (currentMeta != null) {
            // Compare with last known
            val timestampChanged = currentMeta.timestamp > lastKnownTimestamp
            val sizeChanged = currentMeta.size != lastKnownSize

            if (timestampChanged || sizeChanged) {
                Log.i(TAG, ">>> CTG CHANGED! ts=${currentMeta.timestamp} (was ${lastKnownTimestamp}), size=${currentMeta.size} (was ${lastKnownSize})")

                // Emit legacy event for backward compatibility
                val event = CtgChangeEvent(
                    timestamp = currentMeta.timestamp,
                    fingerprint = "${currentMeta.name}|${currentMeta.size}|${currentMeta.timestamp}",
                    handle = currentMeta.handle,
                    fileName = currentMeta.name,
                    fileSize = currentMeta.size
                )

                _ctgChanges.emit(event)

                // NEW: Parse CTG and emit snapshot if parser configured
                if (ctgParser != null) {
                    parseAndEmitSnapshot(storageId)
                }

                lastKnownTimestamp = currentMeta.timestamp
                lastKnownSize = currentMeta.size

                _state.value = _state.value.copy(
                    changeCount = _state.value.changeCount + 1,
                    lastChangeAt = System.currentTimeMillis(),
                    lastKnownTimestamp = lastKnownTimestamp,
                    lastKnownSize = lastKnownSize,
                    statusText = "CTG: Perubahan terdeteksi!"
                )
            }
        } else {
            // Check latest file as fallback
            val latestMeta = findLatestFile(storageId)
            if (latestMeta != null && latestMeta.timestamp > lastKnownTimestamp) {
                Log.i(TAG, ">>> New file detected via timestamp: ${latestMeta.name}")

                val event = CtgChangeEvent(
                    timestamp = latestMeta.timestamp,
                    fingerprint = "${latestMeta.name}|${latestMeta.size}|${latestMeta.timestamp}",
                    handle = latestMeta.handle,
                    fileName = latestMeta.name,
                    fileSize = latestMeta.size
                )

                _ctgChanges.emit(event)

                lastKnownTimestamp = latestMeta.timestamp
                lastKnownSize = latestMeta.size

                _state.value = _state.value.copy(
                    changeCount = _state.value.changeCount + 1,
                    lastChangeAt = System.currentTimeMillis(),
                    lastKnownTimestamp = lastKnownTimestamp,
                    lastKnownSize = lastKnownSize,
                    statusText = "CTG: File baru terdeteksi!"
                )
            }
        }
    }

    /**
     * Reset baseline - call ini saat mulai session baru
     */
    fun resetBaseline() {
        lastKnownTimestamp = baselineTimestamp
        lastKnownSize = baselineSize
        baselineSnapshot = null
        lastEmittedSnapshot = null
        currentSnapshot = null
        cachedCtgHandle = null
        _state.value = _state.value.copy(
            changeCount = 0,
            lastChangeAt = null
        )
        Log.d(TAG, "CtgWatcher baseline reset")
    }

    /**
     * Create baseline snapshot for session start.
     * Call this when starting a new shoot session.
     *
     * @param storageId MTP storage ID to parse CTG from
     * @return The created snapshot, or null if parsing fails or parser not configured
     */
    suspend fun createBaselineSnapshot(storageId: Int): CtgMediaSnapshot? {
        if (ctgParser == null) {
            Log.w(TAG, "CtgParser not configured, cannot create baseline snapshot")
            return null
        }

        val ctgDb = ctgParser.parseCtgFile(storageId) ?: return null
        val snapshot = if (ctgDb.records.isEmpty()) {
            CtgMediaSnapshot.createEmpty(storageId, ctgDb.cameraId)
        } else {
            CtgMediaSnapshot.fromCtgDatabase(ctgDb, storageId, ctgDb.cameraId)
        }

        baselineSnapshot = snapshot
        lastEmittedSnapshot = snapshot
        currentSnapshot = snapshot

        Log.i(TAG, "Baseline snapshot created: ${snapshot.mediaCount} entries, checksum=${snapshot.checksum}")
        return snapshot
    }

    /**
     * Get current snapshot without triggering parse.
     * Returns null if no snapshot exists yet.
     */
    fun getCurrentSnapshot(): CtgMediaSnapshot? = currentSnapshot

    /**
     * Get baseline snapshot for session.
     * Returns null if createBaselineSnapshot() hasn't been called.
     */
    fun getBaselineSnapshot(): CtgMediaSnapshot? = baselineSnapshot

    /**
     * Parse CTG file and emit new snapshot if changed.
     */
    private suspend fun parseAndEmitSnapshot(storageId: Int) {
        val ctgDb = ctgParser?.parseCtgFile(storageId) ?: return

        val newSnapshot = if (ctgDb.records.isEmpty()) {
            CtgMediaSnapshot.createEmpty(storageId, ctgDb.cameraId)
        } else {
            CtgMediaSnapshot.fromCtgDatabase(ctgDb, storageId, ctgDb.cameraId)
        }

        // Compare with last emitted
        lastEmittedSnapshot?.let { last ->
            val differ = CtgStateDiffer()
            val diff = differ.diff(last, newSnapshot)

            if (diff.hasChanges) {
                Log.i(TAG, "CTG content changed: ${diff.summary}")
                _ctgDiffs.emit(diff)
                _ctgSnapshots.emit(newSnapshot)
                lastEmittedSnapshot = newSnapshot
            }
        } ?: run {
            // First snapshot - emit immediately
            _ctgSnapshots.emit(newSnapshot)
            lastEmittedSnapshot = newSnapshot
        }

        currentSnapshot = newSnapshot
    }

    /**
     * Find CTG file on storage
     */
    private suspend fun findCtgFile(storageId: Int): CtgFileMeta? = withContext(Dispatchers.IO) {
        try {
            cachedCtgHandle?.let { handle ->
                val meta = usbHelper.getObjectMetadata(handle)
                if (meta != null) {
                    val name = meta.name.uppercase()
                    if (CTG_PATTERNS.any { name.contains(it) }) {
                        return@withContext CtgFileMeta(
                            handle = handle,
                            name = meta.name,
                            timestamp = meta.modifiedTime ?: 0,
                            size = meta.size
                        )
                    }
                }
                cachedCtgHandle = null
            }

            val handles = usbHelper.getAllObjectHandles(storageId)

            for (handle in handles) {
                val meta = usbHelper.getObjectMetadata(handle) ?: continue
                val name = meta.name.uppercase()

                if (CTG_PATTERNS.any { name.contains(it) }) {
                    cachedCtgHandle = handle
                    return@withContext CtgFileMeta(
                        handle = handle,
                        name = meta.name,
                        timestamp = meta.modifiedTime ?: 0,
                        size = meta.size
                    )
                }
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error finding CTG file", e)
            null
        }
    }

    /**
     * Find latest file on storage (fallback)
     */
    private suspend fun findLatestFile(storageId: Int): CtgFileMeta? = withContext(Dispatchers.IO) {
        try {
            val handles = usbHelper.getAllObjectHandles(storageId)
            if (handles.isEmpty()) return@withContext null

            var latest: CtgFileMeta? = null

            for (handle in handles) {
                val meta = usbHelper.getObjectMetadata(handle) ?: continue
                if (meta.name.lowercase().endsWith(".ctg")) continue  // Skip CTG

                if (latest == null || (meta.modifiedTime ?: 0) > latest.timestamp) {
                    latest = CtgFileMeta(
                        handle = handle,
                        name = meta.name,
                        timestamp = meta.modifiedTime ?: 0,
                        size = meta.size
                    )
                }
            }
            latest
        } catch (e: Exception) {
            Log.e(TAG, "Error finding latest file", e)
            null
        }
    }

    private data class CtgFileMeta(
        val handle: Int,
        val name: String,
        val timestamp: Long,
        val size: Long
    )
}
