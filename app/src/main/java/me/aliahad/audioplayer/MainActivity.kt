package me.aliahad.audioplayer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SheetState
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import me.aliahad.audioplayer.ui.theme.AudioplayerTheme
import java.util.Locale
import kotlin.math.abs

class MainActivity : ComponentActivity() {

    private val viewModel: AudioPlayerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermissionIfNeeded()
        enableEdgeToEdge()
        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()

            val activity = this@MainActivity
            LaunchedEffect(uiState.isNightMode) {
                val transparent = android.graphics.Color.TRANSPARENT
                val barStyle = if (uiState.isNightMode) {
                    SystemBarStyle.dark(transparent)
                } else {
                    SystemBarStyle.light(transparent, transparent)
                }
                activity.enableEdgeToEdge(statusBarStyle = barStyle, navigationBarStyle = barStyle)
            }

            AudioplayerTheme(isNightMode = uiState.isNightMode) {
                val context = LocalContext.current
                val folderPicker = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocumentTree()
                ) { uri ->
                    if (uri != null) {
                        try {
                            context.contentResolver.takePersistableUriPermission(
                                uri,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION
                            )
                        } catch (_: SecurityException) {
                            // Permission might already be granted; continue with selection.
                        }
                        viewModel.onFolderSelected(uri)
                    }
                }

                AudioPlayerScreen(
                    uiState = uiState,
                    onChooseFolder = { folderPicker.launch(null) },
                    onPlayPause = viewModel::togglePlayPause,
                    onNext = viewModel::playNext,
                    onPrevious = viewModel::playPrevious,
                    onStop = viewModel::stopPlayback,
                    onSelectTrack = viewModel::selectTrack,
                    onSeekTo = viewModel::seekTo,
                    onSkipBack = viewModel::skipBack,
                    onSkipForward = viewModel::skipForward,
                    onToggleShuffle = viewModel::toggleShuffle,
                    onCycleRepeatMode = viewModel::cycleRepeatMode,
                    onCyclePlaybackSpeed = viewModel::cyclePlaybackSpeed,
                    onToggleTheme = viewModel::toggleTheme,
                    onSetSkipBackInterval = viewModel::setSkipBackInterval,
                    onSetSkipForwardInterval = viewModel::setSkipForwardInterval,
                    onSetEqualizerEnabled = viewModel::setEqualizerEnabled,
                    onSelectEqualizerPreset = viewModel::selectEqualizerPreset,
                    onSetEqualizerBand = viewModel::setEqualizerBand,
                    onSetBassBoost = viewModel::setBassBoost,
                    onSetLoudness = viewModel::setLoudness,
                    onResetEqualizer = viewModel::resetEqualizer,
                    onBookmarkTap = viewModel::onBookmarkTap,
                    onSaveBookmark = viewModel::saveBookmark,
                    onDismissBookmarkDialog = viewModel::dismissBookmarkDialog,
                    onSeekToTimestamp = viewModel::seekToTimestamp,
                    onDeleteTimestamp = viewModel::deleteBookmark
                )
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), NOTIFICATION_PERMISSION_REQUEST_CODE)
        }
    }

    companion object {
        private const val NOTIFICATION_PERMISSION_REQUEST_CODE = 100
    }
}

