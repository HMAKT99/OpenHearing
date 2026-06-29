package app.openhearing.core.audio.dsp

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

class WdrcTest {
    private val sampleRate = 48_000

    private fun sine(amp: Double, freq: Double, n: Int): FloatArray =
        FloatArray(n) { (amp * sin(2.0 * PI * freq * it / sampleRate)).toFloat() }

    private fun rms(b: FloatArray, from: Int = 0): Double {
        var s = 0.0
        for (i in from until b.size) s += b[i].toDouble() * b[i]
        return sqrt(s / (b.size - from))
    }

    @Test
    fun `quiet signal below threshold is left essentially unchanged`() {
        val wdrc = Wdrc(sampleRate, thresholdDbFs = -20.0, ratio = 4.0, makeupGainDb = 0.0)
        val quiet = sine(amp = 0.02, freq = 1000.0, n = sampleRate / 2) // ~ -34 dBFS
        val before = rms(quiet)
        wdrc.process(quiet)
        val after = rms(quiet, from = sampleRate / 10)
        assertTrue(after in before * 0.7..before * 1.3, "quiet signal should pass ~unchanged")
    }

    @Test
    fun `loud signal above threshold is attenuated`() {
        val wdrc = Wdrc(sampleRate, thresholdDbFs = -20.0, ratio = 4.0, makeupGainDb = 0.0)
        val loud = sine(amp = 0.8, freq = 1000.0, n = sampleRate / 2) // ~ -2 dBFS
        val before = rms(loud)
        wdrc.process(loud)
        val after = rms(loud, from = sampleRate / 10)
        assertTrue(after < before * 0.7, "loud signal should be compressed (after=$after before=$before)")
    }

    @Test
    fun `compression reduces the dynamic range between quiet and loud`() {
        fun outRms(amp: Double): Double {
            val w = Wdrc(sampleRate, thresholdDbFs = -30.0, ratio = 4.0, makeupGainDb = 10.0)
            val b = sine(amp, 1000.0, sampleRate / 2)
            w.process(b)
            return rms(b, from = sampleRate / 10)
        }
        val inRatio = 0.5 / 0.02
        val outRatio = outRms(0.5) / outRms(0.02)
        assertTrue(outRatio < inRatio, "output range ($outRatio) should be smaller than input range ($inRatio)")
    }
}
