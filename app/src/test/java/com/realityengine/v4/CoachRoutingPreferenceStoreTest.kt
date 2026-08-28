package com.realityengine.v4

import org.junit.Assert.assertEquals
import org.junit.Test

class CoachRoutingPreferenceStoreTest {
    private val adaptive = listOf(
        SettingsStore.COACH_PROVIDER_CEREBRAS,
        SettingsStore.COACH_PROVIDER_GROQ,
        SettingsStore.COACH_PROVIDER_GEMINI,
    )

    @Test fun `best preserves adaptive provider order`() {
        assertEquals(
            adaptive,
            CoachRoutingPreferenceStore.applyPreference(
                adaptive,
                CoachRoutingPreferenceStore.BEST,
                nowMs = 1_000L,
            )
        )
    }

    @Test fun `healthy preferred provider moves to front`() {
        assertEquals(
            listOf(
                SettingsStore.COACH_PROVIDER_GEMINI,
                SettingsStore.COACH_PROVIDER_CEREBRAS,
                SettingsStore.COACH_PROVIDER_GROQ,
            ),
            CoachRoutingPreferenceStore.applyPreference(
                adaptive,
                SettingsStore.COACH_PROVIDER_GEMINI,
                preferredCooldownUntilMs = 0L,
                nowMs = 1_000L,
            )
        )
    }

    @Test fun `cooled preferred provider does not override adaptive order`() {
        assertEquals(
            adaptive,
            CoachRoutingPreferenceStore.applyPreference(
                adaptive,
                SettingsStore.COACH_PROVIDER_GEMINI,
                preferredCooldownUntilMs = 5_000L,
                nowMs = 1_000L,
            )
        )
    }

    @Test fun `unconfigured preferred provider cannot enter order`() {
        assertEquals(
            adaptive,
            CoachRoutingPreferenceStore.applyPreference(
                adaptive,
                SettingsStore.COACH_PROVIDER_OPENROUTER,
                nowMs = 1_000L,
            )
        )
    }
}
