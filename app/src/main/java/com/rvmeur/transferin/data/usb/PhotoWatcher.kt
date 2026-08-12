package com.rvmeur.transferin.data.usb

import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
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

data class NewPhotoEvent(
    val handle: Int,
    val fileName: String,
    val fileSize: Long,
    val modifiedTime: Long,
    val contentUri: Uri
)

data class PhotoWatcherState(
    val isWatching: Boolean = false,
    val lastKnownTimestamp: Long = 0,
    val lastKnownHandle: Int = 0,
    val detectedCount: Int = 0,
    val lastDetectedAt: Long? = null,
    val statusText: String = "Photo Watcher siap"
)

class PhotoWatcher(
    private val usbHelper: UsbHelper,
    private val activeStorageIdsProvider: () -> List<Int> = { emptyList() }
) {
    companion object {
        private const val TAG = "PhotoWatcher"
        private const val INIT_RETRY_COUNT = 3
        private const val INIT_RETRY_DELAY_MS = 300L

        private val CTG_PATTERNS = listOf(
            "AUTOMRIC.CTG",
            "AUTOMSC1.CTG",
            "CANON_MDC.CTG"
        )

        private val PHOTO_EXTENSIONS = setOf(
            "jpg", "jpeg", "cr2", "cr3", "nef", "arw", "raf", "rw2", "dng", "orf", "mp4", "mov"
        )
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val numberRegex = Regex("(\\d+)\\.[a-zA-Z0-9]+$")

    private fun extractFileNumber(name: String): Int {
        val match = numberRegex.find(name)
        return match?.groupValues?.get(1)?.toIntOrNull() ?: 0
    }

    private val _state = MutableStateFlow(PhotoWatcherState())
    val state: StateFlow<PhotoWatcherState> = _state.asStateFlow()

    private val _newPhotos = MutableSharedFlow<NewPhotoEvent>(extraBufferCapacity = 64)
    val newPhotos: SharedFlow<NewPhotoEvent> = _newPhotos.asSharedFlow()

    private var watchJob: Job? = null
    private var isInitialized = false

    private var baselineTimestamp: Long = 0
    private var baselineHandle: Int = 0
    private var baselineFileName: String = ""
    private var baselineStorageId: Int = 0
    private var baselineFileNumber: Int = 0

    private var treeUri: Uri? = null

    fun setTreeUri(uri: Uri) {
        treeUri = uri
        Log.d(TAG, "Tree URI set: $uri")
    }

    suspend fun initialize(uploadedFilenames: Set<String> = emptySet()): Boolean {
        if (isInitialized) {
            Log.d(TAG, "PhotoWatcher already initialized")
            return true
        }

        Log.d(TAG, ">>> PhotoWatcher initializing with ${uploadedFilenames.size} uploaded filenames...")

        repeat(INIT_RETRY_COUNT) { attempt ->
            val storageIds = resolveStorageIds()
            if (storageIds.isNotEmpty()) {
                Log.d(TAG, "Resolved storageIds=$storageIds on attempt=${attempt + 1}")
                return initializeWithStorages(storageIds, uploadedFilenames)
            }

            Log.w(
                TAG,
                "Storage not ready for PhotoWatcher on attempt=${attempt + 1}/$INIT_RETRY_COUNT"
            )

            if (attempt < INIT_RETRY_COUNT - 1) {
                delay(INIT_RETRY_DELAY_MS)
            }
        }

        Log.w(TAG, "No storage available, PhotoWatcher cannot initialize")
        _state.value = _state.value.copy(statusText = "Storage tidak tersedia")
        isInitialized = false
        return false
    }

    fun resetBaseline() {
        seenHandles.clear()
        baselineTimestamp = 0
        baselineHandle = 0
        baselineFileName = ""
        baselineStorageId = 0
        baselineFileNumber = 0
        isInitialized = false
        _state.value = _state.value.copy(
            detectedCount = 0,
            lastDetectedAt = null,
            lastKnownTimestamp = 0,
            lastKnownHandle = 0,
            statusText = "Baseline direset"
        )
        Log.d(TAG, "PhotoWatcher baseline reset")
    }

    fun startWatching(pollIntervalMs: Long = 2000L) {
        if (watchJob?.isActive == true) {
            Log.d(TAG, "PhotoWatcher already watching")
            return
        }

        if (!isInitialized) {
            Log.w(TAG, "PhotoWatcher not initialized")
            _state.value = _state.value.copy(
                isWatching = false,
                statusText = "PhotoWatcher belum diinisialisasi"
            )
            return
        }

        if (treeUri == null) {
            Log.w(TAG, "PhotoWatcher starting without treeUri; contentUri will be Uri.EMPTY")
        }

        Log.d(TAG, ">>> PhotoWatcher started watching (interval=${pollIntervalMs}ms)")
        _state.value = _state.value.copy(
            isWatching = true,
            statusText = if (treeUri != null) {
                "Watching..."
            } else {
                "Watching... (auto-detect tethered)"
            }
        )

        watchJob = scope.launch {
            while (isActive) {
                runCatching {
                    checkForNewPhoto()
                }.onFailure { throwable ->
                    Log.e(TAG, "Watcher loop error", throwable)
                    _state.value = _state.value.copy(
                        statusText = "Watcher error: ${throwable.message}"
                    )
                }
                delay(pollIntervalMs)
            }
        }
    }

    fun stopWatching() {
        watchJob?.cancel()
        watchJob = null
        _state.value = _state.value.copy(
            isWatching = false,
            statusText = "Stopped"
        )
        Log.d(TAG, "PhotoWatcher stopped")
    }

    private fun resolveStorageIds(): List<Int> {
        val activeIds = runCatching { activeStorageIdsProvider() }
            .onFailure { Log.w(TAG, "activeStorageIdsProvider failed", it) }
            .getOrDefault(emptyList())
            .filter { it != 0 }
            .distinct()

        if (activeIds.isNotEmpty()) {
            Log.d(TAG, "resolveStorageIds(): using activeStorageIdsProvider -> $activeIds")
            return activeIds
        }

        val fallback = usbHelper.getFirstStorageId()
        val result = if (fallback != 0) listOf(fallback) else emptyList()
        Log.d(TAG, "resolveStorageIds(): fallback usbHelper.getFirstStorageId() -> $result")
        return result
    }

    private val seenHandles = HashSet<Int>()

    private suspend fun initializeWithStorages(storageIds: List<Int>, uploadedFilenames: Set<String>): Boolean = withContext(Dispatchers.IO) {
        seenHandles.clear()
        var maxCardNumber = 0
        var latestPhoto: PhotoMeta? = null

        // Parse highest uploaded file number from database set
        val highestDbNumber = uploadedFilenames.map { extractFileNumber(it) }.maxOrNull() ?: 0

        for (storageId in storageIds) {
            val handles = usbHelper.getAllObjectHandles(storageId)
            for (handle in handles) {
                val meta = usbHelper.getObjectMetadata(handle) ?: continue
                val name = meta.name.orEmpty()
                if (name.isBlank()) continue

                val upperName = name.uppercase()
                val ext = name.substringAfterLast('.', "").lowercase()

                if (CTG_PATTERNS.any { upperName.contains(it) }) continue
                if (ext !in PHOTO_EXTENSIONS) continue

                val num = extractFileNumber(name)
                if (num > maxCardNumber) {
                    maxCardNumber = num
                }

                // If uploadedFilenames is empty (new session), we add all existing files to seenHandles
                // If uploadedFilenames is not empty (reconnect), we only add files that have already been uploaded successfully
                if (uploadedFilenames.isEmpty() || uploadedFilenames.contains(name)) {
                    seenHandles.add(handle)
                }

                val modifiedTime = meta.modifiedTime ?: 0L
                val candidate = PhotoMeta(
                    storageId = storageId,
                    handle = handle,
                    fileName = name,
                    size = meta.size,
                    modifiedTime = modifiedTime
                )

                latestPhoto = when {
                    latestPhoto == null -> candidate
                    candidate.modifiedTime > latestPhoto.modifiedTime -> candidate
                    candidate.modifiedTime == latestPhoto.modifiedTime && candidate.handle > latestPhoto.handle -> candidate
                    else -> latestPhoto
                }
            }
        }

        val mostLatestImageNumber = latestPhoto?.let { extractFileNumber(it.fileName) } ?: 0
        baselineFileNumber = maxOf(maxCardNumber, mostLatestImageNumber, highestDbNumber)

        Log.d(
            TAG,
            ">>> PhotoWatcher initialized: maxCardNumber=$maxCardNumber, mostLatestImageNumber=$mostLatestImageNumber, highestDbNumber=$highestDbNumber -> baselineFileNumber=$baselineFileNumber, totalSeen=${seenHandles.size}"
        )

        if (latestPhoto != null) {
            baselineTimestamp = latestPhoto.modifiedTime
            baselineHandle = latestPhoto.handle
            baselineFileName = latestPhoto.fileName
            baselineStorageId = latestPhoto.storageId
            isInitialized = true

            _state.value = _state.value.copy(
                lastKnownTimestamp = baselineTimestamp,
                lastKnownHandle = baselineHandle,
                statusText = "Baseline: ${latestPhoto.fileName} (num=$baselineFileNumber)"
            )
        } else {
            baselineTimestamp = 0
            baselineHandle = 0
            baselineFileName = ""
            baselineStorageId = 0
            isInitialized = true

            _state.value = _state.value.copy(
                lastKnownTimestamp = 0,
                lastKnownHandle = 0,
                statusText = "Baseline kosong (num=$baselineFileNumber)"
            )
        }
        true
    }

    private suspend fun checkForNewPhoto() = withContext(Dispatchers.IO) {
        val storageIds = resolveStorageIds()
        if (storageIds.isEmpty()) {
            Log.w(TAG, "Storage unavailable while watching")
            return@withContext
        }

        val newHandlesToProcess = mutableListOf<Pair<Int, Int>>()
        for (storageId in storageIds) {
            val handles = usbHelper.getAllObjectHandles(storageId)
            for (handle in handles) {
                if (handle != 0 && !seenHandles.contains(handle)) {
                    newHandlesToProcess.add(Pair(handle, storageId))
                }
            }
        }

        if (newHandlesToProcess.isEmpty()) return@withContext

        Log.d(TAG, "checkForNewPhoto: found ${newHandlesToProcess.size} new handles not in seen list")

        for ((handle, storageId) in newHandlesToProcess) {
            seenHandles.add(handle)

            val meta = usbHelper.getObjectMetadata(handle) ?: continue
            val name = meta.name.orEmpty()
            if (name.isBlank()) continue

            val upperName = name.uppercase()
            val ext = name.substringAfterLast('.', "").lowercase()

            if (CTG_PATTERNS.any { upperName.contains(it) }) continue
            if (ext !in PHOTO_EXTENSIONS) continue

            // Ensure photo number is valid against baseline (only filter if card wasn't formatted)
            val num = extractFileNumber(name)
            if (num < baselineFileNumber && seenHandles.size > 1) {
                Log.d(TAG, "checkForNewPhoto: file $name has number $num < baseline $baselineFileNumber, skipping")
                continue
            }

            Log.i(
                TAG,
                ">>> NEW PHOTO DETECTED: $name, storageId=$storageId, ts=${meta.modifiedTime ?: 0L}, handle=$handle"
            )

            val contentUri = buildContentUri(handle)
            val event = NewPhotoEvent(
                handle = handle,
                fileName = name,
                fileSize = meta.size,
                modifiedTime = meta.modifiedTime ?: 0L,
                contentUri = contentUri
            )

            _newPhotos.emit(event)

            baselineTimestamp = meta.modifiedTime ?: 0L
            baselineHandle = handle
            baselineFileName = name
            baselineStorageId = storageId
            if (num > baselineFileNumber) {
                baselineFileNumber = num
            }

            _state.value = _state.value.copy(
                detectedCount = _state.value.detectedCount + 1,
                lastDetectedAt = System.currentTimeMillis(),
                lastKnownTimestamp = baselineTimestamp,
                lastKnownHandle = baselineHandle,
                statusText = if (contentUri != Uri.EMPTY) {
                    "Foto baru: $name"
                } else {
                    "Foto baru: $name (tanpa SAF URI)"
                }
            )

            Log.d(TAG, ">>> Photo emitted: $name, storageId=$storageId, uri=$contentUri")
        }
    }

    private fun buildContentUri(handle: Int): Uri {
        val base = treeUri
        if (base == null) {
            Log.d(TAG, "buildContentUri: treeUri is null, returning Uri.EMPTY for handle=$handle")
            return Uri.EMPTY
        }

        return runCatching {
            DocumentsContract.buildDocumentUriUsingTree(base, handle.toString())
        }.getOrElse {
            Log.w(TAG, "Failed to build content URI for handle=$handle", it)
            Uri.EMPTY
        }
    }

    private suspend fun findLatestPhotoAcrossStorages(storageIds: List<Int>): PhotoMeta? =
        withContext(Dispatchers.IO) {
            var latest: PhotoMeta? = null

            for (storageId in storageIds) {
                val candidate = findLatestPhoto(storageId)
                if (candidate != null) {
                    latest = when {
                        latest == null -> candidate
                        candidate.modifiedTime > latest!!.modifiedTime -> candidate
                        candidate.modifiedTime == latest!!.modifiedTime && candidate.handle > latest!!.handle -> candidate
                        else -> latest
                    }
                }
            }

            latest
        }

    private suspend fun findLatestPhoto(storageId: Int): PhotoMeta? = withContext(Dispatchers.IO) {
        try {
            val handles = usbHelper.getAllObjectHandles(storageId)
            if (handles.isEmpty()) {
                Log.d(TAG, "No object handles found for storageId=$storageId")
                return@withContext null
            }

            Log.d(TAG, "Found ${handles.size} object handles for storageId=$storageId")

            var latest: PhotoMeta? = null

            for (handle in handles) {
                val meta = usbHelper.getObjectMetadata(handle) ?: continue
                val name = meta.name.orEmpty()
                if (name.isBlank()) continue

                val upperName = name.uppercase()
                val ext = name.substringAfterLast('.', "").lowercase()

                if (CTG_PATTERNS.any { upperName.contains(it) }) continue
                if (ext !in PHOTO_EXTENSIONS) continue

                val modifiedTime = meta.modifiedTime ?: 0L
                val candidate = PhotoMeta(
                    storageId = storageId,
                    handle = handle,
                    fileName = name,
                    size = meta.size,
                    modifiedTime = modifiedTime
                )

                latest = when {
                    latest == null -> candidate
                    candidate.modifiedTime > latest.modifiedTime -> candidate
                    candidate.modifiedTime == latest.modifiedTime && candidate.handle > latest.handle -> candidate
                    else -> latest
                }
            }

            if (latest == null) {
                Log.d(TAG, "No photo-like objects matched filters on storageId=$storageId")
            } else {
                Log.d(TAG, "Latest on storageId=$storageId -> ${latest.fileName} (#${latest.handle})")
            }

            latest
        } catch (e: Exception) {
            Log.e(TAG, "Error finding latest photo for storageId=$storageId", e)
            null
        }
    }

    private data class PhotoMeta(
        val storageId: Int,
        val handle: Int,
        val fileName: String,
        val size: Long,
        val modifiedTime: Long
    )
}