package me.aliahad.audioplayer

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * Pure, Android-free rules for the equalizer and bass booster, kept here so they are unit-testable.
 * The audio itself is processed in-app by EqualizerAudioProcessor, so the effect works the same on
 * every device instead of depending on the OEM's android.media.audiofx implementation.
 */

/** Centre frequencies of the ten graphic-EQ bands (ISO octave bands), in Hz. */
val EQ_BAND_FREQUENCIES_HZ: List<Double> =
    listOf(31.0, 62.0, 125.0, 250.0, 500.0, 1_000.0, 2_000.0, 4_000.0, 8_000.0, 16_000.0)

val EQ_BAND_COUNT: Int = EQ_BAND_FREQUENCIES_HZ.size

/** Each band can be cut or boosted by up to this much. */
const val EQ_BAND_LIMIT_DB = 12.0

/** One-octave bandwidth, so neighbouring bands overlap smoothly. */
const val EQ_BAND_Q = 1.41

/** The bass booster is a low shelf: everything below this corner is lifted. */
const val BASS_BOOST_CORNER_HZ = 100.0

/** Shelf gain at 100 % bass boost. */
const val BASS_BOOST_MAX_DB = 15.0

/** Output gain at 100 % loudness, kept from clipping by the limiter. */
const val LOUDNESS_MAX_DB = 8.0

/**
 * Fraction of the strongest boost that is taken off the input up front. Full compensation would make a
 * bass boost sound like a treble cut; none would push everything into the limiter. Half keeps the bass
 * punchy while the limiter only has to catch the peaks.
 */
const val AUTO_PREAMP_FRACTION = 0.5

const val CUSTOM_PRESET_ID = "custom"

private val FLAT_GAINS: List<Double> = List(EQ_BAND_COUNT) { 0.0 }

/** Built-in presets. Ids are stored in preferences, so never rename one. Gains are per band, 31 Hz → 16 kHz. */
enum class EqPreset(val id: String, val bandGainsDb: List<Double>) {
    FLAT("flat", FLAT_GAINS),
    BASS_BOOST("bass_boost", listOf(5.0, 6.0, 6.0, 4.0, 1.0, 0.0, 0.0, 0.0, 0.0, 0.0)),
    SUBWOOFER("subwoofer", listOf(10.0, 9.0, 6.0, 2.0, -1.0, -2.0, -1.0, 0.0, 0.0, 0.0)),
    DEEP_BASS("deep_bass", listOf(10.0, 8.0, 3.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0)),
    BASS_TREBLE("bass_treble", listOf(7.0, 6.0, 4.0, 1.0, -1.0, -1.0, 0.0, 3.0, 5.0, 6.0)),
    HIP_HOP("hip_hop", listOf(6.0, 5.0, 3.0, 1.0, -1.0, -1.0, 1.0, 1.0, 2.0, 3.0)),
    DANCE("dance", listOf(7.0, 6.0, 3.0, 0.0, -1.0, 0.0, 1.0, 3.0, 4.0, 4.0)),
    ROCK("rock", listOf(5.0, 4.0, 2.0, -1.0, -2.0, -1.0, 2.0, 3.0, 4.0, 4.0)),
    POP("pop", listOf(-1.0, 1.0, 3.0, 4.0, 3.0, 1.0, -1.0, -1.0, 1.0, 2.0)),
    JAZZ("jazz", listOf(3.0, 2.0, 1.0, 2.0, -1.0, -1.0, 0.0, 1.0, 2.0, 3.0)),
    CLASSICAL("classical", listOf(4.0, 3.0, 2.0, 1.0, -1.0, -1.0, 0.0, 2.0, 3.0, 4.0)),
    ACOUSTIC("acoustic", listOf(4.0, 3.0, 2.0, 1.0, 1.0, 1.0, 2.0, 2.0, 3.0, 2.0)),
    VOCAL("vocal", listOf(-2.0, -2.0, -1.0, 1.0, 3.0, 4.0, 4.0, 3.0, 1.0, 0.0)),
    TREBLE_BOOST("treble_boost", listOf(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 1.0, 3.0, 5.0, 7.0)),
    LOUDNESS("loudness", listOf(6.0, 4.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 4.0, 5.0)),

    /** Phone and laptop speakers cannot move air below ~100 Hz, so lift the upper bass they can play. */
    PHONE_SPEAKER("phone_speaker", listOf(0.0, 0.0, 4.0, 4.0, 3.0, 1.0, 0.0, 1.0, 2.0, 1.0));

    companion object {
        fun fromId(id: String?): EqPreset? = entries.firstOrNull { it.id == id }
    }
}

/**
 * The user's equalizer state. [presetId] is an [EqPreset] id or [CUSTOM_PRESET_ID] once a band is
 * moved by hand. Bass boost and loudness are independent of the preset, so choosing a preset never
 * takes away the bass the user dialled in.
 */
