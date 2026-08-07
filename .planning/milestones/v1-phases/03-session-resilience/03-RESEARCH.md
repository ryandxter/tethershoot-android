---
name: session-resilience-research
phase: 03
date: 2026-07-31
---

## Research Summary

Session Resilience requires implementing persistent SFTP connections with automatic reconnection capabilities. The project uses JSch 0.2.26 (MWiede fork) for SSH/SFTP operations, which provides standard Java SSH functionality. Key challenges include thread-safety for Session and ChannelSftp objects across coroutines, implementing heartbeat detection, exponential backoff reconnection, and USB disconnect handling on Android.

## Technical Approaches

### 1. JSch Session Pooling (RESILIENCE-01)

**Current Implementation Analysis:**
The current `JschSftpClient.kt` creates a new JSch Session for each SFTP operation in `withSftp()`:
- Creates `JSch()` instance per operation
- Calls `jsch.getSession()` then `session.connect()`
- Opens `ChannelSftp` then `channel.connect()`
- Executes operation, disconnects, and discards session

**JSch Session Pooling Design:**

```kotlin
// Pooled session wrapper
data class PooledSftpSession(
    val session: Session,
    val channel: ChannelSftp,
    val createdAt: Long = System.currentTimeMillis(),
    var lastUsedAt: Long = System.currentTimeMillis(),
    var isHealthy: Boolean = true
)

// Session pool with configurable size (1-2)
class SftpSessionPool(
    private val host: String,
    private val port: Int,
    private val username: String,
    private val password: String,
    private val maxPoolSize: Int = 2,
    private val sessionTimeout: Int = 10_000,
    private val channelTimeout: Int = 10_000
) {
    private val pool = ConcurrentLinkedQueue<PooledSftpSession>()
    private val poolMutex = Mutex()
    
    // JSch is thread-safe for session creation, but Session/ChannelSftp are NOT
    // Must synchronize channel access
    private val channelMutex = Mutex()
}
```

**Thread-Safety Considerations:**

1. **JSch instance** - Thread-safe, can be shared
2. **Session objects** - NOT thread-safe. Each Session can have only ONE channel at a time. For 1-2 pooled sessions with concurrent access, we need mutex protection for channel acquisition.
3. **ChannelSftp objects** - NOT thread-safe. Each operation (put, get, ls) modifies internal state. Must be used exclusively within a single operation.
4. **Pool management** - Use `ConcurrentLinkedQueue` for pool storage and `Mutex` for pool state changes.

**Pool Size Recommendation:**
- 1 connection: Lower memory, suitable for sequential uploads
- 2 connections: Better throughput for parallel uploads, handles concurrent directory listings

For TransferIn's shoot mode with WorkManager-based uploads, **1 pool size** is sufficient initially since WorkManager schedules work sequentially. Can scale to 2 if parallel uploads are added.

**Implementation Pattern:**

```kotlin
suspend fun <T> withPooledSession(block: suspend (ChannelSftp) -> Result<T>): Result<T> {
    val session = acquireSession() // Blocks until available
    return try {
        val result = withContext(Dispatchers.IO) {
            block(session.channel)
        }
        session.lastUsedAt = System.currentTimeMillis()
        releaseSession(session)
        result
    } catch (e: Exception) {
        session.isHealthy = false
        destroySession(session)
        Result.failure(e)
    }
}
```

### 2. Keepalive Implementation (RESILIENCE-02)

**JSch Keepalive Mechanisms:**

The JSch MWiede fork (0.2.26) supports:
- `Session.setKeepAliveInterval(int seconds)` - Sets SSH-level keepalive
- `Session.setServerAliveInterval(int seconds)` - SSH2 keepalive (preferred)
- `Session.setServerAliveCountMax(int count)` - Max retries before declaring dead

**Recommended Configuration:**

