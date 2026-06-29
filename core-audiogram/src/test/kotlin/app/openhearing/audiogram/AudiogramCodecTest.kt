package app.openhearing.audiogram

import app.openhearing.common.DecibelsHl
import app.openhearing.common.Ear
import app.openhearing.common.Hertz
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AudiogramCodecTest {
    @Test
    fun `round-trips an audiogram`() {
        val original =
            Audiogram(
                listOf(
                    Threshold(Ear.RIGHT, Hertz(1000.0), DecibelsHl(25.0)),
                    Threshold(Ear.LEFT, Hertz(4000.0), DecibelsHl(50.0)),
                ),
            )
        val decoded = AudiogramCodec.decode(AudiogramCodec.encode(original))
        assertEquals(original.thresholds, decoded.thresholds)
    }

    @Test
    fun `empty text decodes to an empty audiogram`() {
        assertTrue(AudiogramCodec.decode("").thresholds.isEmpty())
    }

    @Test
    fun `malformed lines are skipped`() {
        val decoded = AudiogramCodec.decode("LEFT,1000.0,30.0\ngarbage\n,,\nRIGHT,2000.0,40.0")
        assertEquals(2, decoded.thresholds.size)
    }
}
