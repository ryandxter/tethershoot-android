# Phase 1: SFTP Pooling & Reconnection Resilience - Research

**Date:** 2026-08-07

## 1. Technical Domain Analysis

### SFTP Connection Pooling
- **Active State:** The pool maintains a queue of `PooledSftpSession` wrappers containing the JSch `Session` and `ChannelSftp`.
- **Resource Leaks:**
  - JSch sessions and channels must be closed explicitly using `.disconnect()`.
  - Stale channels are currently removed but must be safely cleaned up under lock to avoid concurrent modification exceptions on the `ConcurrentLinkedQueue`.

### Health & Keepalive Telemetry
- **JSch native keepalive:** `session.serverAliveInterval` and `session.serverAliveCountMax` configure SSH keepalives.
- **Polling monitor:** `SftpSessionHealthMonitor` runs a background polling loop checking `session.isConnected && channel.isConnected`.
- **Missing Integration:** `SftpSessionPool` does not expose its sessions queue, preventing the health monitor from checking session status.

### Circuit Breaker & Exponential Backoff
- **Exponential Backoff delays:** 1s, 2s, 4s, 8s, up to 30s.
- **Circuit Breaker:** Trips to OPEN after 5 consecutive failures, with a 60-second recovery cooldown.
- **Missing Integration:** The circuit breaker and reconnector are instantiated but not wired to automatically handle connection drops during file transfers.

## 2. Codebase Scouting & Integration Points

### Reusable Assets
- `SftpSessionPool` (`app/src/main/java/com/reflection/transferin/data/sftp/SftpSessionPool.kt`)
- `SftpSessionReconnector` (`app/src/main/java/com/reflection/transferin/data/sftp/SftpSessionReconnector.kt`)
- `SftpSessionHealthMonitor` (`app/src/main/java/com/reflection/transferin/data/sftp/SftpSessionHealthMonitor.kt`)

### Identified Gaps & Modifications

#### A. SftpSessionPool Exposing Sessions
We must add a method to retrieve tracked sessions to enable health monitoring:
```kotlin
fun getActiveSessions(): List<PooledSftpSession> {
    return pool.toList()
}
```

#### B. SftpSessionHealthMonitor Verification Loop
`checkAllSessions()` must query `pool.getActiveSessions()` and verify each connection, notifying the pool to evict dead sessions:
```kotlin
private suspend fun checkAllSessions() {
    val pool = sessionPool ?: return
    val sessions = pool.getActiveSessions()
    for (session in sessions) {
        val isHealthy = checkSession(session)
        if (!isHealthy) {
            pool.handleSessionFailure(session)
        }
    }
}
```

#### C. JschSftpClient Error Handling & Retry Integration
`withPooledSession` must catch connection failures and run reconnection logic using `SftpSessionReconnector` before failing the operation:
```kotlin
private suspend fun <T> withPooledSession(
    host: String,
    port: Int,
    username: String,
    password: String,
    block: suspend (ChannelSftp) -> Result<T>
): Result<T> {
    val pool = sessionPool
    if (pool != null && pool.isPoolReady) {
        return try {
            val session = pool.acquireSession()
            try {
                block(session.channel).also {
                    session.lastUsedAt = System.currentTimeMillis()
                }
            } catch (e: Exception) {
                pool.markUnhealthy(session)
                if (pool.shouldRetryConnection(e)) {
                    // Try to reconnect and retry once
                    val reconnector = SftpSessionReconnector(config)
                    val reconnectResult = reconnector.reconnectWithBackoff(pool)
                    if (reconnectResult.isSuccess) {
                        val newSession = reconnectResult.getOrThrow()
                        block(newSession.channel)
                    } else {
                        Result.failure(e)
                    }
                } else {
                    Result.failure(e)
                }
            } finally {
                pool.releaseSession(session)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    // ...
}
```

## 3. Validation Architecture

- **Unit Tests:** Mock connectivity failures to verify that the circuit breaker changes state (CLOSED -> OPEN -> HALF_OPEN).
- **Integration Tests:** Verify that connection dropouts do not leak SSH channels or file descriptors on the local device or remote server.

---

*Phase 1 Research Completed: 2026-08-07*
