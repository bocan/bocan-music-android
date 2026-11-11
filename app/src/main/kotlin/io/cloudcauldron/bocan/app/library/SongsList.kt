package io.cloudcauldron.bocan.app.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import io.cloudcauldron.bocan.app.components.TrackList
import io.cloudcauldron.bocan.app.e2e.E2ETags
import io.cloudcauldron.bocan.persistence.model.TrackSort
import kotlin.math.ceil
import kotlinx.coroutines.launch

/**
 * The full songs list with an alphabet rail for fast scrolling through 10k tracks. Row
 * rendering, tap, and long-press come from [TrackList] (stable keys, single contentType).
 *
 * The rail indexes what the list is sorted by ([sort]): titles, artists, or albums. When the
 * screen is too short for 26 letters (a phone in landscape) it shows the letters that fit, and
 * a drag along it reaches every letter whichever are shown.
 */
@Composable
fun SongsList(songs: List<TrackUi>, sort: TrackSort, callbacks: LibraryCallbacks, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    val firstIndexByLetter = remember(songs, sort) { railIndex(songs.map { it.railKey(sort) }) }
    Box(modifier = modifier.fillMaxSize()) {
        // Reserve the rail's width so a row's trailing duration never renders under the
        // alphabet letters at the right edge.
        TrackList(
            tracks = songs,
            callbacks = callbacks,
            listState = listState,
            modifier = Modifier.fillMaxSize().padding(end = RAIL_WIDTH)
        )
        AlphabetRail(firstIndexByLetter, listState, Modifier.align(Alignment.CenterEnd))
    }
}

@Composable
private fun AlphabetRail(firstIndexByLetter: Map<Char, Int>, listState: LazyListState, modifier: Modifier) {
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    fun jumpTo(letter: Char) {
        railTarget(firstIndexByLetter, letter)?.let { index -> scope.launch { listState.scrollToItem(index) } }
    }
    BoxWithConstraints(
        modifier = modifier
            .fillMaxHeight()
            .width(RAIL_WIDTH)
            // Hidden from TalkBack, which scrolls the list itself; the tag keeps the drag reachable in tests.
            .clearAndSetSemantics { testTag = E2ETags.Library.ALPHABET_RAIL }
            .pointerInput(firstIndexByLetter) {
                var last: Char? = null
                detectVerticalDragGestures(onDragEnd = { last = null }, onDragCancel = { last = null }) { change, _ ->
                    val letter = railLetterAt(change.position.y / size.height)
                    if (letter != last) {
                        last = letter
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        jumpTo(letter)
                    }
                }
            }
    ) {
        val letters = railLetters(capacity = (maxHeight / MIN_LETTER_HEIGHT).toInt())
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly,
            modifier = Modifier.fillMaxSize()
        ) {
            letters.forEach { letter ->
                Text(
                    text = letter.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    // Still in the test tree under the cleared rail; every letter shares one tag.
                    modifier = Modifier
                        .testTag(E2ETags.Library.ALPHABET_LETTER)
                        .clickable { jumpTo(letter) }
                )
            }
        }
    }
}

private val RAIL_WIDTH = 24.dp

/** The least height a letter needs to stay legible and tappable; below it the rail skips letters. */
private val MIN_LETTER_HEIGHT = 16.dp

private val ALPHABET: List<Char> = ('A'..'Z').toList()

/** What the rail indexes under [sort]: the field the list is ordered by. */
internal fun TrackUi.railKey(sort: TrackSort): String = when (sort) {
    TrackSort.Title -> title
    TrackSort.Artist -> artist
    TrackSort.Album -> album
}

/** The first row of each letter, from the sort keys in list order. Keys not starting with A to Z are not indexed. */
internal fun railIndex(keys: List<String>): Map<Char, Int> = buildMap {
    keys.forEachIndexed { index, key ->
        val letter = key.firstOrNull()?.uppercaseChar()
        if (letter != null && letter in ALPHABET) putIfAbsent(letter, index)
    }
}

/** Where [letter] goes: its first row, else the next letter's that has rows, else the last letter's. Null for an empty list. */
internal fun railTarget(index: Map<Char, Int>, letter: Char): Int? =
    ALPHABET.dropWhile { it < letter }.firstNotNullOfOrNull { index[it] } ?: index.values.maxOrNull()

/** The letters shown in room for [capacity]: all 26, or every second or third (always from A) when they do not fit. */
internal fun railLetters(capacity: Int): List<Char> {
    if (capacity >= ALPHABET.size) return ALPHABET
    val step = ceil(ALPHABET.size / capacity.coerceAtLeast(1).toDouble()).toInt()
    return ALPHABET.filterIndexed { index, _ -> index % step == 0 }
}

/** The letter under a drag at [fraction] of the rail's height; every letter is reachable, shown or not. */
internal fun railLetterAt(fraction: Float): Char = ALPHABET[(fraction * ALPHABET.size).toInt().coerceIn(0, ALPHABET.lastIndex)]
