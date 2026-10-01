package me.aliahad.audioplayer

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** One-tap bass levels, in percent of the full boost. */
private val BASS_LEVELS: List<Pair<Int, Int>> = listOf(
    0 to R.string.eq_bass_level_off,
    25 to R.string.eq_bass_level_low,
    50 to R.string.eq_bass_level_medium,
    75 to R.string.eq_bass_level_high,
    100 to R.string.eq_bass_level_max
)

@StringRes
private fun EqPreset.labelRes(): Int = when (this) {
    EqPreset.FLAT -> R.string.eq_preset_flat
    EqPreset.BASS_BOOST -> R.string.eq_preset_bass_boost
    EqPreset.SUBWOOFER -> R.string.eq_preset_subwoofer
    EqPreset.DEEP_BASS -> R.string.eq_preset_deep_bass
    EqPreset.BASS_TREBLE -> R.string.eq_preset_bass_treble
    EqPreset.HIP_HOP -> R.string.eq_preset_hip_hop
    EqPreset.DANCE -> R.string.eq_preset_dance
    EqPreset.ROCK -> R.string.eq_preset_rock
    EqPreset.POP -> R.string.eq_preset_pop
    EqPreset.JAZZ -> R.string.eq_preset_jazz
    EqPreset.CLASSICAL -> R.string.eq_preset_classical
    EqPreset.ACOUSTIC -> R.string.eq_preset_acoustic
    EqPreset.VOCAL -> R.string.eq_preset_vocal
    EqPreset.TREBLE_BOOST -> R.string.eq_preset_treble_boost
    EqPreset.LOUDNESS -> R.string.eq_preset_loudness
    EqPreset.PHONE_SPEAKER -> R.string.eq_preset_phone_speaker
}