```kotlin
// In session configuration
session.timeout = 10_000           // TCP connection timeout (ms)
session.serverAliveInterval = 30_000  // 30s as per requirements
session.serverAliveCountMax = 3    // 3 missed keepalives = dead
```

**Health Monitoring Strategy:**

```kotlin
class SftpSessionHealthMonitor(
    private val checkIntervalMs: Long = 10_000
) {
    private val _healthState = MutableStateFlow<Map<String, SessionHealth>>(emptyMap())
    val healthState: StateFlow<Map<String, SessionHealth>> = _healthState.asStateFlow()
    
    fun monitorSession(session: PooledSftpSession) {
        val isAlive = try {
            session.session.isConnected && 
            session.channel.isConnected
        } catch (e: Exception) {
            false
        }
        session.isHealthy = isAlive
    }
    
    data class SessionHealth(
        val sessionId: String,
        val isAlive: Boolean,
        val lastChecked: Long,
        val connectionAgeMs: Long
    )
}
```

**Detection of Dead Sessions:**
- `Session.isConnected()` returns false after disconnect
- `Session.getServerAliveInterval()` failures trigger `JSchException`
- Channel operations throw `SftpException` on connection loss

### 3. Exponential Backoff Reconnection (RESILIENCE-03)

**Backoff Strategy Implementation:**

```kotlin
class SftpSessionReconnector(
    private val maxRetries: Int = 5,
    private val maxBackoffMs: Long = 30_000
) {
    private val backoffDelays = listOf(1_000L, 2_000L, 4_000L, 8_000L, 30_000L)
    
    suspend fun reconnectWithBackoff(
        connectionParams: ConnectionParams,
        onAttempt: suspend (attempt: Int, delayMs: Long) -> Boolean
    ): Result<PooledSftpSession> {
        var lastException: Exception? = null
        
        for (attempt in 1..maxRetries) {
            val delayMs = backoffDelays.getOrElse(attempt - 1) { maxBackoffMs }
            
            // Notify before delay (for UI updates)
            val shouldContinue = onAttempt(attempt, delayMs)
            if (!shouldContinue) return Result.failure(
                lastException ?: CancellationException("Reconnection cancelled")
            )
            
            if (attempt > 1) {
                delay(delayMs)
            }
            
            val result = try {
                createNewSession(connectionParams)
            } catch (e: Exception) {
                lastException = e
                continue
            }
            
            return result
        }
        
        return Result.failure(
            lastException ?: IllegalStateException("Max retries exceeded")
        )
    }
}
```

**Circuit Breaker Pattern:**

```kotlin
class SftpCircuitBreaker(
    private val failureThreshold: Int = 5,
    private val recoveryTimeoutMs: Long = 60_000
) {
    private var consecutiveFailures = 0
    private var lastFailureTime = 0L
    private val stateMutex = Mutex()
    
    val isOpen: Boolean
        get() = consecutiveFailures >= failureThreshold &&
                System.currentTimeMillis() - lastFailureTime < recoveryTimeoutMs
    
    suspend fun recordFailure() {
        stateMutex.withLock {
            consecutiveFailures++
            lastFailureTime = System.currentTimeMillis()
        }
    }
    
    suspend fun recordSuccess() {
        stateMutex.withLock {
            consecutiveFailures = 0
        }
    }
}
```

### 4. USB Disconnect Handling (RESILIENCE-04)

**Android USB Broadcast Handling:**

The project already has `UsbHelper` with USB receiver registration. We need to extend this or create a dedicated `UsbDisconnectHandler` that:

1. **Listens for disconnects:**
```kotlin
// In UsbDisconnectHandler
private val usbReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        when (intent?.action) {
            UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                val device = extractDevice(intent)
                if (isCurrentSessionDevice(device)) {
                    handleDisconnect()
                }
            }
            UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                handleReattach()
            }
        }
    }
}
```

