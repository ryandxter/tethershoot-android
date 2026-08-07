package com.reflection.transferin.data.sftp

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Represents the health status of an SFTP session.
 */
enum class HealthStatus {
    /** Session connected and responsive */
    HEALTHY,
    /** Session connected but experiencing slow responses */
    DEGRADED,
    /** Session disconnected or unresponsive */
    DEAD,
    /** Attempting to reconnect after failure */
    RECONNECTING
}

/**
 * Immutable snapshot of a session's current health state.
 *
 * @property sessionId Unique identifier of the session
 * @property isAlive Whether the session is currently connected and responsive
 * @property lastChecked Timestamp of the last health check (System.currentTimeMillis)
 * @property consecutiveFailures Number of consecutive failed health checks
 * @property connectionAgeMs Time elapsed since session creation
 * @property status Current health status classification
 */
data class SessionHealth(
    val sessionId: String,
    val isAlive: Boolean,
    val lastChecked: Long,
    val consecutiveFailures: Int,
    val connectionAgeMs: Long,
    val status: HealthStatus = if (isAlive) HealthStatus.HEALTHY else HealthStatus.DEAD
)

/**
 * Monitors the health of SFTP sessions in a session pool.
 *
 * Performs periodic health checks on all sessions in the pool, tracking
 * connection state, consecutive failures, and connection age. Emits
 * updated health state via StateFlow for UI consumption.
 *
 * Health checks run at a configurable interval (default 10 seconds) and
 * use SSH-level keepalive (30 second interval) configured on sessions.
 *
 * @property checkIntervalMs Polling interval for health checks in milliseconds (default 10,000)
 */
class SftpSessionHealthMonitor(
    private val context: Context,
    private val checkIntervalMs: Long = 10_000
) {
    private val scope = CoroutineScope(Dispatchers.Default + Job())
    private var monitoringJob: Job? = null
    private val mutex = Mutex()

    private fun getDynamicCheckInterval(): Long {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return checkIntervalMs
        val activeNetwork = cm.activeNetwork ?: return checkIntervalMs
        val capabilities = cm.getNetworkCapabilities(activeNetwork) ?: return checkIntervalMs
        return if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            10_000L
        } else {
            30_000L
        }
    }

    private val _healthState = MutableStateFlow<Map<String, SessionHealth>>(emptyMap())
    /** Current health state for all monitored sessions */
    val healthState: StateFlow<Map<String, SessionHealth>> = _healthState.asStateFlow()

    private var sessionPool: SftpSessionPool? = null

    /**
     * Starts periodic health monitoring of the given session pool.
     *
     * @param pool The SftpSessionPool to monitor
     */
    fun startMonitoring(pool: SftpSessionPool) {
        if (monitoringJob?.isActive == true) {
            return // Already monitoring
        }

        sessionPool = pool
        monitoringJob = scope.launch {
            while (isActive) {
                checkAllSessions()
                delay(getDynamicCheckInterval())
            }
        }
    }

    /**
     * Stops health monitoring and cancels the monitoring coroutine.
     */
    fun stopMonitoring() {
        monitoringJob?.cancel()
        monitoringJob = null
    }

    /**
     * Gets the current health state for a specific session.
     *
     * @param sessionId The unique identifier of the session
     * @return SessionHealth snapshot or null if session is not tracked
     */
    fun getSessionHealth(sessionId: String): SessionHealth? {
        return _healthState.value[sessionId]
    }

    /**
     * Returns all sessions that are currently healthy.
     *
     * @return List of session IDs with HEALTHY status
     */
    fun getHealthySessions(): List<String> {
        return _healthState.value
            .filter { it.value.status == HealthStatus.HEALTHY }
            .keys
            .toList()
    }

    /**
     * Returns all sessions that are currently dead or degraded.
     *
     * @return List of session IDs with DEAD or DEGRADED status
     */
    fun getUnhealthySessions(): List<String> {
        return _healthState.value
            .filter { it.value.status == HealthStatus.DEAD || it.value.status == HealthStatus.DEGRADED }
            .keys
            .toList()
    }

    /**
     * Clears health state for a session (e.g., when session is destroyed).
     *
     * @param sessionId The unique identifier of the session to remove
     */
    suspend fun clearSessionHealth(sessionId: String) {
        mutex.withLock {
            _healthState.value = _healthState.value - sessionId
        }
    }

    /**
     * Checks the health of all sessions in the pool and updates state.
     */
    private suspend fun checkAllSessions() {
        val pool = sessionPool ?: return

        val sessions = pool.getActiveSessions()
        for (session in sessions) {
            val isHealthy = checkSession(session)
            if (!isHealthy) {
                pool.handleSessionFailure(session)
            }
        }

        val currentHealth = _healthState.value.toMutableMap()
        val now = System.currentTimeMillis()

        // Update connection age for all tracked sessions
        currentHealth.keys.forEach { sessionId ->
            currentHealth[sessionId]?.let { health ->
                currentHealth[sessionId] = health.copy(
                    lastChecked = now,
                    connectionAgeMs = now - health.lastChecked
                )
            }
        }

        mutex.withLock {
            _healthState.value = currentHealth
        }
    }

    /**
     * Updates health for a specific session.
     *
     * Called when a session is created, when health check fails/succeeds,
     * or when session is destroyed.
     *
     * @param sessionId Unique identifier of the session
     * @param isAlive Whether the session is currently connected
     * @param consecutiveFailures Number of consecutive check failures
     */
    suspend fun updateSessionHealth(
        sessionId: String,
        isAlive: Boolean,
        consecutiveFailures: Int
    ) {
        mutex.withLock {
            val existingHealth = _healthState.value[sessionId]
            val now = System.currentTimeMillis()
            val connectionAgeMs = existingHealth?.let { now - it.lastChecked } ?: 0L

            val status = when {
                !isAlive -> HealthStatus.DEAD
                consecutiveFailures >= 3 -> HealthStatus.DEGRADED
                consecutiveFailures > 0 -> HealthStatus.DEGRADED
                else -> HealthStatus.HEALTHY
            }

            val updatedHealth = SessionHealth(
                sessionId = sessionId,
                isAlive = isAlive,
                lastChecked = now,
                consecutiveFailures = consecutiveFailures,
                connectionAgeMs = connectionAgeMs,
                status = status
            )

            _healthState.value = _healthState.value + (sessionId to updatedHealth)
        }
    }

    /**
     * Performs a health check on a specific session.
     *
     * @param session The pooled session to check
     * @return true if session is healthy, false otherwise
     */
    suspend fun checkSession(session: PooledSftpSession): Boolean {
        return try {
            val isConnected = session.session.isConnected && session.channel.isConnected

            val currentFailures = _healthState.value[session.id]?.consecutiveFailures ?: 0

            updateSessionHealth(
                sessionId = session.id,
                isAlive = isConnected,
                consecutiveFailures = if (isConnected) 0 else currentFailures + 1
            )

            isConnected
        } catch (e: Exception) {
            val currentFailures = _healthState.value[session.id]?.consecutiveFailures ?: 0
            updateSessionHealth(
                sessionId = session.id,
                isAlive = false,
                consecutiveFailures = currentFailures + 1
            )
            false
        }
    }
}
