package com.reflection.transferin.data.usb

import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import com.reflection.transferin.domain.model.SourceSelectionMode
import com.reflection.transferin.domain.session.DiscoveredFile
import com.reflection.transferin.domain.session.SourceWatcher
import java.util.concurrent.atomic.AtomicLong

class UsbSourceOrchestrator(
    private val usbHelper: UsbHelper,
    private val documentFileScanner: DocumentFileScanner,
    private val fileStabilityChecker: FileStabilityChecker,
    private val sourceWatcher: SourceWatcher
) {

    companion object {
        private const val TAG = "UsbSourceOrchestrator"
        val DEFAULT_ALLOWED_EXTENSIONS = setOf(
            "jpg", "jpeg", "png", "heic", "heif",
            "cr2", "cr3", "nef", "arw", "raf", "rw2", "dng",
            "orf", "erf", "kdc", "mrw", "nrw", "ptx", "pef", "srw", "x3f",
            "mp4", "mov", "m4v", "ctg"
        )
    }

    data class CameraPickItem(
        val id: String,
        val uri: String,
        val displayName: String,
        val relativePath: String,
        val sizeBytes: Long,
        val lastModifiedEpochMs: Long,
        val isStable: Boolean
    )

    private val allKnownFiles = mutableSetOf<String>()
    private val allEmittedFiles = mutableSetOf<String>()
    private val knownCtgFingerprints = mutableMapOf<String, String>()

    private var anchorTimestamp: Long = 0L
    private var lastSeenMaxHandle: Int = 0
    private var liveBaselinePrepared: Boolean = false
    private val operationCounter = AtomicLong(0L)

    private var currentMode: SourceSelectionMode = SourceSelectionMode.BROWSE_FOLDER
    private var currentTreeUri: Uri? = null

    fun setMode(mode: SourceSelectionMode, treeUri: Uri? = null) {
        currentMode = mode
        currentTreeUri = treeUri
        Log.d(TAG, "Source mode set: $mode")
    }

    suspend fun prepareBaseline(
        treeUri: Uri,
        allowedExtensions: Set<String> = DEFAULT_ALLOWED_EXTENSIONS
    ): SnapshotSignature {
        val opId = nextOperationId("baseline")
        Log.d(TAG, "[$opId] === RAW MTP ACCELERATED BASELINE START ===")
        val startScanTime = System.currentTimeMillis()

        val storageId = usbHelper.getFirstStorageId()
        if (storageId == 0) {
            Log.e(TAG, "[$opId] No active MTP session. Falling back to slow scan.")
            return fallbackBaseline(treeUri, allowedExtensions)
        }

        val totalCount = usbHelper.getTotalObjectCount(storageId)
        val latestHandles = usbHelper.getLatestHandles(storageId, 50)
        lastSeenMaxHandle = latestHandles.maxOrNull() ?: 0
        
        for (handle in latestHandles.reversed()) {
            val meta = usbHelper.getObjectMetadata(handle) ?: continue
            if (meta.name.lowercase().endsWith(".ctg")) {
                anchorTimestamp = meta.modifiedTime ?: 0L
                knownCtgFingerprints["root"] = "${meta.name}|${meta.size}|${anchorTimestamp}"
                break
            }
        }
        
        if (anchorTimestamp == 0L && latestHandles.isNotEmpty()) {
            val lastMeta = usbHelper.getObjectMetadata(latestHandles.last())
            anchorTimestamp = lastMeta?.modifiedTime ?: 0L
        }

        latestHandles.forEach { handle ->
            usbHelper.getObjectMetadata(handle)?.let { meta ->
                allKnownFiles.add(buildStableFingerprint(meta.name, meta.size, meta.modifiedTime ?: 0L))
            }
        }

        liveBaselinePrepared = true
        val duration = System.currentTimeMillis() - startScanTime
        Log.d(TAG, "[$opId] Turbo Baseline Ready in ${duration}ms. Total: $totalCount. MaxHandle: $lastSeenMaxHandle")
        return SnapshotSignature(totalCount, "anchor", anchorTimestamp, 1)
    }

    private fun fallbackBaseline(treeUri: Uri, allowedExtensions: Set<String>): SnapshotSignature {
        val results = documentFileScanner.scanTree(treeUri, recursive = true, allowedExtensions = allowedExtensions)
        results.forEach { allKnownFiles.add(buildStableFingerprint(it.name, it.sizeBytes, it.lastModified)) }
        liveBaselinePrepared = true
        return SnapshotSignature(results.size, "fallback", 0L, 1)
    }

    suspend fun emitNewFiles(
        treeUri: Uri,
        allowedExtensions: Set<String> = DEFAULT_ALLOWED_EXTENSIONS
    ): PollResult {
        val opId = nextOperationId("emit")
        if (!liveBaselinePrepared) return PollResult(0, 0, 0, 0)

        val storageId = usbHelper.getFirstStorageId()
        if (storageId == 0) return PollResult(0, 0, 0, 0)

        val latestHandles = usbHelper.getLatestHandles(storageId, 1000)
        val currentMax = latestHandles.maxOrNull() ?: 0
        
        if (currentMax <= lastSeenMaxHandle) {
            return PollResult(foundCount = latestHandles.size, emittedCount = 0, ignoredLateCount = 0, unstableCount = 0)
        }

        val newHandles = latestHandles.filter { it > lastSeenMaxHandle }
        var emitted = 0
        var unstable = 0

        for (handle in newHandles) {
            val meta = usbHelper.getObjectMetadata(handle) ?: continue
            val lowerName = meta.name.lowercase()
            
            if (lowerName.endsWith(".ctg")) {
                knownCtgFingerprints["root"] = "${meta.name}|${meta.size}|${meta.modifiedTime ?: 0L}"
                continue
            }

            val ext = meta.name.substringAfterLast('.', "").lowercase()
            if (ext !in allowedExtensions || ext == "ctg") continue

            val fingerprint = buildStableFingerprint(meta.name, meta.size, meta.modifiedTime ?: 0L)
            if (fingerprint in allKnownFiles || fingerprint in allEmittedFiles) continue

            val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, handle.toString())
            if (fileStabilityChecker.isFileStable(docUri)) {
                try {
                    Log.i(TAG, "[$opId] >>> TURBO EMIT: ${meta.name}")
                    sourceWatcher.emitNewFile(DiscoveredFile(docUri.toString(), meta.name, meta.size, meta.modifiedTime ?: 0L))
                    allEmittedFiles.add(fingerprint)
                    emitted++
                } catch (e: Exception) { Log.e(TAG, "Emit failed", e) }
            } else { unstable++ }
        }

        lastSeenMaxHandle = currentMax
        return PollResult(foundCount = latestHandles.size, emittedCount = emitted, ignoredLateCount = 0, unstableCount = unstable)
    }

    suspend fun emitAllFiles(treeUri: Uri, allowedExtensions: Set<String> = DEFAULT_ALLOWED_EXTENSIONS) {
        val storageId = usbHelper.getFirstStorageId()
        if (storageId == 0) return
        
        val handles = usbHelper.getLatestHandles(storageId, 10000)
        for (handle in handles) {
            val meta = usbHelper.getObjectMetadata(handle) ?: continue
            if (meta.name.lowercase().endsWith(".ctg")) continue
            val ext = meta.name.substringAfterLast('.', "").lowercase()
            if (ext !in allowedExtensions) continue
            
            val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, handle.toString())
            sourceWatcher.emitNewFile(DiscoveredFile(docUri.toString(), meta.name, meta.size, meta.modifiedTime ?: 0L))
        }
    }

    suspend fun listCameraFiles(
        treeUri: Uri,
        allowedExtensions: Set<String> = DEFAULT_ALLOWED_EXTENSIONS
    ): List<CameraPickItem> {
        val scanned = documentFileScanner.scanTree(treeUri, allowedExtensions = allowedExtensions)
        return scanned.map { doc ->
            CameraPickItem(
                id = doc.uri.toString(),
                uri = doc.uri.toString(),
                displayName = doc.name,
                relativePath = doc.relativePath,
                sizeBytes = doc.sizeBytes,
                lastModifiedEpochMs = doc.lastModified,
                isStable = true
            )
        }
    }

    private fun buildStableFingerprint(name: String, size: Long, mod: Long) = "${name.lowercase()}|$size|$mod"
    private fun nextOperationId(prefix: String) = "$prefix-${operationCounter.incrementAndGet()}"
    
    fun beginLiveSession() { liveBaselinePrepared = false }
    fun resetSessionCache() { allKnownFiles.clear(); allEmittedFiles.clear(); lastSeenMaxHandle = 0 }
    
    fun getBaselineFingerprints(): Set<String> = allKnownFiles
    fun getSingleFileInfo(uri: Uri) = documentFileScanner.getSingleFileInfo(uri)
    fun markAsEmitted(name: String, size: Long, mod: Long) { allEmittedFiles.add(buildStableFingerprint(name, size, mod)) }

    data class SnapshotSignature(val fileCount: Int, val lastFileName: String, val lastTimestamp: Long, val folderCount: Int)
    data class PollResult(
        val foundCount: Int,
        val emittedCount: Int,
        val ignoredLateCount: Int,
        val unstableCount: Int,
        val newFoldersDetected: Int = 0,
        val hasActivity: Boolean = emittedCount > 0 || unstableCount > 0 || newFoldersDetected > 0,
        val totalCount: Int = 0
    )
}
