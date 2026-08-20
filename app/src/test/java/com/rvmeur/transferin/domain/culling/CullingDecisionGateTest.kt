package com.rvmeur.transferin.domain.culling

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class CullingDecisionGateTest {

    private lateinit var decisionGate: CullingDecisionGate

    @Before
    fun setUp() {
        decisionGate = CullingDecisionGate()
    }

    @Test
    fun `optimal verdict always proceeds upload under any policy`() {
        assertEquals(
            CullingAction.PROCEED_UPLOAD,
            decisionGate.evaluate(CullingVerdict.OPTIMAL, CullingPolicy.AUTO_SKIP_UPLOAD)
        )
        assertEquals(
            CullingAction.PROCEED_UPLOAD,
            decisionGate.evaluate(CullingVerdict.OPTIMAL, CullingPolicy.WARN_ONLY)
        )
        assertEquals(
            CullingAction.PROCEED_UPLOAD,
            decisionGate.evaluate(CullingVerdict.OPTIMAL, CullingPolicy.DISABLED)
        )
    }

    @Test
    fun `dynamic profile adjusted verdict proceeds upload under any policy`() {
        assertEquals(
            CullingAction.PROCEED_UPLOAD,
            decisionGate.evaluate(CullingVerdict.DYNAMIC_PROFILE_ADJUSTED, CullingPolicy.AUTO_SKIP_UPLOAD)
        )
        assertEquals(
            CullingAction.PROCEED_UPLOAD,
            decisionGate.evaluate(CullingVerdict.DYNAMIC_PROFILE_ADJUSTED, CullingPolicy.WARN_ONLY)
        )
        assertEquals(
            CullingAction.PROCEED_UPLOAD,
            decisionGate.evaluate(CullingVerdict.DYNAMIC_PROFILE_ADJUSTED, CullingPolicy.DISABLED)
        )
    }

    @Test
    fun `underexposed critical skips upload under AUTO_SKIP_UPLOAD`() {
        assertEquals(
            CullingAction.SKIP_UPLOAD_SAVING_QUOTA,
            decisionGate.evaluate(CullingVerdict.UNDEREXPOSED_CRITICAL, CullingPolicy.AUTO_SKIP_UPLOAD)
        )
    }

    @Test
    fun `underexposed critical flags warning only under WARN_ONLY`() {
        assertEquals(
            CullingAction.FLAG_WARNING_ONLY,
            decisionGate.evaluate(CullingVerdict.UNDEREXPOSED_CRITICAL, CullingPolicy.WARN_ONLY)
        )
    }

    @Test
    fun `underexposed critical proceeds upload under DISABLED`() {
        assertEquals(
            CullingAction.PROCEED_UPLOAD,
            decisionGate.evaluate(CullingVerdict.UNDEREXPOSED_CRITICAL, CullingPolicy.DISABLED)
        )
    }

    @Test
    fun `overexposed blown skips upload under AUTO_SKIP_UPLOAD`() {
        assertEquals(
            CullingAction.SKIP_UPLOAD_SAVING_QUOTA,
            decisionGate.evaluate(CullingVerdict.OVEREXPOSED_BLOWN, CullingPolicy.AUTO_SKIP_UPLOAD)
        )
    }

    @Test
    fun `overexposed blown flags warning only under WARN_ONLY`() {
        assertEquals(
            CullingAction.FLAG_WARNING_ONLY,
            decisionGate.evaluate(CullingVerdict.OVEREXPOSED_BLOWN, CullingPolicy.WARN_ONLY)
        )
    }

    @Test
    fun `manual override always proceeds upload regardless of verdict and policy`() {
        assertEquals(
            CullingAction.PROCEED_UPLOAD,
            decisionGate.evaluate(CullingVerdict.UNDEREXPOSED_CRITICAL, CullingPolicy.AUTO_SKIP_UPLOAD, isManualOverride = true)
        )
        assertEquals(
            CullingAction.PROCEED_UPLOAD,
            decisionGate.evaluate(CullingVerdict.OVEREXPOSED_BLOWN, CullingPolicy.AUTO_SKIP_UPLOAD, isManualOverride = true)
        )
        assertEquals(
            CullingAction.PROCEED_UPLOAD,
            decisionGate.evaluate(CullingVerdict.UNDEREXPOSED_CRITICAL, CullingPolicy.WARN_ONLY, isManualOverride = true)
        )
    }
}
