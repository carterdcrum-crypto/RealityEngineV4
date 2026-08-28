package com.realityengine.v4

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryProposalReviewTest {
    @Test fun `review exposes every pending memory item`() {
        val learned = CallerMemoryAiExtractor.Learned(
            likes = listOf("coffee"),
            dislikes = listOf("crowds"),
            facts = listOf("works nights"),
            topics = listOf("weekend plans"),
            starters = listOf("Ask about Friday"),
            unresolved = listOf("confirm time"),
            preferredStyle = "direct",
            summary = "summary stays separate",
        )

        val items = MemoryProposalReview.items(learned)
        assertEquals(7, items.size)
        assertTrue(items.any { it.kind == MemoryProposalReview.Kind.FACT && it.value == "works nights" })
        assertTrue(items.any { it.kind == MemoryProposalReview.Kind.STYLE && it.value == "direct" })
    }

    @Test fun `removing one item leaves the rest pending`() {
        val learned = CallerMemoryAiExtractor.Learned(
            likes = listOf("coffee", "pizza"),
            facts = listOf("works nights"),
        )
        val item = MemoryProposalReview.Item(MemoryProposalReview.Kind.LIKE, "coffee")
        val remaining = MemoryProposalReview.remove(learned, item)

        assertFalse(remaining.likes.contains("coffee"))
        assertTrue(remaining.likes.contains("pizza"))
        assertTrue(remaining.facts.contains("works nights"))
        assertFalse(MemoryProposalReview.isEmpty(remaining))
    }

    @Test fun `review is empty only when all permanent proposal items are gone`() {
        val summaryOnly = CallerMemoryAiExtractor.Learned(summary = "call summary")
        assertTrue(MemoryProposalReview.isEmpty(summaryOnly))
    }
}
