package com.reflection.transferin.data.sftp

import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSch
import com.jcraft.jsch.JSchException
import com.jcraft.jsch.Session
import com.jcraft.jsch.SftpException
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
 * Sessions are configured with SSH keepalive (30s interval, 3 max missed)
 * per RESILIENCE-02 for dead connection detection.
 *
 * @property config The connection configuration for all sessions in the pool
 * @property healthMonitor Optional health monitor for session health tracking
 */
class SftpSessionPool(
    private val config: SftpConnectionConfig,
    private var healthMonitor: SftpSessionHealthMonitor? = null
) {

    /**
     * Callback interface for reconnection events.
     */
    interface ReconnectionCallback {
        /**
         * Called when a session has failed.
         * @param session The session that failed
         */
        suspend fun onSessionFailed(session: PooledSftpSession)

        /**
         * Called when reconnection is being attempted.
         * @param sessionId The ID of the session being reconnected
         */
        suspend fun onReconnecting(sessionId: String)
    }

    private var reconnectionCallback: ReconnectionCallback? = null

    /**
     * Sets the callback for reconnection events.
     * @param callback The callback to receive reconnection events, or null to clear
     */
    fun setReconnectionCallback(callback: ReconnectionCallback?) {
        reconnectionCallback = callback
    }

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

        // Report session destruction to health monitor
        healthMonitor?.clearSessionHealth(session.id)
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
     * Recreates a new session in the pool, replacing any unhealthy sessions.
     *
     * This method is used for reconnection scenarios. It destroys any unhealthy
     * sessions and creates a fresh session with the same configuration.
     *
     * @return Result containing the new pooled session, or failure if creation failed
     */
    suspend fun recreateSession(): Result<PooledSftpSession> {
        return mutex.withLock {
            try {
                // Remove unhealthy sessions first
                val unhealthySessions = pool.filter { !it.isHealthy || !it.session.isConnected }
                unhealthySessions.forEach { session ->
                    try {
                        session.channel.disconnect()
                    } catch (_: Exception) { /* ignore */ }
                    try {
                        session.session.disconnect()
                    } catch (_: Exception) { /* ignore */ }
                    pool.remove(session)
                }

                // Check if we can create more sessions
                val currentHealthy = pool.count { it.isHealthy && it.session.isConnected }
                if (currentHealthy >= config.poolSize) {
                    // Return an existing healthy session instead
                    findHealthySession()?.let {
                        return@withLock Result.success(it)
                    }
                }

                // Create new session
                val newSession = createAndAddSession()
                Result.success(newSession)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /**
     * Handles a session failure by marking it unhealthy and recreating.
     *
     * This is the primary entry point for handling session failures during
     * SFTP operations. It notifies the callback, marks the session unhealthy,
     * and attempts to create a replacement session.
     *
     * @param session The session that failed
     * @return Result containing the new session, or failure if recreation failed
     */
    suspend fun handleSessionFailure(session: PooledSftpSession): Result<PooledSftpSession> {
        // Notify callback
        reconnectionCallback?.onSessionFailed(session)

        // Mark and destroy unhealthy session
        markUnhealthy(session)

        // Notify about reconnection attempt
        reconnectionCallback?.onReconnecting(session.id)

        // Recreate session
        return recreateSession()
    }

    /**
     * Determines whether a connection error is retryable.
     *
     * @param e The exception to evaluate
     * @return true if the error indicates a transient failure that may succeed on retry
     */
    fun shouldRetryConnection(e: Exception): Boolean {
        return when (e) {
            is JSchException -> {
                // Retry on connection-related errors
                e.message?.contains("Connection reset", ignoreCase = true) == true ||
                e.message?.contains("Socket closed", ignoreCase = true) == true ||
                e.message?.contains("Connection refused", ignoreCase = true) == true ||
                e.message?.contains("timeout", ignoreCase = true) == true ||
                e.message?.contains("Broken pipe", ignoreCase = true) == true ||
                e.message?.contains("No route to host", ignoreCase = true) == true
            }
            is SftpException -> {
                // Most SFTP errors are transient and retryable
                // Only certain errors like permission denied are not retryable
                val id = e.id
                id !in listOf(3L, 4L) // SSH_FX_PERMISSION_DENIED=3, SSH_FX_BAD_MESSAGE=4
            }
            else -> false
        }
    }

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

                // Update health monitor
                healthMonitor?.clearSessionHealth(session.id)
            }
            pool.clear()
        }
    }

    /**
     * Sets the health monitor for session health tracking.
     *
     * When set, the pool will report session creation and destruction
     * events to the monitor for health state tracking.
     *
     * @param monitor The health monitor to use
     */
    fun setHealthMonitor(monitor: SftpSessionHealthMonitor) {
        healthMonitor = monitor
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

        // Keepalive configuration per RESILIENCE-02
        // SSH-level keepalive: 30 second interval, 3 missed = dead connection
        session.serverAliveInterval = 30_000  // 30 seconds
        session.serverAliveCountMax = 3         // 3 missed = dead connection

        session.timeout = config.sessionTimeoutMs

        session.connect(config.sessionTimeoutMs)

        val channel = session.openChannel("sftp") as ChannelSftp
        channel.connect(config.channelTimeoutMs)

        val pooledSession = PooledSftpSession(
            session = session,
            channel = channel
        )

        pool.add(pooledSession)

        // Report session creation to health monitor
        healthMonitor?.let { monitor ->
            kotlinx.coroutines.runBlocking {
                monitor.updateSessionHealth(
                    sessionId = pooledSession.id,
                    isAlive = true,
                    consecutiveFailures = 0
                )
            }
        }

        return pooledSession
    }
}
