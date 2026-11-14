package com.rvmeur.transferin

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Representasi rekaman sesi dari tabel `sessions`.
 */
data class SessionRecord(
    val id: String,
    val startedAt: Long,
    val closedAt: Long?,
    val driveFolderId: String?,
    val status: String
)

/**
 * Representasi rekaman item antrean dari tabel `transfer_items`.
 */
data class TransferItem(
    val id: Long,
    val sessionId: String,
    val identity: String,
    val filename: String,
    val captureDate: String?,
    val sizeBytes: Long,
    val md5: String?,
    val localPath: String,
    val groupSecond: String?,
    val status: String,
    val attemptCount: Int,
    val driveFileId: String?,
    val uploadSessionUri: String?,
    val bytesUploaded: Long,
    val lastError: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val driveName: String? = null,
    val culling: String? = null,
    val cullingDetail: String? = null
)

/**
 * Representasi rekaman kredensial dari tabel `credentials`.
 */
data class CredentialsRecord(
    val email: String,
    val refreshTokenEnc: String,
    val updatedAt: Long
)

/**
 * Representasi satu grup foto tertahan pada gerbang culling untuk tinjauan manual (Stage 4).
 */
data class HeldCullingGroup(
    val sessionId: String,
    val groupSecond: String,
    val items: List<TransferItem>
)

/**
 * Status sesi kamera.
 */
object SessionStatus {
    const val OPEN = "OPEN"
    const val CLOSED = "CLOSED"
}

/**
 * Status siklus hidup item antrean unggah ke Google Drive dan gerbang culling.
 */
object ItemStatus {
    const val CULLING_PENDING = "CULLING_PENDING"
    const val CULLING_HOLD = "CULLING_HOLD"
    const val DISCARDED = "DISCARDED"
    const val QUEUED = "QUEUED"
    const val UPLOADING = "UPLOADING"
    const val VERIFYING = "VERIFYING"
    const val DONE = "DONE"
    const val FAILED_FINAL = "FAILED_FINAL"
}

/**
 * Nilai vonis gerbang culling.
 */
object CullingVerdict {
    const val PENDING = "PENDING"
    const val PASS = "PASS"
    const val HOLD = "HOLD"
    const val RELEASED = "RELEASED"
    const val DISCARDED = "DISCARDED"
}

/**
 * Basis data SQLite lokal untuk antrean transfer, sesi, fingerprint unggah terverifikasi,
 * dan penyimpanan token OAuth Google Drive.
 */
