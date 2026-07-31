package com.reflection.transferin.domain.shoot

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.reflection.transferin.domain.model.CameraMedia
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Result of parsing a CTG database file.
 *
 * @property cameraId Camera device identifier (if available)
 * @property storageId MTP storage ID where CTG was found
 * @property records List of media entries parsed from CTG
 * @property parsedAt Timestamp when parsing completed (millis since epoch)
 */
data class CtgDatabase(
    val cameraId: String?,
    val storageId: Int,
    val records: List<CtgMediaEntry>,
    val parsedAt: Long
)

/**
 * Parses Canon CTG (Canon Thumbnail Group) SQLite database files via MTP.
 *
 * CTG files contain metadata about all media on the camera's memory card.
 * This parser downloads the CTG file via MTP and opens it with SQLiteDatabase
 * to extract media entries without requiring the camera to hold a file lock.
 *
 * Architecture:
 * - Pure domain logic (no Android context dependencies)
 * - Constructor injection for testability
 * - Falls back through multiple query patterns for Canon firmware compatibility
 *
 * @property getAllObjectHandles Suspend function to enumerate MTP object handles
 * @property getObjectMetadata Suspend function to get metadata for a handle
 * @property downloadToCache Suspend function to download file to cache
 * @property cacheDir Directory for temporary file storage during parsing
 */
