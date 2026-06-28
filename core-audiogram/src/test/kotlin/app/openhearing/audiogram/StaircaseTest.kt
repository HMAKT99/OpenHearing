package app.openhearing.audiogram

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.math.abs

class StaircaseTest {
    /**
     * Drive a staircase with a deterministic listener that hears iff the
     * presented level is at or above [trueThreshold]. Returns the outcome.
     */
    private fun runWithListener(trueThreshold: Double, config: StaircaseConfig = StaircaseConfig()): StaircaseOutcome {
        val staircase = HughsonWestlakeStaircase(config)
        var level = staircase.currentLevel().value
        repeat(config.maxPresentations + 5) {
            val heard = level >= trueThreshold
            when (val step = staircase.submit(heard)) {
                is StaircaseStep.Present -> level = step.level.value
                is StaircaseStep.Done -> return step.outcome
            }
        }
        error("staircase did not converge within the presentation cap")
    }

    @ParameterizedTest
    @ValueSource(doubles = [0.0, 5.0, 10.0, 25.0, 31.0, 33.0, 40.0, 55.0, 70.0])
    fun `converges within one step of the true threshold`(trueThreshold: Double) {
        val outcome = runWithListener(trueThreshold)
        assertTrue(outcome is StaircaseOutcome.Threshold, "expected a threshold for $trueThreshold")
        val found = (outcome as StaircaseOutcome.Threshold).level.value
        assertTrue(
            abs(found - trueThreshold) <= 5.0,
            "found $found dB HL for true threshold $trueThreshold dB HL",
        )
    }

    @Test
    fun `reports NoResponse when the threshold is beyond the test range`() {
        val outcome = runWithListener(trueThreshold = 200.0)
        assertEquals(StaircaseOutcome.NoResponse, outcome)
    }

    @Test
    fun `returns the floor level when audible at the bottom of the range`() {
        val config = StaircaseConfig(minLevelDbHl = -10.0)
        val outcome = runWithListener(trueThreshold = -50.0, config = config)
        assertTrue(outcome is StaircaseOutcome.Threshold)
        assertEquals(-10.0, (outcome as StaircaseOutcome.Threshold).level.value)
    }

    @Test
    fun `requires two ascending responses before declaring a threshold`() {
        // A listener that only ever responds once at a level shouldn't converge there.
        val config = StaircaseConfig(ascendingResponsesNeeded = 2)
        val staircase = HughsonWestlakeStaircase(config)
        // First presentation at start (40) heard -> goes down; not a convergence.
        val step = staircase.submit(heard = true)
        assertTrue(step is StaircaseStep.Present, "one response must not converge immediately")
    }
}
