package app.openhearing.core.audio

import kotlin.math.abs

/**
 * SAFETY-CRITICAL. The mandatory final stage of every processing chain. It
 * guarantees that no sample leaving the app exceeds a configured peak ceiling,
 * so a bug, a feedback howl, or an aggressive gain setting upstream can never
 * blast loud audio into someone's ears.
 *
 * Phase 0 ships this interface plus [HardCeilingLimiter], a correct, tested
 * brick-wall clamp. Phase 2 adds the production limiter (lookahead + smooth gain
 * release to avoid distortion) behind this same interface — and its dedicated
 * safety test suite is a Phase 2 release gate. Any production limiter MUST keep
 * the invariant verified here: output peak <= ceiling, always.
 */
interface OutputLimiter {
    /** Maximum allowed peak as a linear amplitude in (0, 1]. */
    val ceilingLinear: Float

    /** Limit [buffer] in place so that no sample exceeds [ceilingLinear]. */
    fun processInPlace(buffer: FloatArray)
}

/**
 * A brick-wall peak clamp: simple, allocation-free, and provably within ceiling.
 * Not transparent (hard clipping distorts), but it is the safe floor the smarter
 * Phase 2 limiter must never undershoot. Used now to anchor the safety tests.
 */
class HardCeilingLimiter(override val ceilingLinear: Float) : OutputLimiter {
    init {
        require(ceilingLinear in 0f..1f) { "ceiling must be in (0, 1], was $ceilingLinear" }
        require(ceilingLinear > 0f) { "ceiling must be > 0" }
    }

    override fun processInPlace(buffer: FloatArray) {
        for (i in buffer.indices) {
            val s = buffer[i]
            if (abs(s) > ceilingLinear) {
                buffer[i] = if (s > 0f) ceilingLinear else -ceilingLinear
            }
        }
    }
}