2. **Pauses SFTP operations gracefully:**
```kotlin
private suspend fun handleDisconnect() {
    _connectionState.value = ConnectionState.DISCONNECTED
    
    // Pause pending uploads instead of failing them
    pausePendingUploads()
    
    // Notify SFTP pool to mark sessions as unhealthy
    sftpSessionPool.markAllUnhealthy()
}
```

3. **Resumes on reconnect:**
```kotlin
private suspend fun handleReattach() {
    // Wait for USB enumeration to stabilize
    delay(2_000)
    
    // Re-establish camera session via UsbHelper
    usbHelper.refreshUsbState()
    
    // Check if expected camera is connected
    if (isExpectedCameraConnected()) {
        _connectionState.value = ConnectionState.RECONNECTING
        
        // Reinitialize SFTP sessions
        sftpSessionPool.reinitialize()
        
        _connectionState.value = ConnectionState.CONNECTED
        
        // Resume paused uploads
        resumePausedUploads()
    }
}
```

**Integration with TransferUploadWorker:**

The WorkManager-based upload worker (`TransferUploadWorker`) already handles retries via `Result.retry()`. We need to:
1. Mark uploads as "PAUSED" instead of failing on USB disconnect
2. Use WorkManager's `setInitialDelay()` to defer retry after reconnect
3. Emit a custom `OperationResult` that signals pause vs. retry

### 5. Thread Safety and Coroutines Integration

**Session Access Pattern:**

```kotlin
class SftpSessionPool(
    private val host: String,
    private val port: Int,
    private val username: String,
    private val password: String
) {
    private val jsch = JSch()  // Thread-safe
    private val sessions = ConcurrentLinkedQueue<PooledSftpSession>()
    private val poolMutex = Mutex()  // For pool size changes
    private val sessionLocks = ConcurrentHashMap<String, ReentrantLock>()
    
    suspend fun <T> executeWithSession(block: suspend (ChannelSftp) -> T): T {
        val session = acquireSession()
        return try {
            withContext(Dispatchers.IO) {
                block(session.channel)
            }
        } finally {
            releaseSession(session)
        }
    }
    
    private suspend fun acquireSession(): PooledSftpSession {
        // Fast path: available session
        sessions.poll()?.let { session ->
            if (session.isHealthy && isSessionAlive(session)) {
                return session
            }
            destroySession(session)
        }
        
        // Slow path: create new or wait
        return poolMutex.withLock {
            if (sessions.size < maxPoolSize) {
                createSession()
            } else {
                // Wait for available session
                waitForAvailableSession()
            }
        }
    }
}
```

**Channel Lifecycle in Coroutines:**

```kotlin
// Each channel operation should be isolated
suspend fun uploadFile(/* ... */): Result<Unit> {
    return executeWithSession { channel ->
        channel.put(inputStream, remotePath, progressMonitor, ChannelSftp.OVERWRITE)
    }
}

// NOT this - sharing channel across multiple operations
suspend fun batchOperation(): Result<Unit> {
    val session = acquireSession()
    session.channel.put(...)  // Operation 1
    session.channel.put(...)  // Operation 2 - DANGEROUS, channel state corrupted
    releaseSession(session)
}
```

### 6. Error Handling Patterns

**JSch Exception Types:**

```kotlin
// Common JSch exceptions
com.jcraft.jsch.JSchException           // SSH protocol errors
com.jcraft.jsch.SocketTimeoutException  // Network timeout
com.jcraft.jsch.ChannelSftpException   // SFTP protocol errors
com.jcraft.jsch.SftpException           // SFTP file operation errors
```

**SFTP Error Code Mapping:**

```kotlin
sealed class SftpError(val code: Int, val isRetryable: Boolean) {
    object PermissionDenied : SftpError(SSH_FX_PERMISSION_DENIED, false)
    object NoSuchFile : SftpError(SSH_FX_NO_SUCH_FILE, false)
    object Failure : SftpError(SSH_FX_FAILURE, true)  // Generic, retryable
    object ConnectionLost : SftpError(-1, true)  // Disconnection
}

// In SftpException, error message contains code
val errorCode = extractErrorCode(exception.message)
```

