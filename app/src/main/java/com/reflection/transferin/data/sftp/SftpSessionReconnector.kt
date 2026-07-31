package com.reflection.transferin.data.sftp

import com.jcraft.jsch.JSchException
import com.jcraft.jsch.SftpException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.min

/**
 * Circuit breaker pattern to prevent rapid retry loops after repeated failures.
 *
 * States:
 * - CLOSED: Normal operation, requests pass through
 * - OPEN: Too many failures, requests are blocked
 * - HALF_OPEN: Recovery timeout elapsed, allowing test request
 *
 * @property failureThreshold Number of consecutive failures before opening (default: 5)
 * @property recoveryTimeoutMs Time to wait before attempting recovery (default: 60s)
 */
class SftpCircuitBreaker(
    private val failureThreshold: Int = 5,
    private val recoveryTimeoutMs: Long = 60_000
) {
    private var consecutiveFailures = 0
    private var lastFailureTime = 0L
    private val stateMutex = Mutex()

    enum class CircuitState { CLOSED, OPEN, HALF_OPEN }
    private var state = CircuitState.CLOSED

    /**
     * Returns true if the circuit breaker is OPEN and blocking requests.
     */
    val isOpen: Boolean
        get() = state == CircuitState.OPEN

    /**
     * Returns the current state of the circuit breaker.
     */
    val currentState: CircuitState
        get() = state

    /**
     * Records a failure and potentially opens the circuit.
     */
    suspend fun recordFailure() {
        stateMutex.withLock {
            consecutiveFailures++
            lastFailureTime = System.currentTimeMillis()
            if (consecutiveFailures >= failureThreshold) {
                state = CircuitState.OPEN
            }
        }
    }

    /**
     * Records a success and resets the circuit to CLOSED.
     */
    suspend fun recordSuccess() {
        stateMutex.withLock {
            consecutiveFailures = 0
            state = CircuitState.CLOSED
        }
    }

    /**
     * Attempts to reset the circuit after recovery timeout.
     * Returns true if the circuit moved to HALF_OPEN state.
     */
    suspend fun attemptReset(): Boolean {
        stateMutex.withLock {
            if (state == CircuitState.OPEN) {
                val timeSinceFailure = System.currentTimeMillis() - lastFailureTime
                if (timeSinceFailure >= recoveryTimeoutMs) {
                    state = CircuitState.HALF_OPEN
                    return true
                }
            }
            return false
        }
    }

    /**
     * Returns the number of consecutive failures recorded.
     */
    val failureCount: Int
        get() = consecutiveFailures
}

/**
 * Reconnection state emitted during the reconnection process.
 */
sealed class ReconnectionState {
    /**
     * Attempting reconnection with delay.
     * @param attempt Current attempt number (1-based)
     * @param delayMs Delay before this attempt
     */
    data class Attempting(val attempt: Int, val delayMs: Long) : ReconnectionState()

    /**
     * Reconnection succeeded.
     * @param session The newly created session
     */
    data class Success(val session: PooledSftpSession) : ReconnectionState()

    /**
     * Reconnection failed.
     * @param exception The exception that caused the failure
     */
    data class Failed(val exception: Exception) : ReconnectionState()

    /**
     * Circuit breaker is open and blocking reconnection attempts.
     */
    object CircuitOpen : ReconnectionState()
}

/**
 * Handles SFTP session reconnection with exponential backoff and circuit breaker pattern.
 *
 * Reconnection delays follow the sequence: 1s, 2s, 4s, 8s, max 30s.
 * After 5 consecutive failures, the circuit breaker opens for 60 seconds.
 *
 * @property config The connection configuration for sessions
 * @property maxRetries Maximum number of reconnection attempts (default: 5)
 * @property maxBackoffMs Maximum backoff delay in milliseconds (default: 30s)
 */
