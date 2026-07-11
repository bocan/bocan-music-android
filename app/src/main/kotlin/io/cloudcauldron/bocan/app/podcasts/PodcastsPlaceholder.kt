package io.cloudcauldron.bocan.app.podcasts

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.cloudcauldron.bocan.app.R
import io.cloudcauldron.bocan.app.components.EmptyState

/** The Podcasts bottom destination, a placeholder until phase 07 builds the podcast surfaces. */
@Composable
fun PodcastsPlaceholder(modifier: Modifier = Modifier) {
    EmptyState(
        icon = Icons.Rounded.Podcasts,
        title = stringResource(R.string.podcasts_placeholder_title),
        message = stringResource(R.string.podcasts_placeholder_message),
        modifier = modifier
    )
}