data class EqualizerSettings(
    val enabled: Boolean = true,
    val presetId: String = EqPreset.FLAT.id,
    val bandGainsDb: List<Double> = FLAT_GAINS,
    val bassBoostPercent: Int = 0,
    val loudnessPercent: Int = 0
) {
    /** True when processing would not change the sound, so the processor can pass audio straight through. */
    val isNeutral: Boolean
        get() = !enabled || (bandGainsDb.all { it == 0.0 } && bassBoostPercent == 0 && loudnessPercent == 0)

    val bassBoostDb: Double get() = BASS_BOOST_MAX_DB * bassBoostPercent / 100.0

    val loudnessDb: Double get() = LOUDNESS_MAX_DB * loudnessPercent / 100.0
}

fun sanitizeBandGain(gainDb: Double?): Double =
    gainDb?.takeIf { it.isFinite() }?.coerceIn(-EQ_BAND_LIMIT_DB, EQ_BAND_LIMIT_DB) ?: 0.0

fun sanitizePercent(percent: Int?): Int = (percent ?: 0).coerceIn(0, 100)

/** Repairs a stored or user-supplied state: wrong band count, out-of-range values, unknown preset ids. */
fun sanitizeEqualizerSettings(settings: EqualizerSettings): EqualizerSettings {
    val gains = List(EQ_BAND_COUNT) { index -> sanitizeBandGain(settings.bandGainsDb.getOrNull(index)) }
    val presetId = when {
        settings.presetId == CUSTOM_PRESET_ID -> CUSTOM_PRESET_ID
        EqPreset.fromId(settings.presetId) != null -> settings.presetId
        else -> CUSTOM_PRESET_ID
    }
    return settings.copy(
        presetId = presetId,
        bandGainsDb = gains,
        bassBoostPercent = sanitizePercent(settings.bassBoostPercent),
        loudnessPercent = sanitizePercent(settings.loudnessPercent)
    )
}

/** Band gains are stored as one comma-separated string ("5.0,6.0,..."). */
fun encodeBandGains(gainsDb: List<Double>): String = gainsDb.joinToString(",")

/** A missing, short or garbled value yields flat bands rather than an error. */
fun decodeBandGains(encoded: String?): List<Double> {
    val parts = encoded?.split(',').orEmpty()
    return List(EQ_BAND_COUNT) { index -> sanitizeBandGain(parts.getOrNull(index)?.trim()?.toDoubleOrNull()) }
}

/** Choosing a preset loads its bands; bass boost, loudness and the on/off switch are kept. */
fun EqualizerSettings.withPreset(preset: EqPreset): EqualizerSettings =
    copy(presetId = preset.id, bandGainsDb = preset.bandGainsDb)

/** Moving one band by hand turns the preset into Custom. */
fun EqualizerSettings.withBandGain(band: Int, gainDb: Double): EqualizerSettings {
    if (band !in 0 until EQ_BAND_COUNT) return this
    val gains = bandGainsDb.toMutableList().also { it[band] = sanitizeBandGain(gainDb) }
    return copy(presetId = CUSTOM_PRESET_ID, bandGainsDb = gains)
}

/** Normalised biquad coefficients (a0 = 1), for y = b0·x + b1·x1 + b2·x2 − a1·y1 − a2·y2. */
data class BiquadCoefficients(
    val b0: Double,
    val b1: Double,
    val b2: Double,
    val a1: Double,
    val a2: Double
) {
    companion object {
        val IDENTITY = BiquadCoefficients(1.0, 0.0, 0.0, 0.0, 0.0)
    }
}

/** Highest frequency a filter may be centred on; above it the band is skipped (low sample-rate files). */
private fun isUsable(frequencyHz: Double, sampleRateHz: Int): Boolean =
    sampleRateHz > 0 && frequencyHz > 0.0 && frequencyHz < sampleRateHz * 0.45

/** RBJ Audio EQ Cookbook peaking filter. */
fun peakingCoefficients(frequencyHz: Double, gainDb: Double, q: Double, sampleRateHz: Int): BiquadCoefficients {
    if (gainDb == 0.0 || !isUsable(frequencyHz, sampleRateHz)) return BiquadCoefficients.IDENTITY
    val a = 10.0.pow(gainDb / 40.0)
    val w0 = 2.0 * PI * frequencyHz / sampleRateHz
    val alpha = sin(w0) / (2.0 * q)
    val cosW0 = cos(w0)
    val a0 = 1.0 + alpha / a
    return BiquadCoefficients(
        b0 = (1.0 + alpha * a) / a0,
        b1 = (-2.0 * cosW0) / a0,
        b2 = (1.0 - alpha * a) / a0,
        a1 = (-2.0 * cosW0) / a0,
        a2 = (1.0 - alpha / a) / a0
    )
}

