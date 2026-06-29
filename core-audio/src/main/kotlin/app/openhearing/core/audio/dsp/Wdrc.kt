package app.openhearing.core.audio.dsp

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow

/**
 * Broadband wide-dynamic-range compression (WDRC). Combined with the frequency
 * shaping in [GainEqualizer], this lifts quiet sounds (via makeup gain) while
 * keeping loud sounds from being over-amplified (downward compression above the
 * threshold). A soft knee avoids audible pumping.
 *
 * v1 is single-band and pure (sample-by-sample with an explicit envelope), so its
 * static curve and time behaviour are unit-testable. Multi-band WDRC can replace
 * it behind the same call shape later.
 */
class Wdrc(
    private val sampleRateHz: Int,
    private val thresholdDbFs: Double = -35.0,
    private val ratio: Double = 3.0,
    private val kneeWidthDb: Double = 8.0,
    private val makeupGainDb: Double = 0.0,
    attackMs: Double = 5.0,
    releaseMs: Double = 80.0,
) {
    init {
        require(ratio >= 1.0) { "ratio must be >= 1" }
        require(kneeWidthDb >= 0.0) { "knee must be non-negative" }
    }

    private val attackCoef = timeConstant(attackMs)
    private val releaseCoef = timeConstant(releaseMs)
    private var envelope = 0.0

    private fun timeConstant(ms: Double): Double = if (ms <= 0.0) 0.0 else exp(-1.0 / (ms * 0.001 * sampleRateHz))

    /** Compress [buffer] in place. */
    fun process(buffer: FloatArray) {
        for (i in buffer.indices) {
            val x = buffer[i].toDouble()
            val rectified = abs(x)
            envelope =
                if (rectified > envelope) {
                    attackCoef * envelope + (1 - attackCoef) * rectified
                } else {
                    releaseCoef * envelope + (1 - releaseCoef) * rectified
                }
            val gainDb = makeupGainDb + computeGainReductionDb(levelDb(envelope))
            buffer[i] = (x * 10.0.pow(gainDb / 20.0)).toFloat()
        }
    }

    fun reset() {
        envelope = 0.0
    }

    private fun levelDb(linear: Double): Double =
        if (linear <= MIN_LEVEL_LINEAR) MIN_LEVEL_DB else 20.0 * ln(linear) / LN10

    /** Downward-compression gain (<= 0 dB) for an input [inputDb], with soft knee. */
    private fun computeGainReductionDb(inputDb: Double): Double {
        val overshoot = inputDb - thresholdDbFs
        return when {
            // Below the knee: no compression.
            overshoot <= -kneeWidthDb / 2.0 -> 0.0
            // Within the knee: quadratic interpolation for a smooth transition.
            kneeWidthDb > 0.0 && abs(overshoot) <= kneeWidthDb / 2.0 -> {
                val x = overshoot + kneeWidthDb / 2.0
                -(1.0 - 1.0 / ratio) * (x * x) / (2.0 * kneeWidthDb)
            }
            // Above the knee: full compression ratio.
            else -> -(1.0 - 1.0 / ratio) * overshoot
        }
    }

    private companion object {
        const val MIN_LEVEL_LINEAR = 1e-9
        const val MIN_LEVEL_DB = -180.0
        val LN10 = ln(10.0)
    }
}
