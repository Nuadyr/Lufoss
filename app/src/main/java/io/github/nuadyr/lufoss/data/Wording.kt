package io.github.nuadyr.lufoss.data

import io.github.nuadyr.lufoss.Cfg
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

private const val METRES_PER_KM = 1000.0
private const val ROUND_TO_M = 10
private const val SECONDS_PER_MINUTE = 60.0
private const val MINUTES_PER_HOUR = 60

private val modWords = mapOf(
    "left" to "Turn left",
    "slight left" to "Bear left",
    "sharp left" to "Turn sharp left",
    "right" to "Turn right",
    "slight right" to "Bear right",
    "sharp right" to "Turn sharp right",
    "straight" to "Continue straight",
    "uturn" to "Make a U-turn"
)

private val modArrows = mapOf(
    "left" to "↰",
    "slight left" to "↖",
    "sharp left" to "↲",
    "right" to "↱",
    "slight right" to "↗",
    "sharp right" to "↳",
    "straight" to "↑",
    "uturn" to "↶"
)

/** "450 m" or "1.2 km"; the spoken form says "metres" and "kilometres". Under 1 km rounds to 10 m. */
fun fmtDist(metres: Double, spoken: Boolean = false): String {
    if (metres < METRES_PER_KM) {
        val rounded = max(ROUND_TO_M, (metres / ROUND_TO_M).roundToInt() * ROUND_TO_M)
        return rounded.toString() + (if (spoken) " metres" else " m")
    }
    return String.format(Locale.UK, "%.1f", metres / METRES_PER_KM) + (if (spoken) " kilometres" else " km")
}

/** "7 min" or "1 h 5 min". */
fun fmtTime(seconds: Double): String {
    val m = max(1, (seconds / SECONDS_PER_MINUTE).roundToInt())
    return if (m < MINUTES_PER_HOUR) "$m min" else "${m / MINUTES_PER_HOUR} h ${m % MINUTES_PER_HOUR} min"
}

/** The clock time [seconds] from now, in the device's own time format. */
fun fmtArrival(seconds: Double): String =
    DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(System.currentTimeMillis() + (seconds * 1000).toLong()))

/** The arrow shown beside a step. */
fun arrowFor(step: Step): String = when {
    step.type == "arrive" -> "⚑"
    step.type == "depart" -> "↑"
    else -> modArrows[step.mod] ?: "↑"
}

/** The words for a step, such as "Turn left onto High Street". */
fun instruction(step: Step, loop: Boolean): String {
    val on = if (step.name.isNotEmpty()) " onto " + step.name else ""
    return when (step.type) {
        "arrive" -> if (loop) "Arrive back at your start" else "Arrive at your destination"
        "depart" -> "Walk" + if (step.name.isNotEmpty()) " along " + step.name else ""
        "roundabout", "rotary" -> "Take the roundabout$on"
        else -> (modWords[step.mod] ?: "Continue") + on
    }
}

/** A direction change at a stop, as the router's own turn words. Positive is clockwise (a right turn). */
fun turnMod(change: Double): String {
    val a = abs(change)
    val side = if (change > 0) "right" else "left"
    return when {
        a < Cfg.TURN_STRAIGHT_DEG -> "straight"
        a < Cfg.TURN_SLIGHT_DEG -> "slight $side"
        a < Cfg.TURN_SHARP_DEG -> side
        a < Cfg.TURN_UTURN_DEG -> "sharp $side"
        else -> "uturn"
    }
}
