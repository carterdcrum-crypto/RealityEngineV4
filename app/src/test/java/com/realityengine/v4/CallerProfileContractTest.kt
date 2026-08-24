package com.realityengine.v4

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CallerProfileContractTest {
    @Test fun `compact context includes useful caller memory`() {
        val p = CallerProfileStore.CallerProfile(
            phoneNumber = "+15551234567",
            displayName = "Alex",
            preferredConversationStyle = "direct and calm"
        )
        p.likes += "coffee"
        p.dislikes += "last-minute changes"
        p.topics += "Friday plans"
        p.conversationStarters += "Ask how work went"
        p.importantFacts += "Works evenings"
        p.unresolvedTopics += "Confirm Friday timing"
        p.lastCallSummary = "Discussed moving plans to Friday"

        val text = p.compactContext().joinToString("\n")
        assertTrue(text.contains("Caller: Alex"))
        assertTrue(text.contains("Likes: coffee"))
        assertTrue(text.contains("Dislikes: last-minute changes"))
        assertTrue(text.contains("Preferred style: direct and calm"))
        assertTrue(text.contains("Recent topics: Friday plans"))
        assertTrue(text.contains("Good starters: Ask how work went"))
        assertTrue(text.contains("Works evenings"))
        assertTrue(text.contains("Open topic: Confirm Friday timing"))
        assertTrue(text.contains("Last call: Discussed moving plans to Friday"))
    }

    @Test fun `compact context does not invent empty profile fields`() {
        val text = CallerProfileStore.CallerProfile("5551234567").compactContext().joinToString("\n")
        assertFalse(text.contains("Caller:"))
        assertFalse(text.contains("Likes:"))
        assertFalse(text.contains("Dislikes:"))
        assertFalse(text.contains("Preferred style:"))
        assertFalse(text.contains("Recent topics:"))
        assertFalse(text.contains("Open topic:"))
        assertFalse(text.contains("Last call:"))
    }

    @Test fun `compact context exposes bounded newest memory`() {
        val p = CallerProfileStore.CallerProfile("5551234567")
        repeat(9) { p.likes += "like-$it" }
        repeat(9) { p.topics += "topic-$it" }
        repeat(7) { p.importantFacts += "fact-$it" }
        repeat(5) { p.unresolvedTopics += "open-$it" }

        val text = p.compactContext().joinToString("\n")
        assertTrue(text.contains("like-8"))
        assertFalse(text.contains("like-0"))
        assertTrue(text.contains("topic-8"))
        assertFalse(text.contains("topic-0"))
        assertTrue(text.contains("fact-6"))
        assertFalse(text.contains("fact-0"))
        assertTrue(text.contains("Open topic: open-4"))
        assertFalse(text.contains("Open topic: open-0"))
    }

    @Test fun `signal evidence is labeled and percentage formatted`() {
        val p = CallerProfileStore.CallerProfile("5551234567")
        p.evidenceEvents += CallerProfileStore.EvidenceEvent(
            acoustic = .61f, linguistic = .72f, factual = .83f, combined = .74f,
            context = "timeline changed"
        )
        val text = p.compactContext().joinToString("\n")
        assertTrue(text.contains("acoustic=61%"))
        assertTrue(text.contains("linguistic=72%"))
        assertTrue(text.contains("factual=83%"))
        assertTrue(text.contains("combined=74%"))
        assertTrue(text.contains("timeline changed"))
    }
}