class SftpSessionReconnector(
    private val config: SftpConnectionConfig,
    private val maxRetries: Int = 5,
    private val maxBackoffMs: Long = 30_000
) {
    private val circuitBreaker = SftpCircuitBreaker()

    // Exponential backoff delays per RESILIENCE-03
    // Sequence: 1s, 2s, 4s, 8s, 30s (max)
    private val backoffDelays = listOf(
        1_000L,   // 1st retry: 1 second
        2_000L,   // 2nd retry: 2 seconds
        4_000L,   // 3rd retry: 4 seconds
        8_000L,   // 4th retry: 8 seconds
        30_000L   // 5th retry: 30 seconds (max)
    )

    // Callback for reconnection state updates
    private val _reconnectionState = MutableSharedFlow<ReconnectionState>(
        replay = 0,
        extraBufferCapacity = 10
    )
    val reconnectionState: SharedFlow<ReconnectionState> = _reconnectionState.asSharedFlow()

    // Internal state for monitoring
    private val _internalState = MutableStateFlow(InternalState())
    val state: StateFlow<InternalState> = _internalState.asStateFlow()

    data class InternalState(
        val isReconnecting: Boolean = false,
        val currentAttempt: Int = 0,
        val circuitBreakerState: SftpCircuitBreaker.CircuitState =
            SftpCircuitBreaker.CircuitState.CLOSED
    )

    /**
     * Attempts to reconnect with exponential backoff.
     *
     * @param pool The session pool to recreate sessions in
     * @param onAttempt Optional callback invoked before each attempt.
     *                  Return false to cancel reconnection.
     * @return Result containing the new session on success, or failure exception
     */
    suspend fun reconnectWithBackoff(
        pool: SftpSessionPool,
        onAttempt: suspend (attempt: Int, delayMs: Long) -> Boolean = { _, _ -> true }
    ): Result<PooledSftpSession> {
        // Check circuit breaker state
        if (circuitBreaker.isOpen) {
            if (!circuitBreaker.attemptReset()) {
                _reconnectionState.emit(ReconnectionState.CircuitOpen)
                _internalState.value = InternalState(
                    isReconnecting = false,
                    currentAttempt = 0,
                    circuitBreakerState = SftpCircuitBreaker.CircuitState.OPEN
                )
                return Result.failure(
                    IllegalStateException("Circuit breaker is OPEN - too many consecutive failures. Wait ${circuitBreaker.failureCount} failures ago.")
                )
            }
        }

        _internalState.value = InternalState(
            isReconnecting = true,
            circuitBreakerState = SftpCircuitBreaker.CircuitState.HALF_OPEN
        )

        var lastException: Exception? = null

        for (attempt in 1..maxRetries) {
            val delayMs = backoffDelays.getOrElse(attempt - 1) { maxBackoffMs }

            // Update state for monitoring
            _internalState.value = _internalState.value.copy(currentAttempt = attempt)

            // Notify UI of retry attempt
            _reconnectionState.emit(ReconnectionState.Attempting(attempt, delayMs))

            // Allow callback to cancel (e.g., user disconnected)
            val shouldContinue = onAttempt(attempt, delayMs)
            if (!shouldContinue) {
                _internalState.value = _internalState.value.copy(isReconnecting = false)
                return Result.failure(CancellationException("Reconnection cancelled by user"))
            }

            // Wait before retry (skip wait on first attempt)
            if (attempt > 1) {
                delay(delayMs)
            }

            // Attempt reconnection
            val result = pool.recreateSession()
            if (result.isSuccess) {
                circuitBreaker.recordSuccess()
                result.getOrNull()?.let {
                    _reconnectionState.emit(ReconnectionState.Success(it))
                }
                _internalState.value = _internalState.value.copy(
                    isReconnecting = false,
                    circuitBreakerState = SftpCircuitBreaker.CircuitState.CLOSED
                )
                return result
            }

            lastException = result.exceptionOrNull() as? Exception ?:
                IllegalStateException("Unknown reconnection error")
            circuitBreaker.recordFailure()
        }

        _reconnectionState.emit(ReconnectionState.Failed(lastException!!))
        _internalState.value = _internalState.value.copy(
            isReconnecting = false,
            circuitBreakerState = circuitBreaker.currentState
        )
        return Result.failure(lastException!!)
    }

    /**
     * Returns whether the circuit breaker is currently open.
     */
    val isCircuitOpen: Boolean
        get() = circuitBreaker.isOpen

    /**
     * Returns the current circuit breaker state.
     */
    val circuitState: SftpCircuitBreaker.CircuitState
        get() = circuitBreaker.currentState

    /**
     * Resets the circuit breaker to closed state.
     */
    suspend fun resetCircuitBreaker() {
        circuitBreaker.recordSuccess()
    }
}
