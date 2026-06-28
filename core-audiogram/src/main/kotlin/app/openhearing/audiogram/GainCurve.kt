package app.openhearing.audiogram

import app.openhearing.common.Hertz
import kotlin.math.log10

/**
 * A prescribed frequency-dependent insertion gain (the amplification to apply per
 * frequency), derived from an audiogram by a [FittingStrategy]. The Phase 2 DSP
 * samples this curve to drive its multi-band gain.
 *
 * Gains are in decibels and are already clamped to a safe per-band maximum by the
 * fitting strategy. A master cap and the output limiter (see SafetyConstants /
 * :core-audio) still apply on top — this curve is a target, not a bypass.
 */
class GainCurve(points: List<GainPoint>) {
    /** Per-frequency prescribed gain, sorted ascending by frequency. */
    val points: List<GainPoint> = points.sortedBy { it.frequency.value }

    init {
        require(this.points.isNotEmpty()) { "a gain curve needs at least one point" }
    }

    /**
     * Prescribed gain at [frequency], in dB. Interpolated linearly across the
     * (log-spaced) frequency axis between measured points, and clamped to the
     * nearest endpoint outside the measured range.
     */
    fun gainAt(frequency: Hertz): Double {
        val f = frequency.value
        val first = points.first()
        val last = points.last()
        if (f <= first.frequency.value) return first.gainDb
        if (f >= last.frequency.value) return last.gainDb

        val upperIdx = points.indexOfFirst { it.frequency.value >= f }
        val lower = points[upperIdx - 1]
        val upper = points[upperIdx]
        val span = log10(upper.frequency.value) - log10(lower.frequency.value)
        if (span <= 0.0) return lower.gainDb
        val t = (log10(f) - log10(lower.frequency.value)) / span
        return lower.gainDb + t * (upper.gainDb - lower.gainDb)
    }
}

/** A single point on a [GainCurve]: [gainDb] of insertion gain at [frequency]. */
data class GainPoint(val frequency: Hertz, val gainDb: Double)

/**
 * Maps an audiogram (per ear) to a prescribed [GainCurve]. Different prescriptive
 * formulae plug in behind this seam (half-gain today; a NAL-NL2-style fit later —
 * see docs/FITTING.md for the rationale).
 */
fun interface FittingStrategy {
    fun fit(audiogram: Audiogram, ear: app.openhearing.common.Ear): GainCurve
}

/**
 * The **fractional-gain** rule: prescribe a fixed fraction of the hearing loss as
 * insertion gain at each frequency (loss measured in dB HL above normal). With
 * [fraction] = 0.5 this is the classic **half-gain rule** — simple, transparent,
 * conservative, and a sensible default for a v1 hearing-assist tool. See
 * docs/FITTING.md for why we start here rather than NAL-NL2.
 *
 * Gains are floored at 0 (we never attenuate normal-hearing regions) and capped at
 * [maxBandGainDb] for safety.
 */
class FractionalGainRule(private val fraction: Double = 0.5, private val maxBandGainDb: Double = 40.0) :
    FittingStrategy {
    init {
        require(fraction in 0.0..1.0) { "fraction must be in 0..1" }
        require(maxBandGainDb >= 0.0) { "max band gain must be non-negative" }
    }

    override fun fit(audiogram: Audiogram, ear: app.openhearing.common.Ear): GainCurve {
        val freqs = audiogram.frequenciesFor(ear)
        require(freqs.isNotEmpty()) { "audiogram has no thresholds for $ear" }
        val points =
            freqs.map { freq ->
                val lossDb = audiogram.thresholdAt(ear, freq)!!.value
                val gain = (fraction * lossDb).coerceIn(0.0, maxBandGainDb)
                GainPoint(freq, gain)
            }
        return GainCurve(points)
    }
}

/** The half-gain rule — the default v1 fitting (see [FractionalGainRule]). */
fun halfGainFitting(maxBandGainDb: Double = 40.0): FittingStrategy =
    FractionalGainRule(fraction = 0.5, maxBandGainDb = maxBandGainDb)
