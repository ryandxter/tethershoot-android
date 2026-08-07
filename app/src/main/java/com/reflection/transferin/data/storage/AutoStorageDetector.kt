package com.reflection.transferin.data.storage

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Auto-detects camera storage locations without user folder picking.
 * Uses MediaStore to find where photos/videos are stored.
 *
 * PiuFoto-style: No folder selection needed - automatically finds:
 * - DCIM folder (camera photos)
 * - Pictures folder
 * - WhatsApp/Messenger folders (optional)
 * - Any folder with recent media
 */
class AutoStorageDetector(private val context: Context) {

    companion object {
        private const val TAG = "AutoStorageDetector"

        // Standard camera folder names
        private val CAMERA_FOLDER_KEYWORDS = listOf(
            "DCIM", "100ANDRO", "101ANDRO", "Camera", "CamPhotos",
            "100CANON", "101CANON", "102CANON", "CANON", "MISC",
            "100Nikon", "101Nikon", "102Nikon", "NIKON", "DSC",
            "100MSDCF", "MSDCF", "101MSDCF",
            "100OLYMP", "OLYMPUS",
            "100RICOH", "RICOH",
            "PRIVATE", "MISC"
        )

        // Media types we care about
        private val PHOTO_EXTENSIONS = setOf(
            "jpg", "jpeg", "png", "heic", "heif", "webp", "bmp", "gif"
        )

        private val RAW_EXTENSIONS = setOf(
            "cr2", "cr3", "nef", "arw", "raf", "rw2", "dng",
            "orf", "erf", "kdc", "mrw", "nrw", "ptx", "pef", "srw", "x3f"
        )

        private val VIDEO_EXTENSIONS = setOf(
            "mp4", "mov", "m4v", "3gp", "avi", "mkv", "webm"
        )
    }

    data class StorageLocation(
        val name: String,
        val rootUri: Uri,
        val relativePath: String,
        val type: StorageType,
        val estimatedFileCount: Int,
        val latestModified: Long
    )

    enum class StorageType {
        DCIM,        // Camera photos
        PICTURES,    // General pictures
        WHATSAPP,    // WhatsApp media
        SCREENSHOT,  // Screenshots
        DOWNLOAD,    // Downloads
        OTHER        // Other folders
    }

    /**
     * Auto-detect all storage locations containing media.
     * Returns locations sorted by priority (DCIM first).
     */
    suspend fun detectStorageLocations(): List<StorageLocation> = withContext(Dispatchers.IO) {
        Log.d(TAG, "=== AUTO STORAGE DETECTION START ===")

        val locations = mutableListOf<StorageLocation>()

        // Method 1: Query MediaStore for distinct parent directories
        val mediaStoreLocations = queryMediaStoreLocations()
        locations.addAll(mediaStoreLocations)

        // Method 2: Check known storage paths directly
        val knownPaths = checkKnownStoragePaths()
        locations.addAll(knownPaths)

        // Deduplicate and sort by priority
        val uniqueLocations = deduplicateLocations(locations)
        val sortedLocations = sortByPriority(uniqueLocations)

        Log.d(TAG, "=== AUTO STORAGE DETECTION COMPLETE ===")
        Log.d(TAG, "Found ${sortedLocations.size} storage locations:")
        sortedLocations.forEach { loc ->
            Log.d(TAG, "  - ${loc.name}: ${loc.relativePath} (${loc.estimatedFileCount} files)")
        }

        return@withContext sortedLocations
    }

    /**
     * Get the PRIMARY camera storage (DCIM).
     * This is what PiuFoto uses by default.
     */
    suspend fun getPrimaryCameraStorage(): StorageLocation? = withContext(Dispatchers.IO) {
        val locations = detectStorageLocations()
        // Return DCIM or first available
        locations.find { it.type == StorageType.DCIM } ?: locations.firstOrNull()
    }

    /**
     * Get ALL relevant media storage locations.
     * For PiuFoto-style "shoot and upload", we monitor DCIM primarily.
     */
    suspend fun getAllMediaStorage(): List<StorageLocation> = withContext(Dispatchers.IO) {
        detectStorageLocations()
    }

    /**
     * Fast check if there are new files since last known timestamp.
     * Used for quick "is there new photo?" check.
     */
    suspend fun hasNewFiles(sinceTimestamp: Long): Boolean = withContext(Dispatchers.IO) {
        val projection = arrayOf(MediaStore.Images.Media._ID)
        val selection = "${MediaStore.Images.Media.DATE_MODIFIED} > ?"
        val selectionArgs = arrayOf((sinceTimestamp / 1000).toString())

        try {
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null
            )?.use { cursor ->
                val count = cursor.count
                Log.d(TAG, "hasNewFiles($sinceTimestamp): $count new files")
                return@withContext count > 0
            }
        } catch (e: Exception) {
            Log.e(TAG, "hasNewFiles check failed", e)
        }

