package com.rvmeur.transferin.data.sftp

import android.content.Context
import android.net.Uri
import com.rvmeur.transferin.data.local.db.DatabaseProvider
import com.rvmeur.transferin.domain.session.RemoteFolder

class DelegatingSftpClient(
    private val context: Context,
    private val googleDriveClient: GoogleDriveClient
) : SftpClient {

    constructor(context: Context) : this(
        context = context,
        googleDriveClient = GoogleDriveClient(context)
    )

    private val nasConnectionDao by lazy {
        DatabaseProvider.getDatabase(context).nasConnectionDao()
    }

    override suspend fun testConnection(
        host: String,
        port: Int,
        username: String,
        password: String
    ): Result<Unit> {
        return googleDriveClient.testConnection(host, port, username, password)
    }

    override suspend fun getInitialPath(
        host: String,
        port: Int,
        username: String,
        password: String
    ): Result<String> {
        val conn = nasConnectionDao.getConnectionOrNull()
        val defaultFolder = conn?.defaultRemoteFolder?.trim()
        if (!defaultFolder.isNullOrBlank()) {
            return Result.success(defaultFolder)
        }
        return googleDriveClient.getInitialPath(host, port, username, password)
    }

    override suspend fun listDirectories(
        host: String,
        port: Int,
        username: String,
        password: String,
        path: String
    ): Result<List<RemoteFolder>> {
        return googleDriveClient.listDirectories(host, port, username, password, path)
    }

    override suspend fun createDirectory(
        host: String,
        port: Int,
        username: String,
        password: String,
        parentPath: String,
        folderName: String
    ): Result<String> {
        return googleDriveClient.createDirectory(host, port, username, password, parentPath, folderName)
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
        return googleDriveClient.uploadFileFromUri(host, port, username, password, localUri, remoteDirectory, remoteFileName, onProgress)
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
        return googleDriveClient.uploadWithTwoPhaseCommit(host, port, username, password, localUri, remoteDirectory, remoteFileName, expectedSize, startOffset, onProgress)
    }

    override suspend fun deleteTmpFile(
        host: String,
        port: Int,
        username: String,
        password: String,
        remoteDirectory: String,
        remoteFileName: String
    ): Result<Unit> {
        return googleDriveClient.deleteTmpFile(host, port, username, password, remoteDirectory, remoteFileName)
    }

    override suspend fun listTmpFiles(
        host: String,
        port: Int,
        username: String,
        password: String,
        remoteDirectory: String
    ): Result<List<TmpFileInfo>> {
        return googleDriveClient.listTmpFiles(host, port, username, password, remoteDirectory)
    }

    override suspend fun checkWritePermission(
        host: String,
        port: Int,
        username: String,
        password: String,
        directoryPath: String
    ): Result<Boolean> {
        return googleDriveClient.checkWritePermission(host, port, username, password, directoryPath)
    }
}
