package io.cloudcauldron.bocan.app.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.cloudcauldron.bocan.app.R
import io.cloudcauldron.bocan.app.library.AlbumUi

/** One album in the albums grid: square artwork, title, and artist (with year when known). */
@Composable
fun AlbumCell(album: AlbumUi, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val subtitle = album.year?.let { stringResource(R.string.album_cell_artist_year, album.artist, it) } ?: album.artist
    val description = stringResource(R.string.album_cell_a11y, album.name, subtitle)
    Column(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(8.dp)
            .semantics(mergeDescendants = true) { contentDescription = description }
    ) {
        ArtworkImage(
            artworkHash = album.artworkHash,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(8.dp))
        )
        Text(
            text = album.name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp)
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
