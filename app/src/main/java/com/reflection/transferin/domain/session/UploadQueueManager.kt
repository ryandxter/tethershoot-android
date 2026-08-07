package com.reflection.transferin.domain.session

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.core.net.toUri
import com.reflection.transferin.data.local.entity.TransferQueueItemEntity
import com.reflection.transferin.data.repository.TransferQueueRepositoryImpl
import com.reflection.transferin.data.worker.TransferUploadWorkScheduler
import com.reflection.transferin.domain.model.MediaJob
import java.io.File
import java.util.Locale
import java.util.UUID

class UploadQueueManager(
    private val context: Context,
    private val transferQueueRepository: TransferQueueRepositoryImpl
) {

    private val workScheduler = TransferUploadWorkScheduler(context.applicationContext)

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
        val originFileName = job.originalFileName.takeIf { it.isNotBlank() }

        val fallbackFileName =
            originFileName
                ?: metadata.fileName?.takeIf { it.isNotBlank() }
                ?: File(job.localTempPath).name.takeIf { it.isNotBlank() }
                ?: "unknown_file"

        val fileName = sanitizeFileName(fallbackFileName)
        val mimeType = metadata.mimeType ?: detectMimeType(fileName)
        val fileType = detectFileType(mimeType)
        val fileSizeBytes = metadata.fileSizeBytes.coerceAtLeast(0L)

        val entity = TransferQueueItemEntity(
            id = UUID.randomUUID().toString(),
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
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)

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
}