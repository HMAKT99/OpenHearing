package app.openhearing.audiogram

import app.openhearing.common.Ear
import app.openhearing.common.Hertz

/** A tone to present: which [ear], which [frequency], at what [level]. */
data class Stimulus(val ear: Ear, val frequency: Hertz, val level: app.openhearing.common.DecibelsHl)

/**
 * Drives a full pure-tone screening: for each (ear, frequency) point it runs an
 * independent [HughsonWestlakeStaircase], in a fixed, sensible order, and
 * assembles the per-ear/per-frequency [Audiogram].
 *
 * Android-free state machine: the UI presents [currentStimulus], asks the user,
 * and calls [submitResponse]. Fully unit-testable with a simulated listener.
 */
class PureToneScreening(
    frequencies: List<Hertz> = DEFAULT_SCREENING_FREQUENCIES,
    ears: List<Ear> = listOf(Ear.RIGHT, Ear.LEFT),
    private val config: StaircaseConfig = StaircaseConfig(),
) {
    /** The (ear, frequency) points to measure, in presentation order. */
    private val points: List<Pair<Ear, Hertz>> =
        ears.flatMap { ear -> frequencies.map { freq -> ear to freq } }

    private var index = 0
    private var staircase = HughsonWestlakeStaircase(config)
    private val thresholds = mutableListOf<Threshold>()

    val totalPoints: Int = points.size

    fun completedPoints(): Int = index

    fun isComplete(): Boolean = index >= points.size

    /** The tone to present now, or null when the screening is complete. */
    fun currentStimulus(): Stimulus? {
        if (isComplete()) return null
        val (ear, freq) = points[index]
        return Stimulus(ear, freq, staircase.currentLevel())
    }

    /**
     * Record the response to the current stimulus and advance. When a frequency's
     * search converges, its threshold is added and the next point begins.
     */
    fun submitResponse(heard: Boolean) {
        check(!isComplete()) { "Screening already complete" }
        when (val step = staircase.submit(heard)) {
            is StaircaseStep.Present -> Unit // keep going at the new level
            is StaircaseStep.Done -> {
                val (ear, freq) = points[index]
                val level =
                    when (val outcome = step.outcome) {
                        is StaircaseOutcome.Threshold -> outcome.level
                        // No response in range: record at the test ceiling so the
                        // audiogram and fitting treat it as (at least) this much loss.
                        StaircaseOutcome.NoResponse -> app.openhearing.common.DecibelsHl(config.maxLevelDbHl)
                    }
                thresholds += Threshold(ear, freq, level)
                index++
                if (!isComplete()) staircase = HughsonWestlakeStaircase(config)
            }
        }
    }

    /** The audiogram assembled so far (complete once [isComplete] is true). */
    fun audiogram(): Audiogram = Audiogram(thresholds.toList())

    companion object {
        /**
         * A 6-frequency screening subset (octave frequencies). Ordered 1 kHz first
         * — the most reliable starting reference — then up, then down, matching
         * common clinical practice.
         */
        val DEFAULT_SCREENING_FREQUENCIES: List<Hertz> =
            listOf(1000.0, 2000.0, 4000.0, 8000.0, 500.0, 250.0).map(::Hertz)
    }
}
