package com.realityengine.v4

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CallerMemoryAiExtractorTest {
    @Test
    fun parsesStructuredMemoryAndDeduplicatesArrays() {
        val learned = CallerMemoryAiExtractor.parse(
            """{
              "likes":["coffee","Coffee"],
              "dislikes":["long meetings"],
              "facts":["Works at Acme"],
              "topics":["weekend trip"],
              "starters":["Ask how the trip went"],
              "unresolved":["Deciding where to stay"],
              "style":"Direct and casual",
              "summary":"Talked about a weekend trip and work."
            }"""
        )
        assertEquals(listOf("coffee"), learned.likes)
        assertEquals("Direct and casual", learned.preferredStyle)
        assertEquals("Talked about a weekend trip and work.", learned.summary)
    }

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
}
