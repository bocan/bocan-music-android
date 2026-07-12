package io.cloudcauldron.bocan.app.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.cloudcauldron.bocan.app.R
import io.cloudcauldron.bocan.app.podcasts.ChaptersSheet
import io.cloudcauldron.bocan.playback.SleepTimerState
import io.cloudcauldron.bocan.playback.audio.WaveformSource
import io.cloudcauldron.bocan.playback.podcast.ChaptersParser
import kotlin.math.roundToInt

/**
 * The full-screen Now Playing: ambient artwork background, artwork or lyrics, tappable
 * artist and album, display-only loved and rating, the seek bar and transport, and an
 * overflow with sleep timer, speed, lyrics toggle, and the queue. Loved and rating show
 * no edit affordance: they belong to the Mac.
 */
@Suppress("LongMethod")
@Composable
fun NowPlayingScreen(
    nowPlaying: NowPlayingViewModel,
    queue: QueueViewModel,
    lyrics: LyricsViewModel,
    songDetails: SongDetailsViewModel,
    waveform: WaveformSource,
    onBack: () -> Unit,
    onOpenArtist: (Long) -> Unit,
    onOpenAlbum: (Long) -> Unit,
    onOpenEqualizer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ui by nowPlaying.state.collectAsState()
    var showLyrics by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }
    var showSleep by remember { mutableStateOf(false) }
    var showSpeed by remember { mutableStateOf(false) }
    var showChapters by remember { mutableStateOf(false) }
    var showDetails by remember { mutableStateOf(false) }

    val reducedMotion = isReducedMotion(LocalContext.current)
    val gesture = rememberPlayerGestureState()
    gesture.hasPrevious = ui.previous != null
    gesture.hasNext = ui.next != null
    val gestureActions = PlayerGestureActions(
        onNext = nowPlaying::next,
        onPrevious = nowPlaying::previous,
        onOpenDetails = { showDetails = true },
        onDismiss = onBack
    )
    val gestureLabels = PlayerGestureLabels(
        next = stringResource(R.string.gesture_next_track),
        previous = stringResource(R.string.gesture_previous_track),
        details = stringResource(R.string.gesture_song_details),
        close = stringResource(R.string.gesture_close_player)
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .offset { IntOffset(0, if (reducedMotion) 0 else gesture.verticalOffset.roundToInt()) }
            .ambientBackground(ui.artworkUri)
            .statusBarsPadding()
            .padding(horizontal = 20.dp)
    ) {
        TopRow(
            armed = ui.sleepTimer != SleepTimerState.Idle,
            hasChapters = ui.podcast.chapters.isNotEmpty(),
            onBack = onBack,
            onOverflow = { },
            onSleep = { showSleep = true },
            onSpeed = { showSpeed = true },
            onToggleLyrics = { showLyrics = !showLyrics },
            onQueue = { showQueue = true },
            onChapters = { showChapters = true },
            onEqualizer = onOpenEqualizer,
            onSongDetails = { showDetails = true }
        )
        if (showLyrics) {
            val lyricsUi by lyrics.state.collectAsState()
            LyricsPane(lyricsUi, onSeekToLine = lyrics::seekToLine, modifier = Modifier.weight(1f))
        } else {
            ArtworkStrip(
                ui = ui,
                gesture = gesture,
                reducedMotion = reducedMotion,
                actions = gestureActions,
                labels = gestureLabels,
                onOpenArtist = { ui.display.artistId?.let(onOpenArtist) },
                onOpenAlbum = { ui.display.albumId?.let(onOpenAlbum) },
                modifier = Modifier.weight(1f)
            )
        }
        SeekBar(ui.positionMs, ui.durationMs, onSeek = nowPlaying::seekTo, modifier = Modifier.padding(top = 8.dp))
        if (ui.podcast.isPodcast) {
            PodcastTransportControls(
                isPlaying = ui.isPlaying,
                speed = ui.speed,
                onPlayPause = nowPlaying::togglePlayPause,
                onSkipBack = nowPlaying::skipBack,
                onSkipForward = nowPlaying::skipForward,
                onCycleSpeed = nowPlaying::cycleSpeed,
                modifier = Modifier.padding(vertical = 12.dp)
            )
        } else {
            TransportControls(
                isPlaying = ui.isPlaying,
                repeatMode = ui.repeatMode,
                shuffleActive = ui.shuffleActive,
                onPlayPause = nowPlaying::togglePlayPause,
                onPrevious = nowPlaying::previous,
                onNext = nowPlaying::next,
                onShuffle = nowPlaying::toggleShuffle,
                onShuffleLongPress = nowPlaying::toggleShuffle,
                onCycleRepeat = nowPlaying::cycleRepeat,
                modifier = Modifier.padding(vertical = 12.dp)
            )
        }
        // The oscilloscope fills the strip the mini player used to occupy on this screen.
        // It is dropped while a sheet is open so the sheet's scrim is never punched through.
        val anySheetOpen = showQueue || showSleep || showSpeed || showChapters || showDetails
        if (!anySheetOpen) {
            Oscilloscope(
                source = waveform,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(bottom = 8.dp)
            )
        }
    }

    if (showQueue) {
        val queueUi by queue.state.collectAsState()
        QueueSheet(queueUi, onMove = queue::move, onRemove = queue::removeAt, onClear = queue::clear, onDismiss = { showQueue = false })
    }
    if (showSleep) {
        SleepTimerSheet(
            state = ui.sleepTimer,
            onStart = nowPlaying::startSleepTimer,
            onExtend = nowPlaying::extendSleepTimer,
            onCancel = nowPlaying::cancelSleepTimer,
            onDismiss = { showSleep = false }
        )
    }
    if (showSpeed) {
        SpeedSheet(speed = ui.speed, onSpeed = nowPlaying::setSpeed, onDismiss = { showSpeed = false })
    }
    if (showChapters) {
        ChaptersSheet(
            chapters = ui.podcast.chapters,
            activeIndex = ChaptersParser.activeChapterIndex(ui.podcast.chapters, ui.positionMs),
            onSeek = nowPlaying::seekTo,
            onDismiss = { showChapters = false }
        )
    }
    if (showDetails) {
        val detailsUi by songDetails.state.collectAsState()
        // Fetch the live pipeline line whenever the sheet opens or the item changes underneath.
        LaunchedEffect(showDetails, ui.artworkUri, ui.title) { songDetails.refreshPipeline() }
        SongDetailsSheet(state = detailsUi, onDismiss = { showDetails = false })
    }
}

