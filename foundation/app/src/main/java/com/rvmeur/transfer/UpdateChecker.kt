package com.rvmeur.transfer

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Pengecek dan pengunduh pembaruan rilis OTA GitHub untuk @rvmeur Tether.
 * Target rilis: ryandxter/tethershoot-android
 */
object UpdateChecker {

    private const val REPO_NAME = "ryandxter/tethershoot-android"
    private const val LATEST_URL = "https://api.github.com/repos/$REPO_NAME/releases/latest"
    private const val USER_AGENT = "rvmeur-tether"
    private const val TIMEOUT_MS = 15000

    private const val PREFS_NAME = "transferin_update"
    private const val KEY_LAST_CHECK_MS = "last_check_ms"
    private const val AUTO_CHECK_INTERVAL_MS = 6 * 3600 * 1000L // 6 jam

    data class UpdateInfo(
        val versionCode: Int,
        val versionName: String,
        val apkUrl: String,
        val apkSha256: String
    )

    sealed class CheckResult {
        data class UpdateAvailable(val info: UpdateInfo) : CheckResult()
        object UpToDate : CheckResult()
        data class Failure(val message: String) : CheckResult()
    }

    fun checkLatest(currentVersionCode: Int, onResult: (CheckResult) -> Unit) {
        checkLatest(currentVersionCode, null, onResult)
    }

    fun checkLatest(
        currentVersionCode: Int,
        currentVersionName: String?,
        onResult: (CheckResult) -> Unit
    ) {
        Thread({
            try {
                val latestJsonStr = fetchStringWithRedirects(LATEST_URL)
                val releaseObj = JSONObject(latestJsonStr)
                val assetsArr = releaseObj.optJSONArray("assets")

                var versionJsonDownloadUrl: String? = null
                if (assetsArr != null) {
                    for (i in 0 until assetsArr.length()) {
                        val asset = assetsArr.getJSONObject(i)
                        val name = asset.optString("name", "")
                        if (name == "version.json") {
                            versionJsonDownloadUrl = asset.optString("browser_download_url", "")
                            break
                        }
                    }
                }

                if (versionJsonDownloadUrl.isNullOrEmpty()) {
                    onResult(CheckResult.UpToDate)
                    return@Thread
                }

                val versionJsonStr = fetchStringWithRedirects(versionJsonDownloadUrl)
                val vObj = JSONObject(versionJsonStr)
                val vCode = vObj.optInt("versionCode", -1)
                val vName = vObj.optString("versionName", "")
                val apkFile = vObj.optString("apkFile", "")
                val apkSha256 = vObj.optString("apkSha256", "")

                if (vCode <= 0 || apkFile.isBlank() || apkSha256.isBlank() || vCode <= currentVersionCode) {
                    onResult(CheckResult.UpToDate)
                    return@Thread
                }

                var apkUrl: String? = null
                if (assetsArr != null) {
                    for (i in 0 until assetsArr.length()) {
                        val asset = assetsArr.getJSONObject(i)
                        if (asset.optString("name", "") == apkFile) {
                            val downloadUrl = asset.optString("browser_download_url", "")
                            if (downloadUrl.isNotBlank()) {
                                apkUrl = downloadUrl
                            }
                            break
                        }
                    }
                }

                if (!apkUrl.isNullOrEmpty()) {
                    onResult(CheckResult.UpdateAvailable(UpdateInfo(vCode, vName, apkUrl, apkSha256)))
                } else {
                    onResult(CheckResult.UpToDate)
                }
            } catch (t: Throwable) {
                FoundationLog.error("[UPDATE] Gagal memeriksa pembaruan", t)
                onResult(CheckResult.Failure(t.message ?: "gagal memeriksa pembaruan"))
            }
        }, "UpdateChecker-Thread").start()
    }

