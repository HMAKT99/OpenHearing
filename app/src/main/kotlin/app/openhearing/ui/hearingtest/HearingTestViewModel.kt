package app.openhearing.ui.hearingtest

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.openhearing.audiogram.Audiogram
import app.openhearing.audiogram.FittingStrategy
import app.openhearing.audiogram.GainPoint
import app.openhearing.audiogram.PureToneScreening
import app.openhearing.audiogram.StaircaseConfig
import app.openhearing.common.Ear
import app.openhearing.core.audio.ToneGenerator
import app.openhearing.core.audio.ToneLevel
import app.openhearing.core.audio.TonePlayer
import app.openhearing.data.ProfileRepository
import app.openhearing.data.newProfileFrom
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/** Screening lifecycle phase for the UI. */
enum class TestPhase { NOT_STARTED, IN_PROGRESS, DONE }

/** Per-ear prescribed gain for display. */
data class GainSummary(val ear: Ear, val points: List<GainPoint>)

data class HearingTestUiState(
    val phase: TestPhase = TestPhase.NOT_STARTED,
    val currentEar: Ear? = null,
    val currentFrequencyHz: Double? = null,
    val completed: Int = 0,
    val total: Int = 0,
    val isPlaying: Boolean = false,
    val muted: Boolean = false,
    /** Master output cap in 0..1; further attenuates tone amplitude. Never amplifies. */
    val masterCap: Float = 1.0f,
    val audiogram: Audiogram? = null,
    val gains: List<GainSummary> = emptyList(),
) {
    val progress: Float get() = if (total == 0) 0f else completed.toFloat() / total
}

/**
 * Drives the pure-tone screening end to end: plays the current tone, collects the
 * user's heard/not-heard response, advances the [PureToneScreening], and on
 * completion produces the audiogram and the half-gain fitting.
 *
 * Safety: an instant mute / stop is always available; tone amplitude is attenuated
 * by [HearingTestUiState.masterCap] and ultimately bounded by the TonePlayer's
 * limiter. Output is uncalibrated — the UI must present results as estimates.
 */
@HiltViewModel
class HearingTestViewModel
@Inject
constructor(
    private val toneGenerator: ToneGenerator,
    private val tonePlayer: TonePlayer,
    private val fittingStrategy: FittingStrategy,
    private val profileRepository: ProfileRepository,
) : ViewModel() {
    private val config = StaircaseConfig()
    private var screening: PureToneScreening? = null
    private var playJob: Job? = null

    private val _uiState = MutableStateFlow(HearingTestUiState())
    val uiState: StateFlow<HearingTestUiState> = _uiState.asStateFlow()

    fun start() {
        val s = PureToneScreening(config = config)
        screening = s
        _uiState.value =
            HearingTestUiState(
                phase = TestPhase.IN_PROGRESS,
                total = s.totalPoints,
                masterCap = _uiState.value.masterCap,
            )
        presentCurrent()
    }

    /** Replays the current tone (e.g. if the user wasn't sure). */
    fun replay() = presentCurrent()

    fun onHeard() = respond(heard = true)

    fun onNotHeard() = respond(heard = false)

    fun setMasterCap(cap: Float) {
        _uiState.update { it.copy(masterCap = cap.coerceIn(0f, 1f)) }
    }

    /** Instant mute: stop any tone immediately. */
    fun mute() {
        playJob?.cancel()
        tonePlayer.stop()
        _uiState.update { it.copy(isPlaying = false, muted = true) }
    }

    private fun respond(heard: Boolean) {
        val s = screening ?: return
        if (s.isComplete()) return
        s.submitResponse(heard)
        if (s.isComplete()) {
            finish(s)
        } else {
            presentCurrent()
        }
    }

    private fun presentCurrent() {
        val s = screening ?: return
        val stimulus = s.currentStimulus() ?: return
        _uiState.update {
            it.copy(
                currentEar = stimulus.ear,
                currentFrequencyHz = stimulus.frequency.value,
                completed = s.completedPoints(),
                muted = false,
            )
        }
        val amplitude =
            ToneLevel.amplitudeFor(
                levelDbHl = stimulus.level.value,
                maxLevelDbHl = config.maxLevelDbHl,
                ceiling = ToneGenerator.DEFAULT_MAX_AMPLITUDE * _uiState.value.masterCap,
            )
        playJob?.cancel()
        playJob =
            viewModelScope.launch {
                _uiState.update { it.copy(isPlaying = true) }
                val tone =
                    toneGenerator.generate(
                        frequency = stimulus.frequency,
                        durationMs = TONE_DURATION_MS,
                        amplitude = amplitude,
                    )
                runCatching { tonePlayer.play(tone) }
                _uiState.update { it.copy(isPlaying = false) }
            }
    }

    private fun finish(s: PureToneScreening) {
        playJob?.cancel()
        tonePlayer.stop()
        val audiogram = s.audiogram()
        val gains =
            listOf(Ear.RIGHT, Ear.LEFT).mapNotNull { ear ->
                runCatching { fittingStrategy.fit(audiogram, ear) }
                    .getOrNull()
                    ?.let { GainSummary(ear, it.points) }
            }
        _uiState.update {
            it.copy(
                phase = TestPhase.DONE,
                isPlaying = false,
                completed = s.totalPoints,
                audiogram = audiogram,
                gains = gains,
            )
        }
        // Persist the result as a new, dated profile (and make it active) so
        // assist mode can use it and earlier results stay available as history.
        viewModelScope.launch {
            runCatching {
                profileRepository.save(newProfileFrom(audiogram, name = "Check ${LocalDate.now()}"))
            }
        }
    }

    override fun onCleared() {
        playJob?.cancel()
        tonePlayer.release()
    }

    companion object {
        private const val TONE_DURATION_MS = 1_000L
    }
}
