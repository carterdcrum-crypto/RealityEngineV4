package com.realityengine.v4

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoachProviderPerformanceStoreTest {
    @Test
    fun reliableFastProviderWinsNormalRouting() {
        val now = 1_000_000L
        val samples = listOf(
            CoachProviderPerformanceStore.Stats("GROQ", attempts = 12, successes = 11, emaLatencyMs = 550),
            CoachProviderPerformanceStore.Stats("GEMINI", attempts = 12, successes = 12, emaLatencyMs = 1_900),
            CoachProviderPerformanceStore.Stats("MISTRAL", attempts = 8, successes = 6, emaLatencyMs = 900),
        )
        val ranked = CoachProviderPerformanceStore.rank(samples, listOf("GROQ", "GEMINI", "MISTRAL"), 9, now)
        assertEquals("GROQ", ranked.first())
    }

    @Test
    fun cooldownMovesFailingProviderBehindHealthyProviders() {
        val now = 2_000_000L
        val samples = listOf(
            CoachProviderPerformanceStore.Stats("GROQ", attempts = 10, successes = 10, emaLatencyMs = 400, cooldownUntilMs = now + 60_000),
            CoachProviderPerformanceStore.Stats("GEMINI", attempts = 10, successes = 9, emaLatencyMs = 1_200),
        )
        val ranked = CoachProviderPerformanceStore.rank(samples, listOf("GROQ", "GEMINI"), 5, now)
        assertEquals(listOf("GEMINI", "GROQ"), ranked)
    }

    @Test
    fun explorationTurnPromotesLeastTestedProvider() {
        val now = 3_000_000L
        val samples = listOf(
            CoachProviderPerformanceStore.Stats("GROQ", attempts = 20, successes = 20, emaLatencyMs = 450),
            CoachProviderPerformanceStore.Stats("GEMINI", attempts = 2, successes = 2, emaLatencyMs = 1_300),
            CoachProviderPerformanceStore.Stats("MISTRAL", attempts = 0, successes = 0),
        )
        val ranked = CoachProviderPerformanceStore.rank(samples, listOf("GROQ", "GEMINI", "MISTRAL"), 8, now)
        assertEquals("MISTRAL", ranked.first())
    }

    @Test
    fun rateLimitGetsLongCooldown() {
        val cooldown = CoachProviderPerformanceStore.failureCooldownMs("HTTP 429 rate limited", 1)
        assertTrue(cooldown >= 5 * 60_000L)
    }
}
