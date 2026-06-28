package app.openhearing.data

import app.openhearing.audiogram.Audiogram
import kotlinx.coroutines.flow.Flow

/**
 * A saved hearing-assist profile: a named audiogram plus the user's safety/volume
 * preferences. Phase 0 ships the model + repository interface; Phase 4 backs it
 * with DataStore/Room persistence.
 */
data class HearingProfile(
    val id: String,
    val name: String,
    val audiogram: Audiogram,
    /** User master gain cap in dB; always bounded by SafetyConstants in :core-common. */
    val masterGainCapDb: Double,
)

/** Persistence boundary for hearing profiles. Implementation lands in Phase 4. */
interface ProfileRepository {
    /** All saved profiles, newest first. */
    fun observeProfiles(): Flow<List<HearingProfile>>

    /** The currently active profile, or null if none is selected. */
    fun observeActiveProfile(): Flow<HearingProfile?>

    suspend fun save(profile: HearingProfile)

    suspend fun setActive(profileId: String)

    suspend fun delete(profileId: String)
}