class TransferInDb(context: Context) : SQLiteOpenHelper(
    context.applicationContext,
    DATABASE_NAME,
    null,
    DATABASE_VERSION
) {

    companion object {
        const val DATABASE_NAME = "transferin.db"
        const val DATABASE_VERSION = 4

        @Volatile
        private var instance: TransferInDb? = null

        fun getInstance(context: Context): TransferInDb {
            return instance ?: synchronized(this) {
                instance ?: TransferInDb(context.applicationContext).also { instance = it }
            }
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS sessions (
                id TEXT PRIMARY KEY,
                startedAt INTEGER NOT NULL,
                closedAt INTEGER,
                driveFolderId TEXT,
                status TEXT NOT NULL
            );
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS transfer_items (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                sessionId TEXT NOT NULL,
                identity TEXT UNIQUE NOT NULL,
                filename TEXT NOT NULL,
                captureDate TEXT,
                sizeBytes INTEGER NOT NULL,
                md5 TEXT,
                localPath TEXT NOT NULL,
                groupSecond TEXT,
                status TEXT NOT NULL,
                attemptCount INTEGER DEFAULT 0,
                driveFileId TEXT,
                uploadSessionUri TEXT,
                bytesUploaded INTEGER DEFAULT 0,
                lastError TEXT,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL,
                driveName TEXT,
                culling TEXT,
                cullingDetail TEXT
            );
            """.trimIndent()
        )

        db.execSQL("CREATE INDEX IF NOT EXISTS idx_transfer_items_status ON transfer_items(status);")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_transfer_items_sessionId ON transfer_items(sessionId);")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_transfer_items_groupSecond ON transfer_items(groupSecond);")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_transfer_items_culling ON transfer_items(culling);")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS uploaded_fingerprints (
                identity TEXT PRIMARY KEY,
                driveFileId TEXT NOT NULL,
                verifiedAt INTEGER NOT NULL,
                drive_folder_id TEXT NOT NULL DEFAULT ''
            );
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS credentials (
                id INTEGER PRIMARY KEY CHECK(id = 1),
                email TEXT NOT NULL,
                refreshTokenEnc TEXT NOT NULL,
                updatedAt INTEGER NOT NULL
            );
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE transfer_items ADD COLUMN driveName TEXT;")
            FoundationLog.log("[DB] Migrasi skema transferin.db v$oldVersion -> v$newVersion: kolom driveName ditambahkan ke transfer_items")
        }
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE transfer_items ADD COLUMN culling TEXT;")
            db.execSQL("ALTER TABLE transfer_items ADD COLUMN cullingDetail TEXT;")
            FoundationLog.log("[DB] Migrasi skema transferin.db v$oldVersion -> v$newVersion: kolom culling dan cullingDetail ditambahkan ke transfer_items")
        }
        if (oldVersion < 4) {
            db.execSQL("ALTER TABLE uploaded_fingerprints ADD COLUMN drive_folder_id TEXT NOT NULL DEFAULT '';")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_transfer_items_culling ON transfer_items(culling);")
            FoundationLog.log("[DB] Migrasi skema transferin.db v$oldVersion -> v$newVersion: kolom drive_folder_id pada uploaded_fingerprints dan indeks idx_transfer_items_culling ditambahkan")
        }
    }

    /* =========================================================================
     * API Sesi
     * ========================================================================= */

    fun insertSession(
        id: String,
        startedAt: Long = System.currentTimeMillis(),
        driveFolderId: String? = null,
        status: String = SessionStatus.OPEN
    ): Boolean {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("id", id)
            put("startedAt", startedAt)
            put("driveFolderId", driveFolderId)
            put("status", status)
        }
        val rowId = db.insertWithOnConflict("sessions", null, values, SQLiteDatabase.CONFLICT_REPLACE)
        return rowId != -1L
    }

    fun closeSession(id: String, closedAt: Long = System.currentTimeMillis()): Boolean {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("status", SessionStatus.CLOSED)
            put("closedAt", closedAt)
        }
        return db.update("sessions", values, "id = ?", arrayOf(id)) > 0
    }

    fun updateSessionDriveFolder(id: String, driveFolderId: String): Boolean {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("driveFolderId", driveFolderId)
        }
        return db.update("sessions", values, "id = ?", arrayOf(id)) > 0
    }

    fun getSession(id: String): SessionRecord? {
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT id, startedAt, closedAt, driveFolderId, status FROM sessions WHERE id = ? LIMIT 1",
            arrayOf(id)
        )
        cursor.use {
            if (it.moveToFirst()) {
                return SessionRecord(
                    id = it.getString(0),
                    startedAt = it.getLong(1),
                    closedAt = if (it.isNull(2)) null else it.getLong(2),
                    driveFolderId = if (it.isNull(3)) null else it.getString(3),
                    status = it.getString(4)
                )
            }
        }
        return null
    }

    fun isSessionClosed(id: String): Boolean {
        return getSession(id)?.status == SessionStatus.CLOSED
    }

    /* =========================================================================
     * API Transfer Items
     * ========================================================================= */

    /**
     * Menyisipkan item antrean baru jika identity belum pernah ada.
     * Guard: sizeBytes <= 0 ditolak keras (mengembalikan false + log error).
     * Jika duplikat (identity sudah ada), diabaikan (mengembalikan false + log).
     * Menerima parameter statusAwal (bawaan QUEUED, atau CULLING_PENDING dari orkestrator).
     */
    fun insertItemIgnore(
        sessionId: String,
        identity: String,
        filename: String,
        captureDate: String?,
        sizeBytes: Long,
        md5: String?,
        localPath: String,
        groupSecond: String?,
        driveName: String? = null,
        statusAwal: String = ItemStatus.QUEUED,
        culling: String? = null,
        cullingDetail: String? = null
    ): Boolean {
        if (sizeBytes <= 0L) {
            FoundationLog.error("[UNGGAH] Penolakan item: ukuran berkas $filename adalah $sizeBytes byte (identity: $identity)")
            return false
        }

        val db = writableDatabase
        val now = System.currentTimeMillis()
        val values = ContentValues().apply {
            put("sessionId", sessionId)
            put("identity", identity)
            put("filename", filename)
            put("captureDate", captureDate)
            put("sizeBytes", sizeBytes)
            put("md5", md5)
            put("localPath", localPath)
            put("groupSecond", groupSecond)
            put("status", statusAwal)
            put("attemptCount", 0)
            putNull("driveFileId")
            putNull("uploadSessionUri")
            put("bytesUploaded", 0L)
            putNull("lastError")
            put("createdAt", now)
            put("updatedAt", now)
            driveName?.let { put("driveName", it) } ?: putNull("driveName")
            culling?.let { put("culling", it) } ?: putNull("culling")
            cullingDetail?.let { put("cullingDetail", it) } ?: putNull("cullingDetail")
        }

        val rowId = db.insertWithOnConflict("transfer_items", null, values, SQLiteDatabase.CONFLICT_IGNORE)
        if (rowId == -1L) {
            FoundationLog.log("[UNGGAH] Duplikat dilewati: identity $identity ($filename)")
            return false
        }
        return true
    }

    fun nextQueuedItem(): TransferItem? {
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT * FROM transfer_items WHERE status = ? ORDER BY id ASC LIMIT 1",
            arrayOf(ItemStatus.QUEUED)
        )
        cursor.use {
            if (it.moveToFirst()) {
                return cursorToTransferItem(it)
            }
        }
        return null
    }

    fun getItem(id: Long): TransferItem? {
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT * FROM transfer_items WHERE id = ? LIMIT 1",
            arrayOf(id.toString())
        )
        cursor.use {
            if (it.moveToFirst()) {
                return cursorToTransferItem(it)
            }
        }
        return null
    }

    fun findItemByIdentity(identity: String): TransferItem? {
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT * FROM transfer_items WHERE identity = ? LIMIT 1",
            arrayOf(identity)
        )
        cursor.use {
            if (it.moveToFirst()) {
                return cursorToTransferItem(it)
            }
        }
        return null
    }

    fun updateItem(
        id: Long,
        status: String? = null,
        attemptCount: Int? = null,
        driveFileId: String? = null,
        uploadSessionUri: String? = null,
        bytesUploaded: Long? = null,
        lastError: String? = null,
        driveName: String? = null,
        culling: String? = null,
        cullingDetail: String? = null
    ): Boolean {
        val values = ContentValues().apply {
            status?.let { put("status", it) }
            attemptCount?.let { put("attemptCount", it) }
            driveFileId?.let { put("driveFileId", it) }
            uploadSessionUri?.let { put("uploadSessionUri", it) }
            bytesUploaded?.let { put("bytesUploaded", it) }
            lastError?.let { put("lastError", it) }
            driveName?.let { put("driveName", it) }
            culling?.let { put("culling", it) }
            cullingDetail?.let { put("cullingDetail", it) }
            put("updatedAt", System.currentTimeMillis())
        }
        val db = writableDatabase
        return db.update("transfer_items", values, "id = ?", arrayOf(id.toString())) > 0
    }

    fun tryTransitionStatus(
        id: Long,
        newStatus: String,
        fromStatuses: List<String>,
        driveFileId: String? = null
    ): Boolean {
        if (fromStatuses.isEmpty()) return false
        val db = writableDatabase
        val values = ContentValues().apply {
            put("status", newStatus)
            driveFileId?.let { put("driveFileId", it) }
            put("updatedAt", System.currentTimeMillis())
        }
        val placeholders = fromStatuses.joinToString(",") { "?" }
        val args = Array(fromStatuses.size + 1) { idx ->
            if (idx == 0) id.toString() else fromStatuses[idx - 1]
        }
        return db.update("transfer_items", values, "id = ? AND status IN ($placeholders)", args) > 0
    }

    fun tryMarkDone(
        id: Long,
        driveFileId: String,
        driveName: String? = null,
        bytesUploaded: Long? = null
    ): Boolean {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("status", ItemStatus.DONE)
            put("driveFileId", driveFileId)
            driveName?.let { put("driveName", it) }
            bytesUploaded?.let { put("bytesUploaded", it) }
            put("updatedAt", System.currentTimeMillis())
        }
        return db.update(
            "transfer_items",
            values,
            "id = ? AND status IN (?, ?)",
            arrayOf(id.toString(), ItemStatus.UPLOADING, ItemStatus.VERIFYING)
        ) > 0
    }

    /**
     * Pemulihan item terdampar: item yang tertinggal di status UPLOADING/VERIFYING
     * (proses mati di tengah jalan, atau hasil RETRYABLE yang tidak diantrekan ulang)
     * dikembalikan ke QUEUED. Aman karena DriveUploader melanjutkan lewat sesi
     * resumable tersimpan / verifikasi ulang id berkas yang sudah ada.
     * Mengembalikan jumlah item yang dipulihkan.
     */
    fun requeueStaleInFlight(): Int {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("status", ItemStatus.QUEUED)
            put("updatedAt", System.currentTimeMillis())
        }
        return db.update(
            "transfer_items",
            values,
            "status IN (?, ?)",
            arrayOf(ItemStatus.UPLOADING, ItemStatus.VERIFYING)
        )
    }

    fun countsByStatus(): Map<String, Int> {
        val counts = mutableMapOf(
            ItemStatus.CULLING_PENDING to 0,
            ItemStatus.CULLING_HOLD to 0,
            ItemStatus.DISCARDED to 0,
            ItemStatus.QUEUED to 0,
            ItemStatus.UPLOADING to 0,
            ItemStatus.VERIFYING to 0,
            ItemStatus.DONE to 0,
            ItemStatus.FAILED_FINAL to 0
        )
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT status, COUNT(*) FROM transfer_items GROUP BY status",
            null
        )
        cursor.use {
            while (it.moveToNext()) {
                val status = it.getString(0)
                val count = it.getInt(1)
                counts[status] = count
            }
        }
        return counts
    }

    data class PendingCullingGroup(
        val sessionId: String,
        val groupSecond: String,
        val itemCount: Int
    )

    /**
     * Mengambil daftar grup yang memiliki item berstatus CULLING_PENDING.
     */
    fun getPendingCullingGroups(): List<PendingCullingGroup> {
        val groups = mutableListOf<PendingCullingGroup>()
        val db = readableDatabase
        val cursor = db.rawQuery(
            """
            SELECT sessionId, COALESCE(groupSecond, '') AS grp, COUNT(*)
            FROM transfer_items
            WHERE status = ?
            GROUP BY sessionId, grp
            ORDER BY MIN(id) ASC
            """.trimIndent(),
            arrayOf(ItemStatus.CULLING_PENDING)
        )
        cursor.use {
            while (it.moveToNext()) {
                groups.add(
                    PendingCullingGroup(
                        sessionId = it.getString(0),
                        groupSecond = it.getString(1),
                        itemCount = it.getInt(2)
                    )
                )
            }
        }
        return groups
    }

    /**
     * Mengambil seluruh item dalam satu grup (berdasarkan sessionId dan groupSecond).
     */
    fun getItemsInGroup(sessionId: String, groupSecond: String): List<TransferItem> {
        val items = mutableListOf<TransferItem>()
        val db = readableDatabase
        val cursor = if (groupSecond.isEmpty()) {
            db.rawQuery(
                "SELECT * FROM transfer_items WHERE sessionId = ? AND (groupSecond IS NULL OR groupSecond = '') ORDER BY id ASC",
                arrayOf(sessionId)
            )
        } else {
            db.rawQuery(
                "SELECT * FROM transfer_items WHERE sessionId = ? AND groupSecond = ? ORDER BY id ASC",
                arrayOf(sessionId, groupSecond)
            )
        }
        cursor.use {
            while (it.moveToNext()) {
                items.add(cursorToTransferItem(it))
            }
        }
        return items
    }

    /**
     * Memperbarui vonis gerbang culling untuk seluruh item dalam grup sekaligus.
     * Mengembalikan jumlah baris yang diperbarui.
     */
    fun setGroupCullingVerdict(
        sessionId: String,
        groupSecond: String,
        newStatus: String,
        cullingVerdict: String,
        cullingDetail: String?
    ): Int {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("status", newStatus)
            put("culling", cullingVerdict)
            cullingDetail?.let { put("cullingDetail", it) } ?: putNull("cullingDetail")
            put("updatedAt", System.currentTimeMillis())
        }
        // Vonis hanya boleh menggerakkan item yang masih CULLING_PENDING.
        // Tanpa syarat ini, vonis ulang (mis. sapuan akhir sesi) membangkitkan
        // item DONE kembali ke QUEUED — terbukti 10 Nov 2026: RAF yang sudah
        // terunggah diantrekan ulang, berkas lokalnya sudah tersapu, berakhir
        // FAILED_FINAL, dan JPG pasangannya terunggah dua kali.
        return if (groupSecond.isEmpty()) {
            db.update("transfer_items", values, "sessionId = ? AND (groupSecond IS NULL OR groupSecond = '') AND status = 'CULLING_PENDING'", arrayOf(sessionId))
        } else {
            db.update("transfer_items", values, "sessionId = ? AND groupSecond = ? AND status = 'CULLING_PENDING'", arrayOf(sessionId, groupSecond))
        }
    }

    /**
     * Mengambil daftar item berdasarkan status (pola failedItems yang ada).
     */
    fun itemsByStatus(status: String): List<TransferItem> {
        val items = mutableListOf<TransferItem>()
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT * FROM transfer_items WHERE status = ? ORDER BY id ASC",
            arrayOf(status)
        )
        cursor.use {
            while (it.moveToNext()) {
                items.add(cursorToTransferItem(it))
            }
        }
        return items
    }

    /**
     * Mengambil daftar item berdasarkan status (alias untuk itemsByStatus).
     */
    fun getItemsByStatus(status: String): List<TransferItem> = itemsByStatus(status)

    /**
     * Fungsi pembantu mengambil grup tertahan: item berstatus CULLING_HOLD
     * dikelompokkan per (sessionId, groupSecond).
     */
    fun getHeldCullingGroups(): List<HeldCullingGroup> {
        val heldItems = itemsByStatus(ItemStatus.CULLING_HOLD)
        if (heldItems.isEmpty()) return emptyList()

        val map = LinkedHashMap<Pair<String, String>, MutableList<TransferItem>>()
        for (item in heldItems) {
            val key = Pair(item.sessionId, item.groupSecond ?: "")
            map.getOrPut(key) { mutableListOf() }.add(item)
        }

        return map.map { (key, items) ->
            HeldCullingGroup(
                sessionId = key.first,
                groupSecond = key.second,
                items = items
            )
        }
    }

    fun heldCullingGroups(): List<HeldCullingGroup> = getHeldCullingGroups()

    /**
     * Memeriksa apakah ada item dalam grup yang sudah masuk ke pipa unggah (QUEUED, UPLOADING, DONE).
     */
    fun hasGroupEnteredUploadPipe(sessionId: String, groupSecond: String): Boolean {
        val items = getItemsInGroup(sessionId, groupSecond)
        return items.any {
            it.status == ItemStatus.QUEUED || it.status == ItemStatus.UPLOADING || it.status == ItemStatus.DONE
        }
    }

    fun failedItems(): List<TransferItem> {
        val items = mutableListOf<TransferItem>()
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT * FROM transfer_items WHERE status = ? ORDER BY id ASC",
            arrayOf(ItemStatus.FAILED_FINAL)
        )
        cursor.use {
            while (it.moveToNext()) {
                items.add(cursorToTransferItem(it))
            }
        }
        return items
    }

    fun getItemsBySessionAndStatus(sessionId: String, status: String): List<TransferItem> {
        val items = mutableListOf<TransferItem>()
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT * FROM transfer_items WHERE sessionId = ? AND status = ? ORDER BY id ASC",
            arrayOf(sessionId, status)
        )
        cursor.use {
            while (it.moveToNext()) {
                items.add(cursorToTransferItem(it))
            }
        }
        return items
    }

    fun requeueFailed(): Int {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("status", ItemStatus.QUEUED)
            put("attemptCount", 0)
            putNull("lastError")
            put("updatedAt", System.currentTimeMillis())
        }
        val affected = db.update("transfer_items", values, "status = ?", arrayOf(ItemStatus.FAILED_FINAL))
        FoundationLog.log("[UNGGAH] requeueFailed: $affected item dikembalikan ke antrean QUEUED")
        return affected
    }

    /* =========================================================================
     * API Credentials
     * ========================================================================= */

    fun saveCredentials(email: String, refreshTokenEnc: String): Boolean {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("id", 1)
            put("email", email)
            put("refreshTokenEnc", refreshTokenEnc)
            put("updatedAt", System.currentTimeMillis())
        }
        val rowId = db.insertWithOnConflict("credentials", null, values, SQLiteDatabase.CONFLICT_REPLACE)
        return rowId != -1L
    }

    fun loadCredentials(): CredentialsRecord? {
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT email, refreshTokenEnc, updatedAt FROM credentials WHERE id = 1 LIMIT 1",
            null
        )
        cursor.use {
            if (it.moveToFirst()) {
                return CredentialsRecord(
                    email = it.getString(0),
                    refreshTokenEnc = it.getString(1),
                    updatedAt = it.getLong(2)
                )
            }
        }
        return null
    }

    fun clearCredentials(): Boolean {
        val db = writableDatabase
        return db.delete("credentials", "id = 1", null) > 0
    }

    /* =========================================================================
     * API Uploaded Fingerprints
     * ========================================================================= */

    fun insertFingerprint(
        identity: String,
        driveFileId: String,
        driveFolderId: String,
        verifiedAt: Long = System.currentTimeMillis()
    ): Boolean {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("identity", identity)
            put("driveFileId", driveFileId)
            put("drive_folder_id", driveFolderId)
            put("verifiedAt", verifiedAt)
        }
        val rowId = db.insertWithOnConflict("uploaded_fingerprints", null, values, SQLiteDatabase.CONFLICT_REPLACE)
        return rowId != -1L
    }

    fun findFingerprint(identity: String, driveFolderId: String): String? {
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT driveFileId FROM uploaded_fingerprints WHERE identity = ? AND drive_folder_id = ? LIMIT 1",
            arrayOf(identity, driveFolderId)
        )
        cursor.use {
            if (it.moveToFirst()) {
                return it.getString(0)
            }
        }
        return null
    }

    /* =========================================================================
     * Helper Pemetaan Cursor
     * ========================================================================= */

    private fun cursorToTransferItem(cursor: Cursor): TransferItem {
        val driveNameIdx = cursor.getColumnIndex("driveName")
        val driveName = if (driveNameIdx != -1 && !cursor.isNull(driveNameIdx)) cursor.getString(driveNameIdx) else null

        val cullingIdx = cursor.getColumnIndex("culling")
        val culling = if (cullingIdx != -1 && !cursor.isNull(cullingIdx)) cursor.getString(cullingIdx) else null

        val cullingDetailIdx = cursor.getColumnIndex("cullingDetail")
        val cullingDetail = if (cullingDetailIdx != -1 && !cursor.isNull(cullingDetailIdx)) cursor.getString(cullingDetailIdx) else null

        return TransferItem(
            id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
            sessionId = cursor.getString(cursor.getColumnIndexOrThrow("sessionId")),
            identity = cursor.getString(cursor.getColumnIndexOrThrow("identity")),
            filename = cursor.getString(cursor.getColumnIndexOrThrow("filename")),
            captureDate = if (cursor.isNull(cursor.getColumnIndexOrThrow("captureDate"))) null else cursor.getString(cursor.getColumnIndexOrThrow("captureDate")),
            sizeBytes = cursor.getLong(cursor.getColumnIndexOrThrow("sizeBytes")),
            md5 = if (cursor.isNull(cursor.getColumnIndexOrThrow("md5"))) null else cursor.getString(cursor.getColumnIndexOrThrow("md5")),
            localPath = cursor.getString(cursor.getColumnIndexOrThrow("localPath")),
            groupSecond = if (cursor.isNull(cursor.getColumnIndexOrThrow("groupSecond"))) null else cursor.getString(cursor.getColumnIndexOrThrow("groupSecond")),
            status = cursor.getString(cursor.getColumnIndexOrThrow("status")),
            attemptCount = cursor.getInt(cursor.getColumnIndexOrThrow("attemptCount")),
            driveFileId = if (cursor.isNull(cursor.getColumnIndexOrThrow("driveFileId"))) null else cursor.getString(cursor.getColumnIndexOrThrow("driveFileId")),
            uploadSessionUri = if (cursor.isNull(cursor.getColumnIndexOrThrow("uploadSessionUri"))) null else cursor.getString(cursor.getColumnIndexOrThrow("uploadSessionUri")),
            bytesUploaded = cursor.getLong(cursor.getColumnIndexOrThrow("bytesUploaded")),
            lastError = if (cursor.isNull(cursor.getColumnIndexOrThrow("lastError"))) null else cursor.getString(cursor.getColumnIndexOrThrow("lastError")),
            createdAt = cursor.getLong(cursor.getColumnIndexOrThrow("createdAt")),
            updatedAt = cursor.getLong(cursor.getColumnIndexOrThrow("updatedAt")),
            driveName = driveName,
            culling = culling,
            cullingDetail = cullingDetail
        )
    }
}
