package com.reflection.transferin.data.sftp

import android.content.Context
import android.net.Uri
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import com.jcraft.jsch.SftpProgressMonitor
import com.reflection.transferin.domain.session.RemoteFolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.util.Properties
import java.util.Vector

class JschSftpClient(
    private val context: Context
) : SftpClient {

    private var sessionPool: SftpSessionPool? = null

    /**
     * Initializes the session pool with the given configuration.
     * Must be called before using pooled session operations.
     *
     * @param config The connection configuration for the pool
     */
    fun initializePool(config: SftpConnectionConfig) {
        sessionPool = SftpSessionPool(config)
    }

    override suspend fun testConnection(
        host: String,
        port: Int,
        username: String,
        password: String
    ): Result<Unit> = withPooledSession(host, port, username, password) {
        Result.success(Unit)
    }

    override suspend fun getInitialPath(
        host: String,
        port: Int,
        username: String,
        password: String
    ): Result<String> = withSftp(host, port, username, password) { channel ->
        val path = channel.pwd()
        Result.success(path)
    }

    override suspend fun listDirectories(
        host: String,
        port: Int,
        username: String,
        password: String,
        path: String
    ): Result<List<RemoteFolder>> = withSftp(host, port, username, password) { channel ->
        val safePath = path.trim().ifBlank { channel.pwd() }

        @Suppress("UNCHECKED_CAST")
        val entries = channel.ls(safePath) as Vector<ChannelSftp.LsEntry>

        val folders = entries
            .asSequence()
            .filter { entry ->
                val name = entry.filename
                name != "." &&
                        name != ".." &&
                        entry.attrs.isDir
            }
            .map { entry ->
                val name = entry.filename
                RemoteFolder(
                    name = name,
                    fullPath = buildChildPath(safePath, name)
                )
            }
            .sortedBy { it.name.lowercase() }
            .toList()

        Result.success(folders)
    }

    override suspend fun createDirectory(
        host: String,
        port: Int,
        username: String,
        password: String,
        parentPath: String,
        folderName: String
    ): Result<String> = withSftp(host, port, username, password) { channel ->
        val safeParentPath = parentPath.trim().ifBlank { channel.pwd() }
        val safeFolderName = folderName.trim()

        require(safeFolderName.isNotBlank()) { "Nama folder tidak boleh kosong" }
        require(!safeFolderName.contains("/")) { "Nama folder tidak valid" }

        val fullPath = buildChildPath(safeParentPath, safeFolderName)

        try {
            channel.stat(fullPath)
            Result.failure(IllegalArgumentException("Folder sudah ada"))
        } catch (_: Exception) {
            channel.mkdir(fullPath)
            Result.success(fullPath)
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
    ): Result<Unit> = withSftp(host, port, username, password) { channel ->
        val safeRemoteDirectory = remoteDirectory.trim().ifBlank { channel.pwd() }
        val safeRemoteFileName = sanitizeRemoteFileName(remoteFileName)

        require(safeRemoteFileName.isNotBlank()) { "Nama file remote tidak boleh kosong" }

        val inputStream = context.contentResolver.openInputStream(localUri)
            ?: return@withSftp Result.failure(
                IllegalArgumentException("Gagal membuka file dari URI: $localUri")
            )

        inputStream.use { stream ->
            val totalBytes = queryContentLength(localUri)

            val remoteFullPath = buildChildPath(safeRemoteDirectory, safeRemoteFileName)

            val progressMonitor = object : SftpProgressMonitor {
                private var transferredBytes: Long = 0L

                override fun init(op: Int, src: String?, dest: String?, max: Long) {
                    transferredBytes = 0L
                    onProgress?.invoke(0L, if (totalBytes > 0L) totalBytes else max.coerceAtLeast(0L))
                }

                override fun count(count: Long): Boolean {
                    transferredBytes += count
                    onProgress?.invoke(
                        transferredBytes,
                        if (totalBytes > 0L) totalBytes else 0L
                    )
                    return true
                }

                override fun end() {
                    onProgress?.invoke(
                        totalBytes.takeIf { it > 0L } ?: transferredBytes,
                        totalBytes.takeIf { it > 0L } ?: transferredBytes
                    )
                }
            }

            channel.put(
                stream,
                remoteFullPath,
                progressMonitor,
                ChannelSftp.OVERWRITE
            )
        }

        Result.success(Unit)
    }

    private suspend fun <T> withSftp(
        host: String,
        port: Int,
        username: String,
        password: String,
        block: (ChannelSftp) -> Result<T>
    ): Result<T> = withContext(Dispatchers.IO) {
        var session: Session? = null
        var channel: ChannelSftp? = null

        try {
            val safeHost = host.trim()
            val safeUsername = username.trim()

            require(safeHost.isNotBlank()) { "Host tidak boleh kosong" }
            require(port > 0) { "Port tidak valid" }
            require(safeUsername.isNotBlank()) { "Username tidak boleh kosong" }
            require(password.isNotBlank()) { "Password tidak boleh kosong" }

            val jsch = JSch()

            session = jsch.getSession(safeUsername, safeHost, port)
            session.setPassword(password)

            val config = Properties().apply {
                put("StrictHostKeyChecking", "no")
                put("PreferredAuthentications", "password")
            }

            session.setConfig(config)
            session.timeout = 10_000
            session.connect(10_000)

            channel = session.openChannel("sftp") as ChannelSftp
            channel.connect(10_000)

            block(channel)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            try {
                channel?.disconnect()
            } catch (_: Exception) {
            }

            try {
                session?.disconnect()
            } catch (_: Exception) {
            }
        }
    }

    /**
     * Executes a block using a pooled SFTP session.
     *
     * Acquires a session from the pool, executes the block, and releases
     * the session back to the pool. If the pool is not initialized,
     * falls back to creating a temporary connection.
     *
     * @param host The SFTP server hostname
     * @param port The SFTP server port
     * @param username The authentication username
     * @param password The authentication password
     * @param block The operation to execute with the SFTP channel
     * @return Result of the operation
     */
    private suspend fun <T> withPooledSession(
        host: String,
        port: Int,
        username: String,
        password: String,
        block: suspend (ChannelSftp) -> Result<T>
    ): Result<T> {
        val pool = sessionPool

        return if (pool != null && pool.isPoolReady) {
            withContext(Dispatchers.IO) {
                val session = pool.acquireSession()
                try {
                    block(session.channel).also {
                        session.lastUsedAt = System.currentTimeMillis()
                    }
                } catch (e: Exception) {
                    pool.markUnhealthy(session)
                    Result.failure(e)
                } finally {
                    pool.releaseSession(session)
                }
            }
        } else {
            // Fallback: use temporary connection if pool not ready
            withSftp(host, port, username, password, block)
        }
    }

    private fun buildChildPath(parent: String, childName: String): String {
        return when {
            parent.isBlank() -> "/$childName"
            parent == "/" -> "/$childName"
            parent.endsWith("/") -> "$parent$childName"
            else -> "$parent/$childName"
        }
    }

    private fun sanitizeRemoteFileName(name: String): String {
        return name
            .trim()
            .replace("/", "_")
            .replace("\\", "_")
    }

    private fun queryContentLength(uri: Uri): Long {
        return runCatching {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                afd.length
            } ?: -1L
        }.getOrDefault(-1L)
    }
}