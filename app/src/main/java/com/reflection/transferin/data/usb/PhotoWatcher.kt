package com.reflection.transferin.data.usb

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

    private var treeUri: Uri? = null

    fun setTreeUri(uri: Uri) {
        treeUri = uri
        Log.d(TAG, "Tree URI set: $uri")
    }

    suspend fun initialize(): Boolean {
        if (isInitialized) {
            Log.d(TAG, "PhotoWatcher already initialized")
            return true
        }

        Log.d(TAG, ">>> PhotoWatcher initializing...")

        repeat(INIT_RETRY_COUNT) { attempt ->
            val storageIds = resolveStorageIds()
            if (storageIds.isNotEmpty()) {
                Log.d(TAG, "Resolved storageIds=$storageIds on attempt=${attempt + 1}")
                return initializeWithStorages(storageIds)
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
        baselineTimestamp = 0
        baselineHandle = 0
        baselineFileName = ""
        baselineStorageId = 0
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

    private suspend fun initializeWithStorages(storageIds: List<Int>): Boolean {
        val latestPhoto = findLatestPhotoAcrossStorages(storageIds)
        return if (latestPhoto != null) {
            baselineTimestamp = latestPhoto.modifiedTime
            baselineHandle = latestPhoto.handle
            baselineFileName = latestPhoto.fileName
            baselineStorageId = latestPhoto.storageId
            isInitialized = true

            Log.d(
                TAG,
                ">>> PhotoWatcher initialized: storageId=${latestPhoto.storageId}, latest=${latestPhoto.fileName}, ts=${latestPhoto.modifiedTime}, handle=${latestPhoto.handle}"
            )

            _state.value = _state.value.copy(
                lastKnownTimestamp = baselineTimestamp,
                lastKnownHandle = baselineHandle,
                statusText = "Baseline: ${latestPhoto.fileName}"
            )
            true
        } else {
            Log.w(TAG, "No photos found on storageIds=$storageIds, watcher initialized with empty baseline")
            baselineTimestamp = 0
            baselineHandle = 0
            baselineFileName = ""
            baselineStorageId = 0
            isInitialized = true
            _state.value = _state.value.copy(
                lastKnownTimestamp = 0,
                lastKnownHandle = 0,
                statusText = "Baseline kosong"
            )
            true
        }
    }

    private suspend fun checkForNewPhoto() = withContext(Dispatchers.IO) {
        val storageIds = resolveStorageIds()
        if (storageIds.isEmpty()) {
            Log.w(TAG, "Storage unavailable while watching")
            return@withContext
        }

        val latestPhoto = findLatestPhotoAcrossStorages(storageIds) ?: return@withContext

        val isNewerTimestamp = latestPhoto.modifiedTime > baselineTimestamp
        val isSameTimestampButDifferentHandle =
            latestPhoto.modifiedTime == baselineTimestamp && latestPhoto.handle != baselineHandle
        val isSameTimestampAndHandleButDifferentName =
            latestPhoto.modifiedTime == baselineTimestamp &&
                    latestPhoto.handle == baselineHandle &&
                    latestPhoto.fileName != baselineFileName

        val isStorageChangedWithValidPhoto =
            latestPhoto.storageId != baselineStorageId && latestPhoto.modifiedTime >= baselineTimestamp

        val isNewPhoto =
            isNewerTimestamp ||
                    isSameTimestampButDifferentHandle ||
                    isSameTimestampAndHandleButDifferentName ||
                    isStorageChangedWithValidPhoto

        if (!isNewPhoto) return@withContext

        Log.i(
            TAG,
            ">>> NEW PHOTO DETECTED: ${latestPhoto.fileName}, storageId=${latestPhoto.storageId}, ts=${latestPhoto.modifiedTime}, handle=${latestPhoto.handle} " +
                    "(baselineStorageId=$baselineStorageId, baselineTs=$baselineTimestamp, baselineHandle=$baselineHandle, baselineName=$baselineFileName)"
        )

        val contentUri = buildContentUri(latestPhoto.handle)
        val event = NewPhotoEvent(
            handle = latestPhoto.handle,
            fileName = latestPhoto.fileName,
            fileSize = latestPhoto.size,
            modifiedTime = latestPhoto.modifiedTime,
            contentUri = contentUri
        )

        _newPhotos.emit(event)

        baselineTimestamp = latestPhoto.modifiedTime
        baselineHandle = latestPhoto.handle
        baselineFileName = latestPhoto.fileName
        baselineStorageId = latestPhoto.storageId

        _state.value = _state.value.copy(
            detectedCount = _state.value.detectedCount + 1,
            lastDetectedAt = System.currentTimeMillis(),
            lastKnownTimestamp = baselineTimestamp,
            lastKnownHandle = baselineHandle,
            statusText = if (contentUri != Uri.EMPTY) {
                "Foto baru: ${latestPhoto.fileName}"
            } else {
                "Foto baru: ${latestPhoto.fileName} (tanpa SAF URI)"
            }
        )

        Log.d(TAG, ">>> Photo emitted: ${latestPhoto.fileName}, storageId=${latestPhoto.storageId}, uri=$contentUri")
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