/** RBJ Audio EQ Cookbook low shelf with slope S = 1 (no overshoot around the corner). */
fun lowShelfCoefficients(frequencyHz: Double, gainDb: Double, sampleRateHz: Int): BiquadCoefficients {
    if (gainDb == 0.0 || !isUsable(frequencyHz, sampleRateHz)) return BiquadCoefficients.IDENTITY
    val a = 10.0.pow(gainDb / 40.0)
    val w0 = 2.0 * PI * frequencyHz / sampleRateHz
    val cosW0 = cos(w0)
    val alpha = sin(w0) / 2.0 * sqrt(2.0) // S = 1
    val twoSqrtAAlpha = 2.0 * sqrt(a) * alpha
    val a0 = (a + 1.0) + (a - 1.0) * cosW0 + twoSqrtAAlpha
    return BiquadCoefficients(
        b0 = a * ((a + 1.0) - (a - 1.0) * cosW0 + twoSqrtAAlpha) / a0,
        b1 = 2.0 * a * ((a - 1.0) - (a + 1.0) * cosW0) / a0,
        b2 = a * ((a + 1.0) - (a - 1.0) * cosW0 - twoSqrtAAlpha) / a0,
        a1 = -2.0 * ((a - 1.0) + (a + 1.0) * cosW0) / a0,
        a2 = ((a + 1.0) + (a - 1.0) * cosW0 - twoSqrtAAlpha) / a0
    )
}

/** Gain of one biquad at [frequencyHz], in dB. */
fun BiquadCoefficients.magnitudeDb(frequencyHz: Double, sampleRateHz: Int): Double {
    val w = 2.0 * PI * frequencyHz / sampleRateHz
    val cos1 = cos(w)
    val sin1 = sin(w)
    val cos2 = cos(2.0 * w)
    val sin2 = sin(2.0 * w)
    val numRe = b0 + b1 * cos1 + b2 * cos2
    val numIm = -(b1 * sin1 + b2 * sin2)
    val denRe = 1.0 + a1 * cos1 + a2 * cos2
    val denIm = -(a1 * sin1 + a2 * sin2)
    val power = (numRe * numRe + numIm * numIm) / (denRe * denRe + denIm * denIm)
    return 10.0 * log10(power)
}

/** Filters in the bank: the bass shelf plus one per band. */
val EQ_FILTER_COUNT: Int = EQ_BAND_COUNT + 1

/**
 * Always [EQ_FILTER_COUNT] filters, the bass shelf first, with identity filters for flat bands. A fixed
 * layout lets the processor keep each filter's running state when a slider moves, so changes do not click.
 */
fun equalizerFilterBank(settings: EqualizerSettings, sampleRateHz: Int): List<BiquadCoefficients> {
    val shelf = lowShelfCoefficients(BASS_BOOST_CORNER_HZ, settings.bassBoostDb, sampleRateHz)
    val bands = EQ_BAND_FREQUENCIES_HZ.mapIndexed { index, frequency ->
        peakingCoefficients(frequency, settings.bandGainsDb.getOrElse(index) { 0.0 }, EQ_BAND_Q, sampleRateHz)
    }
    return listOf(shelf) + bands
}

/** The filter chain for [settings]: the bass shelf first, then the ten bands. Identity filters are left out. */
fun equalizerFilterChain(settings: EqualizerSettings, sampleRateHz: Int): List<BiquadCoefficients> {
    if (settings.isNeutral) return emptyList()
    return equalizerFilterBank(settings, sampleRateHz).filter { it != BiquadCoefficients.IDENTITY }
}

/** Strongest boost of the whole chain across the audible range (log-spaced probe, 20 Hz → 20 kHz), in dB. */
fun maxChainGainDb(chain: List<BiquadCoefficients>, sampleRateHz: Int): Double {
    if (chain.isEmpty()) return 0.0
    val nyquistGuard = sampleRateHz * 0.49
    var maxDb = Double.NEGATIVE_INFINITY
    for (step in 0..96) {
        val frequency = 20.0 * 1_000.0.pow(step / 96.0)
        if (frequency >= nyquistGuard) break
        val gain = chain.sumOf { it.magnitudeDb(frequency, sampleRateHz) }
        if (gain > maxDb) maxDb = gain
    }
    return maxDb.coerceAtLeast(0.0)
}

/** Linear gain applied before the filters (partial headroom) and after them (loudness). */
data class EqualizerGains(val preampLinear: Double, val outputLinear: Double)

fun equalizerGains(settings: EqualizerSettings, chain: List<BiquadCoefficients>, sampleRateHz: Int): EqualizerGains {
    if (settings.isNeutral) return EqualizerGains(1.0, 1.0)
    val preampDb = -AUTO_PREAMP_FRACTION * maxChainGainDb(chain, sampleRateHz)
    return EqualizerGains(dbToLinear(preampDb), dbToLinear(settings.loudnessDb))
}

fun dbToLinear(db: Double): Double = 10.0.pow(db / 20.0)
