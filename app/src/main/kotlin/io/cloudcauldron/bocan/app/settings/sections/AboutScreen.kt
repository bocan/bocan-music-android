package io.cloudcauldron.bocan.app.settings.sections

import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import io.cloudcauldron.bocan.app.BuildConfig
import io.cloudcauldron.bocan.app.R
import io.cloudcauldron.bocan.app.components.SettingsNavRow

/**
 * About: version, the privacy explainer (what syncs and what never leaves this
 * phone), a link to the project site, the welcome tour, and the open source
 * licenses as a static generated list (no network, no play services).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onShowTour: () -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.about_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp)
        ) {
            VersionHeader()
            SectionHeading(stringResource(R.string.about_privacy_title))
            Text(
                text = stringResource(R.string.about_privacy_body),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            SettingsNavRow(
                label = stringResource(R.string.about_site_label),
                summary = SITE_URL,
                onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, SITE_URL.toUri())) }
            )
            SettingsNavRow(
                label = stringResource(R.string.about_site_android_label),
                summary = SITE_URL_ANDROID,
                onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, SITE_URL_ANDROID.toUri())) }
            )
            SettingsNavRow(
                label = stringResource(R.string.about_tour_label),
                summary = stringResource(R.string.about_tour_summary),
                onClick = onShowTour
            )
            SectionHeading(stringResource(R.string.about_licenses_title))
            LICENSES.forEach { entry -> LicenseRow(entry) }
        }
    }
}

@Composable
private fun VersionHeader() {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall)
        Text(
            stringResource(R.string.about_version, BuildConfig.VERSION_NAME),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            stringResource(R.string.about_license_line),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun LicenseRow(entry: LicenseEntry) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
        Text(entry.library, style = MaterialTheme.typography.bodyLarge)
        Text(
            entry.license,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SectionHeading(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .padding(top = 24.dp, bottom = 8.dp)
            .semantics { heading() }
    )
}

private const val SITE_URL = "https://github.com/bocan/bocan-music"
private const val SITE_URL_ANDROID = "https://github.com/bocan/bocan-music-android"

/** One dependency in the static licenses list. Names and licenses are proper nouns, not copy. */
private data class LicenseEntry(val library: String, val license: String)

/**
 * The static generated licenses list: every runtime dependency from
 * gradle/libs.versions.toml, regenerated by hand when the catalog changes.
 */
private val LICENSES = listOf(
    LicenseEntry("AndroidX (Core, Activity, Navigation, DataStore, Palette, Glance, WorkManager)", "Apache License 2.0"),
    LicenseEntry("Jetpack Compose (UI, Foundation, Material 3, Icons)", "Apache License 2.0"),
    LicenseEntry("AndroidX Room and SQLite", "Apache License 2.0"),
    LicenseEntry("AndroidX Media3 (ExoPlayer, Session)", "Apache License 2.0"),
    LicenseEntry("Media3 FFmpeg decoder (Jellyfin build)", "Apache License 2.0, with FFmpeg under LGPL 2.1 or later"),
    LicenseEntry("Kotlin and kotlinx (coroutines, serialization)", "Apache License 2.0"),
    LicenseEntry("OkHttp", "Apache License 2.0"),
    LicenseEntry("Coil", "Apache License 2.0"),
    LicenseEntry("Timber", "Apache License 2.0")
)
