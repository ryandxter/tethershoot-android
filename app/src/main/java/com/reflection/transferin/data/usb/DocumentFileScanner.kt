package com.reflection.transferin.data.usb

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.util.Log
import java.util.Locale
import kotlin.system.measureTimeMillis

class DocumentFileScanner(
    private val context: Context
) {

    /**
     * Expose content resolver for orchestrator use
     */
    val contentResolver = context.contentResolver

    companion object {
        private const val TAG = "DocumentFileScanner"
        private const val MIME_TYPE_DIR = DocumentsContract.Document.MIME_TYPE_DIR
        // Konstanta manual untuk menjamin kompatibilitas
        private const val QUERY_ARG_RELOAD = "android.content.query_arg_reload"
    }

    data class ScannedDocument(
        val uri: Uri,
        val documentId: String,
        val name: String,
        val mimeType: String?,
        val sizeBytes: Long,
        val lastModified: Long,
        val relativePath: String,
        val folderId: String? = null  // Optional folder ID for tracking
    ) {
        val extension: String
            get() = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
    }

    fun scanTree(
        treeUri: Uri,
        recursive: Boolean = true,
        includeHidden: Boolean = false,
        allowedExtensions: Set<String>? = null,
        skipZeroByteFiles: Boolean = true
    ): List<ScannedDocument> {
        val normalizedAllowedExtensions = allowedExtensions
            ?.asSequence()
            ?.map { it.trim().lowercase(Locale.ROOT).removePrefix(".") }
            ?.filter { it.isNotBlank() }
            ?.toSet()

        val resolver = context.contentResolver
        
        // TRICK 1: Paksa sistem MTP Android untuk menyadari ada perubahan fisik di kabel
        runCatching {
            resolver.notifyChange(treeUri, null)
        }

        val rootDocumentId = runCatching {
            DocumentsContract.getTreeDocumentId(treeUri)
        }.getOrElse { throwable ->
            Log.e(TAG, "scanTree() failed to get root documentId: treeUri=$treeUri", throwable)
            return emptyList()
        }

        val results = mutableListOf<ScannedDocument>()
        val stack = ArrayDeque<NodeToScan>()
        stack.add(NodeToScan(documentId = rootDocumentId, relativePath = "", depth = 0))

        while (stack.isNotEmpty()) {
            val node = stack.removeLast()

            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
                treeUri,
                node.documentId
            )

            // TRICK 2: Bypass Cache dengan cara menyentuh (Poke) metadata folder
            runCatching {
                resolver.refresh(childrenUri, null, null)
                val folderUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, node.documentId)
                resolver.query(folderUri, null, null, null, null)?.use { it.count }
            }

            val projection = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_SIZE,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED
            )

            try {
                // TRICK 3: FORCE RELOAD via Query Args
                val queryArgs = Bundle().apply {
                    putInt(QUERY_ARG_RELOAD, 1)
                }

                resolver.query(
                    childrenUri,
                    projection,
                    queryArgs,
                    null
                )?.use { cursor ->
                    val documentIdIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                    val displayNameIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    val mimeTypeIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                    val sizeIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
                    val lastModifiedIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)

                    while (cursor.moveToNext()) {
                        val documentId = cursor.getString(documentIdIndex).orEmpty()
                        val rawName = cursor.getString(displayNameIndex).orEmpty()
                        val name = rawName.trim()
                        val mimeType = cursor.getString(mimeTypeIndex)
                        val sizeBytes = cursor.getLong(sizeIndex).coerceAtLeast(0L)
                        val lastModified = cursor.getLong(lastModifiedIndex).coerceAtLeast(0L)

                        if (documentId.isBlank() || name.isBlank()) continue
                        if (!includeHidden && name.startsWith(".")) continue

                        val relativePath = if (node.relativePath.isBlank()) name else "${node.relativePath}/$name"

                        if (mimeType == MIME_TYPE_DIR) {
                            if (recursive) {
                                stack.add(NodeToScan(documentId, relativePath, node.depth + 1))
                            }
                            continue
                        }

                        val fileUri = runCatching {
                            DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
                        }.getOrNull() ?: continue

                        val scanned = ScannedDocument(
                            uri = fileUri,
                            documentId = documentId,
                            name = name,
                            mimeType = mimeType,
                            sizeBytes = sizeBytes,
                            lastModified = lastModified,
                            relativePath = relativePath,
                            folderId = node.documentId // FIXED: Populate folderId for CTG tracking
                        )

                        // FIX: Never skip .ctg files even if they appear as 0 bytes (MTP lag)
                        val isCtg = name.lowercase(Locale.ROOT).endsWith(".ctg")
                        if (skipZeroByteFiles && scanned.sizeBytes <= 0L && !isCtg) continue
                        if (!matchesExtensionFilter(scanned, normalizedAllowedExtensions)) continue

                        results.add(scanned)
                    }
                }
            } catch (throwable: Throwable) {
                Log.e(TAG, "scanTree() query failed for ${node.documentId}", throwable)
            }
        }

        return results.sortedBy { it.relativePath.lowercase(Locale.ROOT) }
    }

    private fun matchesExtensionFilter(
        document: ScannedDocument,
        allowedExtensions: Set<String>?
    ): Boolean {
        if (allowedExtensions.isNullOrEmpty()) return true
        return document.extension in allowedExtensions
    }

    /**
     * Get single file info by document URI.
     * Lightweight operation - no folder scan.
     */
    fun getSingleFileInfo(docUri: Uri): ScannedDocument? {
        val resolver = context.contentResolver

        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED
        )

        return try {
            resolver.query(docUri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val documentId = cursor.getString(cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)).orEmpty()
                    val name = cursor.getString(cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)).orEmpty()
                    val mimeType = cursor.getString(cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE))
                    val sizeBytes = cursor.getLong(cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)).coerceAtLeast(0L)
                    val lastModified = cursor.getLong(cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)).coerceAtLeast(0L)

                    ScannedDocument(
                        uri = docUri,
                        documentId = documentId,
                        name = name,
                        mimeType = mimeType,
                        sizeBytes = sizeBytes,
                        lastModified = lastModified,
                        relativePath = name
                    )
                } else null
            }
        } catch (e: Exception) {
            Log.e(TAG, "getSingleFileInfo failed for $docUri", e)
            null
        }
    }

    /**
     * Get recent changes since a given timestamp.
     * More efficient than full scan when you only need recent files.
     */
    fun getRecentChanges(
        treeUri: Uri,
        sinceTimestamp: Long,
        allowedExtensions: Set<String>? = null
    ): List<ScannedDocument> {
        val normalizedAllowedExtensions = allowedExtensions
            ?.asSequence()
            ?.map { it.trim().lowercase(Locale.ROOT).removePrefix(".") }
            ?.filter { it.isNotBlank() }
            ?.toSet()

        val resolver = context.contentResolver

        // Wake up MTP
        runCatching {
            resolver.notifyChange(treeUri, null)
        }

        val rootDocumentId = runCatching {
            DocumentsContract.getTreeDocumentId(treeUri)
        }.getOrElse {
            Log.e(TAG, "getRecentChanges() failed to get root documentId", it)
            return emptyList()
        }

        val results = mutableListOf<ScannedDocument>()
        val stack = ArrayDeque<NodeToScan>()
        stack.add(NodeToScan(documentId = rootDocumentId, relativePath = "", depth = 0))

        while (stack.isNotEmpty()) {
            val node = stack.removeLast()

            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, node.documentId)

            // Poke cache
            runCatching {
                resolver.refresh(childrenUri, null, null)
            }

            val projection = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_SIZE,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED
            )

            try {
                val queryArgs = Bundle().apply {
                    putInt(QUERY_ARG_RELOAD, 1)
                }

                resolver.query(childrenUri, projection, queryArgs, null)?.use { cursor ->
                    val idIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                    val nameIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    val mimeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                    val sizeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
                    val modIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)

                    while (cursor.moveToNext()) {
                        val documentId = cursor.getString(idIdx).orEmpty()
                        val name = cursor.getString(nameIdx).orEmpty().trim()
                        val mimeType = cursor.getString(mimeIdx)
                        val sizeBytes = cursor.getLong(sizeIdx).coerceAtLeast(0L)
                        val lastModified = cursor.getLong(modIdx).coerceAtLeast(0L)

                        if (documentId.isBlank() || name.isBlank()) continue
                        if (name.startsWith(".")) continue

                        // Only include files modified since timestamp
                        val toleranceMs = 2000L // 2 second tolerance
                        if (lastModified < sinceTimestamp - toleranceMs) continue

                        val relativePath = if (node.relativePath.isBlank()) name else "${node.relativePath}/$name"

                        // Skip directories
                        if (mimeType == MIME_TYPE_DIR) {
                            stack.add(NodeToScan(documentId, relativePath, node.depth + 1))
                            continue
                        }

                        val fileUri = runCatching {
                            DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
                        }.getOrNull() ?: continue

                        val scanned = ScannedDocument(
                            uri = fileUri,
                            documentId = documentId,
                            name = name,
                            mimeType = mimeType,
                            sizeBytes = sizeBytes,
                            lastModified = lastModified,
                            relativePath = relativePath,
                            folderId = node.documentId // FIXED: Populate folderId for hybrid mode
                        )

                        // FIX: Ensure CTG is not skipped even if reported as 0 bytes
                        val isCtg = name.lowercase(Locale.ROOT).endsWith(".ctg")
                        if (scanned.sizeBytes <= 0L && !isCtg) continue
                        if (!matchesExtensionFilter(scanned, normalizedAllowedExtensions)) continue

                        results.add(scanned)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "getRecentChanges() query failed for ${node.documentId}", e)
            }
        }

        return results.sortedBy { it.lastModified }
    }

    /**
     * Get the newest file in a tree without full scan.
     * Useful for quick timestamp check.
     */
    fun getNewestFile(treeUri: Uri): ScannedDocument? {
        val resolver = context.contentResolver

        val rootDocumentId = runCatching {
            DocumentsContract.getTreeDocumentId(treeUri)
        }.getOrElse { return null }

        // Just scan top level - usually newest is there for camera folders
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, rootDocumentId)

        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED
        )

        var newest: ScannedDocument? = null

        try {
            val queryArgs = Bundle().apply {
                putInt(QUERY_ARG_RELOAD, 1)
            }

            resolver.query(childrenUri, projection, queryArgs, null)?.use { cursor ->
                val idIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val sizeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
                val modIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)

                while (cursor.moveToNext()) {
                    val documentId = cursor.getString(idIdx).orEmpty()
                    val name = cursor.getString(nameIdx).orEmpty().trim()
                    val mimeType = cursor.getString(mimeIdx)
                    val sizeBytes = cursor.getLong(sizeIdx).coerceAtLeast(0L)
                    val lastModified = cursor.getLong(modIdx).coerceAtLeast(0L)

                    if (documentId.isBlank() || name.isBlank()) continue
                    if (name.startsWith(".")) continue

                    // Skip directories
                    if (mimeType == MIME_TYPE_DIR) continue

                    val fileUri = runCatching {
                        DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
                    }.getOrNull() ?: continue

                    val scanned = ScannedDocument(
                        uri = fileUri,
                        documentId = documentId,
                        name = name,
                        mimeType = mimeType,
                        sizeBytes = sizeBytes,
                        lastModified = lastModified,
                        relativePath = name
                    )

                    if (newest == null || lastModified > newest.lastModified) {
                        newest = scanned
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "getNewestFile() failed", e)
        }

        return newest
    }

    private data class NodeToScan(
        val documentId: String,
        val relativePath: String,
        val depth: Int
    )
}
