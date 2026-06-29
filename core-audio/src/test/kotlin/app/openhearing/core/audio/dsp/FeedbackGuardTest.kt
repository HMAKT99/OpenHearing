package app.openhearing.core.audio.dsp

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

class FeedbackGuardTest {
    private val sampleRate = 48_000
    private val blockSize = 1024

    private fun process(guard: FeedbackGuard, signal: (Int) -> Float, blocks: Int): Float {
        var sample = 0
        repeat(blocks) {
            val block = FloatArray(blockSize) { signal(sample++) }
            guard.process(block)
        }
        return guard.gain
    }

    @Test
    fun `engages on a sustained loud tone (feedback howl)`() {
        val guard = FeedbackGuard(sampleRate)
        val gain = process(guard, { i -> (0.6 * sin(2.0 * PI * 2000.0 * i / sampleRate)).toFloat() }, blocks = 30)
        assertTrue(gain < 0.5f, "guard should duck strongly on a sustained tone, gain=$gain")
    }

    @Test
    fun `stays mostly open on broadband noise (not feedback)`() {
        val guard = FeedbackGuard(sampleRate)
        val rng = Random(42)
        val gain = process(guard, { (rng.nextDouble(-0.6, 0.6)).toFloat() }, blocks = 30)
        assertTrue(gain > 0.8f, "guard should not duck noise much, gain=$gain")
    }

    @Test
    fun `recovers after the tone stops`() {
        val guard = FeedbackGuard(sampleRate)
        process(guard, { i -> (0.6 * sin(2.0 * PI * 2000.0 * i / sampleRate)).toFloat() }, blocks = 30)
        val recovered = process(guard, { 0f }, blocks = 60)
        assertTrue(recovered > 0.8f, "guard should reopen once feedback clears, gain=$recovered")
    }
}
