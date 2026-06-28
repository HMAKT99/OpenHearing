package app.openhearing.common

/**
 * SAFETY-CRITICAL constants. This object is the single source of truth for every
 * output-loudness limit in the app. The hearing test plays calibrated tones and
 * the assist mode amplifies live sound directly into someone's ears — any code
 * path that can exceed these limits is a critical bug.
 *
 * These are conservative defaults. The real, enforced limiter (and its explicit
 * tests) lands in Phase 2 in :core-audio and MUST reference these values rather
 * than redefining its own. The mapping from digital level to true dB SPL depends
 * on per-device/per-earbud calibration — see docs/SAFETY.md. Until calibrated,
 * the app must err on the quiet side.
 */
object SafetyConstants {
    /**
     * Absolute ceiling for any audio the app produces, in dB SPL. Chosen well
     * below levels associated with rapid noise-induced hearing damage and below
     * a typical hearing-aid maximum output (OSPL90). Conservative on purpose.
     */
    const val MAX_OUTPUT_SPL_DB: Double = 100.0

    /**
     * Ceiling for pure tones played during the hearing screening, in dB SPL.
     * Test tones never need to be as loud as the assist-mode ceiling; capping
     * them lower protects users who already have reduced loudness tolerance.
     */
    const val MAX_TONE_SPL_DB: Double = 90.0

    /**
     * Minimum rise/fall time for any tone, in milliseconds. Tones must ramp on
     * and off gently — never an instantaneous (clicky, startling) onset.
     */
    const val MIN_TONE_RAMP_MS: Long = 20L

    /**
     * Default master output cap applied on top of the audiogram-derived gain, in
     * decibels. The user can lower this but a hard maximum still applies. The UI
     * must always expose this cap plus an instant mute.
     */
    const val DEFAULT_MASTER_GAIN_CAP_DB: Double = 30.0

    /** Largest master gain cap the UI may ever offer, in decibels. */
    const val MAX_MASTER_GAIN_CAP_DB: Double = 40.0

    /**
     * True when [outputSpl] is within the absolute safety ceiling. The Phase 2
     * limiter uses this as its invariant: output that fails this check must be
     * attenuated before it ever reaches the audio device.
     */
    fun isWithinOutputCeiling(outputSpl: DecibelsSpl): Boolean = outputSpl.value <= MAX_OUTPUT_SPL_DB
}
