package com.reflection.transferin.data.usb

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import com.reflection.transferin.data.local.dao.CameraMediaDao
import com.reflection.transferin.data.local.entity.CameraMediaItemEntity
import com.reflection.transferin.data.local.entity.MediaStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Camera Discovery Engine - PiuFoto Style
 *
 * Architecture:
 * 1. MTP Discovery -> Local DB item
 * 2. Thumbnail Cache
 * 3. Upload Queue
 * 4. Reactive UI
 *
 * Key innovation: CTG-based folder change detection
 * When CTG file timestamp changes, folder is "dirty" and needs rescan
 */
class CameraDiscoveryEngine(
    private val context: Context,
    private val cameraMediaDao: CameraMediaDao,
    private val documentFileScanner: DocumentFileScanner,
    private val contentResolver: android.content.ContentResolver
) {
    private var cachedHasDbFiles: Boolean? = null

    companion object {
        private const val TAG = "CameraDiscoveryEngine"

        // MTP format codes
        const val FORMAT_JPEG = 0x3001
        const val FORMAT_TIFF_EP = 0x3002
        const val FORMAT_TIFF = 0x3801
        const val FORMAT_DNG = 0xB101
        const val FORMAT_UNDEFINED = 0x3800

        // Photo extensions
        val PHOTO_EXTENSIONS = setOf("jpg", "jpeg", "png", "heic", "heif")
        val RAW_EXTENSIONS = setOf("cr2", "cr3", "nef", "arw", "raf", "rw2", "dng", "orf", "erf")
        val VIDEO_EXTENSIONS = setOf("mp4", "mov", "m4v", "avi")

        // CTG/DAT/DB database file patterns (multi-vendor camera DB files)
        val DB_PATTERNS = listOf(
            "CANON__.CTG", "CANON_0.CTG", "CANON_1.CTG", "CANON_2.CTG",
            "SONY.DAT", "CAMERADB.DAT", "MEDIABAR.DAT", "NIKON.DAT",
            "FUJI.DAT", "FFDB.DAT", "FFDB.DB", "GOPRO.DB", "DJI.DB",
            "NC_FLLST.DAT"
        )
        // System / Database folders where metadata is usually placed (case-insensitive check)
        val DB_FOLDERS = setOf("AVF_INFO", "FFDB", "CANONMSC", "MISC", "M4ROOT", "PRIVATE", "SYSTEM")

        // Poll interval for CTG detection (ms)
        const val CTG_POLL_INTERVAL_MS = 2000L
    }

    data class DiscoveryResult(
        val newItems: Int = 0,
        val totalItems: Int = 0,
        val dirtyFolders: List<String> = emptyList(),
        val scanTimeMs: Long = 0
    )

    data class CtgState(
        val folderId: String,
        val folderName: String,
        val ctgFileName: String?,
        val modifiedAt: Long,
        val fileCount: Int
    )

    /**
     * Perform baseline scan - discover all existing files
     * Called once when shoot mode starts
     */
    suspend fun performBaselineScan(
        deviceId: String,
        treeUri: Uri,
        storageId: Int
    ): DiscoveryResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        Log.d(TAG, "=== BASELINE SCAN START === deviceId=$deviceId")

        // Scan all folders and files
        val scannedDocs = documentFileScanner.scanTree(treeUri, recursive = true)
        val photoDocs = scannedDocs.filter { doc ->
            val ext = doc.extension.lowercase()
            ext in PHOTO_EXTENSIONS || ext in RAW_EXTENSIONS || ext in VIDEO_EXTENSIONS
        }

        Log.d(TAG, "Baseline scan found ${photoDocs.size} media files")

        // Insert all as discovered (baseline items)
        var insertedCount = 0
        val baselineItems = photoDocs.map { doc ->
            CameraMediaItemEntity(
                deviceId = deviceId,
                storageId = storageId,
                objectHandle = doc.documentId.hashCode(), // Use hash as pseudo-handle
                parentHandle = null,
                fileName = doc.name,
                fileExtension = doc.name.substringAfterLast('.', "").lowercase(),
                format = guessFormat(doc.name),
                sizeBytes = doc.sizeBytes,
                modifiedAt = doc.lastModified,
                captureGroup = generateCaptureGroup(doc.lastModified),
                thumbPath = null,
                localCopyPath = null,
                status = MediaStatus.DISCOVERED
            )
        }

        for (item in baselineItems) {
            val exists = cameraMediaDao.existsByHandle(item.objectHandle, deviceId)
            if (!exists) {
                cameraMediaDao.insert(item)
                insertedCount++
            }
        }

        val duration = System.currentTimeMillis() - startTime
        Log.d(TAG, "=== BASELINE SCAN DONE === found=$insertedCount new, total=${baselineItems.size}, time=${duration}ms")

        DiscoveryResult(
            newItems = insertedCount,
            totalItems = baselineItems.size,
            scanTimeMs = duration
        )
    }

    /**
     * Poll for new files using CTG detection
     * Returns list of new items to upload
     */
    suspend fun pollForNewFiles(
        deviceId: String,
        treeUri: Uri,
        storageId: Int
    ): DiscoveryResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val newItems = mutableListOf<CameraMediaItemEntity>()

        val hasDb = hasDatabaseFiles(treeUri)
        if (!hasDb) {
            // Run Size & Date Diff fallback sync
            Log.d(TAG, "No database files detected. Running fallback Size & Date Diff sync.")
            val scannedDocs = documentFileScanner.scanTree(treeUri, recursive = true)
            val photoDocs = scannedDocs.filter { doc ->
                val ext = doc.extension.lowercase()
                ext in PHOTO_EXTENSIONS || ext in RAW_EXTENSIONS || ext in VIDEO_EXTENSIONS
            }
            val existingHandles = cameraMediaDao.getAllHandlesByStorage(deviceId, storageId).toSet()

            for (doc in photoDocs) {
                val handle = doc.documentId.hashCode()
                if (handle !in existingHandles) {
                    val item = CameraMediaItemEntity(
                        deviceId = deviceId,
                        storageId = storageId,
                        objectHandle = handle,
                        parentHandle = null,
                        fileName = doc.name,
                        fileExtension = doc.name.substringAfterLast('.', "").lowercase(),
                        format = guessFormat(doc.name),
                        sizeBytes = doc.sizeBytes,
                        modifiedAt = doc.lastModified,
                        captureGroup = generateCaptureGroup(doc.lastModified),
                        thumbPath = null,
                        localCopyPath = null,
                        status = MediaStatus.DISCOVERED
                    )
                    newItems.add(item)
                }
            }

            var insertedCount = 0
            for (item in newItems) {
                val result = cameraMediaDao.insertIfNotExists(item)
                if (result > 0) insertedCount++
            }

            val duration = System.currentTimeMillis() - startTime
            return@withContext DiscoveryResult(
                newItems = insertedCount,
                totalItems = newItems.size,
                scanTimeMs = duration
            )
        }

        // Step 1: Scan for DB files to detect folder changes
        val dirtyFolders = detectDirtyFolders(treeUri)

        if (dirtyFolders.isEmpty()) {
            // No folder changes detected
            return@withContext DiscoveryResult(
                newItems = 0,
                totalItems = 0,
                scanTimeMs = System.currentTimeMillis() - startTime
            )
        }

        Log.d(TAG, "Dirty folders detected: ${dirtyFolders.size}")

        // Step 2: Get existing handles for this storage
        val existingHandles = cameraMediaDao.getAllHandlesByStorage(deviceId, storageId).toSet()

        // Step 3: Scan dirty folders for new files
        val scannedDocs = documentFileScanner.scanTree(treeUri, recursive = true)
        val photoDocs = scannedDocs.filter { doc ->
            val ext = doc.extension.lowercase()
            ext in PHOTO_EXTENSIONS || ext in RAW_EXTENSIONS || ext in VIDEO_EXTENSIONS
        }

        for (folder in dirtyFolders) {
            // In a real MTP tree we could filter by folder path,
            // but since scanTree returned all docs, we can match handles.
            for (doc in photoDocs) {
                val handle = doc.documentId.hashCode()
                if (handle !in existingHandles) {
                    // New file!
                    val item = CameraMediaItemEntity(
                        deviceId = deviceId,
                        storageId = storageId,
                        objectHandle = handle,
                        parentHandle = null,
                        fileName = doc.name,
                        fileExtension = doc.name.substringAfterLast('.', "").lowercase(),
                        format = guessFormat(doc.name),
                        sizeBytes = doc.sizeBytes,
                        modifiedAt = doc.lastModified,
                        captureGroup = generateCaptureGroup(doc.lastModified),
                        thumbPath = null,
                        localCopyPath = null,
                        status = MediaStatus.DISCOVERED
                    )
                    newItems.add(item)
                }
            }
        }

        // Step 4: Insert new items to DB
        var insertedCount = 0
        for (item in newItems.distinctBy { it.objectHandle }) {
            val result = cameraMediaDao.insertIfNotExists(item)
            if (result > 0) insertedCount++
        }

        val duration = System.currentTimeMillis() - startTime
        Log.d(TAG, "=== POLL DONE === newItems=$insertedCount, time=${duration}ms")

        DiscoveryResult(
            newItems = insertedCount,
            totalItems = newItems.size,
            dirtyFolders = dirtyFolders.map { it.folderName },
            scanTimeMs = duration
        )
    }

    private fun hasDatabaseFiles(treeUri: Uri): Boolean {
        cachedHasDbFiles?.let { return it }
        val hasDb = checkForDatabasePresence(treeUri)
        cachedHasDbFiles = hasDb
        return hasDb
    }

    private fun checkForDatabasePresence(treeUri: Uri): Boolean {
        try {
            val rootId = DocumentsContract.getTreeDocumentId(treeUri)
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, rootId)
            val projection = arrayOf(
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE
            )
            val mimeTypeDir = DocumentsContract.Document.MIME_TYPE_DIR
            contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val nameIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameIdx) ?: continue
                    val upperName = name.uppercase()
                    val mimeType = cursor.getString(mimeIdx)
                    if (mimeType == mimeTypeDir) {
                        if (DB_FOLDERS.contains(upperName)) {
                            return true
                        }
                    } else {
                        if (DB_PATTERNS.any { upperName.contains(it) }) {
                            return true
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "checkForDatabasePresence failed", e)
        }
        return false
    }

    /**
     * Detect which folders have changed based on database/index file timestamps
     */
    private fun detectDirtyFolders(treeUri: Uri): List<CtgState> {
        val dirtyFolders = mutableListOf<CtgState>()

        try {
            val rootId = DocumentsContract.getTreeDocumentId(treeUri)
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, rootId)

            val projection = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED
            )

            val mimeTypeDir = DocumentsContract.Document.MIME_TYPE_DIR

            contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val idIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val modIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)

                while (cursor.moveToNext()) {
                    val mimeType = cursor.getString(mimeIdx)
                    val name = cursor.getString(nameIdx) ?: continue
                    val upperName = name.uppercase()

                    if (mimeType == mimeTypeDir) {
                        val folderId = cursor.getString(idIdx) ?: continue
                        val ctgState = checkDbInFolder(treeUri, folderId, name)
                        if (ctgState != null) {
                            dirtyFolders.add(ctgState)
                        }

                        if (DB_FOLDERS.contains(upperName)) {
                            scanSubfoldersForDbs(treeUri, folderId, dirtyFolders)
                        }
                    } else {
                        if (DB_PATTERNS.any { upperName.contains(it) }) {
                            val fileId = cursor.getString(idIdx) ?: continue
                            val modified = cursor.getLong(modIdx).coerceAtLeast(0L)
                            dirtyFolders.add(
                                CtgState(
                                    folderId = rootId,
                                    folderName = "root",
                                    ctgFileName = name,
                                    modifiedAt = modified,
                                    fileCount = 0
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "detectDirtyFolders failed", e)
        }

        return dirtyFolders
    }

    private fun scanSubfoldersForDbs(treeUri: Uri, parentFolderId: String, outList: MutableList<CtgState>) {
        try {
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentFolderId)
            val projection = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE
            )
            val mimeTypeDir = DocumentsContract.Document.MIME_TYPE_DIR
            contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val idIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                while (cursor.moveToNext()) {
                    val mimeType = cursor.getString(mimeIdx)
                    if (mimeType == mimeTypeDir) {
                        val folderId = cursor.getString(idIdx) ?: continue
                        val folderName = cursor.getString(nameIdx) ?: continue
                        val ctgState = checkDbInFolder(treeUri, folderId, folderName)
                        if (ctgState != null) {
                            outList.add(ctgState)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "scanSubfoldersForDbs failed for $parentFolderId", e)
        }
    }

    /**
     * Check database file in a specific folder
     */
    private fun checkDbInFolder(treeUri: Uri, folderId: String, folderName: String): CtgState? {
        try {
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, folderId)

            val projection = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                DocumentsContract.Document.COLUMN_SIZE
            )

            contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val idIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val modIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)

                var dbFileName: String? = null
                var dbModified = 0L
                var fileCount = 0

                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameIdx) ?: continue
                    val upperName = name.uppercase()

                    if (DB_PATTERNS.any { upperName.contains(it) }) {
                        dbFileName = name
                        dbModified = cursor.getLong(modIdx).coerceAtLeast(0L)
                    }

                    if (name.substringAfterLast('.', "").lowercase() in PHOTO_EXTENSIONS + RAW_EXTENSIONS) {
                        fileCount++
                    }
                }

                return if (dbFileName != null) {
                    CtgState(
                        folderId = folderId,
                        folderName = folderName,
                        ctgFileName = dbFileName,
                        modifiedAt = dbModified,
                        fileCount = fileCount
                    )
                } else null
            }
        } catch (e: Exception) {
            Log.e(TAG, "checkDbInFolder failed for $folderName", e)
        }

        return null
    }

    /**
     * Generate capture group based on timestamp
     * Groups photos taken in same session (within 1 hour)
     */
    private fun generateCaptureGroup(timestamp: Long): String {
        // Group by hour
        val hourTimestamp = (timestamp / (1000 * 60 * 60)) * (1000 * 60 * 60)
        return "capture_$hourTimestamp"
    }

    /**
     * Guess MTP format from filename
     */
    private fun guessFormat(fileName: String): Int {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "jpg", "jpeg" -> FORMAT_JPEG
            "png" -> FORMAT_TIFF
            "dng" -> FORMAT_DNG
            else -> FORMAT_UNDEFINED
        }
    }

    /**
     * Get pending items for upload
     */
    suspend fun getPendingItems(limit: Int = 10): List<CameraMediaItemEntity> {
        return cameraMediaDao.getPendingItems(MediaStatus.DISCOVERED, limit)
    }

    /**
     * Mark item as queued
     */
    suspend fun markAsQueued(id: Long) {
        cameraMediaDao.updateStatus(id, MediaStatus.QUEUED)
    }

    /**
     * Mark item as uploading
     */
    suspend fun markAsUploading(id: Long) {
        cameraMediaDao.updateStatus(id, MediaStatus.UPLOADING)
    }

    /**
     * Mark item as uploaded
     */
    suspend fun markAsUploaded(id: Long, serverId: String) {
        cameraMediaDao.markAsUploaded(id, MediaStatus.UPLOADED, serverId, System.currentTimeMillis())
    }

    /**
     * Mark item as failed
     */
    suspend fun markAsFailed(id: Long, error: String?) {
        cameraMediaDao.markAsFailed(id, MediaStatus.FAILED, error)
    }

    /**
     * Clear all items for a device
     */
    suspend fun clearAll(deviceId: String) {
        cameraMediaDao.deleteAllByDevice(deviceId)
    }
}
