package app.openhearing.audiogram

import app.openhearing.common.DecibelsHl
import app.openhearing.common.Ear
import app.openhearing.common.Hertz

/**
 * Compact, dependency-free text serialization for an [Audiogram], used by the
 * persistence layer. One threshold per line: `EAR,FREQUENCY_HZ,LEVEL_DBHL`.
 *
 * Pure Kotlin so the round-trip is unit-tested on the JVM (the persistence I/O
 * around it is the only untested part).
 */
object AudiogramCodec {
    fun encode(audiogram: Audiogram): String = audiogram.thresholds.joinToString("\n") { t ->
        "${t.ear.name},${t.frequency.value},${t.level.value}"
    }

    fun decode(text: String): Audiogram {
        if (text.isBlank()) return Audiogram.EMPTY
        val thresholds =
            text.lineSequence()
                .mapNotNull { line ->
                    val parts = line.split(",")
                    if (parts.size != 3) return@mapNotNull null
                    val ear = runCatching { Ear.valueOf(parts[0].trim()) }.getOrNull() ?: return@mapNotNull null
                    val freq = parts[1].trim().toDoubleOrNull() ?: return@mapNotNull null
                    val level = parts[2].trim().toDoubleOrNull() ?: return@mapNotNull null
                    Threshold(ear, Hertz(freq), DecibelsHl(level))
                }
                .toList()
        return Audiogram(thresholds)
    }
}
