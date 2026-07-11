package io.cloudcauldron.bocan.app.player

import io.cloudcauldron.bocan.persistence.daos.LibraryDao
import io.cloudcauldron.bocan.playback.CoroutineDispatchers
import io.cloudcauldron.bocan.playback.MediaId
import io.cloudcauldron.bocan.playback.lyrics.LyricsDoc
import io.cloudcauldron.bocan.playback.lyrics.LyricsRepository
import io.cloudcauldron.bocan.playback.lyrics.LyricsResult
import io.cloudcauldron.bocan.playback.queue.PlaybackTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Lyrics for the current track plus which line is active at the current player position. */
data class LyricsUiState(val result: LyricsResult = LyricsResult.None, val activeLineIndex: Int = -1) {
    val offsetMs: Long
        get() = ((result as? LyricsResult.Loaded)?.doc as? LyricsDoc.Synced)?.offsetMs ?: 0
}

/**
 * Drives the lyrics pane. Resolves lyrics for the current track through [LyricsRepository]
 * and derives the active line from the player position (not a wall clock, so it never
 * drifts). Tapping a line seeks there.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LyricsViewModel(
    private val transport: PlaybackTransport,
    private val libraryDao: LibraryDao,
    private val lyricsRepository: LyricsRepository,
    dispatchers: CoroutineDispatchers
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.default)

    private val lyrics =
        transport.state.map { it.current?.mediaId }
            .distinctUntilChanged()
            .flatMapLatest { mediaId -> flow { emit(resolve(mediaId)) } }

    val state: StateFlow<LyricsUiState> =
        combine(lyrics, transport.state.map { it.positionMs }) { result, positionMs ->
            LyricsUiState(result = result, activeLineIndex = activeLine(result, positionMs))
        }.stateIn(scope, SharingStarted.WhileSubscribed(SUBSCRIBE_TIMEOUT_MS), LyricsUiState())

    /** Seek to a synced line's timestamp. */
    fun seekToLine(timeMs: Long) {
        scope.launch { transport.seekTo(timeMs) }
    }

    fun dispose() = scope.cancel()

    private suspend fun resolve(mediaId: String?): LyricsResult {
        val trackId = (mediaId?.let(MediaId::parse) as? MediaId.Track)?.trackId ?: return LyricsResult.None
        val track = libraryDao.tracksByIds(listOf(trackId)).firstOrNull()
        return if (track == null) LyricsResult.None else lyricsRepository.lyricsFor(trackId, track.lyricsHash)
    }

    private fun activeLine(result: LyricsResult, positionMs: Long): Int {
        val doc = (result as? LyricsResult.Loaded)?.doc as? LyricsDoc.Synced ?: return -1
        return doc.lines.indexOfLast { it.timeMs <= positionMs }
    }

    private companion object {
        const val SUBSCRIBE_TIMEOUT_MS = 5_000L
    }
}
