package app.openhearing.core.audio

import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.pow

/**
 * Small, pure DSP helpers shared across the audio core. Pure functions so they
 * are trivially unit-testable on the JVM (no device required).
 */
object AudioMath {
    /** Convert a linear amplitude (0..1 nominal full scale) to dBFS. */
    fun linearToDbfs(linear: Double): Double {
        val a = abs(linear)
        return if (a <= 0.0) Double.NEGATIVE_INFINITY else 20.0 * log10(a)
    }

    /** Convert a dBFS value (<= 0) to a linear amplitude. */
    fun dbfsToLinear(dbfs: Double): Double = 10.0.pow(dbfs / 20.0)

    /** Peak absolute sample in [buffer], or 0 for an empty buffer. */
    fun peak(buffer: FloatArray): Float {
        var peak = 0f
        for (sample in buffer) {
            val a = abs(sample)
            if (a > peak) peak = a
        }
        return peak
    }
}
