package io.cloudcauldron.bocan.app.surfaces

import android.content.Intent
import io.cloudcauldron.bocan.app.R
import io.cloudcauldron.bocan.app.e2e.E2ETags
import io.cloudcauldron.bocan.app.fixtures.FixtureProfile
import io.cloudcauldron.bocan.app.settings.PodcastSettingsViewModel
import io.cloudcauldron.bocan.app.surfaces.Action.Tap
import io.cloudcauldron.bocan.app.surfaces.Action.Type
import io.cloudcauldron.bocan.app.surfaces.Postcondition.Flipped
import io.cloudcauldron.bocan.app.surfaces.Postcondition.Gone
import io.cloudcauldron.bocan.app.surfaces.Postcondition.IntentSent
import io.cloudcauldron.bocan.app.surfaces.Postcondition.Navigated
import io.cloudcauldron.bocan.app.surfaces.Postcondition.Selected
import io.cloudcauldron.bocan.app.surfaces.Postcondition.Text
import io.cloudcauldron.bocan.app.surfaces.Postcondition.Visible
import io.cloudcauldron.bocan.scrobble.providers.ProviderId
import org.junit.Test

/**
 * The settings root and every section but sync (SyncCrawl) and the equalizer (EqualizerCrawl):
 * every toggle flips, every picker sets, the token dialogs open and cancel, the About links
 * leave for the browser, and the tour reopens the welcome flow.
 */
class SettingsCrawl : CrawlTest(FixtureProfile.PairedLibrary) {
    @Test
    fun crawl() {
        crawl("settings", table(ctx))
        finishCrawl()
    }

    companion object {
        private val SETTINGS = Navigated("settings", E2ETags.Settings.SYNC)
        private const val SITE_URL = "https://github.com/bocan/bocan-music"
        private const val SITE_URL_ANDROID = "https://github.com/bocan/bocan-music-android"

        fun table(c: CrawlContext): List<Step> = playback() + podcasts(c) + scrobbling() + appearance() + about()

        private fun playback() = listOf(
            Step(E2ETags.Nav.SETTINGS, Tap, SETTINGS),
            Step(E2ETags.Settings.PLAYBACK, Tap, Navigated("playback settings", E2ETags.PlaybackSettings.RESUME_ON_RECONNECT)),
            Step(E2ETags.PlaybackSettings.RESUME_ON_RECONNECT, Tap, Flipped),
            Step(E2ETags.PlaybackSettings.EQUALIZER, Tap, Navigated("equalizer", E2ETags.Equalizer.MASTER)),
            Step(E2ETags.Common.BACK, Tap, Navigated("playback settings", E2ETags.PlaybackSettings.RESUME_ON_RECONNECT)),
            Step(E2ETags.Common.BACK, Tap, SETTINGS)
        )

        private fun podcasts(c: CrawlContext): List<Step> {
            val speeds = PodcastSettingsViewModel.SPEED_PRESETS.map { c.string(R.string.speed_value, it.toFloat()) }
            val skips = PodcastSettingsViewModel.SKIP_PRESETS.map { c.string(R.string.podcast_settings_seconds, it) }
            fun pick(tag: String, labels: List<String>) = labels.map { Step(tag, Tap, Selected(tag, it), row = it) }
            return listOf(Step(E2ETags.Settings.PODCASTS, Tap, Visible(E2ETags.PodcastSettings.SPEED_OPTION, speeds.first()))) +
                pick(E2ETags.PodcastSettings.SPEED_OPTION, speeds) +
                pick(E2ETags.PodcastSettings.SKIP_BACK_OPTION, skips) +
                pick(E2ETags.PodcastSettings.SKIP_FORWARD_OPTION, skips) +
                Step(E2ETags.Common.BACK, Tap, SETTINGS)
        }

        private fun scrobbling(): List<Step> {
            fun tokenDialog(provider: String) = listOf(
                Step(E2ETags.Scrobble.provider(provider, E2ETags.Scrobble.CONNECT), Tap, Visible(E2ETags.Scrobble.TOKEN_FIELD)),
                Step(E2ETags.Scrobble.TOKEN_FIELD, Type(TOKEN), Text(E2ETags.Scrobble.TOKEN_FIELD, TOKEN)),
                Step(E2ETags.Scrobble.TOKEN_CANCEL, Tap, Gone(E2ETags.Scrobble.TOKEN_FIELD))
            )
            return listOf(
                Step(E2ETags.Settings.SCROBBLING, Tap, Navigated("scrobbling", E2ETags.Scrobble.MASTER)),
                Step(E2ETags.Scrobble.MASTER, Tap, Flipped)
            ) + tokenDialog(ProviderId.LISTENBRAINZ) + tokenDialog(ProviderId.ROCKSKY) +
                Step(E2ETags.Common.BACK, Tap, SETTINGS)
        }

        private fun appearance() = listOf(
            Step(E2ETags.Settings.APPEARANCE, Tap, Navigated("appearance", E2ETags.Appearance.THEME_SYSTEM)),
            Step(E2ETags.Appearance.THEME_DARK, Tap, Selected(E2ETags.Appearance.THEME_DARK)),
            Step(E2ETags.Appearance.THEME_LIGHT, Tap, Selected(E2ETags.Appearance.THEME_LIGHT)),
            Step(E2ETags.Appearance.THEME_SYSTEM, Tap, Selected(E2ETags.Appearance.THEME_SYSTEM)),
            Step(E2ETags.Appearance.DYNAMIC_COLOR, Tap, Flipped),
            Step(E2ETags.Appearance.PURE_BLACK, Tap, Flipped),
            Step(E2ETags.Common.BACK, Tap, SETTINGS)
        )

        private fun about() = listOf(
            Step(E2ETags.Settings.ABOUT, Tap, Navigated("about", E2ETags.About.SITE)),
            Step(E2ETags.About.SITE, Tap, IntentSent(Intent.ACTION_VIEW, SITE_URL)),
            Step(E2ETags.About.SITE_ANDROID, Tap, IntentSent(Intent.ACTION_VIEW, SITE_URL_ANDROID)),
            Step(E2ETags.About.TOUR, Tap, Navigated("the welcome tour", E2ETags.Onboarding.GET_STARTED)),
            Step(E2ETags.Onboarding.SKIP, Tap, Gone(E2ETags.Onboarding.SKIP))
        )

        private const val TOKEN = "crawl-token"
    }
}
