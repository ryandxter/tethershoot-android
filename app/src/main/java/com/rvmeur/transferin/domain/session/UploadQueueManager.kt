package com.rvmeur.transferin.domain.session

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.core.net.toUri
import com.rvmeur.transferin.data.local.entity.TransferQueueItemEntity
import com.rvmeur.transferin.data.repository.TransferQueueRepositoryImpl
import com.rvmeur.transferin.data.worker.TransferUploadWorkScheduler
import com.rvmeur.transferin.domain.model.MediaJob
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ExifInterface
import java.io.File
import java.util.Locale
import java.util.UUID

class UploadQueueManager(
    private val context: Context,
    private val transferQueueRepository: TransferQueueRepositoryImpl,
    private val workSchedulerOverride: TransferUploadWorkScheduler? = null
) {

    private val workScheduler = workSchedulerOverride ?: TransferUploadWorkScheduler(context.applicationContext)

    suspend fun enqueueMediaJob(
        job: MediaJob,
        sessionId: String,
        targetNasDirPath: String
    ) {
        val safeSessionId = sessionId.trim()
        val safeTargetNasDirPath = normalizeRemoteDirectory(targetNasDirPath)

        if (safeSessionId.isBlank()) return

        val sourceFingerprint = job.sourceFingerprint.trim()
        if (sourceFingerprint.isBlank()) return

        val now = System.currentTimeMillis()

        // Utamakan localTempPath sebagai sumber upload & cleanup.
        val sourceUri = resolveLocalSourceUri(job)
        val metadata = readSourceMetadata(sourceUri)

        // PENTING:
        // - Origin filename dari kamera datang di job.originalFileName.
        // - metadata.fileName (DISPLAY_NAME dari content resolver) bisa berbeda, tapi untuk nama di NAS
        //   kita utamakan originFileName supaya folder di NAS "mirip kamera".
        val originFileName = job.originalFileName.trim().takeIf { it.isNotBlank() }

        val rawFileName = if (originFileName != null) {
            originFileName
        } else {
            val prefixRegex = Regex("^\\d+_(.+)$")
            val fromMetadata = metadata.fileName?.trim()?.takeIf { it.isNotBlank() }
            if (fromMetadata != null) {
                prefixRegex.matchEntire(fromMetadata)?.groupValues?.get(1) ?: fromMetadata
            } else {
                val tempName = File(job.localTempPath).name.trim().takeIf { it.isNotBlank() } ?: "unknown_file"
                prefixRegex.matchEntire(tempName)?.groupValues?.get(1) ?: tempName
            }
        }

        val fileName = sanitizeFileName(rawFileName)
        android.util.Log.d("UploadQueueManager", "enqueueMediaJob: job.originalFileName=${job.originalFileName}, metadata.fileName=${metadata.fileName}, final fileName=$fileName")
        val mimeType = metadata.mimeType ?: detectMimeType(fileName)
        val fileType = detectFileType(mimeType)
        val fileSizeBytes = metadata.fileSizeBytes.coerceAtLeast(0L)

        val entityId = UUID.randomUUID().toString()
        createThumbnail(sourceUri.toString(), entityId, sourceFingerprint)

        val entity = TransferQueueItemEntity(
            id = entityId,
            sessionId = safeSessionId,
            // Simpan path sumber sebagai string; worker akan mengubahnya kembali ke Uri
            sourceUri = sourceUri.toString(),
            sourceFingerprint = sourceFingerprint,
            // Metadata sumber: boleh pakai nama yang sama dengan fileName
            sourceFileName = fileName,
            sourceRelativePath = fileName,
            sourceMimeType = mimeType,
            fileType = fileType,
            priority = 100,
            fileSizeBytes = fileSizeBytes,
            lastModifiedEpochMs = job.lastModifiedEpochMs.takeIf { it > 0L }
                ?: job.createdAtEpochMs.takeIf { it > 0L }
                ?: now,
            targetNasDirPath = safeTargetNasDirPath,
            // PENTING: nama final di NAS = fileName (berbasis originFileName + sanitasi ringan).
            targetFileName = fileName,
            status = "QUEUED",
            progressPercent = 0,
            bytesUploaded = 0L,
            retryCount = 0,
            errorMessage = null,
            createdAtEpochMs = now,
            updatedAtEpochMs = now,
            dispatchedAtEpochMs = null,
            uploadStartedAtEpochMs = null,
            completedAtEpochMs = null
        )

        val queueItemId = transferQueueRepository.insertAndReturnFirstId(entity)
        workScheduler.enqueue(queueItemId)
    }

    /**
     * Resolusi sumber untuk upload:
     * - Utamakan selalu job.localTempPath (file cache lokal dari kamera).
     * - Jika localTempPath kosong (fallback manual/dummy), gunakan job.sourceUri.
     */
    private fun resolveLocalSourceUri(job: MediaJob): Uri {
        val rawTemp = job.localTempPath.trim()
        if (rawTemp.isNotBlank()) {
            return when {
                rawTemp.startsWith("content://") -> rawTemp.toUri()
                rawTemp.startsWith("file://") -> rawTemp.toUri()
                else -> Uri.fromFile(File(rawTemp))
            }
        }

        // Fallback defensif: gunakan sourceUri kalau localTempPath tidak tersedia.
        val rawSource = job.sourceUri.trim()
        require(rawSource.isNotBlank()) { "Source URI and localTempPath are blank" }

        return when {
            rawSource.startsWith("content://") -> rawSource.toUri()
            rawSource.startsWith("file://") -> rawSource.toUri()
            else -> Uri.fromFile(File(rawSource))
        }
    }

    private fun readSourceMetadata(uri: Uri): SourceMetadata {
        if (uri.scheme == "content") {
            val resolver = context.contentResolver
            val mimeType = resolver.getType(uri)
            val projection = arrayOf(
                OpenableColumns.DISPLAY_NAME,
                OpenableColumns.SIZE
            )

            runCatching {
                resolver.query(uri, projection, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)

                    if (cursor.moveToFirst()) {
                        val displayName = if (nameIndex >= 0) cursor.getString(nameIndex) else null
                        val size = if (sizeIndex >= 0) cursor.getLong(sizeIndex) else -1L

                        return SourceMetadata(
                            fileName = displayName?.takeIf { it.isNotBlank() },
                            fileSizeBytes = size.takeIf { it >= 0L } ?: 0L,
                            mimeType = mimeType
                        )
                    }
                }
            }

            return SourceMetadata(
                fileName = null,
                fileSizeBytes = 0L,
                mimeType = mimeType
            )
        }

        if (uri.scheme == "file" || uri.scheme.isNullOrBlank()) {
            val file = File(uri.path.orEmpty())

            return SourceMetadata(
                fileName = file.name.takeIf { it.isNotBlank() },
                fileSizeBytes = file.takeIf { it.exists() }?.length() ?: 0L,
                mimeType = null
            )
        }

        return SourceMetadata(
            fileName = null,
            fileSizeBytes = 0L,
            mimeType = null
        )
    }

    private fun sanitizeFileName(name: String): String {
        return name
            .trim()
            .replace("/", "_")
            .replace("\\", "_")
            .ifBlank { "unknown_file" }
    }

    /**
     * Normalisasi folder remote:
     * - Selalu mulai dengan "/" dan tanpa "/" di akhir (kecuali root "/").
     * Contoh:
     *  "photos"      -> "/photos"
     *  "/photos/"    -> "/photos"
     *  " /photos  "  -> "/photos"
     *  ""            -> "/"
     */
    private fun normalizeRemoteDirectory(path: String): String {
        val trimmed = path.trim()

        return when {
            trimmed.isBlank() -> "/"
            trimmed == "/" -> "/"
            trimmed.startsWith("/") -> trimmed.removeSuffix("/").ifBlank { "/" }
            else -> "/${trimmed.removeSuffix("/")}"
        }
    }

    private fun detectMimeType(fileName: String): String {
        val extension = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        val mime = runCatching { MimeTypeMap.getSingleton()?.getMimeTypeFromExtension(extension) }.getOrNull()

        return mime ?: when (extension) {
            "cr2", "cr3", "nef", "arw", "dng", "raf", "orf", "rw2" -> "image/x-raw"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "heic", "heif" -> "image/heif"
            "mp4" -> "video/mp4"
            "mov" -> "video/quicktime"
            else -> "application/octet-stream"
        }
    }

    private fun detectFileType(mimeType: String): String {
        return when {
            mimeType.startsWith("image/") -> "IMAGE"
            mimeType.startsWith("video/") -> "VIDEO"
            else -> "FILE"
        }
    }

    private data class SourceMetadata(
        val fileName: String?,
        val fileSizeBytes: Long,
        val mimeType: String?
    )

    private fun createThumbnail(sourceUriStr: String, itemId: String, fingerprint: String = ""): String? {
        try {
            val sourceUri = Uri.parse(sourceUriStr)
            val thumbDir = File(context.getExternalFilesDir(null), "camera_thumbnails")
            if (!thumbDir.exists()) thumbDir.mkdirs()
            val thumbFile = File(thumbDir, "thumb_$itemId.jpg")
            val thumbStorage = com.rvmeur.transferin.data.thumbnail.ThumbnailStorageManager(context)

            val lower = sourceUriStr.lowercase()
            if (lower.endsWith(".raf")) {
                val file = if (sourceUri.scheme == "file") {
                    File(sourceUri.path.orEmpty())
                } else if (!sourceUriStr.startsWith("content://")) {
                    File(sourceUriStr)
                } else null

                val rafJpegBytes = if (file != null && file.exists()) {
                    com.rvmeur.transferin.data.usb.vendor.fuji.RafPreviewExtractor.extractEmbeddedJpeg(file).getOrNull()
                } else {
                    context.contentResolver.openInputStream(sourceUri)?.use { input ->
                        val bytes = input.readBytes()
                        com.rvmeur.transferin.data.usb.vendor.fuji.RafPreviewExtractor.extractEmbeddedJpeg(bytes).getOrNull()
                    }
                }

                if (rafJpegBytes != null && rafJpegBytes.isNotEmpty()) {
                    java.io.FileOutputStream(thumbFile).use { fos ->
                        fos.write(rafJpegBytes)
                        fos.flush()
                    }
                    thumbStorage.saveThumbnail(itemId, rafJpegBytes)
                    if (fingerprint.isNotBlank()) {
                        thumbStorage.saveThumbnail(fingerprint, rafJpegBytes)
                    }
                    android.util.Log.d("UploadQueueManager", "Created Fuji RAF thumbnail for itemId=$itemId at ${thumbFile.absolutePath}")
                    return Uri.fromFile(thumbFile).toString()
                }
            }

            var bitmap: Bitmap? = null
            try {
                if (lower.endsWith(".cr2") || lower.endsWith(".cr3") || lower.endsWith(".nef")) {
                    context.contentResolver.openFileDescriptor(sourceUri, "r")?.use { pfd ->
                        val exifInterface = ExifInterface(pfd.fileDescriptor)
                        val thumbBytes = exifInterface.thumbnailBytes
                        if (thumbBytes != null) {
                            bitmap = BitmapFactory.decodeByteArray(thumbBytes, 0, thumbBytes.size)
                        }
                    }
                }

                if (bitmap == null) {
                    val options = BitmapFactory.Options().apply {
                        inJustDecodeBounds = true
                    }
                    context.contentResolver.openInputStream(sourceUri)?.use { inputStream ->
                        BitmapFactory.decodeStream(inputStream, null, options)
                    }

                    val reqWidth = 400
                    val reqHeight = 400
                    var inSampleSize = 1
                    if (options.outHeight > reqHeight || options.outWidth > reqWidth) {
                        val halfHeight = options.outHeight / 2
                        val halfWidth = options.outWidth / 2
                        while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                            inSampleSize *= 2
                        }
                    }

                    val decodeOptions = BitmapFactory.Options().apply {
                        this.inSampleSize = inSampleSize
                    }
                    context.contentResolver.openInputStream(sourceUri)?.use { decodeStream ->
                        bitmap = BitmapFactory.decodeStream(decodeStream, null, decodeOptions)
                    }
                }

                val finalBitmap = bitmap ?: return null
                java.io.ByteArrayOutputStream().use { baos ->
                    finalBitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos)
                    val jpegData = baos.toByteArray()
                    java.io.FileOutputStream(thumbFile).use { fos ->
                        fos.write(jpegData)
                    }
                    thumbStorage.saveThumbnail(itemId, jpegData)
                    if (fingerprint.isNotBlank()) {
                        thumbStorage.saveThumbnail(fingerprint, jpegData)
                    }
                }
                android.util.Log.d("UploadQueueManager", "Created thumbnail for itemId=$itemId at path=${thumbFile.absolutePath}")
                return Uri.fromFile(thumbFile).toString()
            } finally {
                bitmap?.recycle()
            }
        } catch (e: Exception) {
            android.util.Log.e("UploadQueueManager", "Failed to create thumbnail", e)
            return null
        }
    }
}