package app.openhearing.audiogram

import app.openhearing.common.DecibelsHl
import app.openhearing.common.Ear
import app.openhearing.common.Hertz

/**
 * A single measured (or estimated) hearing threshold: for a given [ear] at a
 * given [frequency], the quietest level the listener could reliably detect.
 */
data class Threshold(val ear: Ear, val frequency: Hertz, val level: DecibelsHl)

/**
 * The result of a pure-tone screening: per-ear, per-frequency thresholds.
 *
 * Phase 0 ships the immutable model and lookup only. Phase 1 adds the
 * threshold-seeking staircase that produces these points and the
 * audiogram -> gain-curve fitting that consumes them.
 *
 * This is a screening aid, NOT a diagnostic audiogram. See README/SAFETY notes:
 * not a medical device, not a substitute for a professional hearing exam.
 */
data class Audiogram(val thresholds: List<Threshold>) {
    /** The threshold for [ear] at [frequency], or null if it was not measured. */
    fun thresholdAt(ear: Ear, frequency: Hertz): DecibelsHl? =
        thresholds.firstOrNull { it.ear == ear && it.frequency == frequency }?.level

    /** Frequencies measured for [ear], in ascending order. */
    fun frequenciesFor(ear: Ear): List<Hertz> = thresholds.filter { it.ear == ear }
        .map { it.frequency }
        .distinct()
        .sortedBy { it.value }

    companion object {
        /** An empty audiogram, e.g. before any screening has been run. */
        val EMPTY: Audiogram = Audiogram(emptyList())
    }
}
