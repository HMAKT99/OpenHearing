package app.openhearing.core.audio.dsp

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

/**
 * SAFETY-CRITICAL — the Phase 2 release gate. These tests assert the one invariant
 * that protects the user's ears: **no output sample ever exceeds the ceiling**, for
 * any input whatsoever. If any of these fail, the assist mode must not ship.
 */
class LookaheadLimiterSafetyTest {
    private val sampleRate = 48_000
    private val ceiling = 0.7f

    private fun limiter() = LookaheadLimiter(ceilingLinear = ceiling, sampleRateHz = sampleRate)

    private fun assertWithinCeiling(buffer: FloatArray) {
        val worst = buffer.maxOf { abs(it) }
        assertTrue(worst <= ceiling + 1e-6f, "output peak $worst exceeded ceiling $ceiling")
    }

    @Test
    fun `steady over-ceiling sine stays within the ceiling`() {
        val limiter = limiter()
        val buf = FloatArray(sampleRate) { (3.0 * sin(2.0 * PI * 1000.0 * it / sampleRate)).toFloat() }
        limiter.processInPlace(buf)
        assertWithinCeiling(buf)
    }

    @Test
    fun `sudden full-scale transient after silence is contained`() {
        val limiter = limiter()
        val buf = FloatArray(sampleRate)
        // Silence, then an instantaneous slam to far above the ceiling.
        for (i in sampleRate / 2 until sampleRate) buf[i] = if (i % 2 == 0) 5f else -5f
        limiter.processInPlace(buf)
        assertWithinCeiling(buf)
    }

    @Test
    fun `sustained full-scale square wave is contained`() {
        val limiter = limiter()
        val buf = FloatArray(sampleRate) { if ((it / 50) % 2 == 0) 9f else -9f }
        limiter.processInPlace(buf)
        assertWithinCeiling(buf)
    }

    @Test
    fun `a rising ramp (runaway feedback shape) never escapes`() {
        val limiter = limiter()
        // Exponentially growing amplitude, like an uncontrolled howl.
        val buf =
            FloatArray(sampleRate) {
                val amp = 0.001 * Math.pow(1.0002, it.toDouble())
                (amp * sin(2.0 * PI * 3000.0 * it / sampleRate)).toFloat()
            }
        limiter.processInPlace(buf)
        assertWithinCeiling(buf)
    }

    @ParameterizedTest
    @ValueSource(ints = [1, 7, 99, 2024])
    fun `random garbage input is always contained`(seed: Int) {
        val limiter = limiter()
        val rng = Random(seed)
        repeat(20) {
            val buf = FloatArray(2048) { rng.nextDouble(-50.0, 50.0).toFloat() }
            limiter.processInPlace(buf)
            assertWithinCeiling(buf)
        }
    }

    @Test
    fun `NaN and infinity inputs cannot produce an over-ceiling sample`() {
        val limiter = limiter()
        val buf = floatArrayOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, 100f, -100f, 0f)
        limiter.processInPlace(buf)
        // The brick-wall backstop clamps real magnitudes; NaN cannot be > ceiling.
        for (v in buf) {
            assertTrue(v.isNaN() || abs(v) <= ceiling + 1e-6f, "value $v escaped the ceiling")
        }
    }

    @Test
    fun `quiet signal well under the ceiling is preserved (limiter is transparent)`() {
        val limiter = limiter()
        val original = FloatArray(sampleRate) { (0.1 * sin(2.0 * PI * 1000.0 * it / sampleRate)).toFloat() }
        val buf = original.copyOf()
        limiter.processInPlace(buf)
        // After the look-ahead delay (exactly the look-ahead length in samples), a
        // quiet signal should pass essentially untouched.
        val delay = (2.0 * 0.001 * sampleRate).toInt()
        var maxDelta = 0f
        for (i in sampleRate / 2 until sampleRate) {
            maxDelta = maxOf(maxDelta, abs(buf[i] - original[i - delay]))
        }
        assertTrue(maxDelta < 0.02f, "quiet signal was distorted (max delta $maxDelta)")
    }
}
