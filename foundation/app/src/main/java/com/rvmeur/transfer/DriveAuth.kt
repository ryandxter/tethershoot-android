package com.rvmeur.transfer

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONObject

/**
 * Pengelola autentikasi Google Drive berbasis OAuth 2.0 PKCE (RFC 7636) murni publik.
 *
 * Menggunakan kredensial Android Client ID tanpa client_secret.
 * Refresh token disimpan secara terenkripsi menggunakan AndroidKeyStore (AES/GCM/NoPadding).
 * Access token di-cache di memori dengan masa berlaku otomatis disegarkan (expires_in - 60 dtk).
 */
class DriveAuth(
    private val context: Context,
    private val db: TransferInDb = TransferInDb.getInstance(context)
) {

    companion object {
        // Android Native Client ID dari konfigurasi Google Cloud Console
        const val ANDROID_CLIENT_ID = "574131462713-rei4gea37is28ba2pld620dpu41pp0ge.apps.googleusercontent.com"
        const val ANDROID_REDIRECT_URI = "com.rvmeur.transfer:/oauth2redirect"
        const val SCOPE = "https://www.googleapis.com/auth/drive"

        private const val AUTH_ENDPOINT = "https://accounts.google.com/o/oauth2/v2/auth"
        private const val TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token"
        private const val REVOKE_ENDPOINT = "https://oauth2.googleapis.com/revoke"
        private const val DRIVE_ABOUT_ENDPOINT = "https://www.googleapis.com/drive/v3/about?fields=user(emailAddress)"

        private const val KEY_ALIAS = "transferin_token_key"
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_IV_LENGTH_BYTES = 12
        private const val GCM_TAG_LENGTH_BITS = 128

        private const val PREFS_NAME = "transferin_auth_temp"
        private const val PREF_KEY_VERIFIER = "auth_code_verifier"
        private const val PREF_KEY_STATE = "auth_state"
        private const val UNRESERVED_CHARS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-._~"

        @Volatile
        private var instance: DriveAuth? = null

        fun getInstance(context: Context): DriveAuth {
            return instance ?: synchronized(this) {
                instance ?: DriveAuth(context.applicationContext).also { instance = it }
            }
        }
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val tokenLock = Any()
    private val refreshLock = Any()

    @Volatile
    private var cachedAccessToken: String? = null

    @Volatile
    private var accessTokenExpiresAtMs: Long = 0L

    @Volatile
    private var cachedEmail: String? = null

    /**
     * Callback saat status autentikasi berubah: (isConnected, email).
     * Selalu dipanggil di main/UI thread.
     */
    @Volatile
    var onAuthStatusChanged: ((Boolean, String?) -> Unit)? = null

    /* =========================================================================
     * Enkripsi / Dekripsi AndroidKeyStore AES/GCM/NoPadding
     * ========================================================================= */

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER)
        keyStore.load(null)
        if (keyStore.containsAlias(KEY_ALIAS)) {
            val key = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
            if (key != null) {
                return key
            }
        }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    /**
     * Enkripsi teks rahasia (refresh token).
     * Format keluaran: Base64(iv || ciphertext).
     */
    fun encrypt(plainText: String): String {
        val key = getOrCreateSecretKey()
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val cipherBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

        val combined = ByteArray(iv.size + cipherBytes.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(cipherBytes, 0, combined, iv.size, cipherBytes.size)

        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    /**
     * Dekripsi teks rahasia dari format Base64(iv || ciphertext).
     */
    fun decrypt(encryptedBase64: String): String {
        val combined = Base64.decode(encryptedBase64, Base64.NO_WRAP)
        if (combined.size < GCM_IV_LENGTH_BYTES) {
            throw IllegalArgumentException("Data cipher terlalu pendek untuk GCM IV")
        }

        val iv = ByteArray(GCM_IV_LENGTH_BYTES)
        val cipherBytes = ByteArray(combined.size - GCM_IV_LENGTH_BYTES)
        System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH_BYTES)
        System.arraycopy(combined, GCM_IV_LENGTH_BYTES, cipherBytes, 0, cipherBytes.size)

        val key = getOrCreateSecretKey()
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.DECRYPT_MODE, key, spec)

        val plainBytes = cipher.doFinal(cipherBytes)
        return String(plainBytes, Charsets.UTF_8)
    }

    /* =========================================================================
     * Alur PKCE: Inisiasi & Redirect
     * ========================================================================= */

    private fun generateCodeVerifier(): String {
        val secureRandom = SecureRandom()
        val sb = StringBuilder(64)
        for (i in 0 until 64) {
            sb.append(UNRESERVED_CHARS[secureRandom.nextInt(UNRESERVED_CHARS.length)])
        }
        return sb.toString()
    }

    private fun generateCodeChallenge(verifier: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(verifier.toByteArray(Charsets.US_ASCII))
        return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    private fun generateRandomState(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    /**
     * Memulai alur autentikasi PKCE dengan membuka browser eksternal.
     */
    fun beginAuth(activity: Activity) {
        try {
            val verifier = generateCodeVerifier()
            val challenge = generateCodeChallenge(verifier)
            val state = generateRandomState()

            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putString(PREF_KEY_VERIFIER, verifier)
                .putString(PREF_KEY_STATE, state)
                .apply()

            val authUri = Uri.parse(AUTH_ENDPOINT).buildUpon()
                .appendQueryParameter("client_id", ANDROID_CLIENT_ID)
                .appendQueryParameter("redirect_uri", ANDROID_REDIRECT_URI)
                .appendQueryParameter("response_type", "code")
                .appendQueryParameter("scope", SCOPE)
                .appendQueryParameter("code_challenge", challenge)
                .appendQueryParameter("code_challenge_method", "S256")
                .appendQueryParameter("access_type", "offline")
                .appendQueryParameter("prompt", "consent")
                .appendQueryParameter("state", state)
                .build()

            val intent = Intent(Intent.ACTION_VIEW, authUri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            var isChromeAvailable = false
            try {
                activity.packageManager.getPackageInfo("com.android.chrome", 0)
                isChromeAvailable = true
            } catch (e: PackageManager.NameNotFoundException) {
                isChromeAvailable = false
            } catch (t: Throwable) {
                isChromeAvailable = false
            }

            if (isChromeAvailable) {
                intent.setPackage("com.android.chrome")
                intent.putExtra("android.support.customtabs.extra.SESSION", Bundle())
                FoundationLog.log("[AUTH] Membuka browser untuk otorisasi PKCE (chrome-customtab)")
            } else {
                FoundationLog.log("[AUTH] Membuka browser untuk otorisasi PKCE (browser-umum)")
            }

            activity.startActivity(intent)
        } catch (t: Throwable) {
            FoundationLog.error("[AUTH] Gagal memulai alur autentikasi", t)
        }
    }

    /**
     * Menangani callback redirect dari browser (dipanggil dari MainActivity.onNewIntent).
     * Mengembalikan true jika URI cocok dengan alur autentikasi ini.
     */
    fun handleRedirect(uri: Uri): Boolean {
        val scheme = uri.scheme ?: ""
        val host = uri.host ?: ""
        val path = uri.path ?: ""
        val uriStr = uri.toString()

        val isOurRedirect = (scheme == "com.rvmeur.transfer") &&
                (host == "oauth2redirect" || path == "/oauth2redirect" || uriStr.startsWith("com.rvmeur.transfer:/oauth2redirect"))
        if (!isOurRedirect) {
            return false
        }

        val error = uri.getQueryParameter("error")
        if (!error.isNullOrEmpty()) {
            FoundationLog.error("[AUTH] Otorisasi ditolak/dibatalkan: $error")
            clearTempAuth()
            notifyStatusChanged(false, null)
            return true
        }

        val state = uri.getQueryParameter("state")
        val code = uri.getQueryParameter("code")

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedState = prefs.getString(PREF_KEY_STATE, null)
        val verifier = prefs.getString(PREF_KEY_VERIFIER, null)

        clearTempAuth()

        if (state.isNullOrEmpty() || savedState.isNullOrEmpty() || state != savedState) {
            FoundationLog.error("[AUTH] State verifikasi tidak cocok atau verifier kedaluwarsa")
            notifyStatusChanged(false, null)
            return true
        }

        if (code.isNullOrEmpty()) {
            FoundationLog.error("[AUTH] Kode otorisasi kosong pada callback redirect")
            notifyStatusChanged(false, null)
            return true
        }

        if (verifier.isNullOrEmpty()) {
            FoundationLog.error("[AUTH] Code verifier tidak ditemukan di penyimpanan sementara")
            notifyStatusChanged(false, null)
            return true
        }

        FoundationLog.log("[AUTH] Redirect valid, menukar kode otorisasi di thread latar")
        Thread({
            exchangeCodeForTokens(code, verifier)
        }, "drive-auth-exchange").start()

        return true
    }

    private fun exchangeCodeForTokens(code: String, verifier: String) {
        try {
            val params = mapOf(
                "code" to code,
                "client_id" to ANDROID_CLIENT_ID,
                "redirect_uri" to ANDROID_REDIRECT_URI,
                "grant_type" to "authorization_code",
                "code_verifier" to verifier
            )

            val (status, body) = postForm(TOKEN_ENDPOINT, params)
            if (status !in 200..299) {
                FoundationLog.error("[AUTH] Gagal menukar kode otorisasi (HTTP $status)")
                notifyStatusChanged(false, null)
                return
            }

            val json = JSONObject(body)
            val refreshToken = json.optString("refresh_token", "")
            val accessToken = json.optString("access_token", "")
            val expiresIn = json.optLong("expires_in", 3600L)

            // Guard keras B1: refresh_token harus ada, jangan pernah simpan access_token sebagai refresh
            if (refreshToken.isEmpty()) {
                FoundationLog.error("[AUTH] Respons token TIDAK memuat refresh_token! Gagal mengikat akun permanen. DILARANG menyimpan access token sebagai refresh token.")
                notifyStatusChanged(false, null)
                return
            }

            val refreshTokenEnc = try {
                encrypt(refreshToken)
            } catch (e: Exception) {
                FoundationLog.error("[AUTH] Gagal mengenkripsi refresh token dengan AndroidKeyStore", e)
                notifyStatusChanged(false, null)
                return
            }

            synchronized(tokenLock) {
                cachedAccessToken = accessToken
                accessTokenExpiresAtMs = System.currentTimeMillis() + (expiresIn - 60L) * 1000L
            }

            // Ambil email akun via Drive about endpoint
            var email = ""
            try {
                val (aboutStatus, aboutBody) = getJsonWithBearer(DRIVE_ABOUT_ENDPOINT, accessToken)
                if (aboutStatus in 200..299) {
                    val aboutJson = JSONObject(aboutBody)
                    email = aboutJson.optJSONObject("user")?.optString("emailAddress", "") ?: ""
                } else {
                    FoundationLog.error("[AUTH] Gagal membaca profil Drive (HTTP $aboutStatus)")
                }
            } catch (t: Throwable) {
                FoundationLog.error("[AUTH] Gagal mengambil email pengguna", t)
            }

            if (email.isEmpty()) {
                email = "Google Drive"
            }

            db.saveCredentials(email, refreshTokenEnc)
            synchronized(tokenLock) {
                cachedEmail = email
            }

            FoundationLog.log("[AUTH] Berhasil terhubung ke akun Google Drive ($email)")
            notifyStatusChanged(true, email)
        } catch (t: Throwable) {
            FoundationLog.error("[AUTH] Terjadi kesalahan saat penukaran token", t)
            notifyStatusChanged(false, null)
        }
    }

    private fun clearTempAuth() {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().remove(PREF_KEY_STATE).remove(PREF_KEY_VERIFIER).apply()
        } catch (_: Exception) {
        }
    }

    /* =========================================================================
     * Token Access & Refresh
     * ========================================================================= */

    /**
     * Mengembalikan access token yang valid (dari cache atau disegarkan via refresh token).
     * Mengembalikan null jika belum terhubung atau refresh gagal (invalid_grant).
     */
    fun getAccessToken(): String? {
        val now = System.currentTimeMillis()
        synchronized(tokenLock) {
            if (!cachedAccessToken.isNullOrEmpty() && now < accessTokenExpiresAtMs) {
                return cachedAccessToken
            }
        }

        synchronized(refreshLock) {
            val nowLocked = System.currentTimeMillis()
            synchronized(tokenLock) {
                if (!cachedAccessToken.isNullOrEmpty() && nowLocked < accessTokenExpiresAtMs) {
                    return cachedAccessToken
                }
            }

            val creds = db.loadCredentials()
            if (creds == null) {
                return null
            }

            val refreshToken = try {
                decrypt(creds.refreshTokenEnc)
            } catch (e: Exception) {
                FoundationLog.error("[AUTH] Gagal mendekripsi refresh token", e)
                return null
            }

            return refreshAccessToken(refreshToken, creds.email)
        }
    }

    private fun refreshAccessToken(refreshToken: String, email: String): String? {
        try {
            val params = mapOf(
                "client_id" to ANDROID_CLIENT_ID,
                "grant_type" to "refresh_token",
                "refresh_token" to refreshToken
            )

            val (status, body) = postForm(TOKEN_ENDPOINT, params)
            if (status !in 200..299) {
                if (status == 400 && body.contains("invalid_grant")) {
                    FoundationLog.error("[AUTH] Refresh token dicabut atau tidak valid (invalid_grant). Perlu login ulang.")
                    db.clearCredentials()
                    synchronized(tokenLock) {
                        cachedAccessToken = null
                        accessTokenExpiresAtMs = 0L
                        cachedEmail = null
                    }
                    notifyStatusChanged(false, null)
                    return null
                }
                FoundationLog.error("[AUTH] Gagal menyegarkan access token (HTTP $status)")
                return null
            }

            val json = JSONObject(body)
            val accessToken = json.optString("access_token", "")
            val expiresIn = json.optLong("expires_in", 3600L)
            val newRefreshToken = json.optString("refresh_token", "")

            if (accessToken.isEmpty()) {
                FoundationLog.error("[AUTH] Respons refresh token tidak memiliki access_token")
                return null
            }

            // Jika Google menyertakan refresh token baru (rotasi), perbarui di DB
            if (newRefreshToken.isNotEmpty()) {
                try {
                    val enc = encrypt(newRefreshToken)
                    db.saveCredentials(email, enc)
                } catch (t: Throwable) {
                    FoundationLog.error("[AUTH] Gagal memperbarui rotated refresh token", t)
                }
            }

            synchronized(tokenLock) {
                cachedAccessToken = accessToken
                accessTokenExpiresAtMs = System.currentTimeMillis() + (expiresIn - 60L) * 1000L
                cachedEmail = email
            }

            FoundationLog.log("[AUTH] Access token berhasil disegarkan")
            return accessToken
        } catch (t: Throwable) {
            FoundationLog.error("[AUTH] Eksepsi saat menyegarkan access token", t)
            return null
        }
    }

    /* =========================================================================
     * Status Akun & Pemutusan Kredensial
     * ========================================================================= */

    fun isConnected(): Boolean {
        return db.loadCredentials() != null
    }

    fun accountEmail(): String? {
        synchronized(tokenLock) {
            if (!cachedEmail.isNullOrEmpty()) {
                return cachedEmail
            }
        }
        val creds = db.loadCredentials()
        if (creds != null) {
            synchronized(tokenLock) {
                cachedEmail = creds.email
            }
            return creds.email
        }
        return null
    }

    /**
     * Memutuskan akun Google Drive: menghapus kredensial lokal dan mencabut token secara best-effort.
     */
    fun disconnect() {
        val creds = db.loadCredentials()
        var tokenToRevoke: String? = null
        if (creds != null) {
            try {
                tokenToRevoke = decrypt(creds.refreshTokenEnc)
            } catch (_: Exception) {
            }
        }
        if (tokenToRevoke == null) {
            synchronized(tokenLock) {
                tokenToRevoke = cachedAccessToken
            }
        }

        db.clearCredentials()

        synchronized(tokenLock) {
            cachedAccessToken = null
            accessTokenExpiresAtMs = 0L
            cachedEmail = null
        }

        FoundationLog.log("[AUTH] Akun Google Drive diputuskan (kredensial lokal dihapus)")
        notifyStatusChanged(false, null)

        if (!tokenToRevoke.isNullOrEmpty()) {
            val revokeToken = tokenToRevoke
            Thread({
                try {
                    val params = mapOf("token" to revokeToken)
                    postForm(REVOKE_ENDPOINT, params)
                    FoundationLog.log("[AUTH] Best-effort revoke token selesai")
                } catch (t: Throwable) {
                    FoundationLog.log("[AUTH] Best-effort revoke token diabaikan: ${t.javaClass.simpleName}")
                }
            }, "drive-auth-revoke").start()
        }
    }

    private fun notifyStatusChanged(connected: Boolean, email: String?) {
        mainHandler.post {
            onAuthStatusChanged?.invoke(connected, email)
        }
    }

    /* =========================================================================
     * Jaringan HTTP Polos (HttpURLConnection)
     * ========================================================================= */

    private fun postForm(urlString: String, params: Map<String, String>): Pair<Int, String> {
        val url = URL(urlString)
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 15000
            conn.readTimeout = 15000
            conn.doOutput = true
            conn.doInput = true
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            conn.setRequestProperty("Accept", "application/json")

            val query = StringBuilder()
            var first = true
            for ((key, value) in params) {
                if (first) first = false else query.append('&')
                query.append(URLEncoder.encode(key, "UTF-8"))
                query.append('=')
                query.append(URLEncoder.encode(value, "UTF-8"))
            }

            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(query.toString())
                writer.flush()
            }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else (conn.errorStream ?: conn.inputStream)
            val body = BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
                reader.readText()
            }
            return Pair(code, body)
        } finally {
            conn.disconnect()
        }
    }

    private fun getJsonWithBearer(urlString: String, bearerToken: String): Pair<Int, String> {
        val url = URL(urlString)
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "GET"
            conn.connectTimeout = 15000
            conn.readTimeout = 15000
            conn.doInput = true
            conn.setRequestProperty("Authorization", "Bearer $bearerToken")
            conn.setRequestProperty("Accept", "application/json")

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else (conn.errorStream ?: conn.inputStream)
            val body = BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
                reader.readText()
            }
            return Pair(code, body)
        } finally {
            conn.disconnect()
        }
    }
}