/** Icon for the theme toggle: offers the mode you would switch to (sun while in Night mode). */
internal fun themeToggleIcon(isNightMode: Boolean): ImageVector =
    if (isNightMode) Icons.Filled.LightMode else Icons.Filled.DarkMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioPlayerScreen(
    uiState: PlayerUiState,
    onChooseFolder: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onStop: () -> Unit,
    onSelectTrack: (Int) -> Unit,
    onSeekTo: (Long) -> Unit,
    onSkipBack: () -> Unit,
    onSkipForward: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeatMode: () -> Unit,
    onCyclePlaybackSpeed: () -> Unit,
    onToggleTheme: () -> Unit,
    onSetSkipBackInterval: (Long) -> Unit,
    onSetSkipForwardInterval: (Long) -> Unit,
    onBookmarkTap: () -> Unit,
    onSaveBookmark: (String?) -> Unit,
    onDismissBookmarkDialog: () -> Unit,
    onSeekToTimestamp: (Long) -> Unit,
    onDeleteTimestamp: (Long) -> Unit,
    modifier: Modifier = Modifier,
    onSetEqualizerEnabled: (Boolean) -> Unit = {},
    onSelectEqualizerPreset: (EqPreset) -> Unit = {},
    onSetEqualizerBand: (Int, Double) -> Unit = { _, _ -> },
    onSetBassBoost: (Int) -> Unit = {},
    onSetLoudness: (Int) -> Unit = {},
    onResetEqualizer: () -> Unit = {}
) {
    val currentTrack = uiState.tracks.getOrNull(uiState.currentTrackIndex)
    val hasTracks = uiState.tracks.isNotEmpty()
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showEqualizer by rememberSaveable { mutableStateOf(false) }
    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.surfaceContainerHigh,
            MaterialTheme.colorScheme.background
        )
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .background(backgroundBrush)
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleLarge
                        )
                    },
                    actions = {
                        IconButton(onClick = onToggleTheme) {
                            Icon(
                                imageVector = themeToggleIcon(uiState.isNightMode),
                                contentDescription = stringResource(
                                    if (uiState.isNightMode) R.string.cd_switch_to_light else R.string.cd_switch_to_night
                                )
                            )
                        }
                        // Folder picking lives in the Now Playing card and the empty state, so the
                        // second app-bar slot holds Settings (three actions would clip the centred title).
                        IconButton(onClick = { showSettings = true }) {
                            Icon(
                                imageVector = Icons.Rounded.Settings,
                                contentDescription = stringResource(R.string.cd_settings)
                            )
                        }
                    }
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                if (uiState.isLoading) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }

                var showDetails by rememberSaveable { mutableStateOf(false) }

                if (showDetails && currentTrack != null) {
                    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                    TrackDetailsSheet(
                        track = currentTrack,
                        sheetState = sheetState,
                        onDismiss = { showDetails = false }
                    )
                }

                if (showSettings) {
                    SettingsSheet(
                        skipIntervals = uiState.skipIntervals,
                        onSetSkipBackInterval = onSetSkipBackInterval,
                        onSetSkipForwardInterval = onSetSkipForwardInterval,
                        onDismiss = { showSettings = false }
                    )
                }

                if (showEqualizer) {
                    EqualizerSheet(
                        settings = uiState.equalizer,
                        onSetEnabled = onSetEqualizerEnabled,
                        onSelectPreset = onSelectEqualizerPreset,
                        onSetBand = onSetEqualizerBand,
                        onSetBassBoost = onSetBassBoost,
                        onSetLoudness = onSetLoudness,
                        onReset = onResetEqualizer,
                        onDismiss = { showEqualizer = false }
                    )
                }

                NowPlayingCard(
                    currentTrack = currentTrack,
                    isPlaying = uiState.isPlaying,
                    isLoading = uiState.isLoading,
                    folderUri = uiState.folderUri,
                    errorMessage = uiState.errorMessage,
                    onChooseFolder = onChooseFolder,
                    onShowDetails = { if (currentTrack != null) showDetails = true }
                )

                PlaylistSection(
                    tracks = uiState.tracks,
                    currentTrackIndex = uiState.currentTrackIndex,
                    isPlaying = uiState.isPlaying,
                    onSelectTrack = onSelectTrack,
                    onChooseFolder = onChooseFolder,
                    modifier = Modifier.weight(1f)
                )

                if (uiState.timestamps.isNotEmpty()) {
                    TimestampListSection(
                        timestamps = uiState.timestamps,
                        onSeekToTimestamp = onSeekToTimestamp,
                        onDeleteTimestamp = onDeleteTimestamp
                    )
                }

                PlaybackControls(
                    isPlaying = uiState.isPlaying,
                    hasTracks = hasTracks,
                    currentPosition = uiState.currentPosition,
                    bufferedPosition = uiState.bufferedPosition,
                    duration = uiState.duration,
                    isShuffleEnabled = uiState.isShuffleEnabled,
                    repeatMode = uiState.repeatMode,
                    playbackSpeed = uiState.playbackSpeed,
                    skipIntervals = uiState.skipIntervals,
                    onPlayPause = onPlayPause,
                    onNext = onNext,
                    onPrevious = onPrevious,
                    onStop = onStop,
                    onSeekTo = onSeekTo,
                    onSkipBack = onSkipBack,
                    onSkipForward = onSkipForward,
                    onToggleShuffle = onToggleShuffle,
                    onCycleRepeatMode = onCycleRepeatMode,
                    onCyclePlaybackSpeed = onCyclePlaybackSpeed,
                    onBookmarkTap = onBookmarkTap,
                    equalizerActive = !uiState.equalizer.isNeutral,
                    onOpenEqualizer = { showEqualizer = true }
                )

                uiState.bookmarkDraft?.let { draft ->
                    BookmarkDialog(
                        draft = draft,
                        onSave = onSaveBookmark,
                        onDismiss = onDismissBookmarkDialog
                    )
                }
            }
        }
    }
}

