package com.realityengine.v4

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CallerMemoryAiExtractorTest {
    @Test
    fun mergePreservesExistingAndAvoidsCaseInsensitiveDuplicates() {
        val profile = CallerProfileStore.CallerProfile(
            phoneNumber = "5551234",
            likes = mutableListOf("Coffee"),
            importantFacts = mutableListOf("Has a dog"),
        )
        CallSummaryBuilder.merge(
            profile,
            CallerMemoryAiExtractor.Learned(
                likes = listOf("coffee", "jazz"),
                facts = listOf("Has a dog", "Works nights"),
                unresolved = listOf("Picking a restaurant"),
                preferredStyle = "Warm and concise",
                summary = "Discussed dinner plans.",
            )
        )
        assertEquals(listOf("Coffee", "jazz"), profile.likes)
        assertTrue(profile.importantFacts.contains("Works nights"))
        assertEquals(listOf("Picking a restaurant"), profile.unresolvedTopics)
        assertEquals("Warm and concise", profile.preferredConversationStyle)
        assertEquals("Discussed dinner plans.", profile.lastCallSummary)
    }

    @Test
    fun mergeAddsAllMemoryCategoriesAndKeepsExistingWhenNewFieldsAreBlank() {
        val profile = CallerProfileStore.CallerProfile(
            phoneNumber = "5559999",
            dislikes = mutableListOf("Spam calls"),
            preferredConversationStyle = "Direct",
            lastCallSummary = "Existing summary",
        )
        CallSummaryBuilder.merge(
            profile,
            CallerMemoryAiExtractor.Learned(
                likes = listOf("Live music"),
                dislikes = listOf("spam calls", "Early meetings"),
                facts = listOf("Works nights"),
                topics = listOf("Weekend plans"),
                starters = listOf("Ask how the concert went"),
                unresolved = listOf("Choosing a restaurant"),
            )
        )
        assertEquals(listOf("Live music"), profile.likes)
        assertEquals(listOf("Spam calls", "Early meetings"), profile.dislikes)
        assertEquals(listOf("Works nights"), profile.importantFacts)
        assertEquals(listOf("Weekend plans"), profile.topics)
        assertEquals(listOf("Ask how the concert went"), profile.conversationStarters)
        assertEquals(listOf("Choosing a restaurant"), profile.unresolvedTopics)
        assertEquals("Direct", profile.preferredConversationStyle)
        assertEquals("Existing summary", profile.lastCallSummary)
    }
}
