package app.openhearing.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import app.openhearing.audiogram.Audiogram
import app.openhearing.audiogram.AudiogramCodec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * DataStore (Preferences) keys. v1 persists a single active hearing profile plus
 * settings; multi-profile support can extend this later.
 */
private object Keys {
    val CONSENT = booleanPreferencesKey("consent_accepted")
    val HIGH_CONTRAST = booleanPreferencesKey("high_contrast")
    val COMFORT_CEILING = floatPreferencesKey("comfort_ceiling")
    val PROFILE_NAME = stringPreferencesKey("profile_name")
    val PROFILE_AUDIOGRAM = stringPreferencesKey("profile_audiogram")
    val PROFILE_MASTER_CAP = doublePreferencesKey("profile_master_cap_db")
}

private const val ACTIVE_PROFILE_ID = "active"

/** [SettingsRepository] backed by Preferences DataStore. */
class DataStoreSettingsRepository(private val dataStore: DataStore<Preferences>) : SettingsRepository {
    override fun observeConsentAccepted(): Flow<Boolean> = dataStore.data.map { it[Keys.CONSENT] ?: false }

    override suspend fun setConsentAccepted(accepted: Boolean) {
        dataStore.edit { it[Keys.CONSENT] = accepted }
    }

    override fun observeHighContrast(): Flow<Boolean> = dataStore.data.map { it[Keys.HIGH_CONTRAST] ?: false }

    override suspend fun setHighContrast(enabled: Boolean) {
        dataStore.edit { it[Keys.HIGH_CONTRAST] = enabled }
    }

    override fun observeComfortCeiling(): Flow<Float> =
        dataStore.data.map { (it[Keys.COMFORT_CEILING] ?: DEFAULT_COMFORT_CEILING).coerceIn(MIN_CEILING, MAX_CEILING) }

    override suspend fun setComfortCeiling(value: Float) {
        dataStore.edit { it[Keys.COMFORT_CEILING] = value.coerceIn(MIN_CEILING, MAX_CEILING) }
    }

    private companion object {
        // Conservative default until the user calibrates; bounded for safety.
        const val DEFAULT_COMFORT_CEILING = 0.5f
        const val MIN_CEILING = 0.1f
        const val MAX_CEILING = 0.9f
    }
}

/**
 * [ProfileRepository] backed by Preferences DataStore. v1 stores a single active
 * profile (the screening result + master gain cap).
 */
class DataStoreProfileRepository(private val dataStore: DataStore<Preferences>) : ProfileRepository {
    private fun Preferences.toProfile(): HearingProfile? {
        val audiogramText = this[Keys.PROFILE_AUDIOGRAM] ?: return null
        return HearingProfile(
            id = ACTIVE_PROFILE_ID,
            name = this[Keys.PROFILE_NAME] ?: "My profile",
            audiogram = AudiogramCodec.decode(audiogramText),
            masterGainCapDb = this[Keys.PROFILE_MASTER_CAP] ?: DEFAULT_MASTER_CAP_DB,
        )
    }

    override fun observeProfiles(): Flow<List<HearingProfile>> =
        dataStore.data.map { prefs -> prefs.toProfile()?.let { listOf(it) } ?: emptyList() }

    override fun observeActiveProfile(): Flow<HearingProfile?> = dataStore.data.map { it.toProfile() }

    override suspend fun save(profile: HearingProfile) {
        dataStore.edit { prefs ->
            prefs[Keys.PROFILE_NAME] = profile.name
            prefs[Keys.PROFILE_AUDIOGRAM] = AudiogramCodec.encode(profile.audiogram)
            prefs[Keys.PROFILE_MASTER_CAP] = profile.masterGainCapDb
        }
    }

    // v1 has a single active profile, so activation is implicit.
    override suspend fun setActive(profileId: String) = Unit

    override suspend fun delete(profileId: String) {
        dataStore.edit { prefs ->
            prefs.remove(Keys.PROFILE_NAME)
            prefs.remove(Keys.PROFILE_AUDIOGRAM)
            prefs.remove(Keys.PROFILE_MASTER_CAP)
        }
    }

    private companion object {
        const val DEFAULT_MASTER_CAP_DB = 20.0
    }
}

/** Helper to build an [Audiogram]-bearing profile from a screening result. */
fun activeProfileFrom(audiogram: Audiogram, name: String = "My profile", masterGainCapDb: Double = 20.0) =
    HearingProfile(id = ACTIVE_PROFILE_ID, name = name, audiogram = audiogram, masterGainCapDb = masterGainCapDb)
