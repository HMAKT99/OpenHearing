package app.openhearing.core.audio

import kotlin.math.pow

/**
 * Maps a screening presentation level (nominal dB HL) to a linear tone amplitude.
 *
 * !!! UNCALIBRATED !!!
 * Without per-device / per-earbud calibration we cannot map a level to a true
 * dB SPL at the eardrum. This mapping is therefore *relative and conservative*:
 * higher level -> louder, monotonically, with the maximum level pinned to a safe
 * amplitude ceiling and everything else scaled below it. The resulting audiogram
 * is an estimate, not a clinical measurement — the app must say so. See
 * docs/SAFETY.md and docs/FITTING.md.
 */
object ToneLevel {
    /**
     * Linear amplitude for [levelDbHl], where [maxLevelDbHl] maps to [ceiling] and
     * each 20 dB below that divides the amplitude by 10 (i.e. a standard dB scale).
     * Clamped to `[0, ceiling]`.
     */
    fun amplitudeFor(
        levelDbHl: Double,
        maxLevelDbHl: Double,
        ceiling: Float = ToneGenerator.DEFAULT_MAX_AMPLITUDE,
    ): Float {
        val dbBelowMax = (levelDbHl - maxLevelDbHl).coerceAtMost(0.0)
        val amplitude = ceiling * 10.0.pow(dbBelowMax / 20.0)
        return amplitude.toFloat().coerceIn(0f, ceiling)
    }
}