**Recovery Strategies:**

```kotlin
fun shouldRetry(exception: Exception): RetryDecision {
    return when (exception) {
        is SocketTimeoutException -> RetryDecision.RETRY
        is JSchException -> {
            when {
                exception.message?.contains("Connection reset") == true -> RetryDecision.RETRY
                exception.message?.contains("Auth fail") == true -> RetryDecision.DONT_RETRY
                else -> RetryDecision.RETRY
            }
        }
        is SftpException -> {
            val code = extractErrorCode(exception)
            when (code) {
                SSH_FX_PERMISSION_DENIED -> RetryDecision.DONT_RETRY
                SSH_FX_FAILURE -> RetryDecision.RETRY
                else -> RetryDecision.RETRY
            }
        }
        else -> RetryDecision.RETRY
    }
}

enum class RetryDecision {
    RETRY, DONT_RETRY, PAUSE_AND_WAIT
}
```

## Architecture Recommendations

### Overall Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                      TransferUploadWorker                        │
│                    (WorkManager CoroutineWorker)                  │
└─────────────────────────────────┬───────────────────────────────┘
                                  │
                                  ▼
┌─────────────────────────────────────────────────────────────────┐
│                       SftpSessionPool                            │
│  ┌─────────────────┐  ┌─────────────────┐                       │
│  │  Session #1     │  │  Session #2     │   (max 2 sessions)   │
│  │  ├─ JSch Session│  │  ├─ JSch Session│                       │
│  │  └─ ChannelSftp│  │  └─ ChannelSftp│                       │
│  └─────────────────┘  └─────────────────┘                       │
│         │                    │                                   │
│         ▼                    ▼                                   │
│  ┌─────────────────────────────────────────────────────────────┐│
│  │              SftpSessionHealthMonitor                        ││
│  │  - Keepalive heartbeat (30s)                                ││
│  │  - Dead connection detection                                 ││
│  │  - Session health state flow                                 ││
│  └─────────────────────────────────────────────────────────────┘│
└─────────────────────────────────┬───────────────────────────────┘
                                  │
                                  ▼
┌─────────────────────────────────────────────────────────────────┐
│                     SftpSessionReconnector                       │
│  - Exponential backoff (1s, 2s, 4s, 8s, max 30s)                │
│  - Circuit breaker pattern                                      │
│  - Max retry attempts: 5                                        │
└─────────────────────────────────┬───────────────────────────────┘
                                  │
                                  ▼
┌─────────────────────────────────────────────────────────────────┐
│                      UsbDisconnectHandler                       │
│  - USB_DEVICE_ATTACHED/DETACHED broadcasts                       │
│  - Graceful pause of SFTP operations                            │
│  - Reconnection orchestration                                    │
└─────────────────────────────────────────────────────────────────┘
```

### Key Implementation Decisions

1. **Single JSch Instance**: Create once, reuse for all sessions. JSch is thread-safe for session creation.

2. **Pool Size = 1 for MVP**: Start with 1 persistent session. Scale to 2 only when parallel uploads are implemented.

3. **Channel Isolation**: Each SFTP operation acquires a session, uses the channel for ONE operation, then releases. Never share channels across operations.

4. **Keepalive Configuration**: Set `serverAliveInterval = 30000` (30s) and `serverAliveCountMax = 3` (90s total detection time).

5. **Backoff Delays**: Use `[1000, 2000, 4000, 8000, 30000]` ms as specified.

6. **USB Handler Integration**: Extend existing `UsbHelper` pattern rather than creating a separate handler. Use shared flow for connection state.

### File Structure

```
app/src/main/java/com/reflection/transferin/data/sftp/
├── SftpSessionPool.kt              # Pool management
├── SftpSessionHealthMonitor.kt     # Keepalive & health tracking
├── SftpSessionReconnector.kt       # Exponential backoff
├── SftpConnectionConfig.kt         # Connection parameters
├── SftpException.kt                # Custom exception types
└── JschSftpClient.kt              # Enhanced, uses pool

