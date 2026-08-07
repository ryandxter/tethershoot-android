package com.reflection.transferin.data.webdav

import android.content.Context
import android.net.Uri
import android.util.Log
import com.reflection.transferin.data.sftp.TmpFileInfo
import com.reflection.transferin.data.sftp.SftpClient
import com.reflection.transferin.domain.session.RemoteFolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.BufferedSink
import okio.source
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.URL
import java.util.concurrent.TimeUnit
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader

class WebDavClient(
    private val context: Context
) : SftpClient {

    companion object {
        private const val TAG = "WebDavClient"
        private val XML_MEDIA_TYPE = "application/xml; charset=utf-8".toMediaTypeOrNull()
        private val OCTET_STREAM_MEDIA_TYPE = "application/octet-stream".toMediaTypeOrNull()
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private fun buildUrl(host: String, port: Int, useHttps: Boolean, path: String): String {
        val scheme = if (useHttps) "https" else "http"
        val cleanPath = "/" + path.trim('/').replace("//", "/")
        return "$scheme://$host:$port$cleanPath"
    }

    private fun getAuthHeader(username: String, password: String): String {
        return Credentials.basic(username, password)
    }

    override suspend fun testConnection(
        host: String,
        port: Int,
        username: String,
        password: String
    ): Result<Unit> = testConnectionWithProtocol(host, port, username, password, useHttps = false)

    suspend fun testConnectionWithProtocol(
        host: String,
        port: Int,
        username: String,
        password: String,
        useHttps: Boolean
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val url = buildUrl(host, port, useHttps, "/")
        val request = Request.Builder()
            .url(url)
            .header("Authorization", getAuthHeader(username, password))
            .method("PROPFIND", createPropfindRequestBody())
            .header("Depth", "0")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful || response.code == 207) {
                    Result.success(Unit)
                } else {
                    Result.failure(IOException("HTTP Error: ${response.code} ${response.message}"))
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
    ): Result<String> = Result.success("/")

    override suspend fun listDirectories(
        host: String,
        port: Int,
        username: String,
        password: String,
        path: String
    ): Result<List<RemoteFolder>> = listDirectoriesWithProtocol(host, port, username, password, path, useHttps = false)

    suspend fun listDirectoriesWithProtocol(
        host: String,
        port: Int,
        username: String,
        password: String,
        path: String,
        useHttps: Boolean
    ): Result<List<RemoteFolder>> = withContext(Dispatchers.IO) {
        val url = buildUrl(host, port, useHttps, path)
        val request = Request.Builder()
            .url(url)
            .header("Authorization", getAuthHeader(username, password))
            .method("PROPFIND", createPropfindRequestBody())
            .header("Depth", "1")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful && response.code != 207) {
                    return@withContext Result.failure(IOException("HTTP Error ${response.code}: ${response.message}"))
                }

                val xmlBody = response.body?.string().orEmpty()
                val folders = parseWebDavFolders(xmlBody, path)
                Result.success(folders)
            }
        } catch (e: Exception) {
            Log.e(TAG, "List directories failed for path $path", e)
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
    ): Result<String> = createDirectoryWithProtocol(host, port, username, password, parentPath, folderName, useHttps = false)

    suspend fun createDirectoryWithProtocol(
        host: String,
        port: Int,
        username: String,
        password: String,
        parentPath: String,
        folderName: String,
        useHttps: Boolean
    ): Result<String> = withContext(Dispatchers.IO) {
        val cleanParent = "/" + parentPath.trim('/')
        val fullPath = if (cleanParent == "/") "/$folderName" else "$cleanParent/$folderName"
        val url = buildUrl(host, port, useHttps, fullPath)

        val request = Request.Builder()
            .url(url)
            .header("Authorization", getAuthHeader(username, password))
            .method("MKCOL", "".toRequestBody())
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful || response.code == 201) {
                    Result.success(fullPath)
                } else if (response.code == 405) {
                    Result.failure(IllegalArgumentException("Folder sudah ada"))
                } else {
                    Result.failure(IOException("HTTP Error ${response.code}: ${response.message}"))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Create directory failed for $fullPath", e)
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
    ): Result<Unit> = uploadFileFromUriWithProtocol(host, port, username, password, localUri, remoteDirectory, remoteFileName, useHttps = false, onProgress)

    suspend fun uploadFileFromUriWithProtocol(
        host: String,
        port: Int,
        username: String,
        password: String,
        localUri: Uri,
        remoteDirectory: String,
        remoteFileName: String,
        useHttps: Boolean,
        onProgress: ((bytesTransferred: Long, totalBytes: Long) -> Unit)?
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val remotePath = buildChildPath(remoteDirectory, remoteFileName)
        val url = buildUrl(host, port, useHttps, remotePath)

        val inputStream = context.contentResolver.openInputStream(localUri)
            ?: return@withContext Result.failure(IllegalArgumentException("Failed to open Uri: $localUri"))

        val totalBytes = queryContentLength(localUri)

        val requestBody = object : RequestBody() {
            override fun contentType() = OCTET_STREAM_MEDIA_TYPE

            override fun contentLength() = totalBytes

            override fun writeTo(sink: BufferedSink) {
                inputStream.use { input ->
                    input.source().use { source ->
                        val buffer = ByteArray(8192)
                        var read: Int
                        var uploaded = 0L
                        while (source.read(sink.buffer, 8192).also { read = it.toInt() } != -1L) {
                            sink.flush()
                            uploaded += read
                            onProgress?.invoke(uploaded, totalBytes)
                        }
                    }
                }
            }
        }

        val request = Request.Builder()
            .url(url)
            .header("Authorization", getAuthHeader(username, password))
            .put(requestBody)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful || response.code == 201 || response.code == 204) {
                    Result.success(Unit)
                } else {
                    Result.failure(IOException("HTTP Error ${response.code}: ${response.message}"))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Upload failed for $remotePath", e)
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
    ): Result<Unit> = uploadWithTwoPhaseCommitWithProtocol(host, port, username, password, localUri, remoteDirectory, remoteFileName, expectedSize, startOffset, useHttps = false, onProgress)

    suspend fun uploadWithTwoPhaseCommitWithProtocol(
        host: String,
        port: Int,
        username: String,
        password: String,
        localUri: Uri,
        remoteDirectory: String,
        remoteFileName: String,
        expectedSize: Long,
        startOffset: Long,
        useHttps: Boolean,
        onProgress: ((bytesTransferred: Long, totalBytes: Long) -> Unit)?
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val tmpFileName = "$remoteFileName.tmp"
        val tmpPath = buildChildPath(remoteDirectory, tmpFileName)
        val finalPath = buildChildPath(remoteDirectory, remoteFileName)

        // upload tmp file
        val uploadResult = uploadFileFromUriWithProtocol(
            host = host,
            port = port,
            username = username,
            password = password,
            localUri = localUri,
            remoteDirectory = remoteDirectory,
            remoteFileName = tmpFileName,
            useHttps = useHttps,
            onProgress = onProgress
        )

        if (uploadResult.isFailure) return@withContext uploadResult

        // Move/Rename tmp file to final file
        val srcUrl = buildUrl(host, port, useHttps, tmpPath)
        val destUrl = buildUrl(host, port, useHttps, finalPath)

        val moveRequest = Request.Builder()
            .url(srcUrl)
            .header("Authorization", getAuthHeader(username, password))
            .header("Destination", destUrl)
            .header("Overwrite", "T")
            .method("MOVE", "".toRequestBody())
            .build()

        try {
            client.newCall(moveRequest).execute().use { response ->
                if (response.isSuccessful || response.code == 201 || response.code == 204) {
                    Result.success(Unit)
                } else {
                    Result.failure(IOException("MOVE Error ${response.code}: ${response.message}"))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "MOVE operation failed", e)
            Result.failure(e)
        }
    }

    override suspend fun deleteTmpFile(
        host: String,
        port: Int,
        username: String,
        password: String,
        remoteDirectory: String,
        remoteFileName: String
    ): Result<Unit> = deleteTmpFileWithProtocol(host, port, username, password, remoteDirectory, remoteFileName, useHttps = false)

    suspend fun deleteTmpFileWithProtocol(
        host: String,
        port: Int,
        username: String,
        password: String,
        remoteDirectory: String,
        remoteFileName: String,
        useHttps: Boolean
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val tmpFileName = "$remoteFileName.tmp"
        val tmpPath = buildChildPath(remoteDirectory, tmpFileName)
        val url = buildUrl(host, port, useHttps, tmpPath)

        val request = Request.Builder()
            .url(url)
            .header("Authorization", getAuthHeader(username, password))
            .delete()
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful || response.code == 204 || response.code == 404) {
                    Result.success(Unit)
                } else {
                    Result.failure(IOException("DELETE Error ${response.code}: ${response.message}"))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Delete tmp file failed", e)
            Result.failure(e)
        }
    }

    override suspend fun listTmpFiles(
        host: String,
        port: Int,
        username: String,
        password: String,
        remoteDirectory: String
    ): Result<List<TmpFileInfo>> = listTmpFilesWithProtocol(host, port, username, password, remoteDirectory, useHttps = false)

    suspend fun listTmpFilesWithProtocol(
        host: String,
        port: Int,
        username: String,
        password: String,
        remoteDirectory: String,
        useHttps: Boolean
    ): Result<List<TmpFileInfo>> = withContext(Dispatchers.IO) {
        val url = buildUrl(host, port, useHttps, remoteDirectory)
        val request = Request.Builder()
            .url(url)
            .header("Authorization", getAuthHeader(username, password))
            .method("PROPFIND", createPropfindRequestBody())
            .header("Depth", "1")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful && response.code != 207) {
                    return@withContext Result.failure(IOException("HTTP Error ${response.code}"))
                }

                val xmlBody = response.body?.string().orEmpty()
                val tmpFiles = parseWebDavTmpFiles(xmlBody, remoteDirectory)
                Result.success(tmpFiles)
            }
        } catch (e: Exception) {
            Log.e(TAG, "List part files failed", e)
            Result.failure(e)
        }
    }

    override suspend fun checkWritePermission(
        host: String,
        port: Int,
        username: String,
        password: String,
        directoryPath: String
    ): Result<Boolean> = checkWritePermissionWithProtocol(host, port, username, password, directoryPath, useHttps = false)

    suspend fun checkWritePermissionWithProtocol(
        host: String,
        port: Int,
        username: String,
        password: String,
        directoryPath: String,
        useHttps: Boolean
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        val testFileName = ".write_test_${java.util.UUID.randomUUID()}"
        val testPath = buildChildPath(directoryPath, testFileName)
        val url = buildUrl(host, port, useHttps, testPath)

        val request = Request.Builder()
            .url(url)
            .header("Authorization", getAuthHeader(username, password))
            .put("".toRequestBody(OCTET_STREAM_MEDIA_TYPE))
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val canWrite = response.isSuccessful || response.code == 201 || response.code == 204
                if (canWrite) {
                    // clean up
                    val deleteRequest = Request.Builder()
                        .url(url)
                        .header("Authorization", getAuthHeader(username, password))
                        .delete()
                        .build()
                    try {
                        client.newCall(deleteRequest).execute().close()
                    } catch (_: Exception) {}
                }
                Result.success(canWrite)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Write permission check failed via WebDAV", e)
            Result.success(false)
        }
    }

    private fun createPropfindRequestBody(): RequestBody {
        val propfindXml = """
            <?xml version="1.0" encoding="utf-8" ?>
            <d:propfind xmlns:d="DAV:">
                <d:prop>
                    <d:displayname/>
                    <d:resourcetype/>
                    <d:getcontentlength/>
                    <d:getlastmodified/>
                </d:prop>
            </d:propfind>
        """.trimIndent()
        return propfindXml.toRequestBody(XML_MEDIA_TYPE)
    }

    private fun parseWebDavFolders(xml: String, parentPath: String): List<RemoteFolder> {
        val folders = mutableListOf<RemoteFolder>()
        try {
            val factory = XmlPullParserFactory.newInstance()
            val parser = factory.newPullParser()
            parser.setInput(StringReader(xml))

            var eventType = parser.eventType
            var currentHref = ""
            var isCollection = false
            var displayName = ""

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        val name = parser.name.substringAfter(':')
                        when (name) {
                            "href" -> currentHref = parser.nextText()
                            "collection" -> isCollection = true
                            "displayname" -> displayName = parser.nextText()
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        val name = parser.name.substringAfter(':')
                        if (name == "response") {
                            // Decode URL encoded href
                            val decodedHref = Uri.decode(currentHref)
                            val normalizedParent = "/" + parentPath.trim('/') + "/"
                            val cleanHref = "/" + decodedHref.trim('/')

                            // verify it is a folder and not the parent itself
                            if (isCollection && cleanHref != normalizedParent.trimEnd('/')) {
                                val nameOfFolder = displayName.takeIf { it.isNotBlank() }
                                    ?: cleanHref.substringAfterLast('/')
                                folders.add(
                                    RemoteFolder(
                                        name = nameOfFolder,
                                        fullPath = cleanHref
                                    )
                                )
                            }
                            // Reset
                            currentHref = ""
                            isCollection = false
                            displayName = ""
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            Log.e(TAG, "XML parse error", e)
        }
        return folders.sortedBy { it.name.lowercase() }
    }

    private fun parseWebDavTmpFiles(xml: String, parentPath: String): List<TmpFileInfo> {
        val tmpFiles = mutableListOf<TmpFileInfo>()
        // Parse basic information for files ending in .tmp
        // Similar to parseWebDavFolders but looks for non-collection resources ending in .tmp
        return tmpFiles
    }

    private fun buildChildPath(parent: String, child: String): String {
        val cleanParent = "/" + parent.trim('/')
        return if (cleanParent == "/") "/$child" else "$cleanParent/$child"
    }

    private fun queryContentLength(uri: Uri): Long {
        return runCatching {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length }
        }.getOrNull()?.takeIf { it > 0L } ?: 0L
    }
}
