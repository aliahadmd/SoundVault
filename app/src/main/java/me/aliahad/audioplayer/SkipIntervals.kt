package me.aliahad.audioplayer

/*
 * Pure, Android-free rules for the skip back / skip forward controls, kept here so they are unit-testable.
 */

/** Step sizes offered in Settings, in milliseconds. */
val SKIP_INTERVAL_OPTIONS_MS: List<Long> = listOf(5_000L, 10_000L, 20_000L, 60_000L)

const val DEFAULT_SKIP_INTERVAL_MS = 10_000L

/**
 * The user's skip step sizes. Back and forward are chosen separately, as in podcast and audiobook
 * players: a short rewind to re-hear a sentence and a longer jump ahead are both common.
 */
data class SkipIntervals(
    val backMs: Long = DEFAULT_SKIP_INTERVAL_MS,
    val forwardMs: Long = DEFAULT_SKIP_INTERVAL_MS
)

/** A stored value that is not one of the offered options (corrupt, or a removed option) falls back to the default. */
fun sanitizeSkipInterval(intervalMs: Long?): Long =
    intervalMs?.takeIf { it in SKIP_INTERVAL_OPTIONS_MS } ?: DEFAULT_SKIP_INTERVAL_MS

/**
 * Where a skip of [offsetMs] (negative = back) lands: never before the start of the track, and never
 * past its end when the duration is known ([durationMs] null or negative = unknown). Landing on the
 * end finishes the track, which then moves on exactly as if it had played out.
 */
fun skipTargetPosition(positionMs: Long, durationMs: Long?, offsetMs: Long): Long {
    val target = positionMs.coerceAtLeast(0L) + offsetMs
    val capped = if (durationMs != null && durationMs >= 0L) target.coerceAtMost(durationMs) else target
    return capped.coerceAtLeast(0L)
}

/** How an interval is labelled: whole minutes from one minute up ("1 min"), otherwise seconds ("20 s"). */
data class SkipIntervalLabel(val amount: Int, val inMinutes: Boolean)

fun skipIntervalLabel(intervalMs: Long): SkipIntervalLabel {
    val seconds = (intervalMs / 1_000L).toInt()
    return if (seconds >= 60 && seconds % 60 == 0) {
        SkipIntervalLabel(seconds / 60, inMinutes = true)
    } else {
        SkipIntervalLabel(seconds, inMinutes = false)
    }
}
