package app.openhearing.ui.assist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.openhearing.assist.AssistConfig
import app.openhearing.assist.AssistController
import app.openhearing.audiogram.FittingStrategy
import app.openhearing.audiogram.GainCurve
import app.openhearing.audiogram.GainPoint
import app.openhearing.common.Ear
import app.openhearing.common.Hertz
import app.openhearing.data.HearingProfile
import app.openhearing.data.ProfileRepository
import app.openhearing.data.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AssistUiState(
    val hasProfile: Boolean = false,
    val running: Boolean = false,
    val masterGainDb: Double = DEFAULT_MASTER_GAIN_DB,
) {
    companion object {
        const val DEFAULT_MASTER_GAIN_DB = 12.0
    }
}

/**
 * Drives assist mode: turns the saved profile into a mono gain curve, configures
 * the [AssistController], and reflects run state. Actually starting/stopping the
 * foreground service is done by the screen (it needs a Context + permissions);
 * this VM prepares the config and exposes state.
 */
@HiltViewModel
class AssistViewModel
@Inject
constructor(
    private val controller: AssistController,
    private val profileRepository: ProfileRepository,
    private val settingsRepository: SettingsRepository,
    private val fittingStrategy: FittingStrategy,
) : ViewModel() {
    private val masterGain = MutableStateFlow(AssistUiState.DEFAULT_MASTER_GAIN_DB)

    val uiState: StateFlow<AssistUiState> =
        combine(
            profileRepository.observeActiveProfile(),
            controller.running,
            masterGain,
        ) { profile, running, gain ->
            AssistUiState(
                hasProfile = profile?.audiogram?.thresholds?.isNotEmpty() == true,
                running = running,
                masterGainDb = profile?.masterGainCapDb ?: gain,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), AssistUiState())

    fun setMasterGain(db: Double) {
        masterGain.value = db
        viewModelScope.launch {
            val profile = profileRepository.observeActiveProfile().first()
            if (profile != null) {
                profileRepository.save(profile.copy(masterGainCapDb = db))
            }
        }
    }

    /** Prepare the controller config from the active profile. Returns true if ready. */
    suspend fun prepare(): Boolean {
        val profile = profileRepository.observeActiveProfile().first() ?: return false
        val curve = monoGainCurve(profile) ?: return false
        val ceiling = settingsRepository.observeComfortCeiling().first()
        controller.configure(
            AssistConfig(
                gainCurve = curve,
                masterGainDb = profile.masterGainCapDb,
                ceilingLinear = ceiling,
            ),
        )
        return true
    }

    /** Average the per-ear half-gain fits into a single mono curve (v1 is mono). */
    private fun monoGainCurve(profile: HearingProfile): GainCurve? {
        val perEar =
            listOf(Ear.RIGHT, Ear.LEFT).mapNotNull { ear ->
                runCatching { fittingStrategy.fit(profile.audiogram, ear) }.getOrNull()
            }
        if (perEar.isEmpty()) return null
        val byFreq = sortedMapOf<Double, MutableList<Double>>()
        perEar.forEach { curve ->
            curve.points.forEach { p -> byFreq.getOrPut(p.frequency.value) { mutableListOf() }.add(p.gainDb) }
        }
        val merged = byFreq.map { (freq, gains) -> GainPoint(Hertz(freq), gains.average()) }
        return if (merged.isEmpty()) null else GainCurve(merged)
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
