package com.realityengine.v4

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoachPersonaCatalogTest {
    @Test
    fun catalogContainsExpectedPersonas() {
        assertEquals(
            setOf("ADAPTIVE", "WARM", "DIRECT", "ANALYTICAL", "CALM", "PLAYFUL", "ASSERTIVE"),
            CoachPersonaCatalog.ids,
        )
    }

    @Test
    fun adaptiveUsesLearnedCommunicationStyle() {
        assertEquals("DIRECT", CoachPersonaCatalog.resolve("ADAPTIVE", "AUTO", "Direct and concise").id)
        assertEquals("ANALYTICAL", CoachPersonaCatalog.resolve("ADAPTIVE", "AUTO", "Detailed and explanatory").id)
        assertEquals("PLAYFUL", CoachPersonaCatalog.resolve("ADAPTIVE", "AUTO", "Casual and humorous").id)
        assertEquals("CALM", CoachPersonaCatalog.resolve("ADAPTIVE", "AUTO", "Calm and matter-of-fact").id)
    }

    @Test
    fun contactOverrideWinsOverGlobalPersona() {
        assertEquals("ASSERTIVE", CoachPersonaCatalog.resolve("WARM", "ASSERTIVE", "Casual and humorous").id)
        assertEquals("WARM", CoachPersonaCatalog.resolve("WARM", "AUTO", "Direct and concise").id)
    }

    @Test
    fun coachDirectiveIsSeparateFromFacts() {
        val context = ConversationContext()
        context.rememberFact("Likes fishing")
        context.setCoachDirective("Direct: Keep replies concise")
        val snapshot = context.snapshot()
        assertEquals(listOf("Likes fishing"), snapshot.facts)
        assertTrue(snapshot.asPromptContext().contains("COACH DELIVERY: Direct: Keep replies concise"))
        assertTrue(snapshot.asPromptContext().contains("FACTS: Likes fishing"))
    }
}
