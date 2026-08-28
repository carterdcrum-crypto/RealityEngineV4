package com.realityengine.v4

import org.junit.Assert.assertTrue
import org.junit.Test

class SignalDynamicsTest {
    @Test
    fun acousticScoreRespondsToAbruptVoiceLevelChange() {
        val analyzer = AcousticSignalAnalyzer()
        repeat(10) { analyzer.analyze(frame(amplitude = 2_500, period = 12)) }

        val changed = analyzer.analyze(frame(amplitude = 11_000, period = 5))

        assertTrue("acoustic score should react to a large PCM change", changed.score >= 20)
    }

    @Test
    fun linguisticScoreRespondsToObservableLanguageLoad() {
        val calm = LinguisticSignalAnalyzer.analyze("I will meet you at the office at six.")
        val loaded = LinguisticSignalAnalyzer.analyze(
            "Honestly, um, I think maybe, actually, I mean, I never really said that, you know, maybe later on."
        )

        assertTrue("loaded language should score above plain language", loaded.score > calm.score)
        assertTrue("observable markers should explain the score", loaded.markers.isNotEmpty())
    }

    private fun frame(amplitude: Int, period: Int, samples: Int = 320): ByteArray {
        val out = ByteArray(samples * 2)
        for (i in 0 until samples) {
            val sample = if ((i / period) % 2 == 0) amplitude else -amplitude
            out[i * 2] = (sample and 0xff).toByte()
            out[i * 2 + 1] = ((sample shr 8) and 0xff).toByte()
        }
        return out
    }
}
