package app.openhearing.ui.assist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.openhearing.assist.AssistController
import app.openhearing.assist.AssistSessionFactory
import app.openhearing.data.HearingProfile
import app.openhearing.data.ProfileRepository
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
    val profiles: List<HearingProfile> = emptyList(),
    val activeProfileId: String? = null,
) {
    companion object {
        const val DEFAULT_MASTER_GAIN_DB = 12.0
    }
}

/**
 * Drives assist mode: prepares the session via [AssistSessionFactory], reflects
 * run state, and manages the saved profiles. Master gain changes apply live to a
 * running session (the chain clamps them; the limiter stays downstream). Actually
 * starting/stopping the foreground service is done by the screen (it needs a
 * Context + permissions).
 */
@HiltViewModel
class AssistViewModel
@Inject
constructor(
    private val controller: AssistController,
    private val profileRepository: ProfileRepository,
    private val sessionFactory: AssistSessionFactory,
) : ViewModel() {
    private val masterGain = MutableStateFlow(AssistUiState.DEFAULT_MASTER_GAIN_DB)

    val uiState: StateFlow<AssistUiState> =
        combine(
            profileRepository.observeActiveProfile(),
            profileRepository.observeProfiles(),
            controller.running,
            masterGain,
        ) { active, profiles, running, gain ->
            AssistUiState(
                hasProfile = active?.audiogram?.thresholds?.isNotEmpty() == true,
                running = running,
                masterGainDb = active?.masterGainCapDb ?: gain,
                profiles = profiles,
                activeProfileId = active?.id,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), AssistUiState())

    /** Applies immediately (also to a running session) and persists on the active profile. */
    fun setMasterGain(db: Double) {
        masterGain.value = db
        controller.setMasterGainDb(db)
        viewModelScope.launch {
            val profile = profileRepository.observeActiveProfile().first()
            if (profile != null) {
                profileRepository.save(profile.copy(masterGainCapDb = db))
            }
        }
    }

    fun selectProfile(profileId: String) {
        viewModelScope.launch {
            profileRepository.setActive(profileId)
            // A running session keeps its old curve; restarting applies the new
            // profile. Stop here so the user never hears an unexpected switch.
            if (controller.running.value) return@launch
            sessionFactory.prepare()
        }
    }

    fun deleteProfile(profileId: String) {
        viewModelScope.launch { profileRepository.delete(profileId) }
    }

    /** Prepare the controller config from the active profile. Returns true if ready. */
    suspend fun prepare(): Boolean = sessionFactory.prepare()

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
