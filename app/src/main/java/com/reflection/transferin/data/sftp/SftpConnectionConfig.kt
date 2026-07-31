package com.reflection.transferin.data.sftp

/**
 * Immutable configuration for SFTP connections used by the session pool.
 *
 * @property host The SFTP server hostname or IP address
 * @property port The SFTP server port (default: 22)
 * @property username The authentication username
 * @property password The authentication password
 * @property poolSize Number of persistent sessions to maintain (default: 1)
 * @property sessionTimeoutMs Timeout for session connection in milliseconds
 * @property channelTimeoutMs Timeout for SFTP channel operations in milliseconds
 */
data class SftpConnectionConfig(
    val host: String,
    val port: Int = 22,
    val username: String,
    val password: String,
    val poolSize: Int = 1,
    val sessionTimeoutMs: Int = 10_000,
    val channelTimeoutMs: Int = 10_000
)
