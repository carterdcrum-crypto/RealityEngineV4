package com.realityengine.v4

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationContextTest {
    @Test fun `prompt preserves speaker labels facts and unresolved items`() {
        val context = ConversationContext(maxRecentTurns = 6, targetInputTokens = 650)
        context.rememberFact("Caller prefers text after work")
        context.markUnresolved("Confirm Friday timing")
        context.addTurn(ConversationContext.Speaker.CALLER, "Can we move it to Friday?")
        context.addTurn(ConversationContext.Speaker.USER, "Maybe. What time works?")

        val prompt = context.snapshot().asPromptContext()
        assertTrue(prompt.contains("FACTS: Caller prefers text after work"))
        assertTrue(prompt.contains("OPEN: Confirm Friday timing"))
        assertTrue(prompt.contains("CALLER: Can we move it to Friday?"))
        assertTrue(prompt.contains("USER: Maybe. What time works?"))
    }

    @Test fun `prompt never invents absent profile context`() {
        val context = ConversationContext()
        context.addTurn(ConversationContext.Speaker.CALLER, "I wanted to check in.")
        val prompt = context.snapshot().asPromptContext()
        assertFalse(prompt.contains("FACTS:"))
        assertFalse(prompt.contains("OPEN:"))
        assertTrue(prompt.contains("CALLER: I wanted to check in."))
    }

    @Test fun `snapshot keeps only bounded recent dialogue`() {
        val context = ConversationContext(maxRecentTurns = 4, targetInputTokens = 650)
        repeat(10) { index ->
            val speaker = if (index % 2 == 0) ConversationContext.Speaker.CALLER else ConversationContext.Speaker.USER
            context.addTurn(speaker, "turn-$index")
        }
        val snapshot = context.snapshot()
        assertTrue(snapshot.recentTurns.size <= 4)
        assertTrue(snapshot.asPromptContext().contains("turn-9"))
        assertFalse(snapshot.asPromptContext().contains("CALLER: turn-0\n"))
    }

    @Test fun `token budget trims oldest recent turns first`() {
        val context = ConversationContext(maxRecentTurns = 8, targetInputTokens = 90)
        repeat(8) { index -> context.addTurn(ConversationContext.Speaker.CALLER, "turn-$index " + "detail ".repeat(18)) }
        val snapshot = context.snapshot()
        assertTrue(snapshot.recentTurns.size >= 2)
        assertTrue(snapshot.recentTurns.size < 8)
        assertTrue(snapshot.recentTurns.last().text.startsWith("turn-7"))
    }

    @Test fun `facts and unresolved collections retain newest bounded values`() {
        val context = ConversationContext()
        repeat(8) { context.rememberFact("fact-$it") }
        repeat(5) { context.markUnresolved("open-$it") }
        val snapshot = context.snapshot()
        assertTrue(snapshot.facts.size <= 6)
        assertFalse(snapshot.facts.contains("fact-0"))
        assertTrue(snapshot.facts.contains("fact-7"))
        assertTrue(snapshot.unresolved.size <= 3)
        assertFalse(snapshot.unresolved.contains("open-0"))
        assertTrue(snapshot.unresolved.contains("open-4"))
    }
}
