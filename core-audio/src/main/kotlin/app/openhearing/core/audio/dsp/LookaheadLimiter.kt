package app.openhearing.core.audio.dsp

import app.openhearing.core.audio.OutputLimiter
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max

/**
 * SAFETY-CRITICAL. The production output limiter and the mandatory final stage of
 * the hearing-assist chain.
 *
 * It combines two mechanisms:
 * 1. a **look-ahead smooth gain reduction** — gain starts easing down a few
 *    milliseconds *before* a loud sample is output, so limiting is transparent
 *    (no clicks, minimal distortion); and
 * 2. a **hard brick-wall clamp** applied last, which mathematically guarantees no
 *    output sample ever exceeds the ceiling — even if the smooth stage hasn't fully
 *    caught up. The guarantee does not depend on the smoothing being perfect.
 *
 * Pure and sample-accurate, so the guarantee is exhaustively unit-tested (see the
 * limiter safety suite, the Phase 2 release gate).
 */
class LookaheadLimiter(
    override val ceilingLinear: Float,
    sampleRateHz: Int,
    lookaheadMs: Double = 2.0,
    releaseMs: Double = 60.0,
) : OutputLimiter {
    init {
        require(ceilingLinear in 0f..1f && ceilingLinear > 0f) { "ceiling must be in (0, 1]" }
    }

    private val ceiling = ceilingLinear.toDouble()
    private val delay = FloatArray(max(1, (lookaheadMs * 0.001 * sampleRateHz).toInt()))
    private var writeIndex = 0
    private var gain = 1.0

    // Attack eases gain down over ~the look-ahead window; release recovers slowly.
    private val attackCoef = exp(-1.0 / max(1.0, lookaheadMs * 0.001 * sampleRateHz))
    private val releaseCoef = exp(-1.0 / max(1.0, releaseMs * 0.001 * sampleRateHz))

    override fun processInPlace(buffer: FloatArray) {
        for (i in buffer.indices) {
            buffer[i] = processSample(buffer[i])
        }
    }

    private fun processSample(x: Float): Float {
        val mag = abs(x.toDouble())
        val desired = if (mag > ceiling) ceiling / mag else 1.0
        gain =
            if (desired < gain) {
                attackCoef * gain + (1 - attackCoef) * desired
            } else {
                releaseCoef * gain + (1 - releaseCoef) * desired
            }

        val delayed = delay[writeIndex]
        delay[writeIndex] = x
        writeIndex = (writeIndex + 1) % delay.size

        var y = delayed * gain
        // Brick-wall backstop — the hard guarantee.
        if (y > ceiling) y = ceiling
        if (y < -ceiling) y = -ceiling
        return y.toFloat()
    }

    fun reset() {
        delay.fill(0f)
        writeIndex = 0
        gain = 1.0
    }
}
