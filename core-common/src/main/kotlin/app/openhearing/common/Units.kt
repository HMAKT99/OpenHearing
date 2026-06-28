package app.openhearing.common

/**
 * Strongly-typed audio units. Using value classes prevents mixing up the many
 * "just a Double" quantities that flow through a hearing-assist pipeline
 * (frequencies, the several distinct decibel references, gains).
 *
 * Phase 0 ships the types; the screening/DSP code in later phases builds on them.
 */

/** A frequency in Hertz. */
@JvmInline
value class Hertz(val value: Double) {
    init {
        require(value >= 0.0) { "Frequency must be non-negative, was $value" }
    }

    companion object {
        /** Audiometric test frequencies used by a standard pure-tone screening. */
        val AUDIOMETRIC: List<Hertz> =
            listOf(250.0, 500.0, 1000.0, 2000.0, 3000.0, 4000.0, 6000.0, 8000.0).map(::Hertz)
    }
}

/**
 * Hearing level in decibels (dB HL) — the audiogram reference. 0 dB HL is the
 * threshold of a normally-hearing listener at a given frequency; higher numbers
 * mean worse hearing. This is what a hearing test produces per ear, per frequency.
 */
@JvmInline
value class DecibelsHl(val value: Double)

/**
 * Sound pressure level in decibels (dB SPL) — the physical, calibrated loudness
 * reference. All output-safety limits are expressed in dB SPL because that is
 * what actually reaches the ear. NOTE: mapping app output to true dB SPL requires
 * per-device/per-earbud calibration (tracked in docs/SAFETY.md).
 */
@JvmInline
value class DecibelsSpl(val value: Double)

/**
 * Full-scale decibels (dBFS) — the digital signal reference, <= 0. 0 dBFS is the
 * loudest a digital sample can be; everything else is negative.
 */
@JvmInline
value class DecibelsFs(val value: Double) {
    init {
        require(value <= 0.0) { "dBFS must be <= 0, was $value" }
    }
}

/** Which ear a measurement or signal applies to. */
enum class Ear {
    LEFT,
    RIGHT,
}