class CtgDatabaseParser(
    private val getAllObjectHandles: suspend (storageId: Int) -> List<Int>,
    private val getObjectMetadata: suspend (handle: Int) -> CameraMedia?,
    private val downloadToCache: suspend (handle: Int) -> File?,
    private val cacheDir: File
) {
    companion object {
        private const val TAG = "CtgDatabaseParser"

        // CTG filename patterns to match (from CtgWatcher)
        private val CTG_PATTERNS = listOf(
            "AUTOMRIC.CTG",
            "AUTOMSC1.CTG",
            "CANON_MDC.CTG"
        )

        // Query patterns for different Canon firmware versions
        private val QUERY_PATTERNS = listOf(
            // Pattern 1: Standard with format field (most common)
            QueryPattern(
                sql = "SELECT name, size, modified, handle, format FROM images",
                nameIndex = 0,
                sizeIndex = 1,
                modifiedIndex = 2,
                handleIndex = 3,
                formatIndex = 4,
                capturedTimeIndex = -1
            ),
            // Pattern 2: With date_modified field
            QueryPattern(
                sql = "SELECT name, size, date_modified, handle, format FROM images",
                nameIndex = 0,
                sizeIndex = 1,
                modifiedIndex = 2,
                handleIndex = 3,
                formatIndex = 4,
                capturedTimeIndex = -1
            ),
            // Pattern 3: Basic columns only
            QueryPattern(
                sql = "SELECT name, size, modified, handle FROM images",
                nameIndex = 0,
                sizeIndex = 1,
                modifiedIndex = 2,
                handleIndex = 3,
                formatIndex = -1,
                capturedTimeIndex = -1
            )
        )
    }

    private data class QueryPattern(
        val sql: String,
        val nameIndex: Int,
        val sizeIndex: Int,
        val modifiedIndex: Int,
        val handleIndex: Int,
        val formatIndex: Int,
        val capturedTimeIndex: Int
    )

    /**
     * Parse CTG file for given storage and return media entries.
     *
     * Flow:
     * 1. Enumerate all MTP objects to find CTG files
     * 2. Download CTG file to cache via MTP
     * 3. Open with SQLiteDatabase (READONLY)
     * 4. Query images table with fallback patterns
     * 5. Map results to CtgMediaEntry
     * 6. Clean up cache file in finally block
     *
     * @param storageId MTP storage ID to search for CTG files
     * @param cameraId Optional camera identifier
     * @return CtgDatabase with records, or null on failure
     */
    suspend fun parseCtgFile(storageId: Int, cameraId: String? = null): CtgDatabase? =
        withContext(Dispatchers.IO) {
            try {
                // Step 1: Find CTG file via MTP
                val ctgHandle = findCtgFileHandle(storageId)
                if (ctgHandle == null) {
                    Log.w(TAG, "No CTG file found on storageId=$storageId")
                    return@withContext null
                }

                Log.d(TAG, "Found CTG file with handle=$ctgHandle")

                // Step 2: Download CTG to cache
                val ctgFile = downloadToCache(ctgHandle)
                if (ctgFile == null || !ctgFile.exists() || ctgFile.length() == 0L) {
                    Log.e(TAG, "Failed to download CTG file or file is empty")
                    return@withContext null
                }

                Log.d(TAG, "CTG file downloaded to: ${ctgFile.absolutePath}, size=${ctgFile.length()}")

                try {
                    // Step 3: Open with SQLiteDatabase
                    val database = SQLiteDatabase.openDatabase(
                        ctgFile.absolutePath,
                        null,
                        SQLiteDatabase.OPEN_READONLY
                    )

                    try {
                        // Debug: Log discovered tables
                        logTableNames(database)

                        // Step 4: Query with fallback patterns
                        val records = queryWithFallback(database)

                        Log.d(TAG, "Parsed ${records.size} records from CTG database")

                        // Step 5: Return result
                        CtgDatabase(
                            cameraId = cameraId,
                            storageId = storageId,
                            records = records,
                            parsedAt = System.currentTimeMillis()
                        )
                    } finally {
                        database.close()
                    }
                } finally {
                    // Step 6: Clean up cache file
                    if (ctgFile.exists()) {
                        runCatching { ctgFile.delete() }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing CTG file", e)
                null
            }
        }

    /**
     * Find CTG file handle by enumerating all objects on storage.
     */
    private suspend fun findCtgFileHandle(storageId: Int): Int? {
        val handles = getAllObjectHandles(storageId)

        for (handle in handles) {
            val meta = getObjectMetadata(handle) ?: continue
            val name = meta.name.uppercase()

            if (CTG_PATTERNS.any { pattern -> name.contains(pattern) }) {
                return handle
            }
        }

        return null
    }

    /**
     * Log all table names in the database for debugging.
     */
    private fun logTableNames(database: SQLiteDatabase) {
        try {
            val cursor = database.rawQuery(
                "SELECT name FROM sqlite_master WHERE type='table'",
                null
            )
            cursor.use {
                val tables = mutableListOf<String>()
                while (it.moveToNext()) {
                    tables.add(it.getString(0))
                }
                Log.d(TAG, "CTG database tables: $tables")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not query table names", e)
        }
    }

    /**
     * Try query patterns in order until one succeeds.
     */
    private fun queryWithFallback(database: SQLiteDatabase): List<CtgMediaEntry> {
        for (pattern in QUERY_PATTERNS) {
            try {
                val cursor = database.rawQuery(pattern.sql, null)
                cursor.use {
                    if (it.count == 0) {
                        Log.d(TAG, "Query returned no rows: ${pattern.sql}")
                        return@use
                    }

                    val records = mutableListOf<CtgMediaEntry>()
                    while (it.moveToNext()) {
                        val entry = mapCursorRow(it, pattern)
                        if (entry != null) {
                            // Skip folders (formatCode == 0x3000)
                            if (entry.formatCode != CtgMediaEntry.FORMAT_ASSOCIATION) {
                                records.add(entry)
                            }
                        }
                    }

                    if (records.isNotEmpty()) {
                        Log.d(TAG, "Successfully parsed ${records.size} records with pattern: ${pattern.sql}")
                        return records
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Query pattern failed: ${pattern.sql}", e)
            }
        }

        Log.w(TAG, "All query patterns failed, returning empty list")
        return emptyList()
    }

    /**
     * Map cursor row to CtgMediaEntry using the query pattern.
     */
    private fun mapCursorRow(cursor: Cursor, pattern: QueryPattern): CtgMediaEntry? {
        return try {
            val name = if (pattern.nameIndex >= 0) {
                cursor.getString(pattern.nameIndex) ?: return null
            } else null

            val size = if (pattern.sizeIndex >= 0) {
                cursor.getLong(pattern.sizeIndex)
            } else 0L

            val modified = if (pattern.modifiedIndex >= 0) {
                // Handle both seconds (Unix timestamp) and milliseconds
                val rawModified = cursor.getLong(pattern.modifiedIndex)
                if (rawModified > 1_000_000_000_000) rawModified else rawModified * 1000
            } else 0L

            val handle = if (pattern.handleIndex >= 0) {
                cursor.getInt(pattern.handleIndex)
            } else 0

            val formatCode = if (pattern.formatIndex >= 0 && !cursor.isNull(pattern.formatIndex)) {
                cursor.getInt(pattern.formatIndex)
            } else 0x3801 // Default to JPEG

            CtgMediaEntry(
                handle = handle,
                filename = name ?: "",
                size = size,
                modifiedTime = modified,
                capturedTime = null, // CTG files typically don't have capture time
                formatCode = formatCode
            )
        } catch (e: Exception) {
            Log.w(TAG, "Error mapping cursor row", e)
            null
        }
    }
}
