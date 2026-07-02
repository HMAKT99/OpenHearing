package app.openhearing.assist

import app.openhearing.audiogram.FittingStrategy
import app.openhearing.audiogram.GainCurve
import app.openhearing.audiogram.GainPoint
import app.openhearing.common.Ear
import app.openhearing.common.Hertz
import app.openhearing.data.HearingProfile
import app.openhearing.data.ProfileRepository
import app.openhearing.data.SettingsRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Turns the active profile into an [AssistConfig] on the shared [AssistController].
 * Used by both the assist screen and the quick-settings tile, so session setup
 * stays identical no matter where assist is started from.
 */
@Singleton
class AssistSessionFactory
@Inject
constructor(
    private val controller: AssistController,
    private val profileRepository: ProfileRepository,
    private val settingsRepository: SettingsRepository,
    private val fittingStrategy: FittingStrategy,
) {
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
}
