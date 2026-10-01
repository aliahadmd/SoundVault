package me.aliahad.audioplayer

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.AudioProcessor.UnhandledAudioFormatException
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.roundToInt

/**
 * Equalizer, bass booster and loudness, applied to the decoded PCM inside ExoPlayer's audio sink.
 *
 * Signal path per frame: preamp (partial headroom) → bass shelf + ten bands → loudness → a linked
 * peak limiter that keeps every processed sample at or below [LIMITER_CEILING], so a big bass boost gets
 * louder without clipping. Settings arrive from the main thread through [setSettings] and are picked up
 * at the next buffer. Switching between neutral and processed sound crossfades over [CROSSFADE_SECONDS].
 *
 * The processor is always active for 16-bit and float PCM (the sink only re-reads [isActive] on a
 * flush) and copies audio straight through while the settings are neutral.
 */
@androidx.annotation.OptIn(UnstableApi::class)
class EqualizerAudioProcessor : BaseAudioProcessor() {

    @Volatile
    private var requestedSettings: EqualizerSettings = EqualizerSettings()

    // Everything below is touched only on the playback thread.
    private var appliedSettings: EqualizerSettings? = null
    private var appliedSampleRate = 0
    private var channelCount = 0

    /** Coefficients, five per filter: b0, b1, b2, a1, a2. */
    private val coefficients = DoubleArray(EQ_FILTER_COUNT * 5)

    /** Transposed direct form II state, two values per filter per channel. */
    private var filterState = DoubleArray(0)
    private var dryFrame = DoubleArray(0)
    private var wetFrame = DoubleArray(0)

    private var preamp = 1.0
    private var outputGain = 1.0
    private var limiterGain = 1.0
    private var limiterRelease = 0.0
    private var wet = 0.0
    private var targetWet = 0.0
    private var wetStep = 1.0
    private var snapToTarget = true

    fun setSettings(settings: EqualizerSettings) {
        requestedSettings = sanitizeEqualizerSettings(settings)
    }

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT && inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT) {
            throw UnhandledAudioFormatException(inputAudioFormat)
        }
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val size = inputBuffer.remaining()
        if (size == 0) return
        val format = inputAudioFormat
        applySettingsIfChanged(format)

        val output = replaceOutputBuffer(size)
        if (wet == 0.0 && targetWet == 0.0) {
            output.put(inputBuffer)
            output.flip()
            return
        }

        val input = inputBuffer.duplicate().order(ByteOrder.nativeOrder())
        val channels = format.channelCount
        val isFloat = format.encoding == C.ENCODING_PCM_FLOAT
        while (input.remaining() >= format.bytesPerFrame) {
            for (channel in 0 until channels) {
                dryFrame[channel] = if (isFloat) input.getFloat().toDouble() else input.getShort() / 32_768.0
            }
            processFrame(channels)
            for (channel in 0 until channels) {
                val sample = wetFrame[channel]
                if (isFloat) {
                    output.putFloat(sample.toFloat())
                } else {
                    output.putShort((sample * 32_767.0).roundToInt().coerceIn(-32_768, 32_767).toShort())
                }
            }
        }
        inputBuffer.position(inputBuffer.limit())
        output.flip()
    }

    /** Filters [dryFrame] into [wetFrame], limits it, then mixes in the dry signal while crossfading. */
    private fun processFrame(channels: Int) {
        var peak = 0.0
        for (channel in 0 until channels) {
            var sample = dryFrame[channel] * preamp
            val stateBase = channel * EQ_FILTER_COUNT * 2
            for (filter in 0 until EQ_FILTER_COUNT) {
                val c = filter * 5
                val s = stateBase + filter * 2
                val y = coefficients[c] * sample + filterState[s]
                filterState[s] = coefficients[c + 1] * sample - coefficients[c + 3] * y + filterState[s + 1]
                filterState[s + 1] = coefficients[c + 2] * sample - coefficients[c + 4] * y
                sample = y
            }
            sample *= outputGain
            wetFrame[channel] = sample
            val magnitude = abs(sample)
            if (magnitude > peak) peak = magnitude
        }

        // Instant attack, smooth release; one gain for all channels keeps the stereo image steady.
        var gain = limiterGain + (1.0 - limiterGain) * limiterRelease
        if (peak * gain > LIMITER_CEILING) gain = LIMITER_CEILING / peak
        limiterGain = gain

        if (wet != targetWet) {
            wet = if (wet < targetWet) (wet + wetStep).coerceAtMost(targetWet) else (wet - wetStep).coerceAtLeast(targetWet)
        }
        val wetGain = gain * wet
        val dryGain = 1.0 - wet
        for (channel in 0 until channels) {
            wetFrame[channel] = wetFrame[channel] * wetGain + dryFrame[channel] * dryGain
        }
    }

    private fun applySettingsIfChanged(format: AudioFormat) {
        val settings = requestedSettings
        if (settings == appliedSettings && format.sampleRate == appliedSampleRate && format.channelCount == channelCount) {
            return
        }
        if (format.channelCount != channelCount) {
            channelCount = format.channelCount
            filterState = DoubleArray(channelCount * EQ_FILTER_COUNT * 2)
            dryFrame = DoubleArray(channelCount)
            wetFrame = DoubleArray(channelCount)
        }
        appliedSampleRate = format.sampleRate
        appliedSettings = settings
        limiterRelease = 1.0 - exp(-1.0 / (LIMITER_RELEASE_SECONDS * format.sampleRate))
        wetStep = 1.0 / (CROSSFADE_SECONDS * format.sampleRate)

        if (settings.isNeutral) {
            // Keep the current filters running so the sound can fade out of them.
            targetWet = 0.0
        } else {
            if (wet == 0.0) {
                // Coming back from pass-through: the old filter state is stale.
                filterState.fill(0.0)
                limiterGain = 1.0
            }
            val bank = equalizerFilterBank(settings, format.sampleRate)
            bank.forEachIndexed { index, filter ->
                val c = index * 5
                coefficients[c] = filter.b0
                coefficients[c + 1] = filter.b1
                coefficients[c + 2] = filter.b2
                coefficients[c + 3] = filter.a1
                coefficients[c + 4] = filter.a2
            }
            val gains = equalizerGains(settings, bank, format.sampleRate)
            preamp = gains.preampLinear
            outputGain = gains.outputLinear
            targetWet = 1.0
        }
        if (snapToTarget) {
            wet = targetWet
            snapToTarget = false
        }
    }

    override fun onFlush() {
        // A seek or a new stream: clear the filters and start at the target mix without a fade.
        filterState.fill(0.0)
        limiterGain = 1.0
        appliedSettings = null
        snapToTarget = true
    }

    override fun onReset() {
        appliedSettings = null
        appliedSampleRate = 0
        channelCount = 0
        filterState = DoubleArray(0)
        dryFrame = DoubleArray(0)
        wetFrame = DoubleArray(0)
        preamp = 1.0
        outputGain = 1.0
        limiterGain = 1.0
        wet = 0.0
        targetWet = 0.0
        snapToTarget = true
    }

    companion object {
        /** About −0.2 dBFS, leaving room for rounding to 16 bits. */
        const val LIMITER_CEILING = 0.98
        const val LIMITER_RELEASE_SECONDS = 0.15
        const val CROSSFADE_SECONDS = 0.02
    }
}
