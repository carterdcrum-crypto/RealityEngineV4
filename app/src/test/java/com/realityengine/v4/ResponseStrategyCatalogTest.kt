package com.realityengine.v4

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResponseStrategyCatalogTest {
    @Test fun `expanded catalog contains core and situational strategies`() {
        val ids = ResponseStrategyCatalog.ids

        assertTrue(ids.containsAll(setOf(
            "BONDING",
            "CLARIFY",
            "MIRROR",
            "PIVOT",
            "COGNITIVE_PROBE",
            "VALIDATE",
            "REFRAME",
            "DEESCALATE",
            "BOUNDARY",
            "DIRECT",
            "SUMMARIZE",
            "NEXT_STEP",
        )))
        assertEquals(ids.size, ResponseStrategyCatalog.all.size)
    }

    @Test fun `mode normalization accepts readable variants and rejects unknown modes`() {
        assertEquals("COGNITIVE_PROBE", ResponseStrategyCatalog.normalizeMode("cognitive probe"))
        assertEquals("NEXT_STEP", ResponseStrategyCatalog.normalizeMode("next-step"))
        assertEquals("DEESCALATE", ResponseStrategyCatalog.normalizeMode("deescalate"))
        assertEquals("CLARIFY", ResponseStrategyCatalog.normalizeMode("mystery tactic"))
    }
}
