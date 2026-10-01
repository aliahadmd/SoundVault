package me.aliahad.audioplayer

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink

/**
 * The default renderers, with [equalizer] added to the audio sink's processing chain. It runs on the
 * decoded PCM before the speed / pitch stage, so it applies to every format the player can decode.
 */
@androidx.annotation.OptIn(UnstableApi::class)
class EqualizerRenderersFactory(
    context: Context,
    private val equalizer: EqualizerAudioProcessor
) : DefaultRenderersFactory(context) {

    override fun buildAudioSink(
        context: Context,
        enableFloatOutput: Boolean,
        enableAudioTrackPlaybackParams: Boolean
    ): AudioSink =
        DefaultAudioSink.Builder(context)
            .setEnableFloatOutput(enableFloatOutput)
            .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
            .setAudioProcessors(arrayOf(equalizer))
            .build()
}
