package app.openhearing.audiogram

import app.openhearing.common.DecibelsHl
import app.openhearing.common.Ear
import app.openhearing.common.Hertz
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GainCurveTest {
    private fun audiogram(vararg pairs: Pair<Double, Double>): Audiogram =
        Audiogram(pairs.map { (f, hl) -> Threshold(Ear.LEFT, Hertz(f), DecibelsHl(hl)) })

    @Test
    fun `half-gain prescribes half the loss`() {
        val a = audiogram(1000.0 to 40.0, 2000.0 to 60.0)
        val curve = halfGainFitting().fit(a, Ear.LEFT)
        assertEquals(20.0, curve.gainAt(Hertz(1000.0)), 1e-9)
        assertEquals(30.0, curve.gainAt(Hertz(2000.0)), 1e-9)
    }

    @Test
    fun `normal hearing gets no gain`() {
        val a = audiogram(1000.0 to 0.0, 2000.0 to -5.0)
        val curve = halfGainFitting().fit(a, Ear.LEFT)
        assertEquals(0.0, curve.gainAt(Hertz(1000.0)), 1e-9)
        assertEquals(0.0, curve.gainAt(Hertz(2000.0)), 1e-9)
    }

    @Test
    fun `gain is capped at the safe per-band maximum`() {
        val a = audiogram(4000.0 to 120.0) // severe loss
        val curve = halfGainFitting(maxBandGainDb = 35.0).fit(a, Ear.LEFT)
        assertEquals(35.0, curve.gainAt(Hertz(4000.0)), 1e-9)
    }

    @Test
    fun `interpolates between measured frequencies and clamps outside the range`() {
        val a = audiogram(1000.0 to 40.0, 4000.0 to 80.0) // gains 20 and 40
        val curve = halfGainFitting().fit(a, Ear.LEFT)
        // 2000 Hz is between 1000 and 4000 on a log axis -> gain between 20 and 40.
        val mid = curve.gainAt(Hertz(2000.0))
        assertTrue(mid in 20.0..40.0, "interpolated gain was $mid")
        // Outside the measured range clamps to the nearest endpoint.
        assertEquals(20.0, curve.gainAt(Hertz(250.0)), 1e-9)
        assertEquals(40.0, curve.gainAt(Hertz(8000.0)), 1e-9)
    }

    @Test
    fun `fraction generalizes the rule`() {
        val a = audiogram(1000.0 to 60.0)
        val third = FractionalGainRule(fraction = 1.0 / 3.0).fit(a, Ear.LEFT)
        assertEquals(20.0, third.gainAt(Hertz(1000.0)), 1e-9)
    }
}