@Composable
private fun NowPlayingCard(
    currentTrack: AudioTrack?,
    isPlaying: Boolean,
    isLoading: Boolean,
    folderUri: Uri?,
    errorMessage: String?,
    onChooseFolder: () -> Unit,
    onShowDetails: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.secondary
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MusicNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(26.dp)
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = currentTrack?.title ?: stringResource(R.string.now_playing_placeholder),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = stringResource(
                            when {
                                isLoading -> R.string.status_loading
                                currentTrack == null -> R.string.status_no_track
                                isPlaying -> R.string.status_playing
                                else -> R.string.status_ready
                            }
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onShowDetails()
                }, enabled = currentTrack != null) {
                    Icon(
                        imageVector = Icons.Rounded.MusicNote,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = stringResource(R.string.action_details))
                }
                TextButton(onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onChooseFolder()
                }) {
                    Icon(
                        imageVector = Icons.Rounded.FolderOpen,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(
                            if (folderUri == null) R.string.action_choose_folder else R.string.action_change_folder
                        )
                    )
                }
            }

            if (errorMessage != null) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaylistSection(
    tracks: List<AudioTrack>,
    currentTrackIndex: Int,
    isPlaying: Boolean,
    onSelectTrack: (Int) -> Unit,
    onChooseFolder: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(R.string.playlist_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (tracks.isNotEmpty()) {
                Text(
                    text = pluralStringResource(R.plurals.playlist_track_count, tracks.size, tracks.size),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (tracks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = true),
                contentAlignment = Alignment.Center
            ) {
                PlaylistEmptyState(onChooseFolder = onChooseFolder)
            }
        } else {
            val listState = rememberLazyListState()
            LaunchedEffect(currentTrackIndex) {
                if (currentTrackIndex in tracks.indices) {
                    // Animating across hundreds of rows (e.g. a shuffle jump) is slow; snap when far away.
                    if (abs(listState.firstVisibleItemIndex - currentTrackIndex) > MAX_ANIMATED_SCROLL_DISTANCE) {
                        listState.scrollToItem(currentTrackIndex)
                    } else {
                        listState.animateScrollToItem(currentTrackIndex)
                    }
                }
            }
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = true),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                itemsIndexed(tracks, key = { _, track -> track.uri.toString() }) { index, track ->
                    PlaylistItem(
                        index = index,
                        track = track,
                        isCurrent = index == currentTrackIndex,
                        isPlaying = isPlaying && index == currentTrackIndex,
                        onSelect = { onSelectTrack(index) }
                    )
                }
            }
        }
    }
}

private const val MAX_ANIMATED_SCROLL_DISTANCE = 30

