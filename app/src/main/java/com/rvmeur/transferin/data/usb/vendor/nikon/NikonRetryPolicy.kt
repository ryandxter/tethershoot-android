package com.rvmeur.transferin.data.usb.vendor.nikon

import kotlinx.coroutines.delay
import java.io.IOException
import java.util.concurrent.ThreadLocalRandom

/**
 * Exception thrown when the Nikon EXPEED processor signals 0x2019 DeviceBusy.
 */
class NikonDeviceBusyException(
    message: String = "Nikon camera returned 0x2019 DeviceBusy"
) : IOException(message)

/**
 * Exception thrown when Nikon retry attempts are exhausted due to continuous 0x2019 DeviceBusy responses.
 */
class NikonDeviceBusyTimeoutException(
    message: String,
    val attempts: Int,
    cause: Throwable? = null
) : IOException(message, cause)

/**
 * Configuration options for the adaptive exponential backoff retry policy.
 */
data class NikonRetryConfig(
    val initialDelayMs: Long = 25L,
    val backoffMultiplier: Double = 1.5,
    val maxRetries: Int = 8,
    val maxDelayMs: Long = 1500L,
    val jitterRatio: Double = 0.1
)

/**
 * Adaptive Exponential Backoff Retry Policy for Nikon cameras.
 *
 * Mitigates EXPEED processor card buffer flushing delays (0x2019 PTP_RC_DeviceBusy)
 * without blocking threads or crashing USB tether sessions.
 */
class NikonRetryPolicy(
    val config: NikonRetryConfig = NikonRetryConfig()
) {

    /**
     * Calculates the backoff delay in milliseconds for a zero-indexed [attempt].
     *
     * Formula:
     * Delay_k = min(maxDelayMs, initialDelayMs * (multiplier^k)) * (1 ± jitter)
     */
    fun calculateDelayMs(
        attempt: Int,
        randomFactor: Double = ThreadLocalRandom.current().nextDouble(-1.0, 1.0)
    ): Long {
        val exponent = attempt.coerceAtLeast(0).toDouble()
        val baseDelay = (config.initialDelayMs * Math.pow(config.backoffMultiplier, exponent)).toLong()
        val cappedDelay = minOf(config.maxDelayMs, baseDelay)
        val jitter = (cappedDelay * config.jitterRatio * randomFactor).toLong()
        return (cappedDelay + jitter).coerceAtLeast(1L)
    }

    /**
     * Executes [block] with adaptive exponential backoff on [NikonDeviceBusyException].
     */
    @Throws(NikonDeviceBusyTimeoutException::class, Exception::class)
    suspend fun <T> executeWithRetry(
        operationName: String = "NikonOperation",
        block: suspend (attempt: Int) -> T
    ): T {
        var lastException: Throwable? = null

        for (attempt in 0 until config.maxRetries) {
            try {
                return block(attempt)
            } catch (e: NikonDeviceBusyException) {
                lastException = e
                if (attempt < config.maxRetries - 1) {
                    val delayMs = calculateDelayMs(attempt)
                    delay(delayMs)
                }
            }
        }

        throw NikonDeviceBusyTimeoutException(
            message = "Nikon operation '$operationName' timed out after ${config.maxRetries} retries due to 0x2019 DeviceBusy",
            attempts = config.maxRetries,
            cause = lastException
        )
    }
}
