package app.openhearing.audiogram

import app.openhearing.common.DecibelsHl
import app.openhearing.common.Ear
import app.openhearing.common.Hertz
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AudiogramTest {
    private val sample =
        Audiogram(
            listOf(
                Threshold(Ear.LEFT, Hertz(1000.0), DecibelsHl(20.0)),
                Threshold(Ear.LEFT, Hertz(2000.0), DecibelsHl(35.0)),
                Threshold(Ear.RIGHT, Hertz(1000.0), DecibelsHl(15.0)),
            ),
        )

    @Test
    fun `looks up a measured threshold`() {
        assertEquals(35.0, sample.thresholdAt(Ear.LEFT, Hertz(2000.0))?.value)
    }

    @Test
    fun `returns null for an unmeasured point`() {
        assertNull(sample.thresholdAt(Ear.RIGHT, Hertz(8000.0)))
    }

    @Test
    fun `lists measured frequencies per ear in ascending order`() {
        assertEquals(listOf(1000.0, 2000.0), sample.frequenciesFor(Ear.LEFT).map { it.value })
        assertEquals(listOf(1000.0), sample.frequenciesFor(Ear.RIGHT).map { it.value })
    }

    @Test
    fun `empty audiogram has no thresholds`() {
        assertTrue(Audiogram.EMPTY.thresholds.isEmpty())
    }
}
