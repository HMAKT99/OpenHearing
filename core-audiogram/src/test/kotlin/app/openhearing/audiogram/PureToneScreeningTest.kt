package app.openhearing.audiogram

import app.openhearing.common.Ear
import app.openhearing.common.Hertz
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs

class PureToneScreeningTest {
    /** Run a full screening against per-(ear,frequency) true thresholds. */
    private fun runScreening(screening: PureToneScreening, trueThreshold: (Ear, Hertz) -> Double) {
        var guard = 0
        while (!screening.isComplete()) {
            val stimulus = screening.currentStimulus()
            assertNotNull(stimulus)
            val s = stimulus!!
            screening.submitResponse(s.level.value >= trueThreshold(s.ear, s.frequency))
            check(guard++ < 10_000) { "screening did not terminate" }
        }
    }

    @Test
    fun `measures both ears across all frequencies`() {
        val screening = PureToneScreening()
        assertEquals(PureToneScreening.DEFAULT_SCREENING_FREQUENCIES.size * 2, screening.totalPoints)

        // Flat 30 dB HL loss in both ears.
        runScreening(screening) { _, _ -> 30.0 }

        val audiogram = screening.audiogram()
        assertEquals(screening.totalPoints, audiogram.thresholds.size)
        for (ear in listOf(Ear.LEFT, Ear.RIGHT)) {
            for (freq in PureToneScreening.DEFAULT_SCREENING_FREQUENCIES) {
                val measured = audiogram.thresholdAt(ear, freq)
                assertNotNull(measured, "missing $ear @ ${freq.value} Hz")
                assertTrue(abs(measured!!.value - 30.0) <= 5.0)
            }
        }
    }

    @Test
    fun `recovers a sloping high-frequency loss`() {
        val screening = PureToneScreening(ears = listOf(Ear.RIGHT))
        // Worse hearing as frequency rises — a classic noise/age pattern.
        val truth: (Ear, Hertz) -> Double = { _, f -> if (f.value >= 4000.0) 55.0 else 15.0 }

        runScreening(screening, truth)
        val a = screening.audiogram()

        assertTrue(a.thresholdAt(Ear.RIGHT, Hertz(500.0))!!.value <= 20.0)
        assertTrue(a.thresholdAt(Ear.RIGHT, Hertz(8000.0))!!.value >= 50.0)
    }

    @Test
    fun `progress advances and completes`() {
        val screening = PureToneScreening(frequencies = listOf(Hertz(1000.0)), ears = listOf(Ear.RIGHT))
        assertEquals(0, screening.completedPoints())
        assertFalse(screening.isComplete())
        runScreening(screening) { _, _ -> 20.0 }
        assertTrue(screening.isComplete())
        assertEquals(1, screening.completedPoints())
        assertEquals(null, screening.currentStimulus())
    }
}