@Composable
private fun TopRow(
    armed: Boolean,
    hasChapters: Boolean,
    onBack: () -> Unit,
    onOverflow: () -> Unit,
    onSleep: () -> Unit,
    onSpeed: () -> Unit,
    onToggleLyrics: () -> Unit,
    onQueue: () -> Unit,
    onChapters: () -> Unit,
    onEqualizer: () -> Unit,
    onSongDetails: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        IconButton(onClick = onBack) {
            Icon(Icons.Rounded.ExpandMore, contentDescription = stringResource(R.string.action_back))
        }
        Spacer(Modifier.weight(1f))
        if (armed) {
            Icon(
                Icons.Rounded.Bedtime,
                contentDescription = stringResource(R.string.sleep_timer_armed),
                tint = MaterialTheme.colorScheme.primary
            )
        }
        IconButton(onClick = {
            onOverflow()
            menuOpen = true
        }) {
            Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.action_more))
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            OverflowItem(Icons.Rounded.Bedtime, R.string.sleep_timer_title) {
                menuOpen = false
                onSleep()
            }
            OverflowItem(Icons.Rounded.Speed, R.string.speed_title) {
                menuOpen = false
                onSpeed()
            }
            OverflowItem(Icons.Rounded.Equalizer, R.string.eq_title) {
                menuOpen = false
                onEqualizer()
            }
            OverflowItem(Icons.Rounded.Lyrics, R.string.lyrics_toggle) {
                menuOpen = false
                onToggleLyrics()
            }
            OverflowItem(Icons.Rounded.Info, R.string.song_details_title) {
                menuOpen = false
                onSongDetails()
            }
            if (hasChapters) {
                OverflowItem(Icons.AutoMirrored.Rounded.List, R.string.chapters_title) {
                    menuOpen = false
                    onChapters()
                }
            }
            OverflowItem(Icons.AutoMirrored.Rounded.QueueMusic, R.string.queue_title) {
                menuOpen = false
                onQueue()
            }
        }
    }
}

