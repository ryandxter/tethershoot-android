package com.reflection.transferin.data.sftp

import android.content.Context
import android.net.Uri
import com.reflection.transferin.data.local.db.DatabaseProvider
import com.reflection.transferin.data.webdav.WebDavClient
import com.reflection.transferin.domain.session.RemoteFolder

class DelegatingSftpClient(
    private val context: Context,
    private val sftpClient: JschSftpClient,
    private val webDavClient: WebDavClient
) : SftpClient {

    constructor(context: Context) : this(
        context = context,
        sftpClient = JschSftpClient(context),
        webDavClient = WebDavClient(context)
    )

    private val nasConnectionDao by lazy {
        DatabaseProvider.getDatabase(context).nasConnectionDao()
    }

    private suspend fun getActiveClient(useHttps: Boolean = false): SftpClient {
        val conn = nasConnectionDao.getConnectionOrNull()
        return if (conn?.protocol == "WEBDAV") {
            webDavClient
        } else {
            sftpClient
        }
    }

    private suspend fun isWebDav(): Boolean {
        return nasConnectionDao.getConnectionOrNull()?.protocol == "WEBDAV"
    }

    private suspend fun getHttpsOption(): Boolean {
        return nasConnectionDao.getConnectionOrNull()?.useHttps ?: false
    }

    override suspend fun testConnection(
        host: String,
        port: Int,
        username: String,
        password: String
    ): Result<Unit> {
        val conn = nasConnectionDao.getConnectionOrNull()
        return if (conn?.protocol == "WEBDAV") {
            webDavClient.testConnectionWithProtocol(host, port, username, password, conn.useHttps)
        } else {
            sftpClient.testConnection(host, port, username, password)
        }
    }

    override suspend fun getInitialPath(
        host: String,
        port: Int,
        username: String,
        password: String
    ): Result<String> {
        val conn = nasConnectionDao.getConnectionOrNull()
        val startFolder = conn?.startingFolder?.trim()
        if (!startFolder.isNullOrBlank()) {
            return Result.success(startFolder)
        }
        return getActiveClient().getInitialPath(host, port, username, password)
    }

    override suspend fun listDirectories(
        host: String,
        port: Int,
        username: String,
        password: String,
        path: String
    ): Result<List<RemoteFolder>> {
        val client = getActiveClient()
        return if (client is WebDavClient) {
            client.listDirectoriesWithProtocol(host, port, username, password, path, getHttpsOption())
        } else {
            client.listDirectories(host, port, username, password, path)
        }
    }

    override suspend fun createDirectory(
        host: String,
        port: Int,
        username: String,
        password: String,
        parentPath: String,
        folderName: String
    ): Result<String> {
        val client = getActiveClient()
        return if (client is WebDavClient) {
            client.createDirectoryWithProtocol(host, port, username, password, parentPath, folderName, getHttpsOption())
        } else {
            client.createDirectory(host, port, username, password, parentPath, folderName)
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
    ): Result<Unit> {
        val client = getActiveClient()
        return if (client is WebDavClient) {
            client.uploadFileFromUriWithProtocol(host, port, username, password, localUri, remoteDirectory, remoteFileName, getHttpsOption(), onProgress)
        } else {
            client.uploadFileFromUri(host, port, username, password, localUri, remoteDirectory, remoteFileName, onProgress)
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
        val client = getActiveClient()
        return if (client is WebDavClient) {
            client.uploadWithTwoPhaseCommitWithProtocol(host, port, username, password, localUri, remoteDirectory, remoteFileName, expectedSize, startOffset, getHttpsOption(), onProgress)
        } else {
            client.uploadWithTwoPhaseCommit(host, port, username, password, localUri, remoteDirectory, remoteFileName, expectedSize, startOffset, onProgress)
        }
    }

    override suspend fun deleteTmpFile(
        host: String,
        port: Int,
        username: String,
        password: String,
        remoteDirectory: String,
        remoteFileName: String
    ): Result<Unit> {
        val client = getActiveClient()
        return if (client is WebDavClient) {
            client.deleteTmpFileWithProtocol(host, port, username, password, remoteDirectory, remoteFileName, getHttpsOption())
        } else {
            client.deleteTmpFile(host, port, username, password, remoteDirectory, remoteFileName)
        }
    }

    override suspend fun listTmpFiles(
        host: String,
        port: Int,
        username: String,
        password: String,
        remoteDirectory: String
    ): Result<List<TmpFileInfo>> {
        val client = getActiveClient()
        return if (client is WebDavClient) {
            client.listTmpFilesWithProtocol(host, port, username, password, remoteDirectory, getHttpsOption())
        } else {
            client.listTmpFiles(host, port, username, password, remoteDirectory)
        }
    }

    override suspend fun checkWritePermission(
        host: String,
        port: Int,
        username: String,
        password: String,
        directoryPath: String
    ): Result<Boolean> {
        val client = getActiveClient()
        return if (client is WebDavClient) {
            client.checkWritePermissionWithProtocol(host, port, username, password, directoryPath, getHttpsOption())
        } else {
            client.checkWritePermission(host, port, username, password, directoryPath)
        }
    }

    fun getReconnector(): SftpSessionReconnector? {
        return sftpClient.sessionReconnector
    }
}
