package app.openhearing.audiogram

import app.openhearing.common.DecibelsHl
import kotlin.math.roundToInt

/**
 * Configuration for a single-frequency adaptive threshold search.
 *
 * Defaults follow the widely-used *modified Hughson–Westlake* procedure (the
 * "down 10 dB after a response, up 5 dB after no response" rule, with threshold
 * taken as the lowest level producing responses on at least 2 of up to 3
 * ascending presentations — the criterion described in the ASHA guidelines for
 * manual pure-tone audiometry).
 *
 * This is a screening procedure, not a clinical diagnostic test. See the app's
 * disclaimers: not a medical device, not a substitute for a professional exam.
 */
data class StaircaseConfig(
    val startLevelDbHl: Double = 40.0,
    val stepDownDb: Double = 10.0,
    val stepUpDb: Double = 5.0,
    val minLevelDbHl: Double = -10.0,
    val maxLevelDbHl: Double = 90.0,
    val ascendingResponsesNeeded: Int = 2,
    /** Safety stop so a pathological response pattern can never loop forever. */
    val maxPresentations: Int = 40,
) {
    init {
        require(stepDownDb > 0 && stepUpDb > 0) { "steps must be positive" }
        require(maxLevelDbHl > minLevelDbHl) { "max level must exceed min level" }
        require(ascendingResponsesNeeded >= 1) { "need at least one ascending response" }
    }
}

/** What the caller should do next, returned after each response is submitted. */
sealed interface StaircaseStep {
    /** Present another tone at [level], then call [HughsonWestlakeStaircase.submit] again. */
    data class Present(val level: DecibelsHl) : StaircaseStep

    /** Search converged; [outcome] is the result for this frequency. */
    data class Done(val outcome: StaircaseOutcome) : StaircaseStep
}

/** The result of a single-frequency search. */
sealed interface StaircaseOutcome {
    /** Estimated hearing threshold for this frequency/ear. */
    data class Threshold(val level: DecibelsHl) : StaircaseOutcome

    /**
     * No reliable response within the test's level range — e.g. the threshold is
     * beyond [StaircaseConfig.maxLevelDbHl]. Reported rather than guessed.
     */
    data object NoResponse : StaircaseOutcome
}

/**
 * Adaptive threshold seeker for one ear at one frequency. Deterministic and
 * Android-free, so it is driven entirely by heard/not-heard responses and is
 * exhaustively unit-testable (see the simulated-listener tests).
 *
 * Usage:
 * ```
 * val s = HughsonWestlakeStaircase()
 * var level = s.currentLevel()
 * // present a tone at `level`, ask the user, then:
 * when (val step = s.submit(heard)) {
 *     is StaircaseStep.Present -> level = step.level   // loop
 *     is StaircaseStep.Done -> step.outcome            // finished
 * }
 * ```
 */
class HughsonWestlakeStaircase(private val config: StaircaseConfig = StaircaseConfig()) {
    private var level: Double = config.startLevelDbHl.coerceIn(config.minLevelDbHl, config.maxLevelDbHl)

    /** True when the current level was reached by stepping UP (an ascending trial). */
    private var arrivedAscending: Boolean = false
    private val ascendingHits = HashMap<Int, Int>()
    private var presentations = 0

    /** The level the caller should currently present a tone at. */
    fun currentLevel(): DecibelsHl = DecibelsHl(level)

    private fun key(db: Double): Int = db.roundToInt()

    /**
     * Record whether the tone at [currentLevel] was heard, and advance the search.
     */
    // Multiple early returns are the clearest expression of this state machine's
    // distinct terminal conditions (converged / out-of-range / floor / continue).
    @Suppress("ReturnCount")
    fun submit(heard: Boolean): StaircaseStep {
        presentations++

        if (heard && arrivedAscending) {
            val k = key(level)
            val hits = (ascendingHits[k] ?: 0) + 1
            ascendingHits[k] = hits
            if (hits >= config.ascendingResponsesNeeded) {
                return StaircaseStep.Done(StaircaseOutcome.Threshold(DecibelsHl(level)))
            }
        }

        if (presentations >= config.maxPresentations) {
            return StaircaseStep.Done(bestEffortOutcome())
        }

        if (heard) {
            // Response: go down to find the quietest audible level.
            val next = level - config.stepDownDb
            if (next < config.minLevelDbHl) {
                // Already audible at the floor of the range; that's the threshold.
                return StaircaseStep.Done(StaircaseOutcome.Threshold(DecibelsHl(level)))
            }
            level = next
            arrivedAscending = false
        } else {
            // No response: go up. The next presentation is an ascending trial.
            val next = level + config.stepUpDb
            if (next > config.maxLevelDbHl) {
                return StaircaseStep.Done(bestEffortOutcome())
            }
            level = next
            arrivedAscending = true
        }
        return StaircaseStep.Present(DecibelsHl(level))
    }

    /**
     * Fallback when the range is exhausted or the safety cap is hit: the lowest
     * level that was ever heard on an ascending trial, otherwise NoResponse.
     */
    private fun bestEffortOutcome(): StaircaseOutcome {
        val lowestHeard = ascendingHits.keys.minOrNull()
        return if (lowestHeard != null) {
            StaircaseOutcome.Threshold(DecibelsHl(lowestHeard.toDouble()))
        } else {
            StaircaseOutcome.NoResponse
        }
    }
}
