package app.openhearing.ui.manualentry

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.openhearing.audiogram.Audiogram
import app.openhearing.audiogram.PureToneScreening
import app.openhearing.audiogram.Threshold
import app.openhearing.common.DecibelsHl
import app.openhearing.common.Ear
import app.openhearing.common.Hertz
import app.openhearing.data.ProfileRepository
import app.openhearing.data.newProfileFrom
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/**
 * Manual audiogram entry: lets the user type in thresholds from a professional
 * hearing test instead of running the on-device check. Saves the result as a new
 * named profile, exactly like a completed hearing check.
 */
@HiltViewModel
class ManualEntryViewModel
@Inject
constructor(private val profileRepository: ProfileRepository) : ViewModel() {
    /** Pitches shown for entry, ascending — same set the on-device check measures. */
    val frequencies: List<Double> =
        PureToneScreening.DEFAULT_SCREENING_FREQUENCIES.map { it.value }.sorted()

    private val _levels =
        MutableStateFlow(
            mapOf(
                Ear.RIGHT to frequencies.associateWith { DEFAULT_LEVEL_DB_HL },
                Ear.LEFT to frequencies.associateWith { DEFAULT_LEVEL_DB_HL },
            ),
        )

    /** Current entry per ear and frequency, in dB HL. */
    val levels: StateFlow<Map<Ear, Map<Double, Int>>> = _levels.asStateFlow()

    fun setLevel(ear: Ear, frequencyHz: Double, dbHl: Int) {
        val clamped = dbHl.coerceIn(MIN_LEVEL_DB_HL, MAX_LEVEL_DB_HL)
        _levels.update { current ->
            current + (ear to current.getValue(ear) + (frequencyHz to clamped))
        }
    }

    fun save(onSaved: () -> Unit) {
        val thresholds =
            _levels.value.flatMap { (ear, byFreq) ->
                byFreq.map { (freq, db) -> Threshold(ear, Hertz(freq), DecibelsHl(db.toDouble())) }
            }
        viewModelScope.launch {
            profileRepository.save(
                newProfileFrom(Audiogram(thresholds), name = "Manual ${LocalDate.now()}"),
            )
            onSaved()
        }
    }

    companion object {
        const val MIN_LEVEL_DB_HL = -10
        const val MAX_LEVEL_DB_HL = 90
        const val LEVEL_STEP_DB = 5
        private const val DEFAULT_LEVEL_DB_HL = 0
    }
}
