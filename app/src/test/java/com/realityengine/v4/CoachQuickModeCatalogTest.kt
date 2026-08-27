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
    fun flirtModeIsNonsexualAndPressureFree() {
        val mode = CoachQuickModeCatalog.byId("flirt")
        assertNotNull(mode)
        val prompt = mode!!.promptInstruction.lowercase()
        assertTrue(prompt.contains("nonsexual"))
        assertTrue(prompt.contains("pressure-free"))
        assertTrue(prompt.contains("coercive"))
    }

    @Test
    fun unhingedModeKeepsHardSafetyLimits() {
        val mode = CoachQuickModeCatalog.byId("UNHINGED")!!
        val prompt = mode.promptInstruction.lowercase()
        assertTrue(prompt.contains("non-cruel"))
        assertTrue(prompt.contains("non-threatening"))
        assertTrue(prompt.contains("illegal"))
        assertTrue(prompt.contains("reckless"))
    }
}
