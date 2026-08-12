package com.rvmeur.transferin.data.sftp

import android.content.Context
import android.net.Uri
import android.util.Log
import com.rvmeur.transferin.domain.session.RemoteFolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit

class GoogleDriveClient(private val context: Context) : SftpClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    companion object {
        private const val TAG = "GoogleDriveClient"
    }

    private suspend fun getAccessToken(clientId: String, clientSecret: String, refreshToken: String): String =
        withContext(Dispatchers.IO) {
            val formBody = FormBody.Builder()
                .add("client_id", clientId)
                .add("client_secret", clientSecret)
                .add("refresh_token", refreshToken)
                .add("grant_type", "refresh_token")
                .build()

            val request = Request.Builder()
                .url("https://oauth2.googleapis.com/token")
                .post(formBody)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw Exception("Failed to refresh token: ${response.code} ${response.message}")
                }
                val json = JSONObject(response.body?.string() ?: "")
                json.getString("access_token")
            }
        }

    override suspend fun testConnection(
        host: String,
        port: Int,
        username: String,
        password: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val accessToken = getAccessToken(host, password, username)
            val request = Request.Builder()
                .url("https://www.googleapis.com/drive/v3/about?fields=user")
                .header("Authorization", "Bearer $accessToken")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Result.success(Unit)
                } else {
                    Result.failure(Exception("Google Drive API response error: ${response.code}"))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Connection test failed", e)
            Result.failure(e)
        }
    }

    override suspend fun getInitialPath(
        host: String,
        port: Int,
        username: String,
        password: String
    ): Result<String> {
        return Result.success("root")
    }

    override suspend fun listDirectories(
        host: String,
        port: Int,
        username: String,
        password: String,
        path: String
    ): Result<List<RemoteFolder>> = withContext(Dispatchers.IO) {
        try {
            val accessToken = getAccessToken(host, password, username)
            val parentId = if (path.isBlank() || path == "root") "root" else path
            val query = "'$parentId' in parents and mimeType = 'application/vnd.google-apps.folder' and trashed = false"
            val url = "https://www.googleapis.com/drive/v3/files?q=${Uri.encode(query)}&fields=files(id,name)&pageSize=1000"

            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $accessToken")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("List directories failed: ${response.code}"))
                }
                val filesJson = JSONObject(response.body?.string() ?: "").getJSONArray("files")
                val folders = mutableListOf<RemoteFolder>()
                for (i in 0 until filesJson.length()) {
                    val file = filesJson.getJSONObject(i)
                    folders.add(RemoteFolder(
                        name = file.getString("name"),
                        fullPath = file.getString("id")
                    ))
                }
                Result.success(folders)
            }
        } catch (e: Exception) {
            Log.e(TAG, "List directories failed", e)
            Result.failure(e)
        }
    }

    override suspend fun createDirectory(
        host: String,
        port: Int,
        username: String,
        password: String,
        parentPath: String,
        folderName: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val accessToken = getAccessToken(host, password, username)
            val parentId = when {
                parentPath.isBlank() || parentPath == "root" -> "root"
                parentPath.contains("/") || parentPath.length < 15 || parentPath.contains(" ") -> "root"
                else -> parentPath
            }
            val metadata = JSONObject().apply {
                put("name", folderName)
                put("mimeType", "application/vnd.google-apps.folder")
                put("parents", org.json.JSONArray().apply { put(parentId) })
            }

            val request = Request.Builder()
                .url("https://www.googleapis.com/drive/v3/files")
                .header("Authorization", "Bearer $accessToken")
                .post(metadata.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("Create directory failed: ${response.code}"))
                }
                val created = JSONObject(response.body?.string() ?: "")
                Result.success(created.getString("id"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Create directory failed", e)
            Result.failure(e)
        }
    }

    override suspend fun uploadFileFromUri(
        host: String,
        port: Int,
        username: String,
        password: String,
        localUri: Uri,
        remoteDirectory: String,
        remoteFileName: String,
        onProgress: ((bytesTransferred: Long, totalBytes: Long) -> Unit)?
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val accessToken = getAccessToken(host, password, username)
            val parentId = resolveFolderId(accessToken, remoteDirectory)

            val metadata = JSONObject().apply {
                put("name", remoteFileName)
                put("parents", org.json.JSONArray().apply { put(parentId) })
            }

            val resolvedFileSize = runCatching {
                context.contentResolver.openAssetFileDescriptor(localUri, "r")?.use { it.length }
            }.getOrNull()?.takeIf { it > 0L }
                ?: runCatching {
                    if (localUri.scheme == "file") {
                        java.io.File(localUri.path.orEmpty()).length()
                    } else {
                        -1L
                    }
                }.getOrDefault(-1L)

            val streamRequestBody = object : okhttp3.RequestBody() {
                override fun contentType() = "application/octet-stream".toMediaType()
                override fun contentLength() = resolvedFileSize
                override fun writeTo(sink: okio.BufferedSink) {
                    context.contentResolver.openInputStream(localUri)?.use { input ->
                        val buffer = ByteArray(16384)
                        var read: Int
                        var totalWritten = 0L
                        while (input.read(buffer).also { read = it } != -1) {
                            sink.write(buffer, 0, read)
                            totalWritten += read
                            onProgress?.invoke(totalWritten, resolvedFileSize.coerceAtLeast(totalWritten))
                        }
                    }
                }
            }

            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addPart(
                    MultipartBody.Part.create(
                        metadata.toString().toRequestBody("application/json; charset=UTF-8".toMediaType())
                    )
                )
                .addPart(
                    MultipartBody.Part.create(
                        streamRequestBody
                    )
                )
                .build()

            val request = Request.Builder()
                .url("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart")
                .header("Authorization", "Bearer $accessToken")
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Result.success(Unit)
                } else {
                    Result.failure(Exception("Google Drive upload failed: ${response.code}"))
                }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Upload failed", e)
            Result.failure(e)
        }
    }

    override suspend fun uploadWithTwoPhaseCommit(
        host: String,
        port: Int,
        username: String,
        password: String,
        localUri: Uri,
        remoteDirectory: String,
        remoteFileName: String,
        expectedSize: Long,
        startOffset: Long,
        onProgress: ((bytesTransferred: Long, totalBytes: Long) -> Unit)?
    ): Result<Unit> {
        return uploadFileFromUri(host, port, username, password, localUri, remoteDirectory, remoteFileName, onProgress)
    }

    override suspend fun deleteTmpFile(
        host: String,
        port: Int,
        username: String,
        password: String,
        remoteDirectory: String,
        remoteFileName: String
    ): Result<Unit> {
        return Result.success(Unit)
    }

    override suspend fun listTmpFiles(
        host: String,
        port: Int,
        username: String,
        password: String,
        remoteDirectory: String
    ): Result<List<TmpFileInfo>> {
        return Result.success(emptyList())
    }

    override suspend fun checkWritePermission(
        host: String,
        port: Int,
        username: String,
        password: String,
        directoryPath: String
    ): Result<Boolean> {
        return Result.success(true)
    }

    private suspend fun resolveFolderId(accessToken: String, parentIdOrName: String): String {
        val cleaned = parentIdOrName.trim().trim('/')
        if (cleaned.isBlank() || cleaned == "root" || cleaned == "/") {
            return "root"
        }

        // If it looks like a valid Google Drive ID (alphanumeric, 25-50 chars, no spaces/slashes)
        val driveIdRegex = Regex("^[a-zA-Z0-9_-]{25,55}$")
        if (driveIdRegex.matches(cleaned)) {
            return cleaned
        }

        // It's a folder name! Let's search if it exists under root
        Log.d(TAG, "Resolving folder by name: $cleaned")
        try {
            val query = "'root' in parents and name = '$cleaned' and mimeType = 'application/vnd.google-apps.folder' and trashed = false"
            val url = "https://www.googleapis.com/drive/v3/files?q=${Uri.encode(query)}&fields=files(id,name)&pageSize=1"

            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $accessToken")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val files = JSONObject(response.body?.string() ?: "").getJSONArray("files")
                    if (files.length() > 0) {
                        val folderId = files.getJSONObject(0).getString("id")
                        Log.d(TAG, "Found existing folder named '$parentIdOrName' -> ID: $folderId")
                        return folderId
                    }
                }
            }

            // If we are here, it does not exist. Let's create it!
            Log.d(TAG, "Folder '$cleaned' not found, creating it under root...")
            val createUrl = "https://www.googleapis.com/drive/v3/files"
            val metadata = JSONObject().apply {
                put("name", cleaned)
                put("mimeType", "application/vnd.google-apps.folder")
                put("parents", org.json.JSONArray().apply { put("root") })
            }
            val body = metadata.toString().toRequestBody("application/json; charset=UTF-8".toMediaType())
            val createRequest = Request.Builder()
                .url(createUrl)
                .header("Authorization", "Bearer $accessToken")
                .post(body)
                .build()

            client.newCall(createRequest).execute().use { response ->
                if (response.isSuccessful) {
                    val folderId = JSONObject(response.body?.string() ?: "").getString("id")
                    Log.d(TAG, "Created new folder named '$cleaned' -> ID: $folderId")
                    return folderId
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error resolving/creating folder '$cleaned'", e)
        }

        return "root"
    }
}