@Composable
private fun PlaylistItem(
    index: Int,
    track: AudioTrack,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onSelect: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val cardColor = if (isCurrent) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }
    val contentColor = if (isCurrent) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = cardColor,
        tonalElevation = if (isCurrent) 6.dp else 2.dp,
        shadowElevation = if (isCurrent) 6.dp else 0.dp,
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onSelect()
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .sizeIn(minWidth = 42.dp)
                    .clip(CircleShape)
                    .background(
                        color = if (isCurrent) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = (index + 1).toString().padStart(2, '0'),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isCurrent) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = track.relativePath,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isCurrent) {
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (isCurrent) {
                Icon(
                    imageVector = if (isPlaying) Icons.Rounded.GraphicEq else Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
private fun PlaylistEmptyState(onChooseFolder: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        tonalElevation = 2.dp,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 32.dp, vertical = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.LibraryMusic,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp)
            )
            Text(
                text = stringResource(R.string.empty_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(R.string.empty_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            FilledTonalButton(onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onChooseFolder()
            }) {
                Text(text = stringResource(R.string.action_browse_folders))
            }
        }
    }
}

@Composable
private fun PlaybackControls(
    isPlaying: Boolean,
    hasTracks: Boolean,
    currentPosition: Long,
    bufferedPosition: Long,
    duration: Long,
    isShuffleEnabled: Boolean,
    repeatMode: Int,
    playbackSpeed: Float,
    skipIntervals: SkipIntervals,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onStop: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSkipBack: () -> Unit,
    onSkipForward: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeatMode: () -> Unit,
    onCyclePlaybackSpeed: () -> Unit,
    onBookmarkTap: () -> Unit,
    equalizerActive: Boolean,
    onOpenEqualizer: () -> Unit
) {
    var controlsExpanded by rememberSaveable { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 3.dp,
        shadowElevation = 4.dp
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            PlaybackControlHeader(
                currentPosition = currentPosition,
                duration = duration,
                isExpanded = controlsExpanded,
                onToggleExpanded = { controlsExpanded = !controlsExpanded },
                equalizerActive = equalizerActive,
                onOpenEqualizer = onOpenEqualizer
            )

            AnimatedVisibility(
                visible = !controlsExpanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                MiniPlaybackProgress(
                    currentPosition = currentPosition,
                    bufferedPosition = bufferedPosition,
                    duration = duration
                )
            }

            AnimatedVisibility(
                visible = controlsExpanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                PlaybackProgressScrubber(
                    currentPosition = currentPosition,
                    bufferedPosition = bufferedPosition,
                    duration = duration,
                    hasTracks = hasTracks,
                    onSeekTo = onSeekTo,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }

            TransportControls(
                isPlaying = isPlaying,
                hasTracks = hasTracks,
                isExpanded = controlsExpanded,
                skipIntervals = skipIntervals,
                onPlayPause = onPlayPause,
                onNext = onNext,
                onPrevious = onPrevious,
                onSkipBack = onSkipBack,
                onSkipForward = onSkipForward
            )

            AnimatedVisibility(
                visible = controlsExpanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                SecondaryPlaybackControls(
                    hasTracks = hasTracks,
                    isShuffleEnabled = isShuffleEnabled,
                    repeatMode = repeatMode,
                    playbackSpeed = playbackSpeed,
                    onToggleShuffle = onToggleShuffle,
                    onCycleRepeatMode = onCycleRepeatMode,
                    onCyclePlaybackSpeed = onCyclePlaybackSpeed,
                    onBookmarkTap = onBookmarkTap,
                    onStop = onStop
                )
            }
        }
    }
}

@Composable
private fun PlaybackControlHeader(
    currentPosition: Long,
    duration: Long,
    isExpanded: Boolean,
    onToggleExpanded: () -> Unit,
    equalizerActive: Boolean,
    onOpenEqualizer: () -> Unit
) {
    val safeDuration = duration.takeIf { it > 0L } ?: 0L
    val timeLabel = if (safeDuration > 0L) {
        stringResource(
            R.string.time_progress,
            formatTimestamp(currentPosition.coerceIn(0L, safeDuration)),
            formatTimestamp(safeDuration)
        )
    } else {
        stringResource(
            R.string.time_progress,
            formatTimestamp(currentPosition),
            stringResource(R.string.time_unknown)
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, top = 10.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // One tap to the equalizer and bass boost; filled while it is shaping the sound.
        FilledTonalIconButton(
            onClick = onOpenEqualizer,
            colors = if (equalizerActive) {
                IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                IconButtonDefaults.filledTonalIconButtonColors()
            }
        ) {
            Icon(
                imageVector = Icons.Rounded.GraphicEq,
                contentDescription = stringResource(R.string.cd_equalizer),
                modifier = Modifier.size(20.dp)
            )
        }
        Text(
            text = timeLabel,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.weight(1f))
        IconButton(onClick = onToggleExpanded) {
            Icon(
                imageVector = if (isExpanded) Icons.Rounded.ExpandMore else Icons.Rounded.ExpandLess,
                contentDescription = stringResource(
                    if (isExpanded) R.string.cd_minimize_controls else R.string.cd_maximize_controls
                ),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TransportControls(
    isPlaying: Boolean,
    hasTracks: Boolean,
    isExpanded: Boolean,
    skipIntervals: SkipIntervals,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSkipBack: () -> Unit,
    onSkipForward: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val controlSize = if (isExpanded) 48.dp else 44.dp
    val playSize = if (isExpanded) 56.dp else 52.dp
    val playIconSize = if (isExpanded) 28.dp else 26.dp

    // Five controls at 8 dp spacing stay within a 360 dp-wide screen even when expanded.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, bottom = if (isExpanded) 0.dp else 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledTonalIconButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onPrevious()
                },
                enabled = hasTracks,
                modifier = Modifier.size(controlSize)
            ) {
                Icon(imageVector = Icons.Rounded.SkipPrevious, contentDescription = stringResource(R.string.cd_previous))
            }
            SkipButton(
                intervalMs = skipIntervals.backMs,
                forward = false,
                enabled = hasTracks,
                size = controlSize,
                onClick = onSkipBack
            )
            FilledIconButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onPlayPause()
                },
                enabled = hasTracks,
                modifier = Modifier.size(playSize),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = stringResource(if (isPlaying) R.string.cd_pause else R.string.cd_play),
                    modifier = Modifier.size(playIconSize)
                )
            }
            SkipButton(
                intervalMs = skipIntervals.forwardMs,
                forward = true,
                enabled = hasTracks,
                size = controlSize,
                onClick = onSkipForward
            )
            FilledTonalIconButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onNext()
                },
                enabled = hasTracks,
                modifier = Modifier.size(controlSize)
            ) {
                Icon(imageVector = Icons.Rounded.SkipNext, contentDescription = stringResource(R.string.cd_next))
            }
        }
    }
}

