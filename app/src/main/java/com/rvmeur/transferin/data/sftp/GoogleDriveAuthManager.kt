package com.rvmeur.transferin.data.sftp

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
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
        // Web Client ID (Registered with Authorized Redirect URI: https://rvmeur.com/oauth2callback)
        const val WEB_CLIENT_ID = "343575435197-q6tnos0o1cgt8c0ir8alne21gitioj7i.apps.googleusercontent.com"
        // client_secret web sudah dicabut dari console & dihapus dari repo (rotasi GitGuardian 15 Nov 2026);
        // build rilis memakai Android OAuth client + PKCE tanpa secret (lihat foundation DriveAuth.kt);
        // untuk dev lokal modul ini, isi secret sendiri secara lokal dan JANGAN commit.
        const val WEB_CLIENT_SECRET = ""
        const val WEB_REDIRECT_URI = "https://rvmeur.com/oauth2callback"

        // Android Native Client ID (Registered with Package com.rvmeur.transferin & SHA-1)
        const val ANDROID_CLIENT_ID = "343575435197-ug3en8ich4l1grgae2m6kbpcub0glu77.apps.googleusercontent.com"
        const val ANDROID_REDIRECT_URI = "com.rvmeur.transferin:/oauth2redirect"

        // Active Configuration (Use Updated Web Client ID with verified https://rvmeur.com/oauth2callback)
        const val CLIENT_ID = WEB_CLIENT_ID
        const val CLIENT_SECRET = WEB_CLIENT_SECRET
        const val REDIRECT_URI = WEB_REDIRECT_URI
        const val SCOPE = "https://www.googleapis.com/auth/drive.file"

        fun getGoogleSignInClient(context: Context): GoogleSignInClient {
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestScopes(Scope(SCOPE))
                .requestServerAuthCode(WEB_CLIENT_ID)
                .requestEmail()
                .build()
            return GoogleSignIn.getClient(context, gso)
        }

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

    suspend fun handleAuthorizationCode(code: String, redirectUri: String = REDIRECT_URI): Result<NasConnection> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Exchanging OAuth authorization code using redirectUri=$redirectUri...")
            val formBuilder = FormBody.Builder()
                .add("client_id", CLIENT_ID)
                .add("code", code)
                .add("grant_type", "authorization_code")
                .add("redirect_uri", redirectUri)

            if (CLIENT_SECRET.isNotBlank()) {
                formBuilder.add("client_secret", CLIENT_SECRET)
            }

            val formBody = formBuilder.build()

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
