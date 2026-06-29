package app.openhearing.assist

import app.openhearing.audiogram.GainCurve
import app.openhearing.core.audio.AndroidAudioEngine
import app.openhearing.core.audio.AudioFormat
import app.openhearing.core.audio.dsp.HearingAssistChain
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Immutable configuration for an assist session, derived from the active profile. */
data class AssistConfig(
    val gainCurve: GainCurve,
    val masterGainDb: Double,
    /** Output ceiling (linear) from comfort calibration; the limiter enforces it. */
    val ceilingLinear: Float = HearingAssistChain.DEFAULT_CEILING_LINEAR,
    val sampleRateHz: Int = DEFAULT_SAMPLE_RATE_HZ,
    val framesPerBlock: Int = DEFAULT_FRAMES_PER_BLOCK,
) {
    companion object {
        const val DEFAULT_SAMPLE_RATE_HZ = 48_000

        // ~4 ms at 48 kHz — a low-latency block size; the engine adjusts device buffers.
        const val DEFAULT_FRAMES_PER_BLOCK = 192
    }
}

/**
 * Single source of truth for assist-mode state, shared between the UI and the
 * foreground [AssistService]. The UI sets [config] and starts/stops the service;
 * the service drives the engine through here. Mute is immediate.
 */
@Singleton
class AssistController
@Inject
constructor() {
    private val engine = AndroidAudioEngine()

    @Volatile
    private var config: AssistConfig? = null

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    fun configure(newConfig: AssistConfig) {
        config = newConfig
    }

    fun hasConfig(): Boolean = config != null

    /** Build the chain from the current config and start the real-time loop. */
    fun startEngine() {
        val c = config ?: return
        if (engine.isRunning) return
        val chain =
            HearingAssistChain(
                gainCurve = c.gainCurve,
                sampleRateHz = c.sampleRateHz,
                masterGainDb = c.masterGainDb,
                ceilingLinear = c.ceilingLinear,
            )
        engine.start(
            AudioFormat(c.sampleRateHz, channelCount = 1, framesPerBlock = c.framesPerBlock),
            chain,
        )
        _running.value = engine.isRunning
    }

    fun stopEngine() {
        engine.stop()
        _running.value = false
    }
}
