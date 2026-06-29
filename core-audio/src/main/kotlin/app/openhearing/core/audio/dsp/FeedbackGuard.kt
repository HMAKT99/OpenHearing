package app.openhearing.core.audio.dsp

import kotlin.math.sqrt

/**
 * Detects acoustic feedback ("howl") and ducks gain before it runs away.
 *
 * Feedback is a self-sustaining near-pure tone, so it is highly *periodic*: its
 * normalized autocorrelation at the oscillation lag is close to 1, far higher than
 * speech or noise. When a strong, sustained periodic component appears, the guard
 * progressively attenuates the signal, recovering once it clears.
 *
 * This is a pragmatic v1 guard (detect + duck), not a full adaptive feedback
 * canceller. It is pure and block-based, so its behaviour is unit-testable
 * (sine wave -> engages; noise/speech-like -> stays mostly open).
 */
class FeedbackGuard(
    sampleRateHz: Int,
    private val tonalityThreshold: Double = 0.9,
    private val activationRms: Double = 0.1,
    private val minGain: Float = 0.2f,
    private val attackPerBlock: Float = 0.5f,
    private val releasePerBlock: Float = 0.1f,
) {
    // Feedback in roughly the 500 Hz–4 kHz range -> these autocorrelation lags.
    private val minLag = (sampleRateHz / MAX_FEEDBACK_HZ).coerceAtLeast(1)
    private val maxLag = (sampleRateHz / MIN_FEEDBACK_HZ).coerceAtLeast(minLag + 1)

    private var currentGain = 1.0f

    /** Current attenuation applied, as a linear gain in (0, 1]. 1.0 = fully open. */
    val gain: Float get() = currentGain

    /** Inspect [buffer] for feedback and duck gain in place if detected. */
    fun process(buffer: FloatArray) {
        if (buffer.size <= maxLag) return

        val howling = tonality(buffer) >= tonalityThreshold && rms(buffer) >= activationRms
        val target = if (howling) minGain else 1.0f
        val rate = if (howling) attackPerBlock else releasePerBlock
        val startGain = currentGain
        val endGain = startGain + (target - startGain) * rate

        // Ramp the gain across the block so the ducking itself never clicks.
        val step = (endGain - startGain) / buffer.size
        for (i in buffer.indices) {
            buffer[i] = (buffer[i] * (startGain + step * i))
        }
        currentGain = endGain
    }

    fun reset() {
        currentGain = 1.0f
    }

    /** Peak normalized autocorrelation across the feedback lag range (0..~1). */
    private fun tonality(buffer: FloatArray): Double {
        var energy = 0.0
        for (s in buffer) energy += s.toDouble() * s
        if (energy <= 1e-12) return 0.0

        var best = 0.0
        var lag = minLag
        while (lag <= maxLag) {
            var corr = 0.0
            for (i in lag until buffer.size) {
                corr += buffer[i].toDouble() * buffer[i - lag]
            }
            val normalized = corr / energy
            if (normalized > best) best = normalized
            lag++
        }
        return best
    }

    private fun rms(buffer: FloatArray): Double {
        var sum = 0.0
        for (s in buffer) sum += s.toDouble() * s
        return sqrt(sum / buffer.size)
    }

    private companion object {
        const val MIN_FEEDBACK_HZ = 500
        const val MAX_FEEDBACK_HZ = 4000
    }
}
