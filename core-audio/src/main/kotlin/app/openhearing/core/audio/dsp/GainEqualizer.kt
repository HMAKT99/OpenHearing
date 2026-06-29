package app.openhearing.core.audio.dsp

import app.openhearing.audiogram.GainCurve

/**
 * Realizes a [GainCurve]'s per-frequency insertion gain as a cascade of peaking
 * biquads — one band centred on each measured frequency. This is the frequency-
 * shaping stage of the hearing-assist chain.
 *
 * It is an approximation (neighbouring bands overlap and sum), which is acceptable
 * for a v1 linear fit; a more exact filterbank can replace it behind this class.
 * Mono in/out (v1 processes a single channel — see HearingAssistChain).
 */
class GainEqualizer(gainCurve: GainCurve, private val sampleRateHz: Int, q: Double = Biquad.DEFAULT_Q) {
    private val bands: List<Biquad> =
        gainCurve.points
            .filter { it.frequency.value > 0 && it.frequency.value < sampleRateHz / 2.0 }
            .map { point ->
                Biquad.peaking(
                    centerHz = point.frequency.value,
                    gainDb = point.gainDb,
                    q = q,
                    sampleRateHz = sampleRateHz,
                )
            }

    /** Apply the EQ to [buffer] in place. */
    fun process(buffer: FloatArray) {
        if (bands.isEmpty()) return
        for (i in buffer.indices) {
            var s = buffer[i].toDouble()
            for (band in bands) {
                s = band.processSample(s)
            }
            buffer[i] = s.toFloat()
        }
    }

    fun reset() = bands.forEach { it.reset() }
}
