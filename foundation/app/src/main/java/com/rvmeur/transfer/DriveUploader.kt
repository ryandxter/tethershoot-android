package com.rvmeur.transfer

import android.content.Context
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import org.json.JSONObject

/**
 * Entri berkas remote di folder Google Drive untuk verifikasi integritas nama dan MD5 checksum.
 */
data class RemoteFileEntry(
    val id: String,
    val name: String,
    val size: Long,
    val md5Checksum: String?
)

/**
 * Indeks in-memory berkas remote pada folder tujuan per sesi.
 * Menghubungkan berkas yang sudah ada di Drive dengan berkas yang baru diunggah/dilewati pada sesi ini.
 */
class SessionFolderIndex(
    val folderId: String
) {
    private val filesByName = mutableMapOf<String, MutableList<RemoteFileEntry>>()
    private val claimedNames = mutableMapOf<String, Long>()

    fun initFromRemote(files: List<RemoteFileEntry>) {
        synchronized(this) {
            filesByName.clear()
            claimedNames.clear()
            for (f in files) {
                filesByName.getOrPut(f.name) { mutableListOf() }.add(f)
            }
        }
    }

    fun findMatchingFile(name: String, localMd5: String?): RemoteFileEntry? {
        synchronized(this) {
            val list = filesByName[name] ?: return null
            if (localMd5.isNullOrEmpty()) return null
            return list.firstOrNull { it.md5Checksum.equals(localMd5, ignoreCase = true) }
        }
    }

    fun hasFileNamed(name: String): Boolean {
        synchronized(this) {
            return filesByName.containsKey(name)
        }
    }

    fun isNameTaken(name: String): Boolean {
        synchronized(this) {
            return claimedNames.containsKey(name) || filesByName.containsKey(name)
        }
    }

    fun tryClaimName(name: String, ownerItemId: Long): Boolean {
        synchronized(this) {
            if (filesByName.containsKey(name)) return false
            val currentOwner = claimedNames[name]
            return if (currentOwner == null) {
                claimedNames[name] = ownerItemId
                true
            } else {
                currentOwner == ownerItemId
            }
        }
    }

    fun recordCompleted(name: String, fileId: String, size: Long, md5Checksum: String?) {
        synchronized(this) {
            val entry = RemoteFileEntry(
                id = fileId,
                name = name,
                size = size,
                md5Checksum = md5Checksum?.lowercase(Locale.US)
            )
            filesByName.getOrPut(name) { mutableListOf() }.add(entry)
        }
    }
}

/**
 * Pengunggah berkas ke Google Drive menggunakan Google Drive v3 Resumable Upload
 * berbasis framework murni Android (HttpURLConnection + org.json, tanpa library pihak ketiga).
 *
 * Mendukung:
 * - Streaming potongan 8.388.608 byte (8 MB) langsung dari FileInputStream tanpa memuat ke RAM.
 * - Unggah langsung ke folder tujuan terkunci dari sessions.driveFolderId (tanpa subfolder sesi).
 * - Query-status resume via Content-Range: bytes * / total.
 * - Toleransi gangguan jaringan (IOException / 5xx) maks 5 siklus dengan backoff eksponensial (2, 4, 8, 16, 32 dtk).
 * - Verifikasi ukuran byte-exact dan MD5 checksum terhadap respons Google Drive.
 * - Lapisan integritas nama berkas Stage 3 (indeks berkas remote, deteksi duplikasi MD5 LEWATI, ganti nama deterministik cap waktu).
 */