@Composable
private fun OverflowItem(icon: androidx.compose.ui.graphics.vector.ImageVector, labelRes: Int, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(stringResource(labelRes)) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        onClick = onClick
    )
}

/**
 * The gesture surface: the current card plus, during a horizontal drag, the real previous
 * and next cards tracking in from the opposite edge as one strip (right = next). The block
 * follows the finger; the settle and peek are suppressed under reduced motion, where the
 * content swaps with no slide.
 */
@Composable
private fun ArtworkStrip(
    ui: NowPlayingUiState,
    gesture: PlayerGestureState,
    reducedMotion: Boolean,
    actions: PlayerGestureActions,
    labels: PlayerGestureLabels,
    onOpenArtist: () -> Unit,
    onOpenAlbum: () -> Unit,
    modifier: Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clipToBounds()
            .playerGestures(gesture, reducedMotion, actions, labels)
    ) {
        if (!reducedMotion) {
            val width = gesture.widthPx
            val offset = gesture.horizontalOffset
            // Pager convention: a leftward swipe advances, so the next card peeks in from the
            // right edge and the previous card from the left.
            ui.next?.let { neighbor ->
                NeighborCard(neighbor, Modifier.offset { IntOffset((offset + width).roundToInt(), 0) })
            }
            ui.previous?.let { neighbor ->
                NeighborCard(neighbor, Modifier.offset { IntOffset((offset - width).roundToInt(), 0) })
            }
        }
        CurrentCard(
            ui = ui,
            onOpenArtist = onOpenArtist,
            onOpenAlbum = onOpenAlbum,
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(if (reducedMotion) 0 else gesture.horizontalOffset.roundToInt(), 0) }
        )
    }
}

/** The peeking previous or next card: real artwork and titles, no placeholder, no controls. */
@Composable
private fun NeighborCard(neighbor: NeighborDisplay, modifier: Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier.fillMaxSize().clearAndSetSemantics {}
    ) {
        UriArtwork(
            uri = neighbor.artworkUri,
            modifier = Modifier.fillMaxWidth(0.85f).aspectRatio(1f).clip(RoundedCornerShape(16.dp))
        )
        Text(
            text = neighbor.title,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 20.dp)
        )
        Text(
            text = neighbor.artist,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
private fun CurrentCard(ui: NowPlayingUiState, onOpenArtist: () -> Unit, onOpenAlbum: () -> Unit, modifier: Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier.fillMaxWidth()
    ) {
        UriArtwork(
            uri = ui.artworkUri,
            modifier = Modifier.fillMaxWidth(0.85f).aspectRatio(1f).clip(RoundedCornerShape(16.dp))
        )
        Text(
            text = ui.title,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 20.dp)
        )
        Text(
            text = ui.artist,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable(onClick = onOpenArtist).padding(top = 4.dp)
        )
        Text(
            text = ui.album,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.clickable(onClick = onOpenAlbum)
        )
        ui.podcast.chapterTitle?.let { chapter ->
            Text(
                text = chapter,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        if (!ui.podcast.isPodcast) {
            LovedAndRating(ui.display.loved, ui.display.rating)
        }
    }
}

@Composable
private fun LovedAndRating(loved: Boolean, rating: Int) {
    val stars = rating.coerceIn(0, MAX_STARS)
    val ratingDescription = if (stars > 0) stringResource(R.string.rating_a11y, stars, MAX_STARS) else null
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(top = 8.dp)
            .semantics(mergeDescendants = true) { ratingDescription?.let { contentDescription = it } }
    ) {
        if (loved) {
            Icon(
                Icons.Rounded.Favorite,
                contentDescription = stringResource(R.string.track_loved_a11y),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp).padding(end = 8.dp)
            )
        }
        repeat(stars) {
            Icon(
                Icons.Rounded.Star,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

private const val MAX_STARS = 5
