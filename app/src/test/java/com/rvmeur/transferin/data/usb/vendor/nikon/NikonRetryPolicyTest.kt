package com.rvmeur.transferin.data.usb.vendor.nikon

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class NikonRetryPolicyTest {

    @Test
    fun `calculateDelayMs follows exponential backoff curve and respects maxDelay`() {
        val config = NikonRetryConfig(
            initialDelayMs = 25L,
            backoffMultiplier = 2.0,
            maxRetries = 8,
            maxDelayMs = 500L,
            jitterRatio = 0.0 // zero jitter for exact assertions
        )
        val policy = NikonRetryPolicy(config)

        assertEquals(25L, policy.calculateDelayMs(0, randomFactor = 0.0))
        assertEquals(50L, policy.calculateDelayMs(1, randomFactor = 0.0))
        assertEquals(100L, policy.calculateDelayMs(2, randomFactor = 0.0))
        assertEquals(200L, policy.calculateDelayMs(3, randomFactor = 0.0))
        assertEquals(400L, policy.calculateDelayMs(4, randomFactor = 0.0))
        assertEquals(500L, policy.calculateDelayMs(5, randomFactor = 0.0)) // Capped at maxDelay
        assertEquals(500L, policy.calculateDelayMs(6, randomFactor = 0.0)) // Capped at maxDelay
    }

    @Test
    fun `calculateDelayMs applies jitter within jitterRatio bounds`() {
        val config = NikonRetryConfig(
            initialDelayMs = 100L,
            backoffMultiplier = 1.0,
            maxRetries = 5,
            maxDelayMs = 1000L,
            jitterRatio = 0.1 // ±10%
        )
        val policy = NikonRetryPolicy(config)

        val minDelay = policy.calculateDelayMs(0, randomFactor = -1.0)
        val maxDelay = policy.calculateDelayMs(0, randomFactor = 1.0)

        assertEquals(90L, minDelay)
        assertEquals(110L, maxDelay)
    }

    @Test
    fun `executeWithRetry recovers after 3 consecutive DeviceBusy exceptions`() = runTest {
        val config = NikonRetryConfig(
            initialDelayMs = 1L,
            backoffMultiplier = 1.5,
            maxRetries = 8,
            maxDelayMs = 50L
        )
        val policy = NikonRetryPolicy(config)

        var callCount = 0
        val result = policy.executeWithRetry("TestFetch") { attempt ->
            callCount++
            if (attempt < 3) {
                throw NikonDeviceBusyException("EXPEED flushing buffer")
            }
            "SUCCESS_DATA"
        }

        assertEquals(4, callCount)
        assertEquals("SUCCESS_DATA", result)
    }

    @Test
    fun `executeWithRetry throws NikonDeviceBusyTimeoutException after maxRetries exhausted`() = runTest {
        val config = NikonRetryConfig(
            initialDelayMs = 1L,
            backoffMultiplier = 1.5,
            maxRetries = 8,
            maxDelayMs = 50L
        )
        val policy = NikonRetryPolicy(config)

        var callCount = 0
        try {
            policy.executeWithRetry("FailingOp") {
                callCount++
                throw NikonDeviceBusyException("Continuous busy")
            }
            fail("Expected NikonDeviceBusyTimeoutException")
        } catch (e: NikonDeviceBusyTimeoutException) {
            assertEquals(8, callCount)
            assertEquals(8, e.attempts)
            assertTrue(e.message!!.contains("timed out after 8 retries"))
        }
    }
}
