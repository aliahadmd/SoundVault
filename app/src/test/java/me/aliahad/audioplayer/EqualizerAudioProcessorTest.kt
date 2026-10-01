package me.aliahad.audioplayer

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

private const val RATE = 44_100

/** One second of a stereo sine, as the sink would hand it over. */
private fun sine16(frequencyHz: Double, amplitude: Double, seconds: Double = 1.0): ByteBuffer {
    val frames = (RATE * seconds).roundToInt()
    val buffer = ByteBuffer.allocateDirect(frames * 4).order(ByteOrder.nativeOrder())
    for (i in 0 until frames) {
        val value = (sin(2.0 * PI * frequencyHz * i / RATE) * amplitude * 32_767.0).roundToInt().toShort()
        buffer.putShort(value).putShort(value)
    }
    buffer.flip()
    return buffer
}

private fun sineFloat(frequencyHz: Double, amplitude: Double): ByteBuffer {
    val buffer = ByteBuffer.allocateDirect(RATE * 8).order(ByteOrder.nativeOrder())
    for (i in 0 until RATE) {
        val value = (sin(2.0 * PI * frequencyHz * i / RATE) * amplitude).toFloat()
        buffer.putFloat(value).putFloat(value)
    }
    buffer.flip()
    return buffer
}

private fun processor(settings: EqualizerSettings, encoding: Int = C.ENCODING_PCM_16BIT) =
    EqualizerAudioProcessor().apply {
        setSettings(settings)
        configure(AudioFormat(RATE, 2, encoding))
        flush()
    }

/** Runs [input] through and returns the left channel of the output. */
private fun EqualizerAudioProcessor.run16(input: ByteBuffer): ShortArray {
    queueInput(input)
    val output = getOutput().order(ByteOrder.nativeOrder())
    val left = ShortArray(output.remaining() / 4)
    for (i in left.indices) {
        left[i] = output.getShort()
        output.getShort()
    }
    return left
}

/** RMS of the second half, after the filters have settled, in dBFS. */
private fun ShortArray.settledRmsDb(): Double {
    val tail = copyOfRange(size / 2, size)
    val meanSquare = tail.sumOf { (it / 32_768.0).let { s -> s * s } } / tail.size
    return 10.0 * log10(meanSquare)
}

private fun gainDb(settings: EqualizerSettings, frequencyHz: Double): Double {
    val dry = sine16(frequencyHz, 0.1)
    val dryRms = ShortArray(dry.remaining() / 4) { dry.getShort(it * 4) }.settledRmsDb()
    return processor(settings).run16(sine16(frequencyHz, 0.1)).settledRmsDb() - dryRms
}

class EqualizerAudioProcessorTest : FunSpec({

    test("neutral settings pass audio through bit for bit") {
        val input = sine16(440.0, 0.5)
        val expected = input.duplicate()
        val output = processor(EqualizerSettings()).apply { queueInput(input) }.getOutput()
        output shouldBe expected
    }

    test("full bass boost lifts 50 Hz over 5 kHz exactly as the shelf filter predicts") {
        val settings = EqualizerSettings(bassBoostPercent = 100)
        val shelf = lowShelfCoefficients(BASS_BOOST_CORNER_HZ, BASS_BOOST_MAX_DB, RATE)
        val predicted = shelf.magnitudeDb(50.0, RATE) - shelf.magnitudeDb(5_000.0, RATE)
        val measured = gainDb(settings, 50.0) - gainDb(settings, 5_000.0)
        measured shouldBe (predicted plusOrMinus 0.2)
        measured shouldBeGreaterThan 12.0
    }

    test("the bass boost makes quiet bass louder than it went in") {
        gainDb(EqualizerSettings(bassBoostPercent = 100), 50.0) shouldBeGreaterThan 5.0
    }

    test("a preset changes the response like its bands say") {
        val settings = EqualizerSettings().withPreset(EqPreset.VOCAL)
        val vocalLift = gainDb(settings, 1_000.0) - gainDb(settings, 62.0)
        vocalLift shouldBeGreaterThan 4.0
    }

    test("the limiter keeps full-scale bass with maximum boost and loudness below the ceiling") {
        val settings = EqualizerSettings(bassBoostPercent = 100, loudnessPercent = 100).withPreset(EqPreset.SUBWOOFER)
        val output = processor(settings).run16(sine16(40.0, 1.0))
        val ceiling = (EqualizerAudioProcessor.LIMITER_CEILING * 32_767.0).roundToInt() + 1
        output.maxOf { abs(it.toInt()) } shouldBeLessThanOrEqual ceiling
    }

    test("float PCM is processed and limited too") {
        val processor = processor(EqualizerSettings(bassBoostPercent = 100, loudnessPercent = 100), C.ENCODING_PCM_FLOAT)
        processor.queueInput(sineFloat(40.0, 1.0))
        val output = processor.getOutput().order(ByteOrder.nativeOrder()).asFloatBuffer()
        var peak = 0.0
        var sumSquares = 0.0
        while (output.hasRemaining()) {
            val sample = output.get().toDouble()
            peak = maxOf(peak, abs(sample))
            sumSquares += sample * sample
        }
        peak shouldBeLessThan EqualizerAudioProcessor.LIMITER_CEILING + 1e-6
        sqrt(sumSquares / RATE) shouldBeGreaterThan 0.3
    }

    test("switching the equalizer off fades back to untouched audio") {
        val processor = processor(EqualizerSettings(bassBoostPercent = 80))
        processor.run16(sine16(100.0, 0.3, seconds = 0.1))
        processor.setSettings(EqualizerSettings(enabled = false, bassBoostPercent = 80))
        processor.run16(sine16(100.0, 0.3, seconds = 0.1)) // crossfade happens in here
        val input = sine16(100.0, 0.3, seconds = 0.1)
        val expected = input.duplicate()
        processor.queueInput(input)
        processor.getOutput() shouldBe expected
    }

    test("8-bit or 24-bit input is refused, so the sink skips the processor") {
        val refused = runCatching {
            EqualizerAudioProcessor().configure(AudioFormat(RATE, 2, C.ENCODING_PCM_24BIT))
        }.exceptionOrNull()
        (refused != null) shouldBe true
    }
})
