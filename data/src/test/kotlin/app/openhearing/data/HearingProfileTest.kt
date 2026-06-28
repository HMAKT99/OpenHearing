package app.openhearing.data

import app.openhearing.audiogram.Audiogram
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HearingProfileTest {
    @Test
    fun `a fresh profile starts from an empty audiogram`() {
        val profile =
            HearingProfile(
                id = "default",
                name = "My profile",
                audiogram = Audiogram.EMPTY,
                masterGainCapDb = 20.0,
            )
        assertEquals("My profile", profile.name)
        assertTrue(profile.audiogram.thresholds.isEmpty())
    }
}