class DriveUploader(
    private val context: Context,
    private val driveAuth: DriveAuth,
    private val db: TransferInDb
) {

    companion object {
        const val CHUNK_SIZE = 8388608 // 8.388.608 byte (8 MB, kelipatan 256 KB)
        private const val DRIVE_API_FILES = "https://www.googleapis.com/drive/v3/files"
        private const val DRIVE_UPLOAD_RESUMABLE = "https://www.googleapis.com/upload/drive/v3/files?uploadType=resumable"
        private const val FOLDER_MIME_TYPE = "application/vnd.google-apps.folder"
    }

    enum class UploadResult {
        SUCCESS,
        AUTH_ERROR,
        RETRYABLE_ERROR,
        FAILED_FINAL,
        CANCELLED
    }

    @Volatile
    var isCancelled: () -> Boolean = { false }

    // Cache indeks berkas remote folder tujuan per sessionId di memori
    private val sessionIndexCache = mutableMapOf<String, SessionFolderIndex>()

    fun clearSessionIndex(sessionId: String) {
        synchronized(sessionIndexCache) {
            sessionIndexCache.remove(sessionId)
        }
    }

    /* =========================================================================
     * Indeks Isi Folder Tujuan (Lapisan Integritas Nama — Stage 3)
     * ========================================================================= */

    private fun getOrFetchSessionIndex(sessionId: String, folderId: String, token: String): SessionFolderIndex? {
        sessionIndexCache[sessionId]?.let { return it }
        synchronized(sessionIndexCache) {
            sessionIndexCache[sessionId]?.let { return it }
            val fetched = fetchSessionIndex(sessionId, folderId, token)
            if (fetched != null) {
                sessionIndexCache[sessionId] = fetched
            }
            return fetched
        }
    }

    private fun fetchSessionIndex(sessionId: String, folderId: String, token: String): SessionFolderIndex? {
        val entries = mutableListOf<RemoteFileEntry>()
        var pageToken: String? = null
        try {
            do {
                val q = "'$folderId' in parents and trashed = false and mimeType != '$FOLDER_MIME_TYPE'"
                val fields = "files(id,name,size,md5Checksum),nextPageToken"
                val urlStr = StringBuilder(DRIVE_API_FILES)
                    .append("?q=").append(URLEncoder.encode(q, "UTF-8"))
                    .append("&fields=").append(URLEncoder.encode(fields, "UTF-8"))
                    .append("&pageSize=1000")
                if (!pageToken.isNullOrEmpty()) {
                    urlStr.append("&pageToken=").append(URLEncoder.encode(pageToken, "UTF-8"))
                }
                val conn = (URL(urlStr.toString()).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 30_000
                    readTimeout = 30_000
                    setRequestProperty("Authorization", "Bearer $token")
                }
                try {
                    val code = conn.responseCode
                    if (code !in 200..299) {
                        FoundationLog.error("[INTEGRITAS] Gagal membaca isi folder tujuan (HTTP $code) — unggah ditunda")
                        return null
                    }
                    val json = JSONObject(readStreamSafely(conn.inputStream))
                    val files = json.optJSONArray("files")
                    if (files != null) {
                        for (i in 0 until files.length()) {
                            val f = files.optJSONObject(i) ?: continue
                            val name = f.optString("name", "")
                            if (name.isEmpty()) continue
                            entries.add(
                                RemoteFileEntry(
                                    id = f.optString("id", ""),
                                    name = name,
                                    size = f.optLong("size", 0L),
                                    md5Checksum = f.optString("md5Checksum", "").ifEmpty { null }?.lowercase(Locale.US)
                                )
                            )
                        }
                    }
                    pageToken = json.optString("nextPageToken", "").ifEmpty { null }
                } finally {
                    try { conn.disconnect() } catch (_: Exception) {}
                }
            } while (!pageToken.isNullOrEmpty())
        } catch (t: Throwable) {
            FoundationLog.error("[INTEGRITAS] Eksepsi membaca isi folder tujuan — unggah ditunda", t)
            return null
        }
        val idx = SessionFolderIndex(folderId)
        idx.initFromRemote(entries)
        FoundationLog.log("[INTEGRITAS] Indeks folder tujuan sesi $sessionId dimuat: ${entries.size} berkas")
        return idx
    }

    private fun recordIntoSessionIndex(sessionId: String, name: String, fileId: String, size: Long, md5: String?) {
        val idx = synchronized(sessionIndexCache) { sessionIndexCache[sessionId] } ?: return
        idx.recordCompleted(name, fileId, size, md5)
    }

    private fun buildTimestampedName(item: TransferItem, baseName: String = item.filename): String {
        val cd = item.captureDate ?: ""
        val tIdx = cd.indexOf('T')
        val datePart = if (tIdx > 0) cd.substring(0, tIdx) else ""
        val timePart = if (tIdx >= 0 && cd.length > tIdx + 1) cd.substring(tIdx + 1) else ""
        val suffix = if (datePart.length == 8 && timePart.length == 6) "${datePart}_${timePart}" else item.sessionId
        val dot = baseName.lastIndexOf('.')
        return if (dot > 0) {
            baseName.substring(0, dot) + "_" + suffix + baseName.substring(dot)
        } else {
            baseName + "_" + suffix
        }
    }

    private fun isItemDiscarded(item: TransferItem): Boolean {
        val fresh = db.getItem(item.id)
        return fresh?.status == ItemStatus.DISCARDED
    }

    /**
     * Mengunggah satu [TransferItem] ke folder sesi Google Drive.
     * Mengelola inisiasi, chunk streaming, resume gangguan, dan verifikasi akhir.
     */
    fun upload(item: TransferItem): UploadResult {
        if (isItemDiscarded(item)) {
            FoundationLog.log("[UNGGAH] Dibatalkan: item telah dibuang pengguna")
            return UploadResult.CANCELLED
        }

        if (item.sizeBytes <= 0L) {
            FoundationLog.error("[UNGGAH] Penolakan item: ukuran 0 byte (${item.filename})")
            db.updateItem(item.id, status = ItemStatus.FAILED_FINAL, lastError = "Ukuran berkas 0 byte")
            return UploadResult.FAILED_FINAL
        }

        // 1. Ambil folder tujuan sesi dari basis data (Stage 3: unggah langsung tanpa subfolder)
        val sessionRecord = db.getSession(item.sessionId)
        val folderId = if (sessionRecord != null && !sessionRecord.driveFolderId.isNullOrEmpty()) {
            sessionRecord.driveFolderId
        } else {
            FoundationLog.log("[UNGGAH] Peringatan: driveFolderId sesi ${item.sessionId} kosong — fallback ke root")
            "root"
        }

        // Cek jika sudah pernah diverifikasi (jalan pintas sidik jari di atas cek berkas lokal)
        val fpFileId = db.findFingerprint(item.identity, folderId)
        if (item.status == ItemStatus.DONE || fpFileId != null) {
            if (item.status != ItemStatus.DONE) {
                val marked = db.tryMarkDone(item.id, fpFileId ?: item.driveFileId ?: "")
                if (!marked) {
                    FoundationLog.log("[UNGGAH] Item ${item.filename} berubah status saat penyelamatan sidik jari — dibatalkan")
                    return UploadResult.CANCELLED
                }
            }
            FoundationLog.log("[UNGGAH] Item ${item.filename} sudah pernah selesai dan terverifikasi sebelumnya")
            return UploadResult.SUCCESS
        }

        val localFile = File(item.localPath)
        if (!localFile.exists()) {
            if (isItemDiscarded(item)) {
                FoundationLog.log("[UNGGAH] Dibatalkan: item telah dibuang pengguna")
                return UploadResult.CANCELLED
            }
            val err = "Berkas lokal tidak ditemukan: ${item.localPath}"
            FoundationLog.error("[UNGGAH] $err")
            db.updateItem(item.id, status = ItemStatus.FAILED_FINAL, lastError = err)
            return UploadResult.FAILED_FINAL
        }

        // Cek jika sudah memiliki driveFileId dan hanya perlu verifikasi ulang
        if (!item.driveFileId.isNullOrEmpty()) {
            return verifyDriveFile(item, item.driveFileId, folderId)
        }

        var token = driveAuth.getAccessToken()
        if (token == null) {
            FoundationLog.log("[UNGGAH] Token akses tidak tersedia saat memulai unggah ${item.filename}")
            return UploadResult.AUTH_ERROR
        }

        // 1b. Lapisan integritas nama (Stage 3): nama final diputuskan SEBELUM inisiasi.
        // Item yang sesi resumable-nya sudah berjalan melewati tahap ini — namanya
        // sudah terkunci di sesi server dan tercatat di driveName.
        var effectiveName = item.driveName ?: item.filename
        if (item.uploadSessionUri.isNullOrEmpty()) {
            val idx = getOrFetchSessionIndex(item.sessionId, folderId, token)
            if (idx == null) {
                FoundationLog.error("[INTEGRITAS] Indeks folder tujuan tidak tersedia untuk ${item.filename} — unggah ditunda tanpa menebak")
                return UploadResult.RETRYABLE_ERROR
            }
            // Nama yang DIMAKSUD adalah driveName (skema nama aplikasi);
            // filename kamera hanya identitas forensik. Lapisan ini dulu
            // mereset nama efektif ke filename sehingga driveName tidak pernah
            // sampai ke Drive — akar nama Drive selalu nama kamera.
            val intendedName = item.driveName ?: item.filename
            val existing = idx.findMatchingFile(intendedName, item.md5)
            if (existing != null && existing.id.isNotEmpty()) {
                val ok = db.tryMarkDone(item.id, existing.id, intendedName, item.sizeBytes)
                if (ok) {
                    db.insertFingerprint(item.identity, existing.id, folderId)
                    FoundationLog.log("[INTEGRITAS] LEWATI $intendedName — isi identik (md5 sama) sudah ada di folder tujuan (id=${existing.id})")
                    return UploadResult.SUCCESS
                } else {
                    FoundationLog.log("[UNGGAH] LEWATI dibatalkan — item ${item.filename} sudah dibuang pengguna")
                    return UploadResult.CANCELLED
                }
            }

            val tsCandidate = buildTimestampedName(item, intendedName)
            val dot = tsCandidate.lastIndexOf('.')
            val cBase = if (dot > 0) tsCandidate.substring(0, dot) else tsCandidate
            val cExt = if (dot > 0) tsCandidate.substring(dot) else ""

            var candidate = intendedName
            var step = 0
            var n = 2
            while (true) {
                val matched = idx.findMatchingFile(candidate, item.md5)
                if (matched != null && matched.id.isNotEmpty()) {
                    val ok = db.tryMarkDone(item.id, matched.id, candidate, item.sizeBytes)
                    if (ok) {
                        db.insertFingerprint(item.identity, matched.id, folderId)
                        FoundationLog.log("[INTEGRITAS] LEWATI $candidate — isi identik (md5 sama) sudah ada di folder tujuan (id=${matched.id})")
                        return UploadResult.SUCCESS
                    } else {
                        FoundationLog.log("[UNGGAH] LEWATI dibatalkan — item ${item.filename} sudah dibuang pengguna")
                        return UploadResult.CANCELLED
                    }
                }
                if (idx.tryClaimName(candidate, item.id)) {
                    effectiveName = candidate
                    break
                }
                candidate = if (step == 0) {
                    step = 1
                    tsCandidate
                } else {
                    val next = "${cBase}_${n}${cExt}"
                    n++
                    next
                }
            }
            if (effectiveName != intendedName) {
                FoundationLog.log("[INTEGRITAS] GANTI-NAMA $intendedName -> $effectiveName (nama bentrok, isi beda)")
                db.updateItem(item.id, driveName = effectiveName)
            }
        }

        // 2. Siapkan Session URI (Inisiasi baru atau pakai yang tersimpan di DB)
        var sessionUri = item.uploadSessionUri
        var currentOffset = item.bytesUploaded
        var driveFileId: String? = item.driveFileId

        var disturbanceCycle = item.attemptCount

        while (driveFileId == null) {
            if (isItemDiscarded(item)) {
                FoundationLog.log("[UNGGAH] Dibatalkan: item telah dibuang pengguna")
                return UploadResult.CANCELLED
            }

            if (isCancelled()) {
                return UploadResult.RETRYABLE_ERROR
            }

            // Pastikan token masih valid
            token = driveAuth.getAccessToken()
            if (token == null) {
                FoundationLog.log("[UNGGAH] Token kedaluwarsa/hilang di tengah unggah ${item.filename}")
                return UploadResult.AUTH_ERROR
            }

            // Bila sessionUri tersimpan tidak kosong, query status pada setiap (re)mulai streaming
            if (!sessionUri.isNullOrEmpty()) {
                val statusQuery = queryUploadStatus(sessionUri, item.sizeBytes)
                when (statusQuery) {
                    is QueryStatusResult.Incomplete -> {
                        val serverOffset = statusQuery.nextOffset
                        if (serverOffset != currentOffset) {
                            FoundationLog.log("[UNGGAH] Resume status server untuk ${item.filename}: offset diselaraskan $currentOffset -> $serverOffset/${item.sizeBytes}")
                            currentOffset = serverOffset
                            db.updateItem(item.id, bytesUploaded = serverOffset)
                        } else {
                            FoundationLog.log("[UNGGAH] Resume status server untuk ${item.filename}: offset $currentOffset/${item.sizeBytes}")
                        }
                    }
                    is QueryStatusResult.Complete -> {
                        driveFileId = statusQuery.fileId
                        db.updateItem(item.id, bytesUploaded = item.sizeBytes, driveFileId = driveFileId)
                        FoundationLog.log("[UNGGAH] Server melaporkan unggah ${item.filename} sudah tuntas (id=$driveFileId)")
                        break
                    }
                    is QueryStatusResult.ExpiredOrInvalid -> {
                        FoundationLog.log("[UNGGAH] SESI-RESUME-MATI ${item.filename} — sesi resumable lama dibuang, mulai dari nol")
                        sessionUri = null
                        currentOffset = 0L
                        db.updateItem(item.id, uploadSessionUri = "", bytesUploaded = 0L)
                    }
                    is QueryStatusResult.NetworkError -> {
                        if (isItemDiscarded(item)) {
                            FoundationLog.log("[UNGGAH] Dibatalkan: item telah dibuang pengguna")
                            return UploadResult.CANCELLED
                        }
                        disturbanceCycle++
                        val shouldContinue = handleDisturbance(item, disturbanceCycle, statusQuery.errorMsg)
                        if (!shouldContinue) return UploadResult.FAILED_FINAL
                        continue
                    }
                }
            }

            // Inisiasi resumable upload jika belum ada sessionUri (atau baru dibuang karena sesi mati)
            if (sessionUri.isNullOrEmpty()) {
                val initResult = initiateResumableUpload(item, folderId, token, effectiveName)
                if (initResult == null) {
                    if (isItemDiscarded(item)) {
                        FoundationLog.log("[UNGGAH] Dibatalkan: item telah dibuang pengguna")
                        return UploadResult.CANCELLED
                    }
                    disturbanceCycle++
                    val shouldContinue = handleDisturbance(item, disturbanceCycle, "Gagal inisiasi Resumable Upload")
                    if (!shouldContinue) return UploadResult.FAILED_FINAL
                    continue
                }
                sessionUri = initResult
                currentOffset = 0L
                db.updateItem(item.id, uploadSessionUri = sessionUri, bytesUploaded = 0L)
                FoundationLog.log("[UNGGAH] Sesi resumable diinisiasi untuk ${item.filename}")
            }

            // 3. Streaming potongan berkas ke Google Drive
            val chunkResult = streamChunks(item, localFile, sessionUri, currentOffset)
            when (chunkResult) {
                is ChunkStreamResult.Cancelled -> {
                    FoundationLog.log("[UNGGAH] Dibatalkan: item telah dibuang pengguna")
                    return UploadResult.CANCELLED
                }
                is ChunkStreamResult.Success -> {
                    driveFileId = chunkResult.fileId
                    db.updateItem(item.id, bytesUploaded = item.sizeBytes, driveFileId = driveFileId)
                    FoundationLog.log("[UNGGAH] Seluruh potongan ${item.filename} terkirim (id=$driveFileId)")
                    break
                }
                is ChunkStreamResult.AuthError -> {
                    val savedOffset = db.getItem(item.id)?.bytesUploaded ?: currentOffset
                    db.updateItem(item.id, uploadSessionUri = sessionUri, bytesUploaded = savedOffset)
                    FoundationLog.log("[UNGGAH] Token ditolak (HTTP 401) saat streaming ${item.filename} — progres disimpan pada offset $savedOffset")
                    return UploadResult.AUTH_ERROR
                }
                is ChunkStreamResult.SessionExpired -> {
                    if (isItemDiscarded(item)) {
                        FoundationLog.log("[UNGGAH] Dibatalkan: item telah dibuang pengguna")
                        return UploadResult.CANCELLED
                    }
                    FoundationLog.log("[UNGGAH] SESI-RESUME-MATI ${item.filename} — sesi resumable lama dibuang, mulai dari nol")
                    sessionUri = null
                    currentOffset = 0L
                    db.updateItem(item.id, uploadSessionUri = "", bytesUploaded = 0L)
                    disturbanceCycle++
                    val shouldContinue = handleDisturbance(item, disturbanceCycle, "Sesi resumable kedaluwarsa (HTTP 404/410)")
                    if (!shouldContinue) return UploadResult.FAILED_FINAL
                }
                is ChunkStreamResult.Disturbance -> {
                    if (isItemDiscarded(item)) {
                        FoundationLog.log("[UNGGAH] Dibatalkan: item telah dibuang pengguna")
                        return UploadResult.CANCELLED
                    }
                    disturbanceCycle++
                    currentOffset = chunkResult.lastSuccessfulOffset
                    db.updateItem(item.id, bytesUploaded = currentOffset)

                    val shouldContinue = handleDisturbance(item, disturbanceCycle, chunkResult.errorMsg)
                    if (!shouldContinue) return UploadResult.FAILED_FINAL
                }
                is ChunkStreamResult.FatalError -> {
                    db.updateItem(item.id, status = ItemStatus.FAILED_FINAL, lastError = chunkResult.errorMsg)
                    FoundationLog.error("[UNGGAH] Kesalahan fatal saat unggah ${item.filename}: ${chunkResult.errorMsg}")
                    return UploadResult.FAILED_FINAL
                }
            }
        }

        // 4. Verifikasi berkas di Google Drive
        // Penjaga kompilasi + semantik (arbitrase asisten): loop unggah yang berakhir
        // tanpa id berkas Drive berarti unggahan belum benar-benar selesai di sisi
        // server — perlakukan sebagai gangguan yang bisa dicoba lagi, JANGAN verifikasi.
        val completedFileId = driveFileId
        if (completedFileId == null) {
            FoundationLog.error("[UNGGAH] Unggahan ${item.filename} berakhir tanpa id berkas Drive — akan dicoba lagi")
            return UploadResult.RETRYABLE_ERROR
        }
        val verifyResult = verifyDriveFile(item, completedFileId, folderId)
        if (verifyResult == UploadResult.SUCCESS) {
            recordIntoSessionIndex(item.sessionId, effectiveName, completedFileId, item.sizeBytes, item.md5)
        }
        return verifyResult
    }

    /* =========================================================================
     * Inisiasi Resumable Upload
     * ========================================================================= */

    private fun initiateResumableUpload(item: TransferItem, folderId: String, token: String, uploadName: String = item.filename): String? {
        var conn: HttpURLConnection? = null
        try {
            val url = URL(DRIVE_UPLOAD_RESUMABLE)
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 30_000
                readTimeout = 30_000
                doOutput = true
                instanceFollowRedirects = false
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                setRequestProperty("X-Upload-Content-Length", item.sizeBytes.toString())
                setRequestProperty("X-Upload-Content-Type", resolveMimeType(item.filename))
            }

            val metadataJson = JSONObject().apply {
                put("name", uploadName)
                put("parents", org.json.JSONArray().put(folderId))
            }

            val writer = OutputStreamWriter(conn.outputStream, Charsets.UTF_8)
            writer.write(metadataJson.toString())
            writer.flush()
            writer.close()

            val code = conn.responseCode
            if (code in 200..299) {
                val location = conn.getHeaderField("Location")
                if (!location.isNullOrEmpty()) {
                    return location
                } else {
                    FoundationLog.error("[UNGGAH] Header Location kosong pada inisiasi resumable (HTTP $code)")
                }
            } else {
                val errorBody = readStreamSafely(conn.errorStream)
                FoundationLog.error("[UNGGAH] Gagal inisiasi resumable upload: HTTP $code ($errorBody)")
            }
            return null
        } catch (t: Throwable) {
            FoundationLog.error("[UNGGAH] Eksepsi saat inisiasi resumable upload ${item.filename}", t)
            return null
        } finally {
            try { conn?.disconnect() } catch (_: Exception) {}
        }
    }

    /* =========================================================================
     * Streaming Potongan Berkas (Chunk Upload)
     * ========================================================================= */

    private sealed class ChunkStreamResult {
        data class Success(val fileId: String) : ChunkStreamResult()
        data class Disturbance(val lastSuccessfulOffset: Long, val errorMsg: String) : ChunkStreamResult()
        data class FatalError(val errorMsg: String) : ChunkStreamResult()
        object AuthError : ChunkStreamResult()
        object SessionExpired : ChunkStreamResult()
        object Cancelled : ChunkStreamResult()
    }

    private fun streamChunks(
        item: TransferItem,
        localFile: File,
        sessionUri: String,
        initialOffset: Long
    ): ChunkStreamResult {
        var offset = initialOffset
        val totalBytes = item.sizeBytes
        val mimeType = resolveMimeType(item.filename)

        while (offset < totalBytes) {
            if (isItemDiscarded(item)) {
                return ChunkStreamResult.Cancelled
            }
            if (isCancelled()) {
                return ChunkStreamResult.Disturbance(offset, "Operasi dibatalkan")
            }

            val chunkLenLong = minOf(CHUNK_SIZE.toLong(), totalBytes - offset)
            val chunkLen = chunkLenLong.toInt()
            val chunkEnd = offset + chunkLen - 1

            var conn: HttpURLConnection? = null
            try {
                val url = URL(sessionUri)
                conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "PUT"
                    connectTimeout = 45_000
                    readTimeout = 45_000
                    doOutput = true
                    setFixedLengthStreamingMode(chunkLen)
                    setRequestProperty("Content-Type", mimeType)
                    setRequestProperty("Content-Length", chunkLen.toString())
                    setRequestProperty("Content-Range", "bytes $offset-$chunkEnd/$totalBytes")
                }

                // Streaming dari berkas tanpa memuat ke RAM
                try {
                    FileInputStream(localFile).use { fis ->
                        var skipped = 0L
                        while (skipped < offset) {
                            val s = fis.skip(offset - skipped)
                            if (s <= 0) break
                            skipped += s
                        }

                        val os = conn.outputStream
                        val buf = ByteArray(16384)
                        var bytesRemaining = chunkLen
                        while (bytesRemaining > 0) {
                            val toRead = minOf(buf.size, bytesRemaining)
                            val r = fis.read(buf, 0, toRead)
                            if (r < 0) {
                                return ChunkStreamResult.FatalError("Akhir berkas lokal tercapai sebelum chunk tuntas: sisa $bytesRemaining B")
                            }
                            os.write(buf, 0, r)
                            bytesRemaining -= r
                        }
                        os.flush()
                    }
                } catch (fnf: java.io.FileNotFoundException) {
                    if (isItemDiscarded(item)) {
                        return ChunkStreamResult.Cancelled
                    }
                    return ChunkStreamResult.FatalError("Berkas lokal tidak ditemukan saat streaming: ${fnf.message}")
                }

                val code = conn.responseCode
                when (code) {
                    308 -> {
                        // Respons Resume Incomplete -> baca Range header
                        val rangeHeader = conn.getHeaderField("Range")
                        val serverEnd = parseRangeEnd(rangeHeader)
                        val nextOffset = if (serverEnd >= 0L) {
                            val ackOffset = serverEnd + 1L
                            FoundationLog.log("[UNGGAH] Potongan ${item.filename} diakui server (Range: $rangeHeader): offset $offset -> $ackOffset")
                            ackOffset
                        } else {
                            chunkEnd + 1L
                        }
                        offset = nextOffset
                        db.updateItem(item.id, bytesUploaded = offset)
                    }
                    200, 201 -> {
                        // Selesai seluruhnya
                        val body = readStreamSafely(conn.inputStream)
                        val json = JSONObject(body)
                        val fileId = json.optString("id", "")
                        if (fileId.isNotEmpty()) {
                            return ChunkStreamResult.Success(fileId)
                        } else {
                            return ChunkStreamResult.FatalError("Respons HTTP $code selesai namun ID berkas kosong")
                        }
                    }
                    401 -> {
                        val errBody = readStreamSafely(conn.errorStream)
                        db.updateItem(item.id, bytesUploaded = offset)
                        FoundationLog.error("[UNGGAH] HTTP 401 Unauthorized saat streaming potongan ${item.filename} ($errBody)")
                        return ChunkStreamResult.AuthError
                    }
                    403, 429 -> {
                        val errBody = readStreamSafely(conn.errorStream)
                        return ChunkStreamResult.Disturbance(offset, "Batas laju/akses sementara HTTP $code ($errBody)")
                    }
                    404, 410 -> {
                        val errBody = readStreamSafely(conn.errorStream)
                        FoundationLog.log("[UNGGAH] Sesi resumable kedaluwarsa saat streaming potongan ${item.filename}: HTTP $code ($errBody)")
                        return ChunkStreamResult.SessionExpired
                    }
                    else -> {
                        val errBody = readStreamSafely(conn.errorStream)
                        if (code in 500..599) {
                            return ChunkStreamResult.Disturbance(offset, "Server Google HTTP $code ($errBody)")
                        } else {
                            return ChunkStreamResult.FatalError("Google Drive mengembalikan HTTP $code ($errBody)")
                        }
                    }
                }
            } catch (fnf: java.io.FileNotFoundException) {
                if (isItemDiscarded(item)) {
                    return ChunkStreamResult.Cancelled
                }
                return ChunkStreamResult.FatalError("Berkas lokal tidak ditemukan: ${fnf.message}")
            } catch (t: Throwable) {
                if (isItemDiscarded(item)) {
                    return ChunkStreamResult.Cancelled
                }
                return ChunkStreamResult.Disturbance(offset, "Gangguan koneksi: ${t.javaClass.simpleName} - ${t.message}")
            } finally {
                try { conn?.disconnect() } catch (_: Exception) {}
            }
        }

        // Jika sampai di sini tetapi tidak ada id (misal offset == totalBytes), query status
        return ChunkStreamResult.Disturbance(offset, "Menunggu konfirmasi selesai dari server")
    }

    /* =========================================================================
     * Query Status Resumable Upload
     * ========================================================================= */

    private sealed class QueryStatusResult {
        data class Incomplete(val nextOffset: Long) : QueryStatusResult()
        data class Complete(val fileId: String) : QueryStatusResult()
        object ExpiredOrInvalid : QueryStatusResult()
        data class NetworkError(val errorMsg: String) : QueryStatusResult()
    }

    private fun queryUploadStatus(sessionUri: String, totalBytes: Long): QueryStatusResult {
        var conn: HttpURLConnection? = null
        try {
            val url = URL(sessionUri)
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "PUT"
                connectTimeout = 30_000
                readTimeout = 30_000
                doOutput = true
                setFixedLengthStreamingMode(0)
                setRequestProperty("Content-Range", "bytes */$totalBytes")
            }

            val code = conn.responseCode
            when (code) {
                308 -> {
                    val rangeHeader = conn.getHeaderField("Range")
                    val serverEnd = parseRangeEnd(rangeHeader)
                    val nextOffset = if (serverEnd >= 0L) serverEnd + 1L else 0L
                    return QueryStatusResult.Incomplete(nextOffset)
                }
                200, 201 -> {
                    val body = readStreamSafely(conn.inputStream)
                    val json = JSONObject(body)
                    val fileId = json.optString("id", "")
                    if (fileId.isNotEmpty()) {
                        return QueryStatusResult.Complete(fileId)
                    } else {
                        return QueryStatusResult.NetworkError("Respons query status $code tanpa file ID")
                    }
                }
                404, 410 -> {
                    return QueryStatusResult.ExpiredOrInvalid
                }
                else -> {
                    val err = readStreamSafely(conn.errorStream)
                    return QueryStatusResult.NetworkError("Query status HTTP $code: $err")
                }
            }
        } catch (t: Throwable) {
            return QueryStatusResult.NetworkError("Eksepsi query status: ${t.message}")
        } finally {
            try { conn?.disconnect() } catch (_: Exception) {}
        }
    }

    /* =========================================================================
     * Verifikasi Ukuran & MD5 Checksum
     * ========================================================================= */

    private fun verifyDriveFile(item: TransferItem, driveFileId: String, folderId: String): UploadResult {
        if (!db.tryTransitionStatus(item.id, ItemStatus.VERIFYING, listOf(ItemStatus.UPLOADING, ItemStatus.VERIFYING), driveFileId)) {
            FoundationLog.log("[UNGGAH] Verifikasi dibatalkan — item ${item.filename} sudah dibuang pengguna")
            return UploadResult.CANCELLED
        }

        val token = driveAuth.getAccessToken()
        if (token == null) {
            FoundationLog.log("[UNGGAH] Token akses null saat verifikasi ${item.filename}")
            return UploadResult.AUTH_ERROR
        }

        var conn: HttpURLConnection? = null
        try {
            val verifyUrl = "$DRIVE_API_FILES/$driveFileId?fields=" + URLEncoder.encode("id,size,md5Checksum", "UTF-8")
            val url = URL(verifyUrl)
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 30_000
                readTimeout = 30_000
                setRequestProperty("Authorization", "Bearer $token")
            }

            val code = conn.responseCode
            if (code !in 200..299) {
                val err = readStreamSafely(conn.errorStream)
                FoundationLog.error("[UNGGAH] Gagal membaca metadata Drive untuk verifikasi: HTTP $code ($err)")
                return UploadResult.RETRYABLE_ERROR
            }

            val body = readStreamSafely(conn.inputStream)
            val json = JSONObject(body)

            val remoteSize = json.optLong("size", -1L)
            val remoteMd5 = json.optString("md5Checksum", "").lowercase(Locale.US)
            val localMd5 = (item.md5 ?: "").lowercase(Locale.US)

            val sizeMatches = (remoteSize == item.sizeBytes)
            val md5Matches = (remoteMd5.isNotEmpty() && remoteMd5 == localMd5)

            if (sizeMatches && md5Matches) {
                if (isItemDiscarded(item)) {
                    FoundationLog.log("[UNGGAH] Dibatalkan saat verifikasi akhir: item ${item.filename} telah dibuang pengguna")
                    return UploadResult.CANCELLED
                }
                db.updateItem(item.id, bytesUploaded = item.sizeBytes)
                if (!db.tryMarkDone(item.id, driveFileId)) {
                    FoundationLog.log("[UNGGAH] Item ${item.filename} berubah status saat penulisan DONE — dibatalkan")
                    return UploadResult.CANCELLED
                }
                db.insertFingerprint(item.identity, driveFileId, folderId)
                FoundationLog.log("[UNGGAH] SUKSES TERVERIFIKASI: ${item.filename} (id=$driveFileId, size=$remoteSize B, md5=$remoteMd5)")
                return UploadResult.SUCCESS
            } else {
                val failureReason = "VERIFIKASI-GAGAL: size $remoteSize vs ${item.sizeBytes}, md5 $remoteMd5 vs $localMd5"
                db.updateItem(item.id, status = ItemStatus.FAILED_FINAL, lastError = failureReason)
                FoundationLog.error("[UNGGAH] VERIFIKASI-GAGAL keras: ${item.filename} ($failureReason)")
                return UploadResult.FAILED_FINAL
            }
        } catch (t: Throwable) {
            FoundationLog.error("[UNGGAH] Eksepsi saat verifikasi ${item.filename}", t)
            return UploadResult.RETRYABLE_ERROR
        } finally {
            try { conn?.disconnect() } catch (_: Exception) {}
        }
    }

    /* =========================================================================
     * Penanganan Gangguan & Backoff Eksponensial
     * ========================================================================= */

    /**
     * Menangani gangguan jaringan dengan backoff 2, 4, 8, 16, 32 detik.
     * Mengembalikan false bila telah melampaui batas maks 5 siklus (FAILED_FINAL).
     */
    private fun handleDisturbance(item: TransferItem, cycle: Int, errorMsg: String): Boolean {
        if (isItemDiscarded(item)) {
            return false
        }
        db.updateItem(item.id, attemptCount = cycle, lastError = errorMsg)

        if (cycle > 5) {
            val finalErr = "Melebihi batas 5 siklus gangguan: $errorMsg"
            db.updateItem(item.id, status = ItemStatus.FAILED_FINAL, lastError = finalErr)
            FoundationLog.error("[UNGGAH] Item ${item.filename} FAILED_FINAL ($finalErr)")
            return false
        }

        // Backoff: 1->2s, 2->4s, 3->8s, 4->16s, 5->32s
        val backoffSeconds = 1 shl cycle
        FoundationLog.log("[UNGGAH] Gangguan pada ${item.filename} (siklus $cycle/5: $errorMsg). Menunggu ${backoffSeconds}s sebelum melanjutkan...")

        sleepSlices(backoffSeconds * 1000L)
        return true
    }

    private fun sleepSlices(totalMs: Long) {
        val slice = 100L
        var elapsed = 0L
        while (!isCancelled() && elapsed < totalMs) {
            try {
                Thread.sleep(slice)
            } catch (_: InterruptedException) {
                break
            }
            elapsed += slice
        }
    }

    /* =========================================================================
     * Utilitas Pembantu
     * ========================================================================= */

    private fun resolveMimeType(filename: String): String {
        val lower = filename.lowercase(Locale.US)
        return when {
            lower.endsWith(".jpg") || lower.endsWith(".jpeg") -> "image/jpeg"
            lower.endsWith(".raf") -> "image/x-fuji-raf"
            else -> "application/octet-stream"
        }
    }

    private fun parseRangeEnd(rangeHeader: String?): Long {
        if (rangeHeader.isNullOrEmpty()) return -1L
        // Format: bytes=0-1048575
        val idxDash = rangeHeader.lastIndexOf('-')
        if (idxDash != -1 && idxDash < rangeHeader.length - 1) {
            return rangeHeader.substring(idxDash + 1).trim().toLongOrNull() ?: -1L
        }
        return -1L
    }

    private fun readStreamSafely(stream: InputStream?): String {
        if (stream == null) return ""
        return try {
            BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
        } catch (_: Exception) {
            ""
        }
    }
}