/**
 * Circular-arrow skip button with the step in seconds drawn inside, like Material's replay_10 /
 * forward_10 (which only exist for some steps). The digits are sized in dp, not sp, so large font
 * settings cannot push them out of the circle; TalkBack reads the full description instead.
 */
@Composable
private fun SkipButton(
    intervalMs: Long,
    forward: Boolean,
    enabled: Boolean,
    size: Dp,
    onClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val description = skipIntervalDescription(LocalContext.current.resources, intervalMs, forward)
    val digitSize = with(LocalDensity.current) { 10.dp.toSp() }
    IconButton(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onClick()
        },
        enabled = enabled,
        modifier = Modifier
            .size(size)
            .semantics { contentDescription = description }
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Rounded.Replay,
                contentDescription = null,
                modifier = Modifier
                    .size(34.dp)
                    // Mirrored, the replay arrow is Material's forward arrow.
                    .graphicsLayer { if (forward) scaleX = -1f }
            )
            Text(
                text = (intervalMs / 1_000L).toString(),
                fontSize = digitSize,
                lineHeight = digitSize,
                fontWeight = FontWeight.Bold,
                // The arrow's circle sits slightly below the icon's centre.
                modifier = Modifier.padding(top = 3.dp)
            )
        }
    }
}

@Composable
private fun SecondaryPlaybackControls(
    hasTracks: Boolean,
    isShuffleEnabled: Boolean,
    repeatMode: Int,
    playbackSpeed: Float,
    onToggleShuffle: () -> Unit,
    onCycleRepeatMode: () -> Unit,
    onCyclePlaybackSpeed: () -> Unit,
    onBookmarkTap: () -> Unit,
    onStop: () -> Unit
) {
    val haptic = LocalHapticFeedback.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val shuffleColors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = if (isShuffleEnabled) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            contentColor = if (isShuffleEnabled) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
        FilledTonalIconButton(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onToggleShuffle()
            },
            enabled = hasTracks,
            colors = shuffleColors,
            modifier = Modifier.size(44.dp)
        ) {
            Icon(imageVector = Icons.Rounded.Shuffle, contentDescription = stringResource(R.string.cd_toggle_shuffle))
        }

        val repeatSelected = repeatMode != Player.REPEAT_MODE_OFF
        val repeatColors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = if (repeatSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            contentColor = if (repeatSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
        val repeatIcon = when (repeatMode) {
            Player.REPEAT_MODE_ONE -> Icons.Rounded.RepeatOne
            else -> Icons.Rounded.Repeat
        }
        FilledTonalIconButton(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onCycleRepeatMode()
            },
            enabled = hasTracks,
            colors = repeatColors,
            modifier = Modifier.size(44.dp)
        ) {
            Icon(imageVector = repeatIcon, contentDescription = stringResource(R.string.cd_cycle_repeat))
        }

        TextButton(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onCyclePlaybackSpeed()
            },
            enabled = hasTracks
        ) {
            Icon(
                imageVector = Icons.Rounded.Speed,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = formatPlaybackSpeed(playbackSpeed))
        }

        FilledTonalIconButton(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onBookmarkTap()
            },
            enabled = hasTracks,
            modifier = Modifier.size(44.dp)
        ) {
            Icon(imageVector = Icons.Rounded.BookmarkAdd, contentDescription = stringResource(R.string.cd_add_bookmark))
        }

        // Stop moved here from the transport row, which now holds the skip buttons.
        FilledTonalIconButton(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onStop()
            },
            enabled = hasTracks,
            modifier = Modifier.size(44.dp)
        ) {
            Icon(imageVector = Icons.Rounded.Stop, contentDescription = stringResource(R.string.cd_stop))
        }
    }
}

