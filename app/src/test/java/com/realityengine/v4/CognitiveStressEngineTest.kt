package com.realityengine.v4

import org.junit.Assert.*
import org.junit.Test

class CognitiveStressEngineTest {
    @Test fun scoreIsNormalized() {
        val e=CognitiveStressEngine();e.markPromptEnd(1000);e.markSpeechStart(3000)
        val m=e.analyze("um honestly I basically do not know",4000)
        assertTrue(m.cognitiveStressScore in 0f..1f)
        assertEquals(2000L,m.responseLatencyMs)
        assertTrue(m.fillerCount>=3)
    }

    @Test fun lowLoadScoresBelowHighLoad() {
        val low=CognitiveStressEngine().apply{markPromptEnd(1000);markSpeechStart(1250)}.analyze("I went to the store this morning and bought groceries",4000)
        val high=CognitiveStressEngine().apply{markPromptEnd(1000);markSpeechStart(3200)}.analyze("um uh honestly basically like you know",5000)
        assertTrue(high.cognitiveStressScore>low.cognitiveStressScore)
    }

    @Test fun evidenceMultiplierRemainsBounded() {
        val p=CognitiveStressEngine().applyToEvidence(.95f,.9f,1f)
        assertTrue(p.first<=1f&&p.second<=1f)
    }
}
