package io.cloudcauldron.bocan.app.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Favorite
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.cloudcauldron.bocan.app.R
import io.cloudcauldron.bocan.playback.SleepTimerState

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
    onBack: () -> Unit,
    onOpenArtist: (Long) -> Unit,
    onOpenAlbum: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val ui by nowPlaying.state.collectAsState()
    var showLyrics by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }
    var showSleep by remember { mutableStateOf(false) }
    var showSpeed by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .ambientBackground(ui.artworkUri)
            .systemBarsPadding()
            .padding(horizontal = 20.dp)
    ) {
        TopRow(
            armed = ui.sleepTimer != SleepTimerState.Idle,
            onBack = onBack,
            onOverflow = { },
            onSleep = { showSleep = true },
            onSpeed = { showSpeed = true },
            onToggleLyrics = { showLyrics = !showLyrics },
            onQueue = { showQueue = true }
        )
        if (showLyrics) {
            val lyricsUi by lyrics.state.collectAsState()
            LyricsPane(lyricsUi, onSeekToLine = lyrics::seekToLine, modifier = Modifier.weight(1f))
        } else {
            ArtworkAndMeta(
                ui = ui,
                onOpenArtist = { ui.display.artistId?.let(onOpenArtist) },
                onOpenAlbum = { ui.display.albumId?.let(onOpenAlbum) },
                modifier = Modifier.weight(1f)
            )
        }
        SeekBar(ui.positionMs, ui.durationMs, onSeek = nowPlaying::seekTo, modifier = Modifier.padding(top = 8.dp))
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
}

@Composable
private fun TopRow(
    armed: Boolean,
    onBack: () -> Unit,
    onOverflow: () -> Unit,
    onSleep: () -> Unit,
    onSpeed: () -> Unit,
    onToggleLyrics: () -> Unit,
    onQueue: () -> Unit
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
            OverflowItem(Icons.Rounded.Lyrics, R.string.lyrics_toggle) {
                menuOpen = false
                onToggleLyrics()
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

@Composable
private fun ArtworkAndMeta(ui: NowPlayingUiState, onOpenArtist: () -> Unit, onOpenAlbum: () -> Unit, modifier: Modifier) {
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
        LovedAndRating(ui.display.loved, ui.display.rating)
    }
}

@Composable
private fun LovedAndRating(loved: Boolean, rating: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
        if (loved) {
            Icon(
                Icons.Rounded.Favorite,
                contentDescription = stringResource(R.string.track_loved_a11y),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp).padding(end = 8.dp)
            )
        }
        val stars = rating.coerceIn(0, MAX_STARS)
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