@Composable
private fun MiniPlaybackProgress(
    currentPosition: Long,
    bufferedPosition: Long,
    duration: Long
) {
    val safeDuration = duration.takeIf { it > 0L } ?: 0L
    val playedFraction = progressFraction(currentPosition, safeDuration)
    val bufferedFraction = progressFraction(maxOf(bufferedPosition, currentPosition), safeDuration)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(8.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.58f))
        )
        Box(
            modifier = Modifier
                .fillMaxWidth(bufferedFraction)
                .height(4.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.24f))
        )
        Box(
            modifier = Modifier
                .fillMaxWidth(playedFraction)
                .height(4.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
        )
    }
}

@Composable
private fun PlaybackProgressScrubber(
    currentPosition: Long,
    bufferedPosition: Long,
    duration: Long,
    hasTracks: Boolean,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val safeDuration = duration.takeIf { it > 0L } ?: 0L
    val sliderRange = if (safeDuration > 0L) safeDuration.toFloat() else 1f
    var sliderPosition by remember(safeDuration, hasTracks) {
        mutableStateOf(currentPosition.coerceForSlider(safeDuration).toFloat())
    }
    var isScrubbing by remember { mutableStateOf(false) }

    LaunchedEffect(currentPosition, safeDuration, hasTracks) {
        if (!isScrubbing) {
            sliderPosition = currentPosition.coerceForSlider(safeDuration).toFloat()
        }
    }

    val displayPosition = if (isScrubbing) {
        sliderPosition.toLong()
    } else {
        currentPosition.coerceAtLeast(0L)
    }
    val playedFraction = progressFraction(
        position = if (isScrubbing) sliderPosition.toLong() else currentPosition,
        duration = safeDuration
    )
    val bufferedFraction = progressFraction(
        position = maxOf(bufferedPosition, currentPosition),
        duration = safeDuration
    )
    val trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.58f)
    val bufferedColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(CircleShape)
                    .background(trackColor)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(bufferedFraction)
                    .height(8.dp)
                    .align(Alignment.CenterStart)
                    .clip(CircleShape)
                    .background(bufferedColor)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(playedFraction)
                    .height(8.dp)
                    .align(Alignment.CenterStart)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            )
            Slider(
                value = sliderPosition,
                onValueChange = { value ->
                    if (hasTracks && safeDuration > 0L) {
                        if (!isScrubbing) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                        isScrubbing = true
                        sliderPosition = value.coerceIn(0f, sliderRange)
                    }
                },
                onValueChangeFinished = {
                    isScrubbing = false
                    if (hasTracks && safeDuration > 0L) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSeekTo(sliderPosition.toLong())
                    }
                },
                enabled = hasTracks && safeDuration > 0L,
                valueRange = 0f..sliderRange,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = Color.Transparent,
                    inactiveTrackColor = Color.Transparent,
                    disabledActiveTrackColor = Color.Transparent,
                    disabledInactiveTrackColor = Color.Transparent
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatTimestamp(displayPosition),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = if (safeDuration > 0L) formatTimestamp(safeDuration) else stringResource(R.string.time_unknown),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun Long.coerceForSlider(duration: Long): Long =
    if (duration > 0L) coerceIn(0L, duration) else 0L

