package me.aliahad.audioplayer

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

/*
 * Pure, Android-free rules used by the library scanner and the UI, kept here so they are unit-testable.
 */

private val SUPPORTED_AUDIO_EXTENSIONS = setOf("mp3", "wav", "m4a", "aac", "ogg", "oga", "opus", "flac")

// Playlist and cue formats report an audio/* MIME type but are not playable tracks.
private val PLAYLIST_EXTENSIONS = setOf("m3u", "m3u8", "pls", "cue", "xspf", "wpl")
private val PLAYLIST_MIME_TYPES = setOf(
    "audio/x-mpegurl",
    "audio/mpegurl",
    "application/vnd.apple.mpegurl",
    "application/x-mpegurl",
    "audio/x-scpls",
    "application/x-cue",
    "application/xspf+xml"
)

/** True when a document looks like a playable audio file (not a playlist, not a hidden/trashed file). */
fun isPlayableAudio(displayName: String?, mimeType: String?): Boolean {
    val name = displayName?.takeIf { it.isNotBlank() } ?: return false
    if (name.startsWith(".")) return false // hidden files and MediaStore ".trashed-" entries
    val extension = name.substringAfterLast('.', missingDelimiterValue = "").lowercase(Locale.ROOT)
    val mime = mimeType?.lowercase(Locale.ROOT)
    if (extension in PLAYLIST_EXTENSIONS || mime in PLAYLIST_MIME_TYPES) return false
    return extension in SUPPORTED_AUDIO_EXTENSIONS || (mime != null && mime.startsWith("audio/"))
}

/**
 * Case-insensitive "natural" ordering: digit runs compare by numeric value, so
 * "Track 2" sorts before "Track 10" the way file managers order them.
 */
object NaturalOrderComparator : Comparator<String> {
    override fun compare(a: String, b: String): Int {
        var i = 0
        var j = 0
        while (i < a.length && j < b.length) {
            val ca = a[i]
            val cb = b[j]
            if (ca.isDigit() && cb.isDigit()) {
                val endA = a.indexOfFirstFrom(i) { !it.isDigit() }
                val endB = b.indexOfFirstFrom(j) { !it.isDigit() }
                val numA = a.substring(i, endA).trimStart('0')
                val numB = b.substring(j, endB).trimStart('0')
                val byLength = numA.length.compareTo(numB.length)
                if (byLength != 0) return byLength
                val byDigits = numA.compareTo(numB)
                if (byDigits != 0) return byDigits
                i = endA
                j = endB
            } else {
                val byChar = ca.lowercaseChar().compareTo(cb.lowercaseChar())
                if (byChar != 0) return byChar
                i++
                j++
            }
        }
        val byRemaining = (a.length - i).compareTo(b.length - j)
        // Fall back to a plain comparison so distinct strings never compare equal ("01" vs "1", "a" vs "A").
        return if (byRemaining != 0) byRemaining else a.compareTo(b)
    }

    private inline fun String.indexOfFirstFrom(start: Int, predicate: (Char) -> Boolean): Int {
        for (k in start until length) if (predicate(this[k])) return k
        return length
    }
}

/** "0.75x", "1.0x", "1.25x", "2.0x" — never rounds a preset to a value the player is not using. */
fun formatPlaybackSpeed(speed: Float): String {
    val exact = BigDecimal(speed.toString()).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros()
    val text = if (exact.scale() < 1) exact.setScale(1).toPlainString() else exact.toPlainString()
    return "${text}x"
}
