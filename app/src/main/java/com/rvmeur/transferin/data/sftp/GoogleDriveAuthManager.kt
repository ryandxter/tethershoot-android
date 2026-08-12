package com.rvmeur.transferin.data.sftp

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.rvmeur.transferin.domain.repository.NasConnection
import com.rvmeur.transferin.domain.repository.NasConnectionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class GoogleDriveAuthManager(
    private val context: Context,
    private val nasConnectionRepository: NasConnectionRepository
) {
    companion object {
        private const val TAG = "GoogleDriveAuthManager"
        const val CLIENT_ID = "343575435197-q6tnos0o1cgt8c0ir8alne21gitioj7i.apps.googleusercontent.com"
        const val CLIENT_SECRET = "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"
        const val REDIRECT_URI = "com.rvmeur.transferin:/oauth2redirect"
        const val SCOPE = "https://www.googleapis.com/auth/drive.file"

        fun getAuthorizationUri(): Uri {
            return Uri.parse("https://accounts.google.com/o/oauth2/v2/auth").buildUpon()
                .appendQueryParameter("client_id", CLIENT_ID)
                .appendQueryParameter("redirect_uri", REDIRECT_URI)
                .appendQueryParameter("response_type", "code")
                .appendQueryParameter("scope", SCOPE)
                .appendQueryParameter("access_type", "offline")
                .appendQueryParameter("prompt", "select_account consent")
                .build()
        }

        fun launchAuthorizationBrowser(context: Context) {
            val intent = Intent(Intent.ACTION_VIEW, getAuthorizationUri()).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    private val httpClient = OkHttpClient()

    suspend fun handleAuthorizationCode(code: String): Result<NasConnection> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Exchanging OAuth authorization code for real Google tokens...")
            val formBody = FormBody.Builder()
                .add("client_id", CLIENT_ID)
                .add("client_secret", CLIENT_SECRET)
                .add("code", code)
                .add("grant_type", "authorization_code")
                .add("redirect_uri", REDIRECT_URI)
                .build()

            val request = Request.Builder()
                .url("https://oauth2.googleapis.com/token")
                .post(formBody)
                .build()

            httpClient.newCall(request).execute().use { response ->
                val bodyString = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    Log.e(TAG, "Token exchange error: ${response.code} $bodyString")
                    return@withContext Result.failure(Exception("Gagal menukar token OAuth: ${response.code}"))
                }

                val json = JSONObject(bodyString)
                val refreshToken = json.optString("refresh_token", "")
                val accessToken = json.getString("access_token")

                Log.d(TAG, "OAuth Token exchange successful! Refresh Token retrieved: ${refreshToken.take(10)}...")

                val connection = NasConnection(
                    name = "Google Drive",
                    host = CLIENT_ID,
                    port = 443,
                    username = if (refreshToken.isNotBlank()) refreshToken else accessToken,
                    password = CLIENT_SECRET,
                    defaultRemoteFolder = "root",
                    protocol = "GOOGLE_DRIVE",
                    useHttps = true,
                    startingFolder = "RVM_Photos"
                )

                nasConnectionRepository.saveConnection(connection)
                Result.success(connection)
            }
        } catch (e: Throwable) {
            Log.e(TAG, "OAuth token exchange exception", e)
            Result.failure(e)
        }
    }
}