private fun progressFraction(position: Long, duration: Long): Float =
    if (duration > 0L) {
        (position.coerceIn(0L, duration).toFloat() / duration.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

@Composable
private fun BookmarkDialog(
    draft: BookmarkDraft,
    onSave: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    var noteText by rememberSaveable(draft) { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.bookmark_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = draft.trackTitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(R.string.bookmark_dialog_position, formatTimestamp(draft.positionMs)),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = { Text(stringResource(R.string.bookmark_note_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(noteText.takeIf { it.isNotBlank() }) }) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

@Composable
private fun TimestampListSection(
    timestamps: List<TimestampBookmark>,
    onSeekToTimestamp: (Long) -> Unit,
    onDeleteTimestamp: (Long) -> Unit
) {
    var isExpanded by rememberSaveable { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    isExpanded = !isExpanded
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.bookmarks_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = timestamps.size.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = if (isExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                contentDescription = stringResource(
                    if (isExpanded) R.string.cd_collapse_bookmarks else R.string.cd_expand_bookmarks
                ),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            // Bounded and scrollable so a long list cannot push the playlist and controls off screen.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 220.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                timestamps.forEach { bookmark ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = formatTimestamp(bookmark.positionMs),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable { onSeekToTimestamp(bookmark.positionMs) }
                        )
                        if (bookmark.note != null) {
                            Text(
                                text = bookmark.note,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                        IconButton(onClick = { onDeleteTimestamp(bookmark.id) }) {
                            Icon(
                                imageVector = Icons.Rounded.Delete,
                                contentDescription = stringResource(R.string.cd_delete_bookmark),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSheet(
    skipIntervals: SkipIntervals,
    onSetSkipBackInterval: (Long) -> Unit,
    onSetSkipForwardInterval: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text(
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.titleLarge
            )
            SkipIntervalSetting(
                label = stringResource(R.string.settings_skip_back),
                selectedMs = skipIntervals.backMs,
                onSelect = onSetSkipBackInterval
            )
            SkipIntervalSetting(
                label = stringResource(R.string.settings_skip_forward),
                selectedMs = skipIntervals.forwardMs,
                onSelect = onSetSkipForwardInterval
            )
            Text(
                text = stringResource(R.string.settings_skip_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SkipIntervalSetting(
    label: String,
    selectedMs: Long,
    onSelect: (Long) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = label }
        ) {
            SKIP_INTERVAL_OPTIONS_MS.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = option == selectedMs,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSelect(option)
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = SKIP_INTERVAL_OPTIONS_MS.size)
                ) {
                    val parts = skipIntervalLabel(option)
                    Text(
                        text = stringResource(
                            if (parts.inMinutes) R.string.skip_interval_minutes else R.string.skip_interval_seconds,
                            parts.amount
                        ),
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrackDetailsSheet(
    track: AudioTrack,
    sheetState: SheetState,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.details_title),
                style = MaterialTheme.typography.titleLarge
            )
            DetailRow(label = stringResource(R.string.details_label_title), value = track.title)
            track.artist?.let { artist ->
                DetailRow(label = stringResource(R.string.details_label_artist), value = artist)
            }
            track.album?.let { album ->
                DetailRow(label = stringResource(R.string.details_label_album), value = album)
            }
            track.durationMs?.let { duration ->
                DetailRow(label = stringResource(R.string.details_label_duration), value = formatTimestamp(duration))
            }
            track.fileSizeBytes?.let { size ->
                formatFileSize(size)?.let { readable ->
                    DetailRow(label = stringResource(R.string.details_label_file_size), value = readable)
                }
            }
            DetailRow(label = stringResource(R.string.details_label_location), value = track.relativePath)
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
    }
}

private fun formatFileSize(sizeBytes: Long): String? {
    if (sizeBytes <= 0L) return null
    val units = listOf("B", "KB", "MB", "GB", "TB")
    var value = sizeBytes.toDouble()
    var unitIndex = 0
    while (value >= 1024 && unitIndex < units.lastIndex) {
        value /= 1024
        unitIndex++
    }
    return String.format(Locale.getDefault(), "%.1f %s", value, units[unitIndex])
}

@Preview(showBackground = true)
@Composable
private fun AudioPlayerScreenPreview() {
    AudioplayerTheme(isNightMode = true) {
        AudioPlayerScreen(
            uiState = PlayerUiState(
                folderUri = Uri.parse("content://demo/music"),
                tracks = listOf(
                    AudioTrack(
                        title = "Lo-fi Vibes",
                        uri = Uri.parse("content://demo/lofi"),
                        relativePath = "Late Night/01 Lo-fi Vibes.mp3",
                        artist = "Loft Beats",
                        album = "Late Night",
                        durationMs = 180_000L,
                        fileSizeBytes = 4_200_000L
                    ),
                    AudioTrack(
                        title = "Ocean Echoes",
                        uri = Uri.parse("content://demo/ocean"),
                        relativePath = "Blue/02 Ocean Echoes.mp3",
                        artist = "Tide",
                        album = "Blue",
                        durationMs = 200_000L,
                        fileSizeBytes = 4_600_000L
                    ),
                    AudioTrack(
                        title = "Night Walk",
                        uri = Uri.parse("content://demo/night"),
                        relativePath = "Midnight/03 Night Walk.mp3",
                        artist = "City Lights",
                        album = "Midnight",
                        durationMs = 220_000L,
                        fileSizeBytes = 5_000_000L
                    )
                ),
                currentTrackIndex = 1,
                isPlaying = true,
                currentPosition = 90_000L,
                bufferedPosition = 120_000L,
                duration = 240_000L,
                isShuffleEnabled = true,
                repeatMode = Player.REPEAT_MODE_ALL,
                playbackSpeed = 1.25f
            ),
            onChooseFolder = {},
            onPlayPause = {},
            onNext = {},
            onPrevious = {},
            onStop = {},
            onSelectTrack = {},
            onSeekTo = {},
            onSkipBack = {},
            onSkipForward = {},
            onToggleShuffle = {},
            onCycleRepeatMode = {},
            onCyclePlaybackSpeed = {},
            onToggleTheme = {},
            onSetSkipBackInterval = {},
            onSetSkipForwardInterval = {},
            onBookmarkTap = {},
            onSaveBookmark = {},
            onDismissBookmarkDialog = {},
            onSeekToTimestamp = {},
            onDeleteTimestamp = {}
        )
    }
}