/**
 * The Sound sheet. Bass boost comes first and is the largest control, because raising the bass is what
 * most people open it for; presets and the ten bands are below for finer shaping.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EqualizerSheet(
    settings: EqualizerSettings,
    onSetEnabled: (Boolean) -> Unit,
    onSelectPreset: (EqPreset) -> Unit,
    onSetBand: (Int, Double) -> Unit,
    onSetBassBoost: (Int) -> Unit,
    onSetLoudness: (Int) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.eq_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onReset()
                    }
                ) {
                    Icon(Icons.Rounded.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.size(6.dp))
                    Text(stringResource(R.string.eq_reset))
                }
                val switchLabel = stringResource(R.string.eq_switch)
                Switch(
                    checked = settings.enabled,
                    onCheckedChange = onSetEnabled,
                    modifier = Modifier.semantics { contentDescription = switchLabel }
                )
            }
            if (!settings.enabled) {
                Text(
                    text = stringResource(R.string.eq_off_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Dimmed while off so the state is obvious, but still touchable: moving any control switches it on.
            Column(
                modifier = Modifier.graphicsLayer { alpha = if (settings.enabled) 1f else 0.6f },
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                BassBoostCard(
                    percent = settings.bassBoostPercent,
                    onSetPercent = onSetBassBoost
                )

                PercentSlider(
                    label = stringResource(R.string.eq_loudness),
                    percent = settings.loudnessPercent,
                    onSetPercent = onSetLoudness
                )

                PresetRow(
                    selectedId = settings.presetId,
                    onSelect = { preset ->
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSelectPreset(preset)
                    }
                )

                BandSliders(gainsDb = settings.bandGainsDb, onSetBand = onSetBand)
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BassBoostCard(percent: Int, onSetPercent: (Int) -> Unit) {
    val haptic = LocalHapticFeedback.current
    val label = stringResource(R.string.eq_bass_boost)
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(
                    text = stringResource(R.string.eq_percent, percent),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Slider(
                value = percent.toFloat(),
                onValueChange = { onSetPercent(it.roundToInt()) },
                valueRange = 0f..100f,
                modifier = Modifier.semantics { contentDescription = label }
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                BASS_LEVELS.forEachIndexed { index, (level, levelLabel) ->
                    SegmentedButton(
                        selected = percent == level,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSetPercent(level)
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = BASS_LEVELS.size),
                        icon = {}
                    ) {
                        Text(text = stringResource(levelLabel), maxLines = 1)
                    }
                }
            }
            Text(
                text = stringResource(R.string.eq_bass_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PercentSlider(label: String, percent: Int, onSetPercent: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(
                text = stringResource(R.string.eq_percent, percent),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = percent.toFloat(),
            onValueChange = { onSetPercent(it.roundToInt()) },
            valueRange = 0f..100f,
            modifier = Modifier.semantics { contentDescription = label }
        )
    }
}

@Composable
private fun PresetRow(selectedId: String, onSelect: (EqPreset) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = stringResource(R.string.eq_presets), style = MaterialTheme.typography.titleSmall)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (selectedId == CUSTOM_PRESET_ID) {
                item(key = CUSTOM_PRESET_ID) {
                    FilterChip(
                        selected = true,
                        onClick = {},
                        label = { Text(stringResource(R.string.eq_preset_custom)) },
                        leadingIcon = { Icon(Icons.Rounded.Check, null, Modifier.size(FilterChipDefaults.IconSize)) }
                    )
                }
            }
            items(EqPreset.entries, key = { it.id }) { preset ->
                val selected = preset.id == selectedId
                FilterChip(
                    selected = selected,
                    onClick = { onSelect(preset) },
                    label = { Text(stringResource(preset.labelRes())) },
                    leadingIcon = if (selected) {
                        { Icon(Icons.Rounded.Check, null, Modifier.size(FilterChipDefaults.IconSize)) }
                    } else {
                        null
                    }
                )
            }
        }
    }
}

@Composable
private fun BandSliders(gainsDb: List<Double>, onSetBand: (Int, Double) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = stringResource(R.string.eq_bands), style = MaterialTheme.typography.titleSmall)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
        ) {
            EQ_BAND_FREQUENCIES_HZ.forEachIndexed { band, frequency ->
                val gain = gainsDb.getOrElse(band) { 0.0 }.roundToInt()
                val isKilo = frequency >= 1_000.0
                val amount = if (isKilo) (frequency / 1_000.0).roundToInt() else frequency.roundToInt()
                val bandName = stringResource(if (isKilo) R.string.eq_band_khz else R.string.eq_band_hz, amount)
                val gainText = stringResource(R.string.eq_gain_db, gain)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (gain > 0) "+$gain" else gain.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (gain == 0) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                        maxLines = 1
                    )
                    VerticalSlider(
                        value = gain.toFloat(),
                        onValueChange = { onSetBand(band, it.roundToInt().toDouble()) },
                        valueRange = -EQ_BAND_LIMIT_DB.toFloat()..EQ_BAND_LIMIT_DB.toFloat(),
                        steps = (EQ_BAND_LIMIT_DB * 2).toInt() - 1,
                        description = bandName,
                        state = gainText,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    )
                    Text(
                        text = stringResource(
                            if (isKilo) R.string.eq_band_short_khz else R.string.eq_band_short_hz,
                            amount
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/** A Slider turned 90° so its minimum is at the bottom; touch and accessibility follow the rotation. */
@Composable
private fun VerticalSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    description: String,
    state: String,
    modifier: Modifier = Modifier
) {
    Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = valueRange,
        steps = steps,
        // Keep the 1 dB steps (snapping, one step per accessibility swipe) but not 24 tick dots per band.
        colors = SliderDefaults.colors(
            activeTickColor = Color.Transparent,
            inactiveTickColor = Color.Transparent
        ),
        modifier = modifier
            .graphicsLayer {
                rotationZ = 270f
                transformOrigin = TransformOrigin(0f, 0f)
            }
            .layout { measurable, constraints ->
                val placeable = measurable.measure(
                    Constraints(
                        minWidth = constraints.minHeight,
                        maxWidth = constraints.maxHeight,
                        minHeight = constraints.minWidth,
                        maxHeight = constraints.maxWidth
                    )
                )
                layout(placeable.height, placeable.width) { placeable.place(-placeable.width, 0) }
            }
            .semantics {
                contentDescription = description
                stateDescription = state
            }
    )
}