    fun shouldAutoCheck(context: Context): Boolean {
        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val lastCheckMs = prefs.getLong(KEY_LAST_CHECK_MS, 0L)
            if (lastCheckMs <= 0L) return true
            val now = System.currentTimeMillis()
            (now - lastCheckMs) > AUTO_CHECK_INTERVAL_MS
        } catch (_: Throwable) {
            true
        }
    }

    fun markChecked(context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putLong(KEY_LAST_CHECK_MS, System.currentTimeMillis()).apply()
        } catch (t: Throwable) {
            FoundationLog.error("[UPDATE] Gagal menyimpan markChecked", t)
        }
    }

    fun downloadAndInstall(
        activity: Activity,
        info: UpdateInfo,
        onStatus: (String) -> Unit
    ) {
        Thread({
            try {
                val targetFile = File(activity.cacheDir, "tether-update.apk")
                if (targetFile.exists()) {
                    targetFile.delete()
                }

                downloadFileWithRedirects(info.apkUrl, targetFile)

                if (!info.apkSha256.isNullOrBlank()) {
                    val actualSha = calculateSha256(targetFile)
                    if (!actualSha.equals(info.apkSha256.trim(), ignoreCase = true)) {
                        FoundationLog.error("[UPDATE] Checksum SHA-256 tidak cocok: dihitung=$actualSha, diharapkan=${info.apkSha256}")
                        if (targetFile.exists()) {
                            targetFile.delete()
                        }
                        activity.runOnUiThread {
                            onStatus("unduhan pembaruan rusak — dibatalkan.")
                        }
                        return@Thread
                    }
                }

                activity.runOnUiThread {
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                            !activity.packageManager.canRequestPackageInstalls()
                        ) {
                            onStatus("izinkan pemasangan dari aplikasi ini dulu, lalu coba lagi.")
                            val intent = Intent(
                                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                Uri.parse("package:${activity.packageName}")
                            )
                            activity.startActivity(intent)
                        } else {
                            onStatus("membuka pemasang pembaruan…")
                            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(
                                    FoundationFileProvider.updateUri(),
                                    "application/vnd.android.package-archive"
                                )
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            activity.startActivity(installIntent)
                        }
                    } catch (t: Throwable) {
                        FoundationLog.error("[UPDATE] Gagal membuka installer paket", t)
                        onStatus("pembaruan gagal: coba lagi nanti.")
                    }
                }
            } catch (t: Throwable) {
                FoundationLog.error("[UPDATE] Gagal mengunduh atau memasang pembaruan", t)
                activity.runOnUiThread {
                    onStatus("pembaruan gagal: coba lagi nanti.")
                }
            }
        }, "UpdateDownloader-Thread").start()
    }

    private fun fetchStringWithRedirects(urlString: String): String {
        var currentUrl = urlString
        var redirects = 0
        while (redirects < 10) {
            val url = URL(currentUrl)
            if (!url.protocol.equals("https", ignoreCase = true)) {
                throw SecurityException("hanya URL https yang diizinkan untuk pembaruan")
            }
            val conn = (url.openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", USER_AGENT)
            }
            val code = conn.responseCode
            if (code == HttpURLConnection.HTTP_MOVED_PERM ||
                code == HttpURLConnection.HTTP_MOVED_TEMP ||
                code == HttpURLConnection.HTTP_SEE_OTHER ||
                code == 307 ||
                code == 308
            ) {
                val loc = conn.getHeaderField("Location")
                conn.disconnect()
                if (loc != null) {
                    val nextUrl = URL(URL(currentUrl), loc)
                    if (!nextUrl.protocol.equals("https", ignoreCase = true)) {
                        throw SecurityException("hanya URL https yang diizinkan untuk pembaruan")
                    }
                    currentUrl = nextUrl.toExternalForm()
                    redirects++
                    continue
                }
            }
            if (code !in 200..299) {
                conn.disconnect()
                throw IllegalStateException("HTTP error $code saat mengambil $urlString")
            }
            return conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        }
        throw IllegalStateException("Terlalu banyak redirect saat mengambil $urlString")
    }

    private fun downloadFileWithRedirects(urlString: String, destination: File) {
        var currentUrl = urlString
        var redirects = 0
        while (redirects < 10) {
            val url = URL(currentUrl)
            if (!url.protocol.equals("https", ignoreCase = true)) {
                throw SecurityException("hanya URL https yang diizinkan untuk pembaruan")
            }
            val conn = (url.openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = TIMEOUT_MS
                readTimeout = 30000
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "*/*")
            }
            val code = conn.responseCode
            if (code == HttpURLConnection.HTTP_MOVED_PERM ||
                code == HttpURLConnection.HTTP_MOVED_TEMP ||
                code == HttpURLConnection.HTTP_SEE_OTHER ||
                code == 307 ||
                code == 308
            ) {
                val loc = conn.getHeaderField("Location")
                conn.disconnect()
                if (loc != null) {
                    val nextUrl = URL(URL(currentUrl), loc)
                    if (!nextUrl.protocol.equals("https", ignoreCase = true)) {
                        throw SecurityException("hanya URL https yang diizinkan untuk pembaruan")
                    }
                    currentUrl = nextUrl.toExternalForm()
                    redirects++
                    continue
                }
            }
            if (code !in 200..299) {
                conn.disconnect()
                throw IllegalStateException("HTTP error $code saat mengunduh berkas")
            }
            conn.inputStream.use { input ->
                FileOutputStream(destination).use { output ->
                    input.copyTo(output)
                }
            }
            return
        }
        throw IllegalStateException("Terlalu banyak redirect saat mengunduh berkas")
    }

    private fun calculateSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
