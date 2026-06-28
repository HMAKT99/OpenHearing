package app.openhearing.core.audio

import app.openhearing.common.Hertz
import app.openhearing.common.SafetyConstants
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Generates calibrated-shape pure tones for the hearing screening. Pure and
 * allocation-explicit, so the *shape and safety* of every tone is unit-tested on
 * the JVM with no device.
 *
 * Safety properties enforced here (see docs/SAFETY.md):
 * - amplitude is clamped to [maxAmplitude] — a tone can never be generated louder;
 * - every tone has a raised-cosine on/off ramp of at least
 *   [SafetyConstants.MIN_TONE_RAMP_MS], so onsets are never abrupt/clicky.
 */
class ToneGenerator(
    private val sampleRateHz: Int = DEFAULT_SAMPLE_RATE_HZ,
    /** Hard cap on linear amplitude; conservative headroom below full scale. */
    private val maxAmplitude: Float = DEFAULT_MAX_AMPLITUDE,
) {
    init {
        require(sampleRateHz > 0) { "sampleRateHz must be positive" }
        require(maxAmplitude in 0f..1f) { "maxAmplitude must be in 0..1" }
    }

    /**
     * Generate a mono tone at [frequency] for [durationMs], at linear [amplitude]
     * (clamped to [maxAmplitude]), with a raised-cosine ramp of [rampMs]
     * (raised to the safety minimum if smaller).
     */
    fun generate(
        frequency: Hertz,
        durationMs: Long,
        amplitude: Float,
        rampMs: Long = SafetyConstants.MIN_TONE_RAMP_MS,
    ): FloatArray {
        require(durationMs > 0) { "durationMs must be positive" }
        val amp = amplitude.coerceIn(0f, maxAmplitude)
        val totalSamples = (durationMs * sampleRateHz / 1000L).toInt().coerceAtLeast(1)

        val safeRampMs = maxOf(rampMs, SafetyConstants.MIN_TONE_RAMP_MS)
        // Ramp can be at most half the tone (so on+off ramps fit).
        val rampSamples = min((safeRampMs * sampleRateHz / 1000L).toInt(), totalSamples / 2)

        val out = FloatArray(totalSamples)
        val radiansPerSample = 2.0 * PI * frequency.value / sampleRateHz
        for (i in 0 until totalSamples) {
            val envelope = envelopeAt(i, totalSamples, rampSamples)
            out[i] = (amp * envelope * sin(radiansPerSample * i)).toFloat()
        }
        return out
    }

    /** Raised-cosine ramp up over the first [rampSamples], down over the last. */
    private fun envelopeAt(i: Int, total: Int, rampSamples: Int): Double {
        if (rampSamples <= 0) return 1.0
        return when {
            i < rampSamples -> 0.5 * (1.0 - cos(PI * i / rampSamples))
            i >= total - rampSamples -> {
                val j = total - 1 - i
                0.5 * (1.0 - cos(PI * j / rampSamples))
            }
            else -> 1.0
        }
    }

    companion object {
        const val DEFAULT_SAMPLE_RATE_HZ = 48_000

        /**
         * Default amplitude ceiling for generated tones — well below full scale to
         * leave headroom and to stay conservative while the path is uncalibrated.
         */
        const val DEFAULT_MAX_AMPLITUDE = 0.5f
    }
}
