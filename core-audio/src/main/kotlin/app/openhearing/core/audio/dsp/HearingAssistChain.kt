package app.openhearing.core.audio.dsp

import app.openhearing.audiogram.GainCurve
import app.openhearing.common.SafetyConstants
import app.openhearing.core.audio.AudioProcessor
import kotlin.math.pow

/**
 * The full real-time hearing-assist signal chain (mono, v1):
 *
 * ```
 * input ─► EQ (audiogram gain) ─► WDRC ─► feedback guard ─► master gain ─► LIMITER ─► output
 * ```
 *
 * The [LookaheadLimiter] is always the final stage, so nothing leaves above the
 * ceiling regardless of the audiogram, the master gain, or a feedback event. The
 * master gain is hard-capped by [SafetyConstants]. Pure (no Android, no
 * allocation in [process]) so the whole chain is unit-testable.
 */
class HearingAssistChain(
    gainCurve: GainCurve,
    sampleRateHz: Int,
    masterGainDb: Double,
    ceilingLinear: Float = DEFAULT_CEILING_LINEAR,
) : AudioProcessor {
    private val eq = GainEqualizer(gainCurve, sampleRateHz)
    private val wdrc = Wdrc(sampleRateHz)
    private val guard = FeedbackGuard(sampleRateHz)
    private val limiter = LookaheadLimiter(ceilingLinear, sampleRateHz)

    // Master gain can never exceed the safety cap, and never attenuates below unity
    // here (the user's volume cap scales this separately, upstream). Volatile so the
    // UI thread can adjust it while the audio thread keeps processing; the limiter
    // downstream bounds the output regardless of when the new value lands.
    @Volatile
    private var masterGainLinear: Float = linearGain(masterGainDb)

    /** Live-adjustable master gain; clamped to [0, SafetyConstants.MAX_MASTER_GAIN_CAP_DB]. */
    fun setMasterGainDb(db: Double) {
        masterGainLinear = linearGain(db)
    }

    override fun process(buffer: FloatArray) {
        eq.process(buffer)
        wdrc.process(buffer)
        guard.process(buffer)
        val gain = masterGainLinear // one volatile read per block
        for (i in buffer.indices) {
            buffer[i] = buffer[i] * gain
        }
        limiter.processInPlace(buffer)
    }

    fun reset() {
        eq.reset()
        wdrc.reset()
        guard.reset()
        limiter.reset()
    }

    companion object {
        /**
         * Digital output ceiling (linear). Conservative headroom below full scale.
         * Until the path is calibrated this is a digital cap, not a true dB SPL
         * cap; calibration may lower it further. See docs/SAFETY.md / docs/CALIBRATION.md.
         */
        const val DEFAULT_CEILING_LINEAR = 0.9f

        private fun linearGain(db: Double): Float =
            10.0.pow(db.coerceIn(0.0, SafetyConstants.MAX_MASTER_GAIN_CAP_DB) / 20.0).toFloat()
    }
}
