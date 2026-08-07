package com.reflection.transferin.data.usb

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlin.coroutines.coroutineContext

/**
 * Hybrid observer for real-time MTP/SAF file change detection.
 *
 * Detection strategy:
 * 1. Register ContentObserver to get notified of MTP changes
 * 2. Track file fingerprints to detect truly NEW files
 * 3. NOT rely on folder timestamp (unreliable on many cameras)
 */
class MtpContentObserver(
    private val context: Context,
    private val treeUri: Uri
) {
    companion object {
        private const val TAG = "MtpContentObserver"
    }

    sealed class ChangeEvent {
        data class NewFileDetected(val documentUri: Uri, val documentId: String) : ChangeEvent()
        object SystemNotified : ChangeEvent()
        data class Error(val message: String, val throwable: Throwable? = null) : ChangeEvent()
    }

    // Track fingerprints of files we've already seen/notified about
    private val notifiedFingerprints = mutableSetOf<String>()

    private var contentObserver: ContentObserver? = null
    private var isObserving = false

    /**
     * Call this BEFORE baseline preparation to pre-populate notified fingerprints.
     * This prevents baseline files from being emitted as "new" later.
     */
    fun prePopulateFingerprints(fingerprints: Set<String>) {
        notifiedFingerprints.clear()
        notifiedFingerprints.addAll(fingerprints)
        Log.d(TAG, "Pre-populated ${fingerprints.size} fingerprints from baseline")
    }

    /**
     * Call this when baseline is ready to mark all baseline files as "not new".
     */
    fun markBaselineReady(fingerprints: Set<String>) {
        notifiedFingerprints.clear()
        notifiedFingerprints.addAll(fingerprints)
        Log.d(TAG, "Baseline ready: ${fingerprints.size} files marked as existing")
    }

    fun observeChanges(): Flow<ChangeEvent> = callbackFlow {
        val resolver = context.contentResolver

        contentObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                super.onChange(selfChange, uri)
                Log.d(TAG, "ContentObserver.onChange: selfChange=$selfChange")
                // System notified us of a change - just signal, don't scan here
                trySend(ChangeEvent.SystemNotified)
            }
        }

        try {
            resolver.registerContentObserver(treeUri, true, contentObserver!!)
            isObserving = true
            Log.d(TAG, "ContentObserver registered for: $treeUri")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register ContentObserver", e)
            trySend(ChangeEvent.Error("Failed to register observer", e))
        }

        awaitClose {
            stopObserving()
        }
    }

    /**
     * Check for new files and return only truly new ones.
     * Call this periodically or when SystemNotified event is received.
     */
    fun checkForNewFiles(): List<Pair<Uri, String>> {
        val newFiles = mutableListOf<Pair<Uri, String>>()

        try {
            val rootId = DocumentsContract.getTreeDocumentId(treeUri)
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, rootId)

            val projection = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_SIZE,
                DocumentsContract.Document.COLUMN_MIME_TYPE
            )

            val queryArgs = android.os.Bundle().apply {
                putInt("android.content.query_arg_reload", 1)
            }

            context.contentResolver.query(childrenUri, projection, queryArgs, null)?.use { cursor ->
                val idIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val sizeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
                val mimeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)

                while (cursor.moveToNext()) {
                    val docId = cursor.getString(idIdx) ?: continue
                    val name = cursor.getString(nameIdx) ?: continue
                    val size = cursor.getLong(sizeIdx)
                    val mimeType = cursor.getString(mimeIdx)

                    // Skip directories
                    if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) continue

                    val fingerprint = "${name.lowercase()}|$size"
                    val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)

                    if (fingerprint !in notifiedFingerprints) {
                        notifiedFingerprints.add(fingerprint)
                        newFiles.add(Pair(docUri, docId))
                        Log.d(TAG, "[MTP-NEW] Detected: $name (fp=$fingerprint)")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "checkForNewFiles failed", e)
        }

        return newFiles
    }

    fun stopObserving() {
        if (isObserving) {
            try {
                contentObserver?.let {
                    context.contentResolver.unregisterContentObserver(it)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error unregistering observer", e)
            }
            isObserving = false
            contentObserver = null
            Log.d(TAG, "ContentObserver unregistered")
        }
    }

    fun reset() {
        notifiedFingerprints.clear()
    }
}
