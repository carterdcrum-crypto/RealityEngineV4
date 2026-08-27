package com.realityengine.v4

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoachQuickModeCatalogTest {
    @Test
    fun exposesBothOneShotModes() {
        assertEquals(setOf(CoachQuickModeCatalog.UNHINGED, CoachQuickModeCatalog.FLIRT), CoachQuickModeCatalog.all.map { it.id }.toSet())
    }

    @Test
    fun flirtModeIsClearlyRomanticButNonsexualAndPressureFree() {
        val mode = CoachQuickModeCatalog.byId("flirt")
        assertNotNull(mode)
        val prompt = mode!!.promptInstruction.lowercase()
        assertTrue(prompt.contains("boldly romantic"))
        assertTrue(prompt.contains("clearly flirty"))
        assertTrue(prompt.contains("nonsexual"))
        assertTrue(prompt.contains("pressure-free"))
        assertTrue(prompt.contains("coercive"))
    }

    @Test
    fun unhingedModeIsHighChaosWithHardSafetyLimits() {
        val mode = CoachQuickModeCatalog.byId("UNHINGED")!!
        val prompt = mode.promptInstruction.lowercase()
        assertTrue(prompt.contains("maximum chaos-comedy"))
        assertTrue(prompt.contains("genuinely unhinged"))
        assertTrue(prompt.contains("dangerous"))
        assertTrue(prompt.contains("illegal"))
        assertTrue(prompt.contains("real-world harm"))
        assertTrue(mode.temperature >= 0.9)
    }
}
