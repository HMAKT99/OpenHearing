package app.openhearing.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.openhearing.common.Hertz
import app.openhearing.core.audio.ToneGenerator
import app.openhearing.core.audio.TonePlayer
import app.openhearing.data.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** [consentAccepted] is null while loading, then true/false. */
data class RootUiState(
    val consentAccepted: Boolean? = null,
    val highContrast: Boolean = false,
    val comfortCeiling: Float = 0.5f,
)

@HiltViewModel
class RootViewModel
@Inject
constructor(
    private val settings: SettingsRepository,
    private val toneGenerator: ToneGenerator,
    private val tonePlayer: TonePlayer,
) : ViewModel() {
    private var previewJob: Job? = null

    val uiState: StateFlow<RootUiState> =
        combine(
            settings.observeConsentAccepted(),
            settings.observeHighContrast(),
            settings.observeComfortCeiling(),
        ) { consent, highContrast, ceiling ->
            RootUiState(consentAccepted = consent, highContrast = highContrast, comfortCeiling = ceiling)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), RootUiState())

    fun acceptDisclaimer() {
        viewModelScope.launch { settings.setConsentAccepted(true) }
    }

    fun setHighContrast(enabled: Boolean) {
        viewModelScope.launch { settings.setHighContrast(enabled) }
    }

    fun setComfortCeiling(value: Float) {
        viewModelScope.launch { settings.setComfortCeiling(value) }
    }

    /**
     * Play a short 1 kHz tone at the chosen ceiling so the user can hear how
     * loud the maximum will be and pick a comfortable level. The TonePlayer's
     * own limiter still bounds the output.
     */
    fun previewComfort(ceiling: Float) {
        previewJob?.cancel()
        previewJob =
            viewModelScope.launch {
                val tone = toneGenerator.generate(Hertz(1000.0), durationMs = 800, amplitude = ceiling)
                runCatching { tonePlayer.play(tone) }
            }
    }

    override fun onCleared() {
        previewJob?.cancel()
        tonePlayer.release()
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
