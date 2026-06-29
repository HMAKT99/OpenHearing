package app.openhearing.core.audio.dsp

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

class BiquadTest {
    private val sampleRate = 48_000

    /** RMS gain (dB) the filter applies to a steady sine at [freq]. */
    private fun gainDbAt(filter: Biquad, freq: Double): Double {
        val n = sampleRate // 1 second
        var inSq = 0.0
        var outSq = 0.0
        // Skip a settling transient before measuring.
        for (i in 0 until n) {
            val x = sin(2.0 * PI * freq * i / sampleRate)
            val y = filter.processSample(x)
            if (i > sampleRate / 10) {
                inSq += x * x
                outSq += y * y
            }
        }
        return 20.0 * kotlin.math.log10(sqrt(outSq) / sqrt(inSq))
    }

    @Test
    fun `peaking filter boosts near its center frequency`() {
        val filter = Biquad.peaking(centerHz = 2000.0, gainDb = 12.0, q = 1.4, sampleRateHz = sampleRate)
        val gain = gainDbAt(filter, 2000.0)
        assertEquals(12.0, gain, 1.5, "center-frequency boost should be ~12 dB")
    }

    @Test
    fun `peaking filter leaves far-away frequencies roughly unchanged`() {
        val filter = Biquad.peaking(centerHz = 4000.0, gainDb = 12.0, q = 1.4, sampleRateHz = sampleRate)
        val gain = gainDbAt(filter, 200.0)
        assertTrue(kotlin.math.abs(gain) < 1.5, "far-band gain should be near 0 dB, was $gain")
    }

    @Test
    fun `zero-gain peaking filter is transparent`() {
        val filter = Biquad.peaking(centerHz = 1000.0, gainDb = 0.0, q = 1.4, sampleRateHz = sampleRate)
        assertEquals(0.0, gainDbAt(filter, 1000.0), 0.1)
    }
}
