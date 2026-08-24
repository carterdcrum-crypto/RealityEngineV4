package com.realityengine.v4

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ResponseCoachContractTest {
    @Before fun reset() {
        ResponseCoachState.resetForTest()
    }

    @Test fun `publishing result exposes ranked best and alternatives`() {
        val best = LiveResponseEngine.Suggestion("CLARIFY", "calm/curious", "What changed since we last talked?", "Gets specifics")
        val alt = LiveResponseEngine.Suggestion("MIRROR", "neutral/firm", "So the timing is the main issue?", "Reflects concern")
        ResponseCoachState.publish(LiveResponseEngine.Result(best, listOf(alt), 120, 34))

        val state = ResponseCoachState.current()
        assertEquals(best, state.best)
        assertEquals(listOf(alt), state.alternatives)
        assertEquals(120, state.inputTokens)
        assertEquals(34, state.outputTokens)
        assertTrue(state.updatedAt > 0L)
    }

    @Test fun `chosen response clears visible suggestions without losing classification`() {
        val suggestion = LiveResponseEngine.Suggestion("BONDING", "warm/relaxed", "I get why that bothered you.")
        ResponseCoachState.publish(LiveResponseEngine.Result(suggestion, emptyList(), 20, 8))
        val chosen = LiveResponseEngine.ChosenResponse(suggestion, .91f, "FOLLOWED")
        ResponseCoachState.publishChosen(chosen)
        ResponseCoachState.clearSuggestions()

        val state = ResponseCoachState.current()
        assertEquals(chosen, state.chosen)
        assertEquals(null, state.best)
        assertTrue(state.alternatives.isEmpty())
    }

    @Test fun `reset removes stale call state`() {
        val suggestion = LiveResponseEngine.Suggestion("PIVOT", "light/playful", "Let's switch gears for a second.")
        ResponseCoachState.publish(LiveResponseEngine.Result(suggestion, emptyList(), 10, 5))
        assertFalse(ResponseCoachState.current().best == null)

        ResponseCoachState.resetForTest()
        assertEquals(ResponseCoachState.Snapshot(), ResponseCoachState.current())
    }
}
