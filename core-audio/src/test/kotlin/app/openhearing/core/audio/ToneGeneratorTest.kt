package app.openhearing.core.audio

import app.openhearing.common.Hertz
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs

class ToneGeneratorTest {
    private val sampleRate = 48_000
    private val gen = ToneGenerator(sampleRateHz = sampleRate, maxAmplitude = 0.5f)

    @Test
    fun `produces the requested number of samples`() {
        val tone = gen.generate(Hertz(1000.0), durationMs = 500, amplitude = 0.3f)
        assertEquals(sampleRate / 2, tone.size) // 500 ms at 48 kHz
    }

    @Test
    fun `never exceeds the requested amplitude`() {
        val tone = gen.generate(Hertz(1000.0), durationMs = 300, amplitude = 0.3f)
        assertTrue(tone.all { abs(it) <= 0.3f + 1e-6f }, "peak exceeded requested amplitude")
    }

    @Test
    fun `clamps amplitude to the safety ceiling`() {
        val tone = gen.generate(Hertz(1000.0), durationMs = 300, amplitude = 5.0f) // absurd request
        assertTrue(tone.all { abs(it) <= 0.5f + 1e-6f }, "amplitude was not clamped to the ceiling")
    }

    @Test
    fun `ramps gently — onset and offset start near silence`() {
        val tone = gen.generate(Hertz(1000.0), durationMs = 300, amplitude = 0.4f, rampMs = 20)
        assertTrue(abs(tone.first()) < 0.01f, "onset was not ramped")
        assertTrue(abs(tone.last()) < 0.01f, "offset was not ramped")
    }

    @Test
    fun `forces a minimum ramp even if zero is requested`() {
        val tone = gen.generate(Hertz(1000.0), durationMs = 300, amplitude = 0.4f, rampMs = 0)
        // First sample must still be near zero — no abrupt, clicky onset.
        assertTrue(abs(tone.first()) < 0.01f, "a zero ramp must be raised to the safety minimum")
    }

    @Test
    fun `frequency is approximately correct (zero-crossing count)`() {
        val freq = 1000.0
        val durationMs = 1000L
        val tone = gen.generate(Hertz(freq), durationMs = durationMs, amplitude = 0.4f)
        // Count rising zero crossings in the steady middle section (skip ramps).
        var crossings = 0
        for (i in 2_000 until tone.size - 2_000) {
            if (tone[i - 1] <= 0f && tone[i] > 0f) crossings++
        }
        // ~1000 cycles per second over ~0.92 s of steady tone.
        assertTrue(crossings in 850..1000, "unexpected zero-crossing count: $crossings")
    }

    @Test
    fun `level mapping is monotonic and capped`() {
        val loud = ToneLevel.amplitudeFor(90.0, maxLevelDbHl = 90.0, ceiling = 0.5f)
        val mid = ToneLevel.amplitudeFor(70.0, maxLevelDbHl = 90.0, ceiling = 0.5f)
        val quiet = ToneLevel.amplitudeFor(30.0, maxLevelDbHl = 90.0, ceiling = 0.5f)
        assertEquals(0.5f, loud, 1e-6f)
        assertTrue(mid < loud && quiet < mid, "amplitude must increase with level")
        // 20 dB below max -> ~ ceiling / 10.
        assertEquals(0.05f, mid, 1e-3f)
    }
}
