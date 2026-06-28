package app.openhearing.core.audio

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs

/**
 * SAFETY-CRITICAL tests. Phase 0 pins the core invariant of the output limiter:
 * nothing leaves above the ceiling. Phase 2 expands this into the full limiter
 * safety suite (ramps, sudden transients, sustained overload, feedback howl).
 */
class OutputLimiterTest {
    @Test
    fun `clamps samples above the ceiling and leaves quiet samples untouched`() {
        val limiter = HardCeilingLimiter(ceilingLinear = 0.5f)
        val buffer = floatArrayOf(0.0f, 0.25f, 0.5f, 0.9f, -0.9f, -0.4f)

        limiter.processInPlace(buffer)

        // Nothing exceeds the ceiling...
        assertTrue(buffer.all { abs(it) <= 0.5f }, "no sample may exceed the ceiling")
        // ...and samples already within range are unchanged.
        assertEquals(0.25f, buffer[1])
        assertEquals(-0.4f, buffer[5])
        // Over-ceiling samples are pinned to +/- ceiling.
        assertEquals(0.5f, buffer[3])
        assertEquals(-0.5f, buffer[4])
    }

    @Test
    fun `extreme input can never escape the ceiling`() {
        val limiter = HardCeilingLimiter(ceilingLinear = 0.8f)
        val buffer = FloatArray(1024) { if (it % 2 == 0) 100f else -100f }

        limiter.processInPlace(buffer)

        assertTrue(buffer.all { abs(it) <= 0.8f })
    }

    @Test
    fun `rejects an out-of-range ceiling`() {
        assertThrows(IllegalArgumentException::class.java) { HardCeilingLimiter(0f) }
        assertThrows(IllegalArgumentException::class.java) { HardCeilingLimiter(1.5f) }
    }

    @Test
    fun `dbfs round-trips through linear`() {
        val linear = AudioMath.dbfsToLinear(-6.0)
        assertEquals(-6.0, AudioMath.linearToDbfs(linear), 1e-9)
    }
}
