package io.cloudcauldron.bocan.app.podcasts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.cloudcauldron.bocan.app.R
import io.cloudcauldron.bocan.app.components.ArtworkImage

/** A show's detail: header (artwork, title, author, description) and its episodes newest-first. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShowDetailScreen(viewModel: ShowDetailViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val ui by viewModel.state.collectAsState()
    var notesEpisode by remember { mutableStateOf<EpisodeUi?>(null) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(ui.header.title, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            item(key = "header") { ShowHeader(ui.header) }
            items(ui.episodes, key = { it.id }, contentType = { "episode" }) { episode ->
                EpisodeRow(
                    episode = episode,
                    onPlay = { viewModel.play(episode.id) },
                    onMarkPlayed = { viewModel.markPlayed(episode.id) },
                    onMarkUnplayed = { viewModel.markUnplayed(episode.id) },
                    onShowNotes = { notesEpisode = episode }
                )
            }
        }
    }

    notesEpisode?.let { episode ->
        ShowNotesSheet(title = episode.title, descriptionHtml = episode.descriptionHtml, onDismiss = { notesEpisode = null })
    }
}

@Composable
private fun ShowHeader(header: ShowHeaderUi) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Row {
            ArtworkImage(
                artworkHash = header.artworkHash,
                modifier = Modifier.size(96.dp).clip(RoundedCornerShape(10.dp))
            )
            Column(modifier = Modifier.padding(start = 12.dp)) {
                Text(header.title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                header.author?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        header.descriptionHtml?.let { html ->
            val plain = ShowNotesSanitizer.sanitize(html).replace(Regex("<[^>]*>"), "").trim()
            if (plain.isNotEmpty()) {
                Text(
                    text = plain,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = if (expanded) Int.MAX_VALUE else 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 12.dp)
                )
                Text(
                    text = stringResource(if (expanded) R.string.show_description_less else R.string.show_description_more),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp).clickable { expanded = !expanded }
                )
            }
        }
    }
}
