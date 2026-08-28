package com.realityengine.v4

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CallSummaryBuilderTest {
    @Test fun `summary reports peak signal and chronological high-signal timeline for current call`() {
        val p = CallerProfileStore.CallerProfile("5551234567")
        p.topics += "Friday plans"
        p.preferredConversationStyle = "direct"
        p.conversationStarters += "Confirm the time"
        p.evidenceEvents += CallerProfileStore.EvidenceEvent(1_000L,.40f,.50f,.30f,.45f,"baseline")
        p.evidenceEvents += CallerProfileStore.EvidenceEvent(31_000L,.60f,.70f,.50f,.62f,"timing changed")
        p.evidenceEvents += CallerProfileStore.EvidenceEvent(91_000L,.80f,.75f,.90f,.86f,"conflicting detail")
        p.evidenceEvents += CallerProfileStore.EvidenceEvent(61_000L,.55f,.60f,.65f,.59f,"follow-up")

        val s = CallSummaryBuilder.buildSummary(p, 1_000L)
        assertTrue(s.contains("Latest topic: Friday plans"))
        assertTrue(s.contains("Preferred style: direct"))
        assertTrue(s.contains("Highest signal: 86% combined"))
        assertTrue(s.contains("80% acoustic"))
        assertTrue(s.contains("75% linguistic"))
        assertTrue(s.contains("90% factual"))
        assertTrue(s.contains("near: conflicting detail"))
        assertTrue(s.contains("@0:30 62%"))
        assertTrue(s.contains("@1:00 59%"))
        assertTrue(s.contains("@1:30 86%"))
        assertTrue(s.indexOf("@0:30") < s.indexOf("@1:00"))
        assertTrue(s.indexOf("@1:00") < s.indexOf("@1:30"))
        assertTrue(s.contains("Useful next opener: Confirm the time"))
    }

    @Test fun `events from older calls are excluded`() {
        val p = CallerProfileStore.CallerProfile("5551234567")
        p.evidenceEvents += CallerProfileStore.EvidenceEvent(1_000L,.95f,.95f,.95f,.95f,"old-call-peak")
        p.evidenceEvents += CallerProfileStore.EvidenceEvent(101_000L,.60f,.65f,.55f,.62f,"current-call")

        val s = CallSummaryBuilder.buildSummary(p, 100_000L)
        assertFalse(s.contains("old-call-peak"))
        assertTrue(s.contains("current-call"))
        assertTrue(s.contains("Highest signal: 62% combined"))
    }

    @Test fun `weak evidence does not create a high-signal claim or timeline`() {
        val p = CallerProfileStore.CallerProfile("5551234567")
        p.evidenceEvents += CallerProfileStore.EvidenceEvent(1_000L,.10f,.20f,.30f,.20f,"ordinary")
        p.evidenceEvents += CallerProfileStore.EvidenceEvent(2_000L,.30f,.25f,.35f,.31f,"ordinary")
        val s = CallSummaryBuilder.buildSummary(p, 1_000L)
        assertFalse(s.contains("Highest signal:"))
        assertFalse(s.contains("Timeline:"))
    }

    @Test fun `empty profile produces empty summary`() {
        assertTrue(CallSummaryBuilder.buildSummary(CallerProfileStore.CallerProfile("5551234567"), 1_000L).isEmpty())
    }

    @Test fun `timeline keeps only three strongest events but displays them chronologically`() {
        val p = CallerProfileStore.CallerProfile("5551234567")
        p.evidenceEvents += CallerProfileStore.EvidenceEvent(0L,.5f,.5f,.5f,.56f,"weakest-high")
        p.evidenceEvents += CallerProfileStore.EvidenceEvent(10_000L,.6f,.6f,.6f,.60f,"keep-a")
        p.evidenceEvents += CallerProfileStore.EvidenceEvent(20_000L,.7f,.7f,.7f,.70f,"keep-b")
        p.evidenceEvents += CallerProfileStore.EvidenceEvent(30_000L,.8f,.8f,.8f,.80f,"keep-c")
        val s = CallSummaryBuilder.buildSummary(p, 1_000L)
        val timeline = s.substringAfter("Timeline: ", "").substringBefore(" • ")
        assertFalse(timeline.contains("weakest-high"))
        assertTrue(timeline.contains("keep-a"))
        assertTrue(timeline.contains("keep-b"))
        assertTrue(timeline.contains("keep-c"))
        assertTrue(timeline.indexOf("keep-a") < timeline.indexOf("keep-b"))
        assertTrue(timeline.indexOf("keep-b") < timeline.indexOf("keep-c"))
    }
}