        return@withContext false
    }

    /**
     * Get count of media files in a location.
     */
    suspend fun getMediaCount(relativePath: String? = null): Int = withContext(Dispatchers.IO) {
        val projection = arrayOf(MediaStore.Images.Media._ID)
        val selection = relativePath?.let {
            "${MediaStore.Images.Media.DATA} LIKE ?"
        }
        val selectionArgs = relativePath?.let {
            arrayOf("%/$it/%")
        }

        try {
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null
            )?.use { cursor ->
                return@withContext cursor.count
            }
        } catch (e: Exception) {
            Log.e(TAG, "getMediaCount failed", e)
        }

        return@withContext 0
    }

    /**
     * Query MediaStore for distinct storage locations.
     */
    private fun queryMediaStoreLocations(): List<StorageLocation> {
        val locations = mutableListOf<StorageLocation>()
        val seenPaths = mutableSetOf<String>()

        // Query images
        queryMediaType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, locations, seenPaths)

        // Query videos (optional, for completeness)
        queryMediaType(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, locations, seenPaths)

        return locations
    }

    private fun queryMediaType(
        contentUri: Uri,
        locations: MutableList<StorageLocation>,
        seenPaths: MutableSet<String>
    ) {
        val projection = arrayOf(
            MediaStore.MediaColumns.DATA,
            MediaStore.MediaColumns.DATE_MODIFIED
        )

        // Group by directory - use DISTINCT query
        val distinctDirs = mutableSetOf<String>()

        try {
            context.contentResolver.query(
                contentUri,
                projection,
                null,
                null,
                "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"
            )?.use { cursor ->
                val dataIdx = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                val modIdx = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)

                while (cursor.moveToNext()) {
                    val path = cursor.getString(dataIdx) ?: continue
                    val dir = extractParentDirectory(path) ?: continue

                    if (dir !in distinctDirs) {
                        distinctDirs.add(dir)
                        val modified = cursor.getLong(modIdx) * 1000 // Convert to ms

                        val storageType = determineStorageType(dir)
                        val uri = buildStorageUri(dir)

                        locations.add(
                            StorageLocation(
                                name = extractFolderName(dir),
                                rootUri = uri,
                                relativePath = dir,
                                type = storageType,
                                estimatedFileCount = 0, // Will be updated
                                latestModified = modified
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "queryMediaType failed for $contentUri", e)
        }
    }

    /**
     * Check known storage paths directly.
     */
    private fun checkKnownStoragePaths(): List<StorageLocation> {
        val locations = mutableListOf<StorageLocation>()

        val knownPaths = listOf(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        )

        for (path in knownPaths) {
            if (path.exists() && path.isDirectory) {
                val dir = path.absolutePath.removePrefix("/storage/emulated/0/")
                val storageType = determineStorageType(dir)

                locations.add(
                    StorageLocation(
                        name = path.name,
                        rootUri = android.net.Uri.fromFile(path),
                        relativePath = dir,
                        type = storageType,
                        estimatedFileCount = 0,
                        latestModified = path.lastModified()
                    )
                )
            }
        }

        return locations
    }

    private fun deduplicateLocations(locations: List<StorageLocation>): List<StorageLocation> {
        val seen = mutableSetOf<String>()
        return locations.filter { loc ->
            if (loc.relativePath in seen) {
                false
            } else {
                seen.add(loc.relativePath)
                true
            }
        }
    }

    private fun sortByPriority(locations: List<StorageLocation>): List<StorageLocation> {
        return locations.sortedWith(compareBy<StorageLocation> {
            // Sort by type priority
            when (it.type) {
                StorageType.DCIM -> 0
                StorageType.PICTURES -> 1
                StorageType.SCREENSHOT -> 2
                StorageType.WHATSAPP -> 3
                StorageType.DOWNLOAD -> 4
                StorageType.OTHER -> 5
            }
        }.thenByDescending { it.latestModified })
    }

    private fun extractParentDirectory(path: String): String? {
        // Extract directory path, e.g., "DCIM/100ANDRO" from "/storage/emulated/0/DCIM/100ANDRO/IMG_1234.jpg"
        val parts = path.split("/")
        if (parts.size >= 2) {
            // Get last 2 parts: "DCIM/100ANDRO"
            return parts.takeLast(2).joinToString("/")
        }
        return parts.lastOrNull()
    }

    private fun extractFolderName(path: String): String {
        return path.split("/").lastOrNull() ?: path
    }

    private fun determineStorageType(path: String): StorageType {
        val upperPath = path.uppercase()
        val lowerPath = path.lowercase()

        return when {
            upperPath.contains("DCIM") -> StorageType.DCIM
            upperPath.contains("PICTURES") || upperPath.contains("PHOTOS") -> StorageType.PICTURES
            lowerPath.contains("whatsapp") -> StorageType.WHATSAPP
            lowerPath.contains("screenshot") -> StorageType.SCREENSHOT
            upperPath.contains("DOWNLOAD") -> StorageType.DOWNLOAD
            else -> StorageType.OTHER
        }
    }

    private fun buildStorageUri(relativePath: String): Uri {
        // For SAF, we need content:// URI
        // Try to find existing tree URI or create content:// URI
        val primaryStorage = "/storage/emulated/0/$relativePath"

        // Use MediaStore to find the tree URI
        return try {
            val projection = arrayOf(MediaStore.MediaColumns.DATA)
            val selection = "${MediaStore.MediaColumns.DATA} LIKE ?"
            val selectionArgs = arrayOf("%/$relativePath/%")

            context.contentResolver.query(
                MediaStore.Files.getContentUri("external"),
                projection,
                selection,
                selectionArgs,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val data = cursor.getString(0)
                    if (data != null) {
                        // Return tree URI format for DocumentFile
                        return Uri.parse("content://com.android.externalstorage.documents/tree/primary:$relativePath")
                    }
                }
            }

            // Fallback: construct tree URI
            Uri.parse("content://com.android.externalstorage.documents/tree/primary:$relativePath")
        } catch (e: Exception) {
            Log.e(TAG, "buildStorageUri failed for $relativePath", e)
            Uri.parse("content://com.android.externalstorage.documents/tree/primary:$relativePath")
        }
    }
}
