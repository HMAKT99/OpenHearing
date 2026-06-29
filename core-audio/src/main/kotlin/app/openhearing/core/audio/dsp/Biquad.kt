package app.openhearing.core.audio.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * A second-order IIR (biquad) filter, processed sample-by-sample in Direct Form I.
 * Pure math with explicit state, so its frequency response is unit-testable on the
 * JVM. Coefficients are designed with the standard RBJ "audio EQ cookbook" formulae.
 */
class Biquad(
    private var b0: Double,
    private var b1: Double,
    private var b2: Double,
    private var a1: Double,
    private var a2: Double,
) {
    private var x1 = 0.0
    private var x2 = 0.0
    private var y1 = 0.0
    private var y2 = 0.0

    /** Process one sample. */
    fun processSample(x: Double): Double {
        val y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
        x2 = x1
        x1 = x
        y2 = y1
        y1 = y
        return y
    }

    /** Reset filter memory (e.g. when (re)starting a stream). */
    fun reset() {
        x1 = 0.0
        x2 = 0.0
        y1 = 0.0
        y2 = 0.0
    }

    /** Replace coefficients in place (keeps state) — used when gain is retuned. */
    fun setCoefficients(b0: Double, b1: Double, b2: Double, a1: Double, a2: Double) {
        this.b0 = b0
        this.b1 = b1
        this.b2 = b2
        this.a1 = a1
        this.a2 = a2
    }

    companion object {
        /**
         * Peaking-EQ biquad: boosts/cuts [gainDb] around [centerHz] with bandwidth
         * controlled by [q]. This is how the audiogram's per-frequency insertion
         * gain is realized in the time domain.
         */
        fun peaking(centerHz: Double, gainDb: Double, q: Double, sampleRateHz: Int): Biquad {
            require(centerHz > 0 && centerHz < sampleRateHz / 2.0) { "centerHz out of range" }
            require(q > 0) { "q must be positive" }
            val a = 10.0.pow(gainDb / 40.0)
            val w0 = 2.0 * PI * centerHz / sampleRateHz
            val cosW0 = cos(w0)
            val alpha = sin(w0) / (2.0 * q)

            val b0 = 1.0 + alpha * a
            val b1 = -2.0 * cosW0
            val b2 = 1.0 - alpha * a
            val a0 = 1.0 + alpha / a
            val a1 = -2.0 * cosW0
            val a2 = 1.0 - alpha / a
            return Biquad(b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0)
        }

        /** Coefficients only, for retuning an existing [Biquad] in place. */
        fun peakingCoefficients(centerHz: Double, gainDb: Double, q: Double, sampleRateHz: Int): DoubleArray {
            val f = peaking(centerHz, gainDb, q, sampleRateHz)
            return doubleArrayOf(f.b0, f.b1, f.b2, f.a1, f.a2)
        }

        /** Default filter sharpness for the EQ bands (~1 octave). */
        const val DEFAULT_Q = 1.4
    }
}
