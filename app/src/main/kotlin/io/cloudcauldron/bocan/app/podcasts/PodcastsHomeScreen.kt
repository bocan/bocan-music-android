package io.cloudcauldron.bocan.app.podcasts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material3.Badge
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.cloudcauldron.bocan.app.R
import io.cloudcauldron.bocan.app.components.ArtworkImage
import io.cloudcauldron.bocan.app.components.EmptyState

/** The Podcasts home: a continue-listening shelf then the subscribed-shows grid with unplayed badges. */
@Composable
fun PodcastsHomeScreen(
    viewModel: PodcastsViewModel,
    onOpenShow: (Long) -> Unit,
    onResume: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val ui by viewModel.state.collectAsState()
    if (ui.shows.isEmpty() && ui.continueListening.isEmpty()) {
        EmptyState(
            icon = Icons.Rounded.Podcasts,
            title = stringResource(R.string.podcasts_empty_title),
            message = stringResource(R.string.podcasts_empty_message),
            modifier = modifier
        )
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 150.dp),
        contentPadding = PaddingValues(12.dp),
        modifier = modifier.fillMaxSize()
    ) {
        if (ui.continueListening.isNotEmpty()) {
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                ContinueShelf(ui.continueListening, onResume)
            }
        }
        items(ui.shows, key = { it.id }) { show ->
            ShowCell(show, onClick = { onOpenShow(show.id) })
        }
    }
}

@Composable
private fun ContinueShelf(items: List<ContinueUi>, onResume: (String) -> Unit) {
    Column(modifier = Modifier.padding(bottom = 8.dp)) {
        Text(
            stringResource(R.string.podcasts_continue),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        LazyRow {
            items(items, key = { it.episodeId }) { card ->
                val cardDescription = stringResource(R.string.podcasts_resume_a11y, card.title)
                Column(
                    modifier = Modifier
                        .width(200.dp)
                        .padding(end = 12.dp)
                        .clickable { onResume(card.episodeId) }
                        .semantics(mergeDescendants = true) { contentDescription = cardDescription }
                ) {
                    Box(modifier = Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(10.dp))) {
                        ArtworkImage(artworkHash = null, modifier = Modifier.fillMaxSize())
                    }
                    Text(
                        card.title,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                    LinearProgressIndicator(progress = { card.progress }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                }
            }
        }
    }
}

@Composable
private fun ShowCell(show: ShowUi, onClick: () -> Unit) {
    val cellDescription = if (show.unplayedCount > 0) {
        pluralStringResource(R.plurals.show_cell_unplayed_a11y, show.unplayedCount, show.title, show.author.orEmpty(), show.unplayedCount)
    } else {
        stringResource(R.string.show_cell_a11y, show.title, show.author.orEmpty())
    }
    Column(
        modifier = Modifier
            .padding(8.dp)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = cellDescription }
    ) {
        Box {
            ArtworkImage(
                artworkHash = show.artworkHash,
                modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(10.dp))
            )
            if (show.unplayedCount > 0) {
                Badge(modifier = Modifier.align(Alignment.TopEnd).padding(6.dp)) { Text(show.unplayedCount.toString()) }
            }
        }
        Text(
            show.title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 6.dp)
        )
        show.author?.let {
            Text(
                it,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
