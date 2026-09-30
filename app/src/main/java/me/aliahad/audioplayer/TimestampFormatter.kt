package me.aliahad.audioplayer

import java.util.Locale

fun formatTimestamp(positionMs: Long): String {
    val totalSeconds = positionMs.coerceAtLeast(0L) / 1000
    val hours = (totalSeconds / 3600).toInt()
    val minutes = ((totalSeconds % 3600) / 60).toInt()
    val seconds = (totalSeconds % 60).toInt()
    return if (hours > 0) {
        String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.ROOT, "%d:%02d", minutes, seconds)
    }
}

fun parseTimestamp(formatted: String): Long {
    val parts = formatted.split(":").map { it.toInt() }
    return when (parts.size) {
        3 -> (parts[0] * 3600L + parts[1] * 60L + parts[2]) * 1000L
        2 -> (parts[0] * 60L + parts[1]) * 1000L
        else -> throw IllegalArgumentException("Invalid timestamp format: $formatted")
    }
}
