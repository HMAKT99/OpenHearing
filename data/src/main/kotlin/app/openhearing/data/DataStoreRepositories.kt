package app.openhearing.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
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
import java.util.UUID

/**
 * DataStore (Preferences) keys. Profiles are stored as an encoded list (see
 * [ProfileListCodec]); the legacy single-profile keys are migrated on read and
 * cleared on the next write.
 */
private object Keys {
    val CONSENT = booleanPreferencesKey("consent_accepted")
    val HIGH_CONTRAST = booleanPreferencesKey("high_contrast")
    val COMFORT_CEILING = floatPreferencesKey("comfort_ceiling")
    val PROFILES = stringPreferencesKey("profiles")
    val ACTIVE_PROFILE_ID = stringPreferencesKey("active_profile_id")

    // Legacy single-profile keys (pre-multi-profile builds).
    val PROFILE_NAME = stringPreferencesKey("profile_name")
    val PROFILE_AUDIOGRAM = stringPreferencesKey("profile_audiogram")
    val PROFILE_MASTER_CAP = doublePreferencesKey("profile_master_cap_db")
}

private const val LEGACY_PROFILE_ID = "active"

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
 * Encodes a profile list into a single preference string. Uses ASCII unit/record
 * separators, which cannot appear in the audiogram codec output; profile names
 * are sanitized on encode. Internal for tests.
 */
internal object ProfileListCodec {
    private const val FIELD = '\u001F'
    private const val RECORD = '\u001E'

    fun encode(profiles: List<HearingProfile>): String = profiles.joinToString(RECORD.toString()) { p ->
        listOf(
            sanitize(p.id),
            sanitize(p.name),
            p.masterGainCapDb.toString(),
            AudiogramCodec.encode(p.audiogram),
        ).joinToString(FIELD.toString())
    }

    fun decode(text: String): List<HearingProfile> = text.split(RECORD).mapNotNull { record ->
        val fields = record.split(FIELD)
        if (fields.size < FIELD_COUNT) return@mapNotNull null
        runCatching {
            HearingProfile(
                id = fields[0],
                name = fields[1],
                audiogram = AudiogramCodec.decode(fields[3]),
                masterGainCapDb = fields[2].toDouble(),
            )
        }.getOrNull()
    }

    private fun sanitize(value: String): String = value.replace(FIELD, ' ').replace(RECORD, ' ')

    private const val FIELD_COUNT = 4
}

/**
 * [ProfileRepository] backed by Preferences DataStore. Stores a list of named
 * profiles plus the active profile id; each hearing check or manual entry saves
 * a new profile, so the list doubles as result history (newest first).
 */
class DataStoreProfileRepository(private val dataStore: DataStore<Preferences>) : ProfileRepository {
    private fun Preferences.profileList(): List<HearingProfile> {
        val encoded = this[Keys.PROFILES]
        if (encoded != null) return ProfileListCodec.decode(encoded)
        // Legacy single-profile storage from pre-multi-profile builds.
        val audiogramText = this[Keys.PROFILE_AUDIOGRAM] ?: return emptyList()
        return listOf(
            HearingProfile(
                id = LEGACY_PROFILE_ID,
                name = this[Keys.PROFILE_NAME] ?: "My profile",
                audiogram = AudiogramCodec.decode(audiogramText),
                masterGainCapDb = this[Keys.PROFILE_MASTER_CAP] ?: DEFAULT_MASTER_CAP_DB,
            ),
        )
    }

    private fun Preferences.activeProfile(): HearingProfile? {
        val profiles = profileList()
        val activeId = this[Keys.ACTIVE_PROFILE_ID]
        return profiles.firstOrNull { it.id == activeId } ?: profiles.firstOrNull()
    }

    override fun observeProfiles(): Flow<List<HearingProfile>> = dataStore.data.map { it.profileList() }

    override fun observeActiveProfile(): Flow<HearingProfile?> = dataStore.data.map { it.activeProfile() }

    /** Upserts [profile] at the front (newest first) and makes it active. */
    override suspend fun save(profile: HearingProfile) {
        dataStore.edit { prefs ->
            val others = prefs.profileList().filterNot { it.id == profile.id }
            prefs.writeProfiles(listOf(profile) + others)
            prefs[Keys.ACTIVE_PROFILE_ID] = profile.id
        }
    }

    override suspend fun setActive(profileId: String) {
        dataStore.edit { prefs ->
            if (prefs.profileList().any { it.id == profileId }) {
                prefs[Keys.ACTIVE_PROFILE_ID] = profileId
            }
        }
    }

    override suspend fun delete(profileId: String) {
        dataStore.edit { prefs ->
            val remaining = prefs.profileList().filterNot { it.id == profileId }
            prefs.writeProfiles(remaining)
            if (prefs[Keys.ACTIVE_PROFILE_ID] == profileId) {
                val next = remaining.firstOrNull()?.id
                if (next != null) prefs[Keys.ACTIVE_PROFILE_ID] = next else prefs.remove(Keys.ACTIVE_PROFILE_ID)
            }
        }
    }

    private fun MutablePreferences.writeProfiles(profiles: List<HearingProfile>) {
        this[Keys.PROFILES] = ProfileListCodec.encode(profiles)
        // Legacy keys are superseded once the list exists.
        remove(Keys.PROFILE_NAME)
        remove(Keys.PROFILE_AUDIOGRAM)
        remove(Keys.PROFILE_MASTER_CAP)
    }

    private companion object {
        const val DEFAULT_MASTER_CAP_DB = 20.0
    }
}

/** Builds a new uniquely-identified profile from a hearing-check or manual-entry result. */
fun newProfileFrom(audiogram: Audiogram, name: String, masterGainCapDb: Double = 20.0) = HearingProfile(
    id = UUID.randomUUID().toString(),
    name = name,
    audiogram = audiogram,
    masterGainCapDb = masterGainCapDb,
)