app/src/main/java/com/reflection/transferin/service/
└── UsbDisconnectHandler.kt        # USB lifecycle handling
```

## Validation Architecture

### Unit Tests

1. **Session Pool Tests:**
   - Test pool initialization with configured size
   - Test session acquisition and release
   - Test unhealthy session detection and removal
   - Test concurrent access (multiple coroutines acquiring sessions)

2. **Health Monitor Tests:**
   - Test keepalive interval configuration
   - Test dead session detection
   - Test health state emission

3. **Reconnector Tests:**
   - Test exponential backoff delays
   - Test max retry limit
   - Test circuit breaker opening after threshold

4. **USB Handler Tests:**
   - Mock UsbManager broadcasts
   - Test disconnect handling
   - Test reconnection flow

### Integration Tests

1. **Mock SFTP Server Tests:**
   - Use Apache MINA SSHD as embedded SFTP server
   - Test pool operations against mock server
   - Test reconnection on simulated disconnect

2. **USB Disconnect Simulation:**
   - Use USB device mock or physical device
   - Verify pause/resume of uploads

### Manual Testing Checklist

- [ ] Session pool maintains persistent connection across multiple uploads
- [ ] Keepalive keeps connection alive through idle periods
- [ ] Dead connection detected and reconnected within 90s (30s interval × 3)
- [ ] Exponential backoff delays applied: 1s, 2s, 4s, 8s, 30s
- [ ] USB disconnect pauses uploads gracefully
- [ ] USB reconnect resumes uploads without data loss
- [ ] No session leaks after extended operation
- [ ] Thread-safe concurrent uploads

## Potential Pitfalls

1. **Channel State Corruption**: JSch ChannelSftp maintains internal state. Sharing a channel across multiple operations causes race conditions. **Must** acquire-release per operation.

2. **Session Timeout vs Keepalive**: JSch has both `timeout` (TCP connect) and `serverAliveInterval`. Ensure `timeout` > `serverAliveInterval` to avoid premature connection failures.

3. **Android USB Broadcast Timing**: `ACTION_USB_DEVICE_DETACHED` may fire before `ACTION_USB_DEVICE_ATTACHED` during cable replug. Debounce with 1-2s delay.

4. **Coroutine Cancellation**: Session operations may be cancelled. Ensure `isActive` checks and proper cleanup in finally blocks.

5. **WorkManager State Persistence**: Upload queue state survives app restart, but session pool does not. Re-initialize pool on app launch.

6. **Memory Leaks**: Sessions hold TCP connections. Ensure `disconnect()` is called on all exit paths, including exceptions.

7. **JSch Version Compatibility**: Using MWiede fork (0.2.26). Check that keepalive APIs (`setServerAliveInterval`) are supported.

8. **SFTP Protocol Version**: SFTP has versions 0-6. JSch defaults to v3. Some servers require v4+. May need to set `sftpProtocolVersion` in session config.

9. **Firewall/Keepalive Interaction**: Some NAT/firewalls kill idle connections faster than 30s. Consider reducing interval to 20s if connection drops are observed.

10. **Concurrent Channel Opening**: Each JSch Session can open only ONE SFTP channel at a time. Pool size of 2 allows 2 concurrent operations, but each session can only handle one channel simultaneously.

## References

- JSch MWiede Fork: https://github.com/mwiede/jsch
- JSch Documentation: http://www.jcraft.com/jsch/
- SSH SFTP Protocol: https://tools.ietf.org/html/draft-ietf-secsh-filexfer-13
- Android USB: https://developer.android.com/guide/topics/connectivity/usb/

---

*Research completed: 2026-07-31*
