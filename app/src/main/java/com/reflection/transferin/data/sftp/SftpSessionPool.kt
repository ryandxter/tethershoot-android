package com.reflection.transferin.data.sftp

import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Properties
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Represents a pooled SFTP session with health tracking.
 *
 * @property id Unique identifier for this session
 * @property session The underlying JSch Session
 * @property channel The SFTP channel
 * @property createdAt Timestamp when session was created
 * @property lastUsedAt Timestamp of last usage
 * @property isHealthy Whether the session is healthy and can be used
 */
data class PooledSftpSession(
    val id: String = UUID.randomUUID().toString(),
    val session: Session,
    val channel: ChannelSftp,
    val createdAt: Long = System.currentTimeMillis(),
    var lastUsedAt: Long = System.currentTimeMillis(),
    var isHealthy: Boolean = true
)

/**
 * Manages a pool of persistent SFTP sessions for connection reuse.
 *
 * The pool maintains a configurable number of persistent connections, reducing
 * connection overhead for repeated SFTP operations. Each session follows
 * the acquire→use→release pattern.
 *
 * @property config The connection configuration for all sessions in the pool
 */
class SftpSessionPool(private val config: SftpConnectionConfig) {

    private val pool: ConcurrentLinkedQueue<PooledSftpSession> = ConcurrentLinkedQueue()
    private val mutex = Mutex()

    /**
     * Acquires a session from the pool, creating a new one if needed.
     *
     * Returns an available healthy session or creates a new one up to poolSize.
     * If no sessions are available and pool is at capacity, blocks until one is released.
     *
     * @return The acquired pooled session
     * @throws IllegalStateException if pool is full and no session becomes available
     */
    suspend fun acquireSession(): PooledSftpSession {
        // First, try to get an existing healthy session from the pool
        findHealthySession()?.let { return it }

        // Need to create a new session
        return mutex.withLock {
            // Double-check after acquiring lock
            findHealthySession()?.let { return it }

            // Check if we can create more sessions
            val currentSize = pool.count { it.isHealthy }
            if (currentSize >= config.poolSize) {
                // Pool is at capacity - wait and retry
                throw IllegalStateException(
                    "Session pool is at capacity ($currentSize/${config.poolSize}). " +
                    "Ensure sessions are properly released."
                )
            }

            createAndAddSession()
        }
    }

    /**
     * Returns a session to the pool for reuse.
     *
     * The session remains connected and healthy for subsequent operations.
     *
     * @param session The session to release back to the pool
     */
    suspend fun releaseSession(session: PooledSftpSession) {
        session.lastUsedAt = System.currentTimeMillis()
        // Session is already in the pool, just update timestamp
    }

    /**
     * Disconnects and removes a session from the pool.
     *
     * Use this for cleanup when a session is no longer needed or
     * when permanently removing it from the pool.
     *
     * @param session The session to destroy
     */
    suspend fun destroySession(session: PooledSftpSession) {
        mutex.withLock {
            try {
                session.channel.disconnect()
            } catch (_: Exception) {
                // Ignore channel disconnect errors
            }

            try {
                session.session.disconnect()
            } catch (_: Exception) {
                // Ignore session disconnect errors
            }

            pool.remove(session)
        }
    }

    /**
     * Marks a session as unhealthy and removes it from the pool.
     *
     * Call this when a session encounters an error during operation.
     * The session will be disconnected and removed from the pool.
     *
     * @param session The session that became unhealthy
     */
    suspend fun markUnhealthy(session: PooledSftpSession) {
        session.isHealthy = false
        destroySession(session)
    }

    /**
     * Checks if the pool has at least one healthy session available.
     *
     * @return true if at least one healthy session exists in the pool
     */
    val isPoolReady: Boolean
        get() = pool.any { it.isHealthy && it.session.isConnected }

    /**
     * Returns the current number of healthy sessions in the pool.
     */
    val healthySessionCount: Int
        get() = pool.count { it.isHealthy && it.session.isConnected }

    /**
     * Destroys all sessions and clears the pool.
     */
    suspend fun destroyAll() {
        mutex.withLock {
            pool.forEach { session ->
                try {
                    session.channel.disconnect()
                } catch (_: Exception) { /* ignore */ }

                try {
                    session.session.disconnect()
                } catch (_: Exception) { /* ignore */ }
            }
            pool.clear()
        }
    }

    private fun findHealthySession(): PooledSftpSession? {
        return pool.firstOrNull { it.isHealthy && it.session.isConnected }
    }

    private fun createAndAddSession(): PooledSftpSession {
        val jsch = JSch()

        val session = jsch.getSession(
            config.username.trim(),
            config.host.trim(),
            config.port
        )
        session.setPassword(config.password)

        val sessionConfig = Properties().apply {
            put("StrictHostKeyChecking", "no")
            put("PreferredAuthentications", "password")
        }
        session.setConfig(sessionConfig)
        session.timeout = config.sessionTimeoutMs

        session.connect(config.sessionTimeoutMs)

        val channel = session.openChannel("sftp") as ChannelSftp
        channel.connect(config.channelTimeoutMs)

        val pooledSession = PooledSftpSession(
            session = session,
            channel = channel
        )

        pool.add(pooledSession)
        return pooledSession
    }
}
