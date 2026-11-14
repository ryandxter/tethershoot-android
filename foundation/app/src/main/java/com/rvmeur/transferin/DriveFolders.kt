package com.rvmeur.transferin

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import org.json.JSONArray
import org.json.JSONObject

/**
 * Item folder Google Drive dengan ID dan nama tampilan.
 */
data class DriveFolderItem(
    val id: String,
    val name: String
)

/**
 * Pengelola penjelajah folder Google Drive untuk TransferIn Foundation (Stage 3).
 *
 * Menerapkan:
 * - Query subfolder via Google Drive v3 files.list (HttpURLConnection, token DriveAuth).
 * - Filter query: '<id>' in parents and mimeType='application/vnd.google-apps.folder' and trashed=false.
 * - Pengurutan berdasarkan nama (orderBy name).
 * - Pemeliharaan tumpukan jejak navigasi (breadcrumbs).
 * - Persistensi folder aktif dan jejak nama di SharedPreferences (otomatis kembali ke folder terakhir saat dibuka).
 * - Penanda log [FOLDER].
 * - Eksekusi panggilan jaringan di thread latar.
 */
class DriveFolders(
    private val context: Context,
    private val driveAuth: DriveAuth
) {
    companion object {
        const val ROOT_ID = "root"
        const val ROOT_NAME = "Drive Saya"
        private const val PREFS_NAME = "transferin_drive_folders"
        private const val KEY_TRAIL_JSON = "folder_trail_json"
        private const val DRIVE_API_FILES = "https://www.googleapis.com/drive/v3/files"
        private const val FOLDER_MIME_TYPE = "application/vnd.google-apps.folder"
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val mainHandler = Handler(Looper.getMainLooper())

    // Tumpukan jejak navigasi (elemen pertama selalu root)
    private val trail = mutableListOf<DriveFolderItem>()

    init {
        loadSavedTrail()
    }

    /* =========================================================================
     * Manajemen Status Jejak Navigasi & Persistensi SharedPreferences
     * ========================================================================= */

    private fun loadSavedTrail() {
        synchronized(trail) {
            trail.clear()
            val jsonStr = prefs.getString(KEY_TRAIL_JSON, null)
            if (!jsonStr.isNullOrEmpty()) {
                try {
                    val array = JSONArray(jsonStr)
                    for (i in 0 until array.length()) {
                        val obj = array.getJSONObject(i)
                        val id = obj.optString("id", "")
                        val name = obj.optString("name", "")
                        if (id.isNotEmpty()) {
                            trail.add(DriveFolderItem(id, name))
                        }
                    }
                } catch (t: Throwable) {
                    FoundationLog.error("[FOLDER] Gagal mem-parse riwayat jejak folder dari preferensi", t)
                    trail.clear()
                }
            }

            if (trail.isEmpty() || trail[0].id != ROOT_ID) {
                trail.clear()
                trail.add(DriveFolderItem(ROOT_ID, ROOT_NAME))
                saveTrailInternal()
            }

            FoundationLog.log("[FOLDER] Folder aktif dimuat dari preferensi: ${getCurrentPath()} (id=${getCurrentFolder().id})")
        }
    }

    private fun saveTrailInternal() {
        try {
            val array = JSONArray()
            for (item in trail) {
                val obj = JSONObject().apply {
                    put("id", item.id)
                    put("name", item.name)
                }
                array.put(obj)
            }
            prefs.edit().putString(KEY_TRAIL_JSON, array.toString()).apply()
        } catch (t: Throwable) {
            FoundationLog.error("[FOLDER] Gagal menyimpan jejak folder ke preferensi", t)
        }
    }

    /**
     * Mengembalikan item folder yang sedang aktif (posisi puncak tumpukan jejak).
     */
    fun getCurrentFolder(): DriveFolderItem {
        synchronized(trail) {
            return trail.lastOrNull() ?: DriveFolderItem(ROOT_ID, ROOT_NAME)
        }
    }

    /**
     * Mengembalikan teks jejak path folder aktif (misal: "Drive Saya / Klien A / Sesi 1").
     */
    fun getCurrentPath(): String {
        synchronized(trail) {
            return if (trail.isEmpty()) {
                ROOT_NAME
            } else {
                trail.joinToString(" / ") { it.name }
            }
        }
    }

    /**
     * Memeriksa apakah navigasi dapat naik ke induk (jika bukan berada di root).
     */
    fun canGoUp(): Boolean {
        synchronized(trail) {
            return trail.size > 1
        }
    }

    /**
     * Naik satu tingkat ke folder induk dan memperbarui penyimpanan persisten.
     */
    fun goUp(): DriveFolderItem {
        synchronized(trail) {
            if (trail.size > 1) {
                trail.removeAt(trail.size - 1)
                saveTrailInternal()
            }
            val current = getCurrentFolder()
            FoundationLog.log("[FOLDER] Naik ke '${current.name}' (id=${current.id}). Jejak: ${getCurrentPath()}")
            return current
        }
    }

    /**
     * Masuk ke dalam subfolder dan memperbarui penyimpanan persisten.
     */
    fun enterFolder(folder: DriveFolderItem) {
        synchronized(trail) {
            trail.add(folder)
            saveTrailInternal()
            FoundationLog.log("[FOLDER] Masuk ke '${folder.name}' (id=${folder.id}). Jejak: ${getCurrentPath()}")
        }
    }

    /**
     * Mengembalikan navigasi kembali ke root Drive.
     */
    fun resetToRoot() {
        synchronized(trail) {
            trail.clear()
            trail.add(DriveFolderItem(ROOT_ID, ROOT_NAME))
            saveTrailInternal()
            FoundationLog.log("[FOLDER] Navigasi di-reset ke root (${ROOT_NAME})")
        }
    }

    /* =========================================================================
     * Panggilan Jaringan Google Drive files.list (Thread Latar)
     * ========================================================================= */

    /**
     * Mengambil daftar subfolder langsung di bawah [folderId] melalui Google Drive API v3.
     * Menggunakan thread latar terdedikasi dan memanggil [callback] di thread UI (MainLooper).
     */
    fun listSubfolders(
        folderId: String,
        callback: (List<DriveFolderItem>?, String?) -> Unit
    ) {
        FoundationLog.log("[FOLDER] Memuat subfolder untuk folder id=$folderId di thread latar...")
        Thread({
            var conn: HttpURLConnection? = null
            try {
                val token = driveAuth.getAccessToken()
                if (token == null) {
                    val err = "Token akses Google Drive belum tersedia. Hubungkan akun terlebih dahulu."
                    FoundationLog.error("[FOLDER] $err")
                    mainHandler.post { callback(null, err) }
                    return@Thread
                }

                // Query: '<id>' in parents and mimeType='application/vnd.google-apps.folder' and trashed=false
                val escapedId = folderId.replace("'", "\\'")
                val query = "'$escapedId' in parents and mimeType = '$FOLDER_MIME_TYPE' and trashed = false"

                val urlStr = buildString {
                    append(DRIVE_API_FILES)
                    append("?q=").append(URLEncoder.encode(query, "UTF-8"))
                    append("&fields=").append(URLEncoder.encode("files(id,name)", "UTF-8"))
                    append("&orderBy=").append(URLEncoder.encode("name", "UTF-8"))
                    append("&pageSize=1000")
                }

                val url = URL(urlStr)
                conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 15_000
                    readTimeout = 15_000
                    setRequestProperty("Authorization", "Bearer $token")
                }

                val code = conn.responseCode
                if (code in 200..299) {
                    val body = readStreamSafely(conn.inputStream)
                    val json = JSONObject(body)
                    val filesArray = json.optJSONArray("files")
                    val result = mutableListOf<DriveFolderItem>()
                    if (filesArray != null) {
                        for (i in 0 until filesArray.length()) {
                            val f = filesArray.getJSONObject(i)
                            val id = f.optString("id", "")
                            val name = f.optString("name", "")
                            if (id.isNotEmpty()) {
                                result.add(DriveFolderItem(id, if (name.isNotEmpty()) name else "(Folder tanpa nama)"))
                            }
                        }
                    }
                    FoundationLog.log("[FOLDER] Berhasil memuat ${result.size} subfolder untuk id=$folderId")
                    mainHandler.post { callback(result, null) }
                } else {
                    val errBody = readStreamSafely(conn.errorStream)
                    val err = "Google Drive mengembalikan HTTP $code: $errBody"
                    FoundationLog.error("[FOLDER] $err")
                    mainHandler.post { callback(null, err) }
                }
            } catch (t: Throwable) {
                val err = "Gangguan jaringan saat memuat folder: ${t.javaClass.simpleName} - ${t.message}"
                FoundationLog.error("[FOLDER] $err", t)
                mainHandler.post { callback(null, err) }
            } finally {
                try { conn?.disconnect() } catch (_: Exception) {}
            }
        }, "drive-folders-list").start()
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
