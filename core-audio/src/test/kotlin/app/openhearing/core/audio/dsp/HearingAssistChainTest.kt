package app.openhearing.core.audio.dsp

import app.openhearing.audiogram.GainCurve
import app.openhearing.audiogram.GainPoint
import app.openhearing.common.Hertz
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt

class HearingAssistChainTest {
    private val sampleRate = 48_000

    private val curve =
        GainCurve(
            listOf(
                GainPoint(Hertz(1000.0), 18.0),
                GainPoint(Hertz(2000.0), 24.0),
                GainPoint(Hertz(4000.0), 24.0),
            ),
        )

    private fun sine(amp: Double, freq: Double, n: Int) =
        FloatArray(n) { (amp * sin(2.0 * PI * freq * it / sampleRate)).toFloat() }

    private fun rms(b: FloatArray, from: Int): Double {
        var s = 0.0
        for (i in from until b.size) s += b[i].toDouble() * b[i]
        return sqrt(s / (b.size - from))
    }

    @Test
    fun `amplifies a quiet tone in the prescribed band`() {
        val chain = HearingAssistChain(curve, sampleRate, masterGainDb = 6.0)
        val buf = sine(amp = 0.02, freq = 2000.0, n = sampleRate)
        val before = rms(buf, 0)
        chain.process(buf)
        val after = rms(buf, sampleRate / 5)
        assertTrue(after > before, "quiet in-band tone should be amplified (before=$before after=$after)")
    }

    @Test
    fun `output never exceeds the ceiling even for loud input`() {
        val chain = HearingAssistChain(curve, sampleRate, masterGainDb = 40.0, ceilingLinear = 0.9f)
        val buf = sine(amp = 0.9, freq = 2000.0, n = sampleRate)
        chain.process(buf)
        assertTrue(buf.all { abs(it) <= 0.9f + 1e-6f }, "chain output exceeded ceiling")
    }

    @Test
    fun `silence in produces silence out`() {
        val chain = HearingAssistChain(curve, sampleRate, masterGainDb = 20.0)
        val buf = FloatArray(sampleRate)
        chain.process(buf)
        assertTrue(buf.all { it == 0f }, "silence should stay silent")
    }

    @Test
    fun `master gain is hard-capped by safety limits`() {
        // Requesting an absurd master gain must not blow past the ceiling.
        val chain = HearingAssistChain(curve, sampleRate, masterGainDb = 999.0, ceilingLinear = 0.8f)
        val buf = sine(amp = 0.5, freq = 1000.0, n = sampleRate)
        chain.process(buf)
        assertTrue(buf.all { abs(it) <= 0.8f + 1e-6f })
    }

    @Test
    fun `live master gain change takes effect between blocks`() {
        val chain = HearingAssistChain(curve, sampleRate, masterGainDb = 0.0)
        // Settle the chain, then compare the same input at 0 dB vs +12 dB.
        chain.process(sine(amp = 0.02, freq = 2000.0, n = sampleRate))
        val quiet = sine(amp = 0.02, freq = 2000.0, n = sampleRate / 10)
        chain.process(quiet)
        chain.setMasterGainDb(12.0)
        val boosted = sine(amp = 0.02, freq = 2000.0, n = sampleRate / 10)
        chain.process(boosted)
        assertTrue(
            rms(boosted, 0) > rms(quiet, 0) * 2.0,
            "raising master gain live should audibly boost output",
        )
    }

    @Test
    fun `live master gain change never exceeds cap or ceiling`() {
        val chain = HearingAssistChain(curve, sampleRate, masterGainDb = 0.0, ceilingLinear = 0.8f)
        chain.setMasterGainDb(999.0) // absurd request while "running"
        val buf = sine(amp = 0.9, freq = 1000.0, n = sampleRate)
        chain.process(buf)
        assertTrue(buf.all { abs(it) <= 0.8f + 1e-6f }, "live gain change must stay limited")
    }
}
