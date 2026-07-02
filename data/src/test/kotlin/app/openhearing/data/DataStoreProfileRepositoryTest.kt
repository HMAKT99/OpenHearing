package app.openhearing.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import app.openhearing.audiogram.Audiogram
import app.openhearing.audiogram.Threshold
import app.openhearing.common.DecibelsHl
import app.openhearing.common.Ear
import app.openhearing.common.Hertz
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private class FakeDataStore : DataStore<Preferences> {
    private val state = MutableStateFlow(emptyPreferences())
    override val data: Flow<Preferences> = state

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        state.value = transform(state.value)
        return state.value
    }
}

class DataStoreProfileRepositoryTest {
    private val audiogram =
        Audiogram(
            listOf(
                Threshold(Ear.RIGHT, Hertz(1000.0), DecibelsHl(20.0)),
                Threshold(Ear.LEFT, Hertz(2000.0), DecibelsHl(35.0)),
            ),
        )

    private fun profile(id: String, name: String) =
        HearingProfile(id = id, name = name, audiogram = audiogram, masterGainCapDb = 12.0)

    @Test
    fun `codec round-trips a profile list`() {
        val profiles = listOf(profile("a", "First"), profile("b", "Second"))
        val decoded = ProfileListCodec.decode(ProfileListCodec.encode(profiles))
        assertEquals(profiles, decoded)
    }

    @Test
    fun `codec sanitizes separator characters in names`() {
        val hostile = profile("a", "Name\u001Fwith\u001Eseparators")
        val decoded = ProfileListCodec.decode(ProfileListCodec.encode(listOf(hostile)))
        assertEquals(1, decoded.size)
        assertEquals("Name with separators", decoded.single().name)
    }

    @Test
    fun `save makes the profile active and newest first`() = runTest {
        val repo = DataStoreProfileRepository(FakeDataStore())
        repo.save(profile("a", "First"))
        repo.save(profile("b", "Second"))
        assertEquals(listOf("b", "a"), repo.observeProfiles().first().map { it.id })
        assertEquals("b", repo.observeActiveProfile().first()?.id)
    }

    @Test
    fun `setActive switches and delete falls back to remaining profile`() = runTest {
        val repo = DataStoreProfileRepository(FakeDataStore())
        repo.save(profile("a", "First"))
        repo.save(profile("b", "Second"))
        repo.setActive("a")
        assertEquals("a", repo.observeActiveProfile().first()?.id)
        repo.delete("a")
        assertEquals("b", repo.observeActiveProfile().first()?.id)
        repo.delete("b")
        assertNull(repo.observeActiveProfile().first())
        assertTrue(repo.observeProfiles().first().isEmpty())
    }

    @Test
    fun `legacy single-profile keys are readable as a one-item list`() = runTest {
        val store = FakeDataStore()
        store.updateData {
            mutablePreferencesOf().apply {
                this[stringPreferencesKey("profile_name")] = "Old profile"
                this[stringPreferencesKey("profile_audiogram")] =
                    app.openhearing.audiogram.AudiogramCodec.encode(audiogram)
            }
        }
        val repo = DataStoreProfileRepository(store)
        val active = repo.observeActiveProfile().first()
        assertEquals("Old profile", active?.name)
        assertEquals(audiogram, active?.audiogram)
    }
}